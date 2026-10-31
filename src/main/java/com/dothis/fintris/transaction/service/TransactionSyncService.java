package com.dothis.fintris.transaction.service;

import com.dothis.fintris.account.entity.LinkedAccount;
import com.dothis.fintris.account.entity.LinkedAccountType;
import com.dothis.fintris.account.exception.AccountLinkRejectedException;
import com.dothis.fintris.account.repository.LinkedAccountRepository;
import com.dothis.fintris.codef.client.CodefBankTransactionClient;
import com.dothis.fintris.codef.client.CodefBankTransactionClient.BankTransaction;
import com.dothis.fintris.codef.client.CodefBankTransactionClient.BankTransactionsResult;
import com.dothis.fintris.transaction.dto.AccountSyncResponse;
import com.dothis.fintris.transaction.entity.Transaction;
import com.dothis.fintris.transaction.entity.TransactionSource;
import com.dothis.fintris.transaction.entity.TransactionStatus;
import com.dothis.fintris.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

// CODEF 호출이 오래 걸릴 수 있어 DB 트랜잭션을 걸지 않는다. 저장이 중간에 끊겨도 다음 동기화가 겹치는 구간을 중복 키로 걸러낸다
@Service
@RequiredArgsConstructor
public class TransactionSyncService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int INITIAL_MONTHS = 3;
    private static final int MERCHANT_MAX_LENGTH = 255;
    private static final DateTimeFormatter KEY_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final TransactionRepository transactionRepository;
    private final LinkedAccountRepository linkedAccountRepository;
    private final CodefBankTransactionClient codefBankTransactionClient;

    // 사용자의 연동 은행 계좌를 차례로 동기화한다. 한 계좌가 실패하면 그 오류를 그대로 던진다
    public List<AccountSyncResponse> syncUser(Long userId) {
        return linkedAccountRepository.findAllByUser_IdAndDeletedAtIsNullOrderByCreatedAtAsc(userId).stream()
                .filter(account -> account.getType() == LinkedAccountType.BANK)
                .map(account -> new AccountSyncResponse(account.getId(), syncAccount(account)))
                .toList();
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
            throw new AccountLinkRejectedException(fetched.failureCode(), fetched.failureMessage());
        }

        Map<String, BankTransaction> byKey = keyed(account, fetched.transactions());
        Set<String> existing = byKey.isEmpty() ? Set.of()
                : new HashSet<>(transactionRepository.findExistingExternalKeys(byKey.keySet()));
        List<Transaction> fresh = byKey.entrySet().stream()
                .filter(entry -> !existing.contains(entry.getKey()))
                .map(entry -> toEntity(account, entry.getKey(), entry.getValue()))
                .toList();
        transactionRepository.saveAll(fresh);

        account.markSynced(now, latestBalance(fetched.transactions()));
        linkedAccountRepository.save(account);
        return fresh.size();
    }

    // 같은 초에 일시·금액·잔액이 모두 같은 거래는 응답 순서대로 번호를 붙여 구분한다
    private Map<String, BankTransaction> keyed(LinkedAccount account, List<BankTransaction> transactions) {
        Map<String, BankTransaction> byKey = new LinkedHashMap<>();
        for (BankTransaction transaction : transactions) {
            String base = externalKey(account, transaction);
            String key = base;
            for (int sequence = 2; byKey.containsKey(key); sequence++) {
                key = base + "#" + sequence;
            }
            byKey.put(key, transaction);
        }
        return byKey;
    }

    // CODEF 거래에는 고유 ID가 없다. 은행이 나중에 바꿀 수 있는 설명은 키에 넣지 않는다
    private String externalKey(LinkedAccount account, BankTransaction transaction) {
        return String.join("|", String.valueOf(account.getId()), transaction.occurredAt().format(KEY_TIME),
                transaction.direction().name(), String.valueOf(transaction.amount()),
                transaction.balanceAfter() == null ? "-" : String.valueOf(transaction.balanceAfter()));
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

    // 보낸분/받는분, 적요, 거래구분 순으로 처음 채워진 값을 쓴다
    private String merchantOrPayee(BankTransaction transaction) {
        return Stream.of(transaction.desc1(), transaction.desc3(), transaction.desc2())
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.length() > MERCHANT_MAX_LENGTH ? value.substring(0, MERCHANT_MAX_LENGTH) : value)
                .findFirst()
                .orElse(null);
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
