package com.dothis.fintris.transaction.service;

import com.dothis.fintris.account.entity.LinkedAccount;
import com.dothis.fintris.account.entity.LinkedAccountStatus;
import com.dothis.fintris.account.entity.LinkedAccountType;
import com.dothis.fintris.account.exception.AccountLinkRejectedException;
import com.dothis.fintris.account.repository.LinkedAccountRepository;
import com.dothis.fintris.codef.client.CodefBankTransactionClient;
import com.dothis.fintris.codef.client.CodefBankTransactionClient.BankTransaction;
import com.dothis.fintris.codef.client.CodefBankTransactionClient.BankTransactionsResult;
import com.dothis.fintris.transaction.entity.Transaction;
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
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// CODEF 호출이 오래 걸릴 수 있어 DB 트랜잭션을 걸지 않는다. 저장이 중간에 끊겨도 다음 동기화가 겹치는 구간을 중복 키로 걸러낸다
@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionSyncService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int INITIAL_MONTHS = 3;
    private static final int MERCHANT_MAX_LENGTH = 255;
    // 다시 인증해야 하는 실패. 지금까지 확인한 코드만 담았고, 그 밖의 실패는 일시 오류(ERROR)로 본다
    private static final Set<String> REAUTH_FAILURE_CODES = Set.of("CF-12800", "CF-12801", "CF-12824", "CF-03002");
    private static final DateTimeFormatter KEY_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final TransactionRepository transactionRepository;
    private final LinkedAccountRepository linkedAccountRepository;
    private final CodefBankTransactionClient codefBankTransactionClient;

    // 사용자의 연동 은행 계좌를 차례로 동기화한다. 한 계좌가 실패해도 나머지는 계속하고 실패 수를 센다
    public SyncSummary syncUser(Long userId) {
        List<LinkedAccount> banks = linkedAccountRepository.findAllByUser_IdAndDeletedAtIsNullOrderByCreatedAtAsc(userId).stream()
                .filter(account -> account.getType() == LinkedAccountType.BANK)
                .toList();
        int failed = 0;
        for (LinkedAccount account : banks) {
            try {
                syncAccount(account);
            } catch (RuntimeException e) {
                failed++;
                log.warn("거래내역 동기화 실패: institution={}", account.getInstitutionCode());
            }
        }
        return new SyncSummary(banks.size(), failed);
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
        String source = String.join("|", String.valueOf(linkedAccountId), occurredAt.format(KEY_TIME),
                String.valueOf(amount), desc3 == null ? "" : desc3.strip());
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

    // 응답이 과거순이라 뒤에서부터 잔액이 있는 첫 거래를 찾는다
    private Long latestBalance(List<BankTransaction> transactions) {
        for (int i = transactions.size() - 1; i >= 0; i--) {
            if (transactions.get(i).balanceAfter() != null) {
                return transactions.get(i).balanceAfter();
            }
        }
        return null;
    }
}
