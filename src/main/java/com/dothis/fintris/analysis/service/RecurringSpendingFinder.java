package com.dothis.fintris.analysis.service;

import com.dothis.fintris.analysis.dto.SpendingAnalysisResponse.Recurring;
import com.dothis.fintris.transaction.entity.Transaction;
import com.dothis.fintris.transaction.service.RecurringPaymentDetector;
import com.dothis.fintris.transaction.service.RecurringPaymentDetector.RecurringPayment;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

// 소비 분석 반복 지출 (API 명세 v4 8장 recurring). 순서: 반복 소비 → 중복 구독 → 고정지출
// - REPEAT: 줄일 수 있는 카테고리 중 기간 전체 결제 횟수의 20% 이상, 4번 이상 쓴 카테고리
//   기능명세서 F-01의 "같은 가맹점 월 4회 이상"은 평범한 생활 소비까지 걸려서 카테고리 비중으로 바꿨다
//   필수 지출(식비·교통 등)은 서버가 판정하지 않고, 개인화된 판단은 에이전트(초불이)에 맡긴다
// - DUPLICATE_SUBSCRIPTION: 구독료 카테고리에 가맹점이 2곳 이상
// - FIXED: 고정지출 탐지(RecurringPaymentDetector)에 걸리고 이번 기간에 낸 출금
@Component
@RequiredArgsConstructor
public class RecurringSpendingFinder {

    static final int REPEAT_MIN_COUNT = 4;
    static final int REPEAT_MIN_SHARE_PERCENT = 20;
    // 배달/외식, 카페/간식, 쇼핑/의류, 문화/여가, 생활/생필품(편의점이 주로 여기로 들어간다)
    static final Set<String> REPEAT_CATEGORY_CODES = Set.of("DELIVERY_DINING", "CAFE", "SHOPPING", "LEISURE", "LIVING");
    private static final String SUBSCRIPTION_CODE = "SUBSCRIPTION";

    private final RecurringPaymentDetector recurringPaymentDetector;

    // history는 고정지출 판정에 쓰는 기간 앞 몇 달치를 포함한 쓴 돈 거래
    public List<Recurring> find(List<Transaction> history, LocalDate periodFrom) {
        List<RecurringPayment> fixed = recurringPaymentDetector.detect(history).stream()
                .filter(payment -> !payment.lastPaidOn().isBefore(periodFrom))
                .toList();
        Set<String> fixedPayees = fixed.stream().map(payment -> payeeKey(payment.payee())).collect(Collectors.toSet());
        Set<String> duplicatePayees = new HashSet<>();

        List<Transaction> inPeriod = history.stream()
                .filter(t -> !t.getOccurredAt().toLocalDate().isBefore(periodFrom))
                .toList();
        Map<String, List<Transaction>> byCategory = inPeriod.stream()
                .filter(t -> t.getCategory() != null && t.getMerchantOrPayee() != null)
                .collect(Collectors.groupingBy(t -> t.getCategory().getName(), LinkedHashMap::new, Collectors.toList()));

        List<Recurring> repeats = new ArrayList<>();
        List<Recurring> duplicates = new ArrayList<>();
        byCategory.forEach((name, transactions) -> {
            Map<String, List<Transaction>> byMerchant = transactions.stream()
                    .collect(Collectors.groupingBy(t -> payeeKey(t.getMerchantOrPayee())));
            long amount = transactions.stream().mapToLong(Transaction::getAmount).sum();
            if (SUBSCRIPTION_CODE.equals(transactions.getFirst().getCategory().getCode())) {
                if (byMerchant.size() >= 2) {
                    duplicatePayees.addAll(byMerchant.keySet());
                    String merchants = byMerchant.values().stream().map(list -> list.getFirst().getMerchantOrPayee())
                            .sorted().collect(Collectors.joining(" · "));
                    duplicates.add(new Recurring("DUPLICATE_SUBSCRIPTION", name, merchants + " 같이 구독 중", amount));
                }
                return;
            }
            if (!REPEAT_CATEGORY_CODES.contains(transactions.getFirst().getCategory().getCode())) {
                return;
            }
            // 고정지출로 잡힌 가맹점은 반복 소비로 다시 세지 않는다
            byMerchant.keySet().removeAll(fixedPayees);
            int count = byMerchant.values().stream().mapToInt(List::size).sum();
            if (count >= REPEAT_MIN_COUNT && count * 100L >= (long) inPeriod.size() * REPEAT_MIN_SHARE_PERCENT) {
                List<Transaction> top = byMerchant.values().stream().max(Comparator.comparingInt(List::size)).orElseThrow();
                long repeatAmount = byMerchant.values().stream().flatMap(List::stream).mapToLong(Transaction::getAmount).sum();
                repeats.add(new Recurring("REPEAT", name, repeatSummary(count, top), repeatAmount));
            }
        });
        repeats.sort(Comparator.comparingLong(Recurring::amount).reversed());

        List<Recurring> result = new ArrayList<>(repeats);
        result.addAll(duplicates);
        // 중복 구독에 이미 나온 구독은 고정지출로 다시 보여주지 않는다
        fixed.stream().filter(payment -> !duplicatePayees.contains(payeeKey(payment.payee())))
                .forEach(payment -> result.add(new Recurring("FIXED", payment.payee(),
                        "매월 " + payment.payDay() + "일", payment.payAmount())));
        return result;
    }

    // "15회 · 스타벅스 9회, 그 외 6회"
    private String repeatSummary(int total, List<Transaction> top) {
        String summary = total + "회 · " + top.getFirst().getMerchantOrPayee() + " " + top.size() + "회";
        return total > top.size() ? summary + ", 그 외 " + (total - top.size()) + "회" : summary;
    }

    // RecurringPaymentDetector와 같이 띄어쓰기만 다른 이름은 같은 곳으로 본다
    private String payeeKey(String merchantOrPayee) {
        return merchantOrPayee.replaceAll("\\s+", "");
    }
}
