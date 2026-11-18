package com.dothis.fintris.dutchpay.service;

import com.dothis.fintris.block.entity.Block;
import com.dothis.fintris.dutchpay.code.DutchPayErrorCode;
import com.dothis.fintris.dutchpay.dto.DutchPayRequest;
import com.dothis.fintris.dutchpay.dto.DutchPayResponse;
import com.dothis.fintris.dutchpay.entity.DutchPay;
import com.dothis.fintris.dutchpay.entity.DutchPayTransaction;
import com.dothis.fintris.dutchpay.repository.DutchPayRepository;
import com.dothis.fintris.dutchpay.repository.DutchPayTransactionRepository;
import com.dothis.fintris.global.exception.GeneralException;
import com.dothis.fintris.transaction.code.TransactionErrorCode;
import com.dothis.fintris.transaction.entity.Transaction;
import com.dothis.fintris.transaction.entity.TransactionDirection;
import com.dothis.fintris.transaction.repository.TransactionRepository;
import com.dothis.fintris.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

// 더치페이 (N-06). 거래는 그대로 두고 묶음에 내 몫만 남긴다.
// 받은 돈은 결제마다 (결제 금액 - 내 몫)만큼 그 결제가 반영된 블록에 돌려놓는다. 합은 받은 돈(결제 합까지)이다.
// 실제로 통장에 들어온 돈이라 불변식이 유지되고, 블록마다 내 몫만큼만 빠진 상태가 된다.
// 주의: 입금을 대기 자금으로 넣는 기능이 붙으면 더치페이에 묶인 입금은 건너뛰어야 한다(두 번 잡힘)
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DutchPayService {

    private final DutchPayRepository dutchPayRepository;
    private final DutchPayTransactionRepository dutchPayTransactionRepository;
    private final TransactionRepository transactionRepository;
    private final DutchPayShareCalculator shareCalculator;

    @Transactional
    public DutchPayResponse create(Long userId, DutchPayRequest request) {
        List<Transaction> payments = loadOwned(userId, request.paymentTransactionIds(), request.receivedTransactionIds(), true);
        List<Transaction> received = loadOwned(userId, request.receivedTransactionIds(), List.of(), false);
        checkNotLinked(payments, received);

        long receivedTotal = received.stream().mapToLong(Transaction::getAmount).sum();
        List<Long> shares = shareCalculator.myShares(payments.stream().map(Transaction::getAmount).toList(), receivedTotal);

        User user = payments.getFirst().getUser();
        DutchPay dutchPay = dutchPayRepository.save(DutchPay.builder().user(user).build());

        List<DutchPayTransaction> paymentLinks = new ArrayList<>();
        for (int i = 0; i < payments.size(); i++) {
            paymentLinks.add(paymentLink(dutchPay, payments.get(i), shares.get(i)));
        }
        List<DutchPayTransaction> links = new ArrayList<>(paymentLinks);
        for (Transaction transaction : received) {
            links.add(DutchPayTransaction.builder().dutchPay(dutchPay).transaction(transaction).build());
        }
        dutchPayTransactionRepository.saveAll(links);

        return toResponse(dutchPay, paymentLinks, received, receivedTotal);
    }

    // 받은 돈 중 이 결제 몫(결제 금액 - 내 몫)을 결제가 반영된 블록에 돌려놓는다. 블록에 반영되지 않은 결제는 뺀 적이 없어 건너뛴다
    private DutchPayTransaction paymentLink(DutchPay dutchPay, Transaction payment, long myShare) {
        long returned = payment.getAmount() - myShare;
        Block block = payment.getBlock();
        boolean returnable = returned > 0 && block != null && block.getDeletedAt() == null;
        if (returnable) {
            block.deposit(returned);
        }
        return DutchPayTransaction.builder()
                .dutchPay(dutchPay).transaction(payment).myShare(myShare)
                .block(returnable ? block : null).returnedAmount(returnable ? returned : null)
                .build();
    }

    // 같은 거래를 두 번 고르지 않았는지, 내 거래인지, 방향이 맞는지, 취소되지 않았는지 본다
    private List<Transaction> loadOwned(Long userId, List<Long> ids, List<Long> otherIds, boolean payment) {
        Set<Long> unique = new HashSet<>(ids);
        if (unique.size() != ids.size() || otherIds.stream().anyMatch(unique::contains)) {
            throw GeneralException.of(DutchPayErrorCode.DUTCH_PAY_DUPLICATE_TRANSACTION);
        }

        Map<Long, Transaction> byId = transactionRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Transaction::getId, Function.identity()));
        List<Transaction> result = new ArrayList<>(ids.size());
        for (Long id : ids) {
            Transaction transaction = byId.get(id);
            if (transaction == null) {
                throw GeneralException.of(TransactionErrorCode.TRANSACTION_NOT_FOUND);
            }
            if (!transaction.isOwnedBy(userId)) {
                throw GeneralException.of(TransactionErrorCode.TRANSACTION_FORBIDDEN);
            }
            if (transaction.isCanceled()) {
                throw GeneralException.of(DutchPayErrorCode.DUTCH_PAY_CANCELED_TRANSACTION);
            }
            TransactionDirection expected = payment ? TransactionDirection.OUT : TransactionDirection.IN;
            if (transaction.getDirection() != expected) {
                throw GeneralException.of(payment
                        ? DutchPayErrorCode.DUTCH_PAY_INVALID_PAYMENT
                        : DutchPayErrorCode.DUTCH_PAY_INVALID_RECEIVED);
            }
            result.add(transaction);
        }
        return result;
    }

    private void checkNotLinked(List<Transaction> payments, List<Transaction> received) {
        List<Long> ids = new ArrayList<>();
        payments.forEach(t -> ids.add(t.getId()));
        received.forEach(t -> ids.add(t.getId()));
        if (!dutchPayTransactionRepository.findAllByTransaction_IdIn(ids).isEmpty()) {
            throw GeneralException.of(DutchPayErrorCode.DUTCH_PAY_ALREADY_LINKED);
        }
    }

    private DutchPayResponse toResponse(DutchPay dutchPay, List<DutchPayTransaction> paymentLinks,
                                        List<Transaction> received, long receivedTotal) {
        List<DutchPayResponse.Payment> paymentItems = paymentLinks.stream()
                .map(link -> new DutchPayResponse.Payment(link.getTransaction().getId(), link.getTransaction().getAmount(),
                        link.getMyShare(), link.getBlock() == null ? null : new DutchPayResponse.ReturnedBlock(
                        link.getBlock().getId(), link.getBlock().getName(), link.getReturnedAmount())))
                .toList();
        List<DutchPayResponse.Received> receivedItems = received.stream()
                .map(t -> new DutchPayResponse.Received(t.getId(), t.getAmount()))
                .toList();
        long paidTotal = paymentLinks.stream().mapToLong(link -> link.getTransaction().getAmount()).sum();
        long myShareTotal = paymentLinks.stream().mapToLong(DutchPayTransaction::getMyShare).sum();
        long returnedTotal = paymentLinks.stream()
                .mapToLong(link -> link.getReturnedAmount() == null ? 0 : link.getReturnedAmount()).sum();
        return new DutchPayResponse(dutchPay.getId(), paymentItems, receivedItems,
                paidTotal, receivedTotal, myShareTotal, returnedTotal, dutchPay.getVersion());
    }
}
