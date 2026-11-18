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
// 받은 돈은 결제 합까지 가장 큰 결제의 블록에 더한다. 실제로 통장에 들어온 돈이라 불변식이 유지된다.
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

        List<DutchPayTransaction> links = new ArrayList<>();
        for (int i = 0; i < payments.size(); i++) {
            links.add(DutchPayTransaction.builder()
                    .dutchPay(dutchPay).transaction(payments.get(i)).myShare(shares.get(i)).build());
        }
        for (Transaction transaction : received) {
            links.add(DutchPayTransaction.builder().dutchPay(dutchPay).transaction(transaction).build());
        }
        dutchPayTransactionRepository.saveAll(links);

        long paidTotal = payments.stream().mapToLong(Transaction::getAmount).sum();
        returnToBlock(dutchPay, payments, Math.min(receivedTotal, paidTotal));

        return toResponse(dutchPay, payments, shares, received, paidTotal, receivedTotal);
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

    // 가장 큰 결제(같으면 앞에 고른 것)의 블록에 더한다. 그 결제가 블록에 반영되지 않았으면 더하지 않는다
    private void returnToBlock(DutchPay dutchPay, List<Transaction> payments, long amount) {
        Transaction largest = payments.getFirst();
        for (Transaction payment : payments) {
            if (payment.getAmount() > largest.getAmount()) {
                largest = payment;
            }
        }
        Block block = largest.getBlock();
        if (amount <= 0 || block == null || block.getDeletedAt() != null) {
            return;
        }
        block.deposit(amount);
        dutchPay.returnTo(block, amount);
    }

    private DutchPayResponse toResponse(DutchPay dutchPay, List<Transaction> payments, List<Long> shares,
                                        List<Transaction> received, long paidTotal, long receivedTotal) {
        List<DutchPayResponse.Payment> paymentItems = new ArrayList<>();
        for (int i = 0; i < payments.size(); i++) {
            paymentItems.add(new DutchPayResponse.Payment(payments.get(i).getId(), payments.get(i).getAmount(), shares.get(i)));
        }
        List<DutchPayResponse.Received> receivedItems = received.stream()
                .map(t -> new DutchPayResponse.Received(t.getId(), t.getAmount()))
                .toList();
        Block block = dutchPay.getBlock();
        DutchPayResponse.ReturnedBlock returnedBlock = block == null ? null
                : new DutchPayResponse.ReturnedBlock(block.getId(), block.getName(), dutchPay.getReturnedAmount());
        long myShareTotal = shares.stream().mapToLong(Long::longValue).sum();
        return new DutchPayResponse(dutchPay.getId(), paymentItems, receivedItems,
                paidTotal, receivedTotal, myShareTotal, returnedBlock, dutchPay.getVersion());
    }
}
