package com.dothis.fintris.dutchpay.service;

import com.dothis.fintris.block.entity.Block;
import com.dothis.fintris.dutchpay.entity.DutchPay;
import com.dothis.fintris.dutchpay.entity.DutchPayTransaction;
import com.dothis.fintris.dutchpay.repository.DutchPayRepository;
import com.dothis.fintris.dutchpay.repository.DutchPayTransactionRepository;
import com.dothis.fintris.transaction.entity.Transaction;
import com.dothis.fintris.transaction.entity.TransactionDirection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// 더치페이의 내 몫과 블록에 돌려놓은 돈을 지금 거래 상태에 맞게 다시 맞춘다 (N-06)
// 만들기·수정·해제, 그리고 묶인 거래의 블록·금액이 바뀔 때(카테고리 수정, 취소·부분취소, 삭제) 부른다
// 호출하는 쪽의 트랜잭션 안에서 쓴다. 블록 잔액 충돌은 @Version으로 막는다
@Component
@RequiredArgsConstructor
public class DutchPayRecalculator {

    private final DutchPayRepository dutchPayRepository;
    private final DutchPayTransactionRepository dutchPayTransactionRepository;
    private final DutchPayShareCalculator shareCalculator;

    // 묶인 거래면 그 더치페이 전체를 다시 계산한다
    public void recalculateFor(Transaction transaction) {
        dutchPayTransactionRepository.findByTransaction_Id(transaction.getId())
                .ifPresent(link -> recalculate(dutchPayTransactionRepository.findAllByDutchPay_Id(link.getDutchPay().getId())));
    }

    // 취소된 거래는 0원으로 본다. 결제마다 (결제 금액 - 내 몫)을 지금 반영된 블록에 돌려놓는다
    public void recalculate(List<DutchPayTransaction> links) {
        List<DutchPayTransaction> payments = links.stream().filter(DutchPayRecalculator::isPayment).toList();
        long receivedTotal = links.stream().filter(link -> !isPayment(link))
                .mapToLong(link -> effectiveAmount(link.getTransaction())).sum();
        List<Long> shares = shareCalculator.myShares(
                payments.stream().map(link -> effectiveAmount(link.getTransaction())).toList(), receivedTotal);

        Map<Block, Long> deltas = new LinkedHashMap<>();
        for (int i = 0; i < payments.size(); i++) {
            DutchPayTransaction link = payments.get(i);
            long share = shares.get(i);
            long returned = effectiveAmount(link.getTransaction()) - share;
            Block block = link.getTransaction().getBlock();
            boolean returnable = returned > 0 && block != null && block.getDeletedAt() == null;
            undo(link, deltas);
            if (returnable) {
                deltas.merge(block, returned, Long::sum);
            }
            link.apply(share, returnable ? block : null, returnable ? returned : null);
        }
        apply(deltas);
    }

    // 해제: 돌려놓은 돈을 되돌리고 묶음을 지운다
    public void dissolve(DutchPay dutchPay, List<DutchPayTransaction> links) {
        Map<Block, Long> deltas = new LinkedHashMap<>();
        links.forEach(link -> undo(link, deltas));
        apply(deltas);
        dutchPayTransactionRepository.deleteAll(links);
        dutchPayRepository.delete(dutchPay);
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
