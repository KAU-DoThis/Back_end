package com.dothis.fintris.dutchpay.service;

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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

// 더치페이 (N-06). 거래는 그대로 두고 묶음에 내 몫만 남긴다. 거래 목록에는 통장에 찍힌 금액 그대로 보인다
// 내 몫은 사용자가 정한다(없으면 결제 합 - 받은 돈). 1/N로 나누지 않는다
// 결제 - 내 몫은 그 결제가 반영된 블록에 돌려놓고(받은 돈까지만), 받은 돈의 나머지는 나눌 돈으로 넣는다
// 계산과 블록·나눌 돈 변경은 DutchPayRecalculator가 한다
// 주의: 입금을 대기 자금으로 넣는 기능이 붙으면 더치페이에 묶인 입금은 건너뛰어야 한다(두 번 잡힘)
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DutchPayService {

    private final DutchPayRepository dutchPayRepository;
    private final DutchPayTransactionRepository dutchPayTransactionRepository;
    private final TransactionRepository transactionRepository;
    private final DutchPayRecalculator recalculator;

    @Transactional
    public DutchPayResponse create(Long userId, DutchPayRequest request) {
        Selection selection = select(userId, request, null);
        DutchPay dutchPay = dutchPayRepository.save(DutchPay.builder().user(selection.payments().getFirst().getUser())
                .myShare(request.myShare()).build());

        List<DutchPayTransaction> links = new ArrayList<>();
        selection.all().forEach(transaction -> links.add(link(dutchPay, transaction)));
        dutchPayTransactionRepository.saveAll(links);
        recalculator.recalculate(links);
        return toResponse(dutchPay, links);
    }

    public DutchPayResponse get(Long userId, Long dutchPayId) {
        DutchPay dutchPay = owned(userId, dutchPayRepository.findById(dutchPayId));
        return toResponse(dutchPay, dutchPayTransactionRepository.findAllByDutchPay_Id(dutchPayId));
    }

    // 고른 결제·받은 돈을 통째로 바꾼다. 친구가 나중에 보낸 돈을 붙이거나 잘못 고른 거래를 뺄 때 쓴다
    @Transactional
    public DutchPayResponse update(Long userId, Long dutchPayId, DutchPayRequest request) {
        DutchPay dutchPay = owned(userId, dutchPayRepository.findForUpdate(dutchPayId));
        List<DutchPayTransaction> current = dutchPayTransactionRepository.findAllByDutchPay_Id(dutchPayId);
        Selection selection = select(userId, request, dutchPayId);
        // 내 몫을 안 보내면 사용자가 고쳐 둔 값을 그대로 둔다. 자동으로 되돌리기는 changeMyShare로 한다
        if (request.myShare() != null) {
            dutchPay.changeMyShare(request.myShare());
        }

        Set<Long> selected = selection.all().stream().map(Transaction::getId).collect(Collectors.toSet());
        Set<Long> kept = new HashSet<>();
        List<DutchPayTransaction> removed = new ArrayList<>();
        List<DutchPayTransaction> links = new ArrayList<>();
        for (DutchPayTransaction link : current) {
            if (selected.contains(link.getTransaction().getId())) {
                kept.add(link.getTransaction().getId());
                links.add(link);
            } else {
                removed.add(link);
            }
        }
        recalculator.remove(removed);
        List<DutchPayTransaction> added = selection.all().stream()
                .filter(transaction -> !kept.contains(transaction.getId()))
                .map(transaction -> link(dutchPay, transaction))
                .toList();
        dutchPayTransactionRepository.saveAll(added);
        links.addAll(added);

        recalculator.recalculate(links);
        return toResponse(dutchPay, links);
    }

    // 수정 버튼으로 내 몫만 고친다. null이면 서비스가 정한 값(결제 합 - 받은 돈)으로 되돌린다
    @Transactional
    public DutchPayResponse changeMyShare(Long userId, Long dutchPayId, Long myShare) {
        DutchPay dutchPay = owned(userId, dutchPayRepository.findForUpdate(dutchPayId));
        List<DutchPayTransaction> links = dutchPayTransactionRepository.findAllByDutchPay_Id(dutchPayId);
        long paidTotal = links.stream().map(DutchPayTransaction::getTransaction)
                .filter(t -> t.getDirection() == TransactionDirection.OUT && !t.isCanceled())
                .mapToLong(Transaction::getAmount).sum();
        if (myShare != null && myShare > paidTotal) {
            throw GeneralException.of(DutchPayErrorCode.DUTCH_PAY_INVALID_MY_SHARE);
        }
        dutchPay.changeMyShare(myShare);
        recalculator.recalculate(links);
        return toResponse(dutchPay, links);
    }

    // 해제: 블록에 돌려놓은 돈을 다시 빼고 묶음을 지운다. 결제는 다시 전체 금액이 쓴 돈이 된다
    @Transactional
    public void delete(Long userId, Long dutchPayId) {
        DutchPay dutchPay = owned(userId, dutchPayRepository.findForUpdate(dutchPayId));
        recalculator.dissolve(dutchPay, dutchPayTransactionRepository.findAllByDutchPay_Id(dutchPayId));
    }

    // 동기화에서 카드 취소·부분취소로 금액이 바뀐 거래. 묶인 더치페이가 있으면 다시 계산한다
    @Transactional
    public void recalculateFor(Long transactionId) {
        transactionRepository.findById(transactionId).ifPresent(recalculator::recalculateFor);
    }

    private DutchPay owned(Long userId, Optional<DutchPay> found) {
        DutchPay dutchPay = found.orElseThrow(() -> GeneralException.of(DutchPayErrorCode.DUTCH_PAY_NOT_FOUND));
        if (!dutchPay.isOwnedBy(userId)) {
            throw GeneralException.of(DutchPayErrorCode.DUTCH_PAY_FORBIDDEN);
        }
        return dutchPay;
    }

    private static DutchPayTransaction link(DutchPay dutchPay, Transaction transaction) {
        return DutchPayTransaction.builder().dutchPay(dutchPay).transaction(transaction).build();
    }

    private record Selection(List<Transaction> payments, List<Transaction> received) {
        List<Transaction> all() {
            List<Transaction> all = new ArrayList<>(payments);
            all.addAll(received);
            return all;
        }
    }

    // editingId: 수정 중인 더치페이. 그 더치페이에 이미 들어 있는 거래는 다시 골라도 된다
    private Selection select(Long userId, DutchPayRequest request, Long editingId) {
        List<Transaction> payments = loadOwned(userId, request.paymentTransactionIds(), request.receivedTransactionIds(), true);
        List<Transaction> received = loadOwned(userId, request.receivedTransactionIds(), List.of(), false);
        Selection selection = new Selection(payments, received);
        checkNotLinked(selection, editingId);
        long paidTotal = payments.stream().filter(t -> !t.isCanceled()).mapToLong(Transaction::getAmount).sum();
        if (request.myShare() != null && request.myShare() > paidTotal) {
            throw GeneralException.of(DutchPayErrorCode.DUTCH_PAY_INVALID_MY_SHARE);
        }
        return selection;
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

    private void checkNotLinked(Selection selection, Long editingId) {
        List<Long> ids = selection.all().stream().map(Transaction::getId).toList();
        boolean linkedElsewhere = dutchPayTransactionRepository.findAllByTransaction_IdIn(ids).stream()
                .anyMatch(link -> editingId == null || !editingId.equals(link.getDutchPay().getId()));
        if (linkedElsewhere) {
            throw GeneralException.of(DutchPayErrorCode.DUTCH_PAY_ALREADY_LINKED);
        }
    }

    // 취소된 거래는 합계에서 0원으로 본다
    private DutchPayResponse toResponse(DutchPay dutchPay, List<DutchPayTransaction> links) {
        List<DutchPayResponse.Payment> payments = new ArrayList<>();
        List<DutchPayResponse.Received> received = new ArrayList<>();
        long paidTotal = 0;
        long receivedTotal = 0;
        long myShareTotal = 0;
        long returnedTotal = 0;
        for (DutchPayTransaction link : links) {
            Transaction transaction = link.getTransaction();
            if (transaction.getDirection() == TransactionDirection.OUT) {
                payments.add(new DutchPayResponse.Payment(transaction.getId(), transaction.getAmount(), link.getMyShare(),
                        link.getBlock() == null ? null : new DutchPayResponse.ReturnedBlock(
                                link.getBlock().getId(), link.getBlock().getName(), link.getReturnedAmount())));
                paidTotal += transaction.isCanceled() ? 0 : transaction.getAmount();
                myShareTotal += link.getMyShare();
                returnedTotal += link.getReturnedAmount() == null ? 0 : link.getReturnedAmount();
            } else {
                received.add(new DutchPayResponse.Received(transaction.getId(), transaction.getAmount()));
                receivedTotal += transaction.isCanceled() ? 0 : transaction.getAmount();
            }
        }
        long buffer = dutchPay.getBufferAmount() == null ? 0 : dutchPay.getBufferAmount();
        long outstanding = Math.max(0, paidTotal - myShareTotal - receivedTotal);
        return new DutchPayResponse(dutchPay.getId(), payments, received,
                paidTotal, receivedTotal, myShareTotal, dutchPay.getMyShare() != null, returnedTotal, outstanding, buffer,
                dutchPay.getWaitingFund() == null ? null : dutchPay.getWaitingFund().getId(), dutchPay.getVersion());
    }
}
