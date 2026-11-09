package com.dothis.fintris.transaction.service;

import com.dothis.fintris.account.code.AccountErrorCode;
import com.dothis.fintris.account.entity.LinkedAccount;
import com.dothis.fintris.account.repository.LinkedAccountRepository;
import com.dothis.fintris.category.entity.Category;
import com.dothis.fintris.category.repository.CategoryRepository;
import com.dothis.fintris.global.exception.GeneralException;
import com.dothis.fintris.transaction.code.TransactionErrorCode;
import com.dothis.fintris.transaction.dto.TransactionListFilter;
import com.dothis.fintris.transaction.dto.TransactionListResponse;
import com.dothis.fintris.transaction.dto.TransactionListResponse.Counts;
import com.dothis.fintris.transaction.dto.TransactionListResponse.Day;
import com.dothis.fintris.transaction.dto.TransactionListResponse.Item;
import com.dothis.fintris.transaction.dto.TransactionListResponse.Period;
import com.dothis.fintris.transaction.dto.TransactionListResponse.Summary;
import com.dothis.fintris.transaction.dto.TransactionResponse;
import com.dothis.fintris.transaction.dto.TransactionResponse.AccountItem;
import com.dothis.fintris.transaction.entity.Transaction;
import com.dothis.fintris.transaction.entity.TransactionStatus;
import com.dothis.fintris.transaction.repository.DayTotal;
import com.dothis.fintris.transaction.repository.TransactionRepository;
import com.dothis.fintris.transaction.repository.TransactionTotals;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TransactionService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final TransactionRepository transactionRepository;
    private final LinkedAccountRepository linkedAccountRepository;
    private final CategoryRepository categoryRepository;
    private final BlockReflector blockReflector;

    public TransactionResponse getTransaction(Long userId, Long transactionId) {
        return TransactionResponse.from(ownedTransaction(userId, transactionId));
    }

    // 카테고리가 바뀌면 연결된 블록이 달라질 수 있어 블록 반영을 다시 맞춘다. 블록 충돌이면 409 BLOCK_VERSION_CONFLICT
    // TODO: 개인 사전(수정한 가맹점 → 카테고리) 저장 위치가 정해지면 함께 저장한다
    @Transactional
    public TransactionResponse changeCategory(Long userId, Long transactionId, Long categoryId) {
        Transaction transaction = ownedTransaction(userId, transactionId);
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> GeneralException.of(TransactionErrorCode.TRANSACTION_CATEGORY_NOT_FOUND));
        transaction.changeCategoryByUser(category);
        blockReflector.reconcile(transaction, blockReflector.blocksConfirmedAt(userId).orElse(null));
        return TransactionResponse.from(transaction);
    }

    private Transaction ownedTransaction(Long userId, Long transactionId) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> GeneralException.of(TransactionErrorCode.TRANSACTION_NOT_FOUND));
        if (!transaction.isOwnedBy(userId)) {
            throw GeneralException.of(TransactionErrorCode.TRANSACTION_FORBIDDEN);
        }
        return transaction;
    }

    // 요약·건수는 계좌·블록 조건만 따르고, filter(확인 필요·임시)는 목록과 날짜별 합계에만 건다
    public TransactionListResponse getTransactions(Long userId, YearMonth month, Long linkedAccountId, Long blockId,
                                                   TransactionListFilter filter, int page, int size) {
        LocalDate today = LocalDate.now(KST);
        YearMonth target = month != null ? month : YearMonth.from(today);
        LocalDateTime from = target.atDay(1).atStartOfDay();
        LocalDateTime to = target.plusMonths(1).atDay(1).atStartOfDay();
        TransactionStatus status = filter.getStatus();

        AccountItem account = linkedAccountId == null ? null : AccountItem.from(ownedAccount(userId, linkedAccountId));

        TransactionTotals totals = transactionRepository.sumTotals(userId, from, to, linkedAccountId, blockId);
        LocalDate periodEnd = target.equals(YearMonth.from(today)) ? today : target.atEndOfMonth();
        Summary summary = new Summary(new Period(target.atDay(1), periodEnd), totals.spent(),
                totals.provisionalCount(), totals.income(), null, account);
        Counts counts = new Counts(totals.needsReviewCount(), totals.provisionalCount());

        Slice<Transaction> slice = transactionRepository.findPage(userId, from, to, linkedAccountId, blockId, status,
                PageRequest.of(page, size));
        List<Day> days = groupByDay(userId, slice.getContent(), linkedAccountId, blockId, status);

        return new TransactionListResponse(summary, null, counts, days, slice.hasNext());
    }

    private LinkedAccount ownedAccount(Long userId, Long linkedAccountId) {
        LinkedAccount account = linkedAccountRepository.findById(linkedAccountId)
                .orElseThrow(() -> GeneralException.of(AccountErrorCode.ACCOUNT_NOT_FOUND));
        if (!account.isOwnedBy(userId)) {
            throw GeneralException.of(AccountErrorCode.ACCOUNT_FORBIDDEN);
        }
        return account;
    }

    // 페이지에 담긴 거래를 최신 날짜부터 묶는다. 합계는 페이지가 아니라 그날 전체 기준이다
    private List<Day> groupByDay(Long userId, List<Transaction> transactions, Long linkedAccountId, Long blockId,
                                 TransactionStatus status) {
        if (transactions.isEmpty()) {
            return List.of();
        }
        Map<LocalDate, List<Item>> itemsByDate = new LinkedHashMap<>();
        for (Transaction transaction : transactions) {
            itemsByDate.computeIfAbsent(transaction.getOccurredAt().toLocalDate(), date -> new ArrayList<>())
                    .add(Item.of(TransactionResponse.from(transaction)));
        }

        // 최신순이라 마지막 거래가 가장 이른 날짜다
        LocalDate earliest = transactions.getLast().getOccurredAt().toLocalDate();
        LocalDate latest = transactions.getFirst().getOccurredAt().toLocalDate();
        Map<LocalDate, Long> totals = transactionRepository.sumByDay(userId, earliest.atStartOfDay(),
                        latest.plusDays(1).atStartOfDay(), linkedAccountId, blockId, status).stream()
                .collect(Collectors.toMap(DayTotal::date, DayTotal::total));

        return itemsByDate.entrySet().stream()
                .map(entry -> new Day(entry.getKey(), totals.getOrDefault(entry.getKey(), 0L), entry.getValue()))
                .toList();
    }
}
