package com.dothis.fintris.dutchpay.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

// 받은 돈을 결제 금액 비율대로 나눠 결제마다 내 몫을 구한다 (N-06)
@Component
public class DutchPayShareCalculator {

    // payments: 결제 금액 목록. 반환값은 같은 순서의 내 몫
    public List<Long> myShares(List<Long> payments, long received) {
        long paidTotal = payments.stream().mapToLong(Long::longValue).sum();
        // 받은 돈이 결제보다 많으면 결제 합까지만 뺀다. 내 몫은 0원 밑으로 가지 않는다
        long deduct = Math.min(received, paidTotal);
        if (deduct <= 0) {
            return List.copyOf(payments);
        }

        List<Long> deductions = new ArrayList<>(payments.size());
        long[] remainders = new long[payments.size()];
        long distributed = 0;
        for (int i = 0; i < payments.size(); i++) {
            long scaled = Math.multiplyExact(payments.get(i), deduct);
            deductions.add(scaled / paidTotal);
            remainders[i] = scaled % paidTotal;
            distributed += scaled / paidTotal;
        }

        // 나누고 남은 원은 버린 소수점이 큰 결제부터 1원씩 더 뺀다(같으면 큰 결제 먼저).
        // 한 결제에 몰아주면 작은 결제의 내 몫이 0원 밑으로 갈 수 있다
        List<Integer> order = IntStream.range(0, payments.size()).boxed()
                .sorted(Comparator.<Integer>comparingLong(i -> remainders[i]).reversed()
                        .thenComparing(Comparator.<Integer>comparingLong(payments::get).reversed()))
                .toList();
        long left = deduct - distributed;
        for (int k = 0; k < left; k++) {
            int i = order.get(k);
            deductions.set(i, deductions.get(i) + 1);
        }

        return IntStream.range(0, payments.size())
                .mapToObj(i -> payments.get(i) - deductions.get(i))
                .toList();
    }
}
