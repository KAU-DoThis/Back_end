package com.dothis.fintris.transaction.service;

import com.dothis.fintris.transaction.entity.Transaction;
import com.dothis.fintris.transaction.entity.TransactionDirection;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

// 매달 같은 곳에 비슷한 금액이 나가는 출금(월세·통신비·구독료·반복 송금)을 고정지출 후보로 찾는다 (기능명세서 F-04-3·4)
// 규칙: 같은 가맹점·수취인, 금액 편차 10% 이내, 날짜 편차 3일 이내, 서로 다른 달 3번 이상
@Component
public class RecurringPaymentDetector {

    static final int MIN_MONTHS = 3;
    static final int MAX_DAY_GAP = 3;
    static final int AMOUNT_TOLERANCE_PERCENT = 10;

    public List<RecurringPayment> detect(List<Transaction> transactions) {
        Map<String, List<Transaction>> byPayee = new LinkedHashMap<>();
        for (Transaction transaction : transactions) {
            if (!isCandidate(transaction)) {
                continue;
            }
            byPayee.computeIfAbsent(payeeKey(transaction.getMerchantOrPayee()), key -> new ArrayList<>()).add(transaction);
        }
        List<RecurringPayment> found = new ArrayList<>();
        for (List<Transaction> group : byPayee.values()) {
            RecurringPayment payment = detectInGroup(group);
            if (payment != null) {
                found.add(payment);
            }
        }
        found.sort(Comparator.comparingInt(RecurringPayment::payDay));
        return found;
    }

    private boolean isCandidate(Transaction transaction) {
        return transaction.getDirection() == TransactionDirection.OUT && !transaction.isCanceled()
                && !transaction.isExcluded() && transaction.getMerchantOrPayee() != null
                && !transaction.getMerchantOrPayee().isBlank();
    }

    // 띄어쓰기만 다른 이름(스타벅스 강남점 / 스타벅스강남점)은 같은 곳으로 본다
    private String payeeKey(String merchantOrPayee) {
        return merchantOrPayee.replaceAll("\\s+", "");
    }

    private RecurringPayment detectInGroup(List<Transaction> group) {
        long medianAmount = median(group.stream().map(Transaction::getAmount).toList());
        List<Transaction> similar = group.stream().filter(t -> withinAmount(t.getAmount(), medianAmount)).toList();
        int payDay = (int) median(similar.stream().map(t -> (long) t.getOccurredAt().getDayOfMonth()).toList());

        // 달마다 납부일에 가장 가까운 한 건만 남긴다
        Map<YearMonth, Transaction> perMonth = new TreeMap<>();
        for (Transaction transaction : similar) {
            if (dayGap(transaction.getOccurredAt().getDayOfMonth(), payDay) > MAX_DAY_GAP) {
                continue;
            }
            perMonth.merge(YearMonth.from(transaction.getOccurredAt()), transaction, (kept, next) ->
                    dayGap(next.getOccurredAt().getDayOfMonth(), payDay)
                            < dayGap(kept.getOccurredAt().getDayOfMonth(), payDay) ? next : kept);
        }
        // 비슷한 금액이 한 달에 여러 번 나가는 곳(자주 가는 카페 등)은 고정지출이 아니다
        if (perMonth.size() < MIN_MONTHS || similar.size() > perMonth.size() + 1) {
            return null;
        }

        List<Transaction> monthly = new ArrayList<>(perMonth.values());
        Transaction latest = monthly.getLast();
        long min = monthly.stream().mapToLong(Transaction::getAmount).min().orElseThrow();
        long max = monthly.stream().mapToLong(Transaction::getAmount).max().orElseThrow();
        return new RecurringPayment(latest.getMerchantOrPayee(), payDay, latest.getAmount(), min, max,
                monthly.size(), latest.getOccurredAt().toLocalDate(),
                latest.getLinkedAccount() == null ? null : latest.getLinkedAccount().getId());
    }

    private boolean withinAmount(long amount, long median) {
        return Math.abs(amount - median) * 100 <= median * AMOUNT_TOLERANCE_PERCENT;
    }

    // 월말·월초에 걸치면(30일 ↔ 1일) 가까운 쪽으로 잰다
    static int dayGap(int day, int payDay) {
        int gap = Math.abs(day - payDay);
        return Math.min(gap, 31 - gap);
    }

    private long median(List<Long> values) {
        List<Long> sorted = values.stream().sorted().toList();
        return sorted.get((sorted.size() - 1) / 2);
    }

    // payAmount는 가장 최근에 낸 금액. 금액이 달마다 같으면 minAmount = maxAmount
    public record RecurringPayment(String payee, int payDay, long payAmount, long minAmount, long maxAmount,
                                   int months, LocalDate lastPaidOn, Long linkedAccountId) {
    }
}
