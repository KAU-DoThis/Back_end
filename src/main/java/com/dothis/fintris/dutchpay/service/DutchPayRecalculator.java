package com.dothis.fintris.dutchpay.service;

import com.dothis.fintris.block.entity.Block;
import com.dothis.fintris.dutchpay.entity.DutchPay;
import com.dothis.fintris.dutchpay.entity.DutchPayTransaction;
import com.dothis.fintris.dutchpay.repository.DutchPayRepository;
import com.dothis.fintris.dutchpay.repository.DutchPayTransactionRepository;
import com.dothis.fintris.transaction.entity.Transaction;
import com.dothis.fintris.transaction.entity.TransactionDirection;
import com.dothis.fintris.waitingfund.entity.WaitingFund;
import com.dothis.fintris.waitingfund.entity.WaitingFundSource;
import com.dothis.fintris.waitingfund.repository.WaitingFundRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// 더치페이의 내 몫, 블록에 돌려놓은 돈, 나눌 돈에 넣은 돈을 지금 거래 상태에 맞게 다시 맞춘다 (N-06)
// 만들기·수정·해제, 그리고 묶인 거래의 블록·금액이 바뀔 때(카테고리 수정, 취소·부분취소, 삭제) 부른다
// 호출하는 쪽의 트랜잭션 안에서 쓴다. 블록 잔액 충돌은 @Version으로 막는다
@Component
@RequiredArgsConstructor
public class DutchPayRecalculator {

    static final String BUFFER_LABEL = "더치페이 남은 돈";
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("M월 d일");

    private final DutchPayRepository dutchPayRepository;
    private final DutchPayTransactionRepository dutchPayTransactionRepository;
    private final DutchPayShareCalculator shareCalculator;
    private final WaitingFundRepository waitingFundRepository;

    // 묶인 거래면 그 더치페이 전체를 다시 계산한다
    public void recalculateFor(Transaction transaction) {
        dutchPayTransactionRepository.findByTransaction_Id(transaction.getId())
                .ifPresent(link -> recalculate(dutchPayTransactionRepository.findAllByDutchPay_Id(link.getDutchPay().getId())));
    }

    // 내 몫 합 = 사용자가 정한 값(없으면 결제 합 - 받은 돈). 블록에는 결제 - 내 몫을 돌려놓되 받은 돈까지만,
    // 받은 돈에서 블록에 돌려놓고 남은 돈은 나눌 돈으로 넣는다. 받은 돈 = 블록에 돌려놓은 돈 + 나눌 돈이라 불변식이 유지된다
    // 취소된 거래는 0원으로 본다
    public void recalculate(List<DutchPayTransaction> links) {
        DutchPay dutchPay = links.getFirst().getDutchPay();
        // 금액이 같은 결제끼리 남는 1원이 조회 순서에 따라 옮겨 다니지 않게 거래 ID 순으로 둔다
        List<DutchPayTransaction> payments = links.stream().filter(DutchPayRecalculator::isPayment)
                .sorted(Comparator.comparing(link -> link.getTransaction().getId())).toList();
        List<Long> paid = payments.stream().map(link -> effectiveAmount(link.getTransaction())).toList();
        long paidTotal = paid.stream().mapToLong(Long::longValue).sum();
        long receivedTotal = links.stream().filter(link -> !isPayment(link))
                .mapToLong(link -> effectiveAmount(link.getTransaction())).sum();
        long myShareTotal = myShareTotal(dutchPay.getMyShare(), paidTotal, receivedTotal);

        // 결제마다 내 몫과 돌려놓을 돈을 결제 금액 비율로 나눈다. 아직 못 받은 돈은 돌려놓지 않는다
        List<Long> shares = shareCalculator.myShares(paid, paidTotal - myShareTotal);
        List<Long> toReturn = new ArrayList<>(payments.size());
        for (int i = 0; i < payments.size(); i++) {
            toReturn.add(paid.get(i) - shares.get(i));
        }
        long returnTotal = Math.min(paidTotal - myShareTotal, receivedTotal);
        List<Long> notReturned = shareCalculator.myShares(toReturn, returnTotal);

        Map<Block, Long> deltas = new LinkedHashMap<>();
        long returnedToBlocks = 0;
        for (int i = 0; i < payments.size(); i++) {
            DutchPayTransaction link = payments.get(i);
            long returned = toReturn.get(i) - notReturned.get(i);
            Block block = link.getTransaction().getBlock();
            boolean returnable = returned > 0 && block != null && block.getDeletedAt() == null;
            undo(link, deltas);
            if (returnable) {
                deltas.merge(block, returned, Long::sum);
                returnedToBlocks += returned;
            }
            link.apply(shares.get(i), returnable ? block : null, returnable ? returned : null);
        }
        apply(deltas);
        // 블록에 반영되지 않은 결제 몫도 나눌 돈으로 간다
        applyBuffer(dutchPay, receivedTotal - returnedToBlocks, payments);
    }

    // 사용자가 정한 내 몫은 0 ~ 결제 합 안으로 맞춘다 (결제가 취소돼 줄어들 수 있다)
    static long myShareTotal(Long chosen, long paidTotal, long receivedTotal) {
        if (chosen == null) {
            return Math.max(0, paidTotal - receivedTotal);
        }
        return Math.clamp(chosen, 0, paidTotal);
    }

    // 해제: 돌려놓은 돈과 나눌 돈에 넣은 돈을 되돌리고 묶음을 지운다
    public void dissolve(DutchPay dutchPay, List<DutchPayTransaction> links) {
        Map<Block, Long> deltas = new LinkedHashMap<>();
        links.forEach(link -> undo(link, deltas));
        apply(deltas);
        applyBuffer(dutchPay, 0, List.of());
        dutchPayTransactionRepository.deleteAll(links);
        dutchPayRepository.delete(dutchPay);
    }

    // 전에 넣은 금액과의 차이만 더하고 뺀다. 이미 블록으로 나눈 돈은 되돌릴 수 없어 0에서 멈춘다
    private void applyBuffer(DutchPay dutchPay, long buffer, List<DutchPayTransaction> payments) {
        long before = dutchPay.getBufferAmount() == null ? 0 : dutchPay.getBufferAmount();
        WaitingFund fund = dutchPay.getWaitingFund();
        long delta = buffer - before;
        if (delta > 0 && fund == null) {
            fund = waitingFundRepository.save(WaitingFund.builder()
                    .user(dutchPay.getUser())
                    .source(WaitingFundSource.OTHER)
                    .label(BUFFER_LABEL)
                    .description(description(payments))
                    .amount(delta)
                    .originalAmount(delta)
                    .build());
        } else if (delta > 0) {
            fund.deposit(delta);
        } else if (delta < 0 && fund != null) {
            fund.withdraw(Math.min(fund.getAmount(), -delta), LocalDateTime.now(KST));
        }
        dutchPay.applyBuffer(fund, buffer);
    }

    // 예: "10월 13일 결제 더치페이"
    private static String description(List<DutchPayTransaction> payments) {
        return payments.isEmpty() ? null
                : payments.getFirst().getTransaction().getOccurredAt().format(DAY) + " 결제 더치페이";
    }

    // 묶음에서 거래를 뺀다. 그 거래로 돌려놓은 돈을 되돌린 뒤 지운다. 남은 거래는 호출하는 쪽이 다시 계산한다
    public void remove(List<DutchPayTransaction> links) {
        Map<Block, Long> deltas = new HashMap<>();
        links.forEach(link -> undo(link, deltas));
        apply(deltas);
        dutchPayTransactionRepository.deleteAll(links);
    }

    // 거래를 지우기 전에 부른다. 남은 결제가 없으면 더치페이를 해제하고, 있으면 남은 거래로 다시 계산한다
    public void unlink(Transaction transaction) {
        dutchPayTransactionRepository.findByTransaction_Id(transaction.getId()).ifPresent(link -> {
            DutchPay dutchPay = link.getDutchPay();
            List<DutchPayTransaction> links = dutchPayTransactionRepository.findAllByDutchPay_Id(dutchPay.getId());
            List<DutchPayTransaction> rest = links.stream().filter(other -> other != link).toList();
            if (rest.stream().noneMatch(DutchPayRecalculator::isPayment)) {
                dissolve(dutchPay, links);
                return;
            }
            remove(List.of(link));
            recalculate(rest);
        });
    }

    private static boolean isPayment(DutchPayTransaction link) {
        return link.getTransaction().getDirection() == TransactionDirection.OUT;
    }

    private static long effectiveAmount(Transaction transaction) {
        return transaction.isCanceled() ? 0 : transaction.getAmount();
    }

    // 전에 돌려놓은 돈을 빼는 몫으로 모은다. 삭제된 블록은 남은 돈을 이미 나눌 돈으로 보냈으니 빼지 않는다
    private static void undo(DutchPayTransaction link, Map<Block, Long> deltas) {
        Block block = link.getBlock();
        if (block != null && link.getReturnedAmount() != null && block.getDeletedAt() == null) {
            deltas.merge(block, -link.getReturnedAmount(), Long::sum);
        }
        link.apply(link.getMyShare(), null, null);
    }

    // 블록마다 더하고 뺀 몫을 합쳐 한 번만 바꾼다. 빼는 쪽은 블록 반영(BlockReflector)과 같이 0에서 멈춘다
    private static void apply(Map<Block, Long> deltas) {
        deltas.forEach((block, delta) -> {
            if (delta > 0) {
                block.deposit(delta);
            } else if (delta < 0) {
                block.withdraw(Math.min(block.getBalance(), -delta));
            }
        });
    }
}
