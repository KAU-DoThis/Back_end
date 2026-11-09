package com.dothis.fintris.transaction.service;

import com.dothis.fintris.account.entity.LinkedAccount;
import com.dothis.fintris.account.entity.LinkedAccountStatus;
import com.dothis.fintris.account.entity.LinkedAccountType;
import com.dothis.fintris.account.exception.AccountLinkRejectedException;
import com.dothis.fintris.account.repository.LinkedAccountRepository;
import com.dothis.fintris.codef.client.CodefBankTransactionClient;
import com.dothis.fintris.codef.client.CodefCardApprovalClient;
import com.dothis.fintris.codef.client.CodefCardApprovalClient.ApprovalStatus;
import com.dothis.fintris.codef.client.CodefCardApprovalClient.CardApproval;
import com.dothis.fintris.codef.client.CodefCardApprovalClient.CardApprovalsResult;
import com.dothis.fintris.codef.client.CodefBankTransactionClient.BankTransaction;
import com.dothis.fintris.codef.client.CodefBankTransactionClient.BankTransactionsResult;
import com.dothis.fintris.transaction.entity.Transaction;
import com.dothis.fintris.transaction.entity.TransactionDirection;
import com.dothis.fintris.transaction.entity.TransactionSource;
import com.dothis.fintris.transaction.entity.TransactionStatus;
import com.dothis.fintris.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

// CODEF 호출이 오래 걸릴 수 있어 DB 트랜잭션을 걸지 않는다. 저장이 중간에 끊겨도 다음 동기화가 겹치는 구간을 중복 키로 걸러낸다
@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionSyncService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int INITIAL_MONTHS = 3;
    private static final int MERCHANT_MAX_LENGTH = 255;
    private static final int MERCHANT_TYPE_MAX_LENGTH = 100;
    // 다시 인증해야 하는 실패. 지금까지 확인한 코드만 담았고, 그 밖의 실패는 일시 오류(ERROR)로 본다
    private static final Set<String> REAUTH_FAILURE_CODES = Set.of("CF-12800", "CF-12801", "CF-12824", "CF-03002");
    private static final DateTimeFormatter KEY_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final TransactionRepository transactionRepository;
    private final LinkedAccountRepository linkedAccountRepository;
    private final CodefBankTransactionClient codefBankTransactionClient;
    private final CodefCardApprovalClient codefCardApprovalClient;
    private final CheckCardDuplicateMatcher checkCardDuplicateMatcher;

    // 은행 계좌는 하나씩, 카드는 카드사 단위로 동기화한다. 하나가 실패해도 나머지는 계속하고 실패한 계좌·카드 수를 센다
    public SyncSummary syncUser(Long userId) {
        List<LinkedAccount> accounts = linkedAccountRepository.findAllByUser_IdAndDeletedAtIsNullOrderByCreatedAtAsc(userId);
        // 동기화가 lastSyncedAt을 바꾸기 전에 이번에 조회할 가장 이른 날을 정해 둔다 (체크카드 중복 판정 범위)
        LocalDate checkFrom = accounts.stream().anyMatch(account -> account.getLastSyncedAt() == null)
                ? LocalDate.now(KST).minusMonths(INITIAL_MONTHS)
                : accounts.stream().map(LinkedAccount::getLastSyncedAt).min(LocalDateTime::compareTo)
                        .map(at -> at.toLocalDate().minusDays(1)).orElse(LocalDate.now(KST));
        int failed = 0;
        for (LinkedAccount bank : accounts.stream().filter(account -> account.getType() == LinkedAccountType.BANK).toList()) {
            try {
                syncAccount(bank);
            } catch (RuntimeException e) {
                failed++;
                log.warn("거래내역 동기화 실패: institution={}", bank.getInstitutionCode());
            }
        }
        Map<String, List<LinkedAccount>> cardsByInstitution = accounts.stream()
                .filter(account -> account.getType() == LinkedAccountType.CARD)
                .collect(Collectors.groupingBy(LinkedAccount::getInstitutionCode, LinkedHashMap::new, Collectors.toList()));
        for (List<LinkedAccount> cards : cardsByInstitution.values()) {
            try {
                syncCardInstitution(cards);
            } catch (RuntimeException e) {
                failed += cards.size();
                log.warn("카드 승인내역 동기화 실패: institution={}", cards.getFirst().getInstitutionCode());
            }
        }
        boolean hasBank = accounts.stream().anyMatch(account -> account.getType() == LinkedAccountType.BANK);
        if (hasBank && !cardsByInstitution.isEmpty()) {
            try {
                excludeCheckCardDuplicates(userId, checkFrom);
            } catch (RuntimeException e) {
                log.warn("체크카드 중복 판정 실패");
            }
        }
        return new SyncSummary(accounts.size(), failed);
    }

    // 체크카드 결제가 카드·은행에 둘 다 찍혀 쓴 돈이 두 번 세지지 않게 은행 쪽 출금을 소비 제외한다
    // TODO: 블록 반영(F-21)이 붙으면 제외한 은행 출금이 블록에서 빠졌던 금액도 되돌린다
    void excludeCheckCardDuplicates(Long userId, LocalDate from) {
        List<Transaction> duplicates = checkCardDuplicateMatcher.findBankDuplicates(
                transactionRepository.findOutgoingSince(userId, from.atStartOfDay()));
        duplicates.forEach(Transaction::excludeFromSpending);
        if (!duplicates.isEmpty()) {
            transactionRepository.saveAll(duplicates);
        }
    }

    // 같은 카드사의 카드들은 connectedId가 같아 한 번에 조회한다. 새로 저장한 거래 수를 돌려준다
    public int syncCardInstitution(List<LinkedAccount> cards) {
        LinkedAccount first = cards.getFirst();
        LocalDateTime now = LocalDateTime.now(KST);
        LocalDate end = now.toLocalDate();
        // 한 장이라도 처음이면 3개월, 아니면 가장 오래된 동기화 하루 전부터
        LocalDate start = cards.stream().anyMatch(card -> card.getLastSyncedAt() == null)
                ? end.minusMonths(INITIAL_MONTHS)
                : cards.stream().map(LinkedAccount::getLastSyncedAt).min(LocalDateTime::compareTo)
                        .orElseThrow().toLocalDate().minusDays(1);

        CardApprovalsResult fetched = codefCardApprovalClient.fetchApprovals(first.getConnectedId(),
                first.getInstitutionCode(), start, end);
        if (!fetched.isSuccess()) {
            cards.forEach(card -> markFailed(card, fetched.failureCode()));
            throw new AccountLinkRejectedException(fetched.failureCode(), fetched.failureMessage());
        }

        Map<String, CardApproval> byKey = keyedApprovals(first.getInstitutionCode(), fetched.approvals());
        Map<String, Transaction> existing = byKey.isEmpty() ? Map.of()
                : transactionRepository.findAllByUser_IdAndExternalKeyIn(first.getUser().getId(), byKey.keySet()).stream()
                        .collect(Collectors.toMap(Transaction::getExternalKey, Function.identity()));
        List<Transaction> toSave = new ArrayList<>();
        int created = 0;
        for (Map.Entry<String, CardApproval> entry : byKey.entrySet()) {
            Transaction saved = existing.get(entry.getKey());
            if (saved == null) {
                toSave.add(toCardEntity(cardFor(cards, entry.getValue().cardNumber()), entry.getKey(), entry.getValue()));
                created++;
            } else if (applyCancel(saved, entry.getValue())) {
                toSave.add(saved);
            }
        }
        transactionRepository.saveAll(toSave);

        // 카드는 잔액이 없어 동기화 시각만 갱신한다
        cards.forEach(card -> card.markSynced(now, null));
        linkedAccountRepository.saveAll(cards);
        return created;
    }

    // 처음이면 최근 3개월, 이후에는 마지막 동기화 하루 전부터 가져온다. 새로 저장한 거래 수를 돌려준다
    public int syncAccount(LinkedAccount account) {
        if (account.getType() != LinkedAccountType.BANK || account.getAccountNumber() == null) {
            return 0;
        }
        LocalDateTime now = LocalDateTime.now(KST);
        LocalDate end = now.toLocalDate();
        LocalDate start = account.getLastSyncedAt() == null
                ? end.minusMonths(INITIAL_MONTHS)
                : account.getLastSyncedAt().toLocalDate().minusDays(1);

        BankTransactionsResult fetched = codefBankTransactionClient.fetchTransactions(account.getConnectedId(),
                account.getInstitutionCode(), account.getAccountNumber(), start, end);
        if (!fetched.isSuccess()) {
            markFailed(account, fetched.failureCode());
            throw new AccountLinkRejectedException(fetched.failureCode(), fetched.failureMessage());
        }

        Map<String, BankTransaction> byKey = keyed(account, fetched.transactions());
        Set<String> existing = byKey.isEmpty() ? Set.of()
                : new HashSet<>(transactionRepository.findExistingExternalKeys(account.getUser().getId(), byKey.keySet()));
        List<Transaction> fresh = byKey.entrySet().stream()
                .filter(entry -> !existing.contains(entry.getKey()))
                .map(entry -> toEntity(account, entry.getKey(), entry.getValue()))
                .toList();
        transactionRepository.saveAll(fresh);

        // 응답의 현재 잔액이 없으면 마지막 거래의 거래 후 잔액을 쓴다
        Long balance = fetched.currentBalance() != null ? fetched.currentBalance() : latestBalance(fetched.transactions());
        account.markSynced(now, balance);
        linkedAccountRepository.save(account);
        return fresh.size();
    }

    // 인증 문제면 재연동 안내(EXPIRED), 그 밖이면 ERROR로 표시한다. 다음 동기화가 성공하면 ACTIVE로 돌아간다
    private void markFailed(LinkedAccount account, String failureCode) {
        LinkedAccountStatus status = REAUTH_FAILURE_CODES.contains(failureCode)
                ? LinkedAccountStatus.EXPIRED : LinkedAccountStatus.ERROR;
        if (account.getStatus() != status) {
            account.markSyncFailed(status);
            linkedAccountRepository.save(account);
        }
    }

    // 같은 초에 키가 같은 거래는 응답 순서대로 번호를 붙여 구분한다
    private Map<String, BankTransaction> keyed(LinkedAccount account, List<BankTransaction> transactions) {
        Map<String, BankTransaction> byKey = new LinkedHashMap<>();
        for (BankTransaction transaction : transactions) {
            String base = bankExternalKey(account.getId(), transaction.occurredAt(), transaction.amount(), transaction.desc3());
            String key = base;
            for (int sequence = 2; byKey.containsKey(key); sequence++) {
                key = base + "#" + sequence;
            }
            byKey.put(key, transaction);
        }
        return byKey;
    }

    // CODEF 거래에는 고유 ID가 없다. v5 규칙: 은행 = 계좌 + 거래일시 + 금액 + 적요(desc3)의 해시
    static String bankExternalKey(Long linkedAccountId, LocalDateTime occurredAt, long amount, String desc3) {
        return sha256(String.join("|", String.valueOf(linkedAccountId), occurredAt.format(KEY_TIME),
                String.valueOf(amount), desc3 == null ? "" : desc3.strip()));
    }

    private static String sha256(String source) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 쓸 수 없습니다.", e);
        }
    }

    private Transaction toEntity(LinkedAccount account, String externalKey, BankTransaction transaction) {
        return Transaction.builder()
                .user(account.getUser())
                .linkedAccount(account)
                .occurredAt(transaction.occurredAt())
                .amount(transaction.amount())
                .direction(transaction.direction())
                .merchantOrPayee(merchantOrPayee(transaction))
                .balanceAfter(transaction.balanceAfter())
                .source(TransactionSource.CODEF)
                .status(TransactionStatus.CONFIRMED)
                .externalKey(externalKey)
                .build();
    }

    // v5 규칙: 은행 거래의 가맹점·수취인은 적요(desc3)만 쓴다
    // TODO: 수취인이 사람 이름이면 마스킹(김OO)해 저장한다 — 사람 이름 판별 기준이 정해지면 붙인다
    private String merchantOrPayee(BankTransaction transaction) {
        String value = transaction.desc3();
        if (value == null || value.isBlank()) {
            return null;
        }
        value = value.strip();
        return value.length() > MERCHANT_MAX_LENGTH ? value.substring(0, MERCHANT_MAX_LENGTH) : value;
    }

    // 거절된 승인은 결제되지 않아 저장하지 않는다. 승인번호가 없으면 키가 겹칠 수 있어 응답 순서대로 번호를 붙인다
    private Map<String, CardApproval> keyedApprovals(String institutionCode, List<CardApproval> approvals) {
        Map<String, CardApproval> byKey = new LinkedHashMap<>();
        for (CardApproval approval : approvals) {
            if (approval.status() == ApprovalStatus.REJECTED) {
                continue;
            }
            String base = cardExternalKey(institutionCode, approval);
            String key = base;
            for (int sequence = 2; byKey.containsKey(key); sequence++) {
                key = base + "#" + sequence;
            }
            byKey.put(key, approval);
        }
        return byKey;
    }

    // v5 규칙: 카드 = 기관 + 승인번호 + 승인일시. 취소도 같은 키로 와서 기존 거래를 고친다
    static String cardExternalKey(String institutionCode, CardApproval approval) {
        String occurredAt = approval.occurredAt().format(KEY_TIME);
        String source = approval.approvalNo() != null
                ? String.join("|", "CARD", institutionCode, approval.approvalNo(), occurredAt)
                : String.join("|", "CARD", institutionCode, String.valueOf(approval.cardNumber()), occurredAt,
                        String.valueOf(approval.amount()), String.valueOf(approval.merchantName()));
        return sha256(source);
    }

    // 이미 저장한 승인이 취소·부분취소로 바뀌었으면 고친다. 바뀐 게 있으면 true
    private boolean applyCancel(Transaction saved, CardApproval approval) {
        long remaining = remainingAmount(approval);
        if ((approval.status() == ApprovalStatus.CANCELED || remaining <= 0) && !saved.isCanceled()) {
            saved.cancel();
            return true;
        }
        if (approval.status() == ApprovalStatus.PARTIAL_CANCELED && remaining > 0 && saved.getAmount() != remaining) {
            saved.changeAmount(remaining);
            return true;
        }
        return false;
    }

    private long remainingAmount(CardApproval approval) {
        return approval.status() == ApprovalStatus.PARTIAL_CANCELED
                ? approval.amount() - approval.cancelAmount() : approval.amount();
    }

    // 승인내역의 카드번호로 연동 카드를 찾는다. 가림 방식이 달라 못 찾으면 뒤 4자리, 그래도 없으면 첫 카드
    private LinkedAccount cardFor(List<LinkedAccount> cards, String cardNumber) {
        if (cardNumber != null) {
            for (LinkedAccount card : cards) {
                if (cardNumber.equals(card.getAccountNumber())) {
                    return card;
                }
            }
            String digits = cardNumber.replaceAll("\\D", "");
            String last4 = digits.length() >= 4 ? digits.substring(digits.length() - 4) : null;
            List<LinkedAccount> sameLast4 = cards.stream()
                    .filter(card -> last4 != null && last4.equals(card.getAccountNumberLast4()))
                    .toList();
            if (sameLast4.size() == 1) {
                return sameLast4.getFirst();
            }
        }
        return cards.getFirst();
    }

    private Transaction toCardEntity(LinkedAccount card, String externalKey, CardApproval approval) {
        long remaining = remainingAmount(approval);
        return Transaction.builder()
                .user(card.getUser())
                .linkedAccount(card)
                .occurredAt(approval.occurredAt())
                .amount(remaining > 0 ? remaining : approval.amount())
                .direction(TransactionDirection.OUT)
                .merchantOrPayee(truncate(approval.merchantName(), MERCHANT_MAX_LENGTH))
                .merchantType(truncate(approval.merchantType(), MERCHANT_TYPE_MAX_LENGTH))
                .source(TransactionSource.CODEF)
                .status(TransactionStatus.CONFIRMED)
                .isCanceled(approval.status() == ApprovalStatus.CANCELED || remaining <= 0)
                .externalKey(externalKey)
                .build();
    }

    private String truncate(String value, int maxLength) {
        return value == null || value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    // 응답이 과거순이라 마지막 거래가 가장 최신이다. 그 잔액이 없으면 앞 거래 잔액은 이미 지난 값이라 쓰지 않는다
    private Long latestBalance(List<BankTransaction> transactions) {
        return transactions.isEmpty() ? null : transactions.getLast().balanceAfter();
    }
}
