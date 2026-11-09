package com.dothis.fintris.transaction.service;

import com.dothis.fintris.account.entity.LinkedAccount;
import com.dothis.fintris.account.code.AccountErrorCode;
import com.dothis.fintris.account.entity.LinkedAccountType;
import com.dothis.fintris.account.repository.LinkedAccountRepository;
import com.dothis.fintris.category.entity.Category;
import com.dothis.fintris.category.entity.CategoryType;
import com.dothis.fintris.category.repository.CategoryRepository;
import com.dothis.fintris.global.exception.GeneralException;
import com.dothis.fintris.transaction.code.TransactionErrorCode;
import com.dothis.fintris.transaction.dto.TransactionListFilter;
import com.dothis.fintris.transaction.dto.TransactionListResponse;
import com.dothis.fintris.transaction.dto.TransactionResponse;
import com.dothis.fintris.transaction.entity.ClassifiedBy;
import com.dothis.fintris.transaction.entity.Transaction;
import com.dothis.fintris.transaction.entity.TransactionDirection;
import com.dothis.fintris.transaction.entity.TransactionSource;
import com.dothis.fintris.transaction.entity.TransactionStatus;
import com.dothis.fintris.transaction.repository.DayTotal;
import com.dothis.fintris.transaction.repository.TransactionRepository;
import com.dothis.fintris.transaction.repository.TransactionTotals;
import com.dothis.fintris.user.entity.AuthProvider;
import com.dothis.fintris.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.SliceImpl;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long TRANSACTION_ID = 101L;

    @Mock TransactionRepository transactionRepository;
    @Mock LinkedAccountRepository linkedAccountRepository;
    @Mock CategoryRepository categoryRepository;
    @InjectMocks TransactionService service;

    private User user(Long id) {
        User user = User.builder().email("user@example.com").authProvider(AuthProvider.EMAIL).build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Category living() {
        Category category = Category.builder().code("LIVING").name("생활/생필품")
                .categoryType(CategoryType.EXPENSE).displayOrder(7).build();
        ReflectionTestUtils.setField(category, "id", 7L);
        return category;
    }

    private Transaction.TransactionBuilder<?, ?> transaction(User owner) {
        return Transaction.builder().user(owner).occurredAt(LocalDateTime.of(2026, 10, 13, 19, 22))
                .amount(38_000L).direction(TransactionDirection.OUT).merchantOrPayee("네이버페이")
                .source(TransactionSource.CODEF).status(TransactionStatus.CONFIRMED);
    }

    private void given(Transaction transaction) {
        ReflectionTestUtils.setField(transaction, "id", TRANSACTION_ID);
        when(transactionRepository.findById(TRANSACTION_ID)).thenReturn(Optional.of(transaction));
    }

    @Test
    @DisplayName("내 거래를 계좌·카테고리와 함께 돌려준다")
    void getTransaction() {
        LinkedAccount card = LinkedAccount.builder().user(user(USER_ID)).institutionCode("0306")
                .type(LinkedAccountType.CARD).connectedId("cid-1").accountNumberLast4("1234").build();
        ReflectionTestUtils.setField(card, "id", 3L);
        given(transaction(user(USER_ID)).linkedAccount(card).category(living())
                .classifiedBy(ClassifiedBy.AUTO).confidence(new BigDecimal("0.950")).build());

        TransactionResponse response = service.getTransaction(USER_ID, TRANSACTION_ID);

        assertThat(response.transactionId()).isEqualTo(TRANSACTION_ID);
        assertThat(response.linkedAccount())
                .isEqualTo(new TransactionResponse.AccountItem(3L, "신한카드", "1234"));
        assertThat(response.category())
                .isEqualTo(new TransactionResponse.CategoryItem(7L, "LIVING", "생활/생필품"));
        assertThat(response.categoryUncertain()).isFalse();
        assertThat(response.block()).isNull();
        assertThat(response.inputMethod()).isNull();
    }

    @Test
    @DisplayName("자동 분류 신뢰도가 0.7보다 낮으면 카테고리가 불확실하다")
    void lowConfidenceIsUncertain() {
        given(transaction(user(USER_ID)).category(living())
                .classifiedBy(ClassifiedBy.AUTO).confidence(new BigDecimal("0.690")).build());

        assertThat(service.getTransaction(USER_ID, TRANSACTION_ID).categoryUncertain()).isTrue();
    }

    @Test
    @DisplayName("사용자가 고친 분류는 신뢰도와 상관없이 확실하다")
    void userClassifiedIsCertain() {
        given(transaction(user(USER_ID)).category(living())
                .classifiedBy(ClassifiedBy.USER).confidence(new BigDecimal("0.100")).build());

        assertThat(service.getTransaction(USER_ID, TRANSACTION_ID).categoryUncertain()).isFalse();
    }

    @Test
    @DisplayName("빠른 입력 거래는 계좌 없이 입력 방식을 표시한다")
    void manualTransaction() {
        given(transaction(user(USER_ID)).source(TransactionSource.MANUAL).build());

        TransactionResponse response = service.getTransaction(USER_ID, TRANSACTION_ID);

        assertThat(response.linkedAccount()).isNull();
        assertThat(response.inputMethod()).isEqualTo("빠른 입력");
    }

    @Test
    @DisplayName("다른 사용자의 거래면 403")
    void forbidden() {
        given(transaction(user(2L)).build());

        assertThatThrownBy(() -> service.getTransaction(USER_ID, TRANSACTION_ID))
                .isInstanceOf(GeneralException.class)
                .extracting(e -> ((GeneralException) e).getCode())
                .isEqualTo(TransactionErrorCode.TRANSACTION_FORBIDDEN);
    }

    @Test
    @DisplayName("없는 거래면 404")
    void notFound() {
        when(transactionRepository.findById(TRANSACTION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getTransaction(USER_ID, TRANSACTION_ID))
                .isInstanceOf(GeneralException.class)
                .extracting(e -> ((GeneralException) e).getCode())
                .isEqualTo(TransactionErrorCode.TRANSACTION_NOT_FOUND);
    }

    private static final YearMonth OCTOBER = YearMonth.of(2026, 10);
    private static final LocalDateTime OCT_1 = LocalDateTime.of(2026, 10, 1, 0, 0);
    private static final LocalDateTime NOV_1 = LocalDateTime.of(2026, 11, 1, 0, 0);

    private Transaction at(long id, LocalDateTime occurredAt) {
        Transaction transaction = transaction(user(USER_ID)).occurredAt(occurredAt).build();
        ReflectionTestUtils.setField(transaction, "id", id);
        return transaction;
    }

    @Test
    @DisplayName("목록은 최신 날짜부터 묶고 날짜별 합계와 요약을 붙인다")
    void getTransactions() {
        Transaction late = at(3L, LocalDateTime.of(2026, 10, 14, 20, 0));
        Transaction early = at(2L, LocalDateTime.of(2026, 10, 14, 9, 0));
        Transaction older = at(1L, LocalDateTime.of(2026, 10, 10, 12, 0));
        when(transactionRepository.sumTotals(USER_ID, OCT_1, NOV_1, null, null))
                .thenReturn(new TransactionTotals(588_000L, 30_000L, 1L, 2L));
        when(transactionRepository.findPage(eq(USER_ID), eq(OCT_1), eq(NOV_1), isNull(), isNull(), isNull(), any()))
                .thenReturn(new SliceImpl<>(List.of(late, early, older), PageRequest.of(0, 3), true));
        when(transactionRepository.sumByDay(USER_ID, LocalDateTime.of(2026, 10, 10, 0, 0),
                LocalDateTime.of(2026, 10, 15, 0, 0), null, null, null))
                .thenReturn(List.of(new DayTotal(LocalDate.of(2026, 10, 14), 15_300L),
                        new DayTotal(LocalDate.of(2026, 10, 10), 30_800L)));

        TransactionListResponse response = service.getTransactions(USER_ID, OCTOBER, null, null,
                TransactionListFilter.ALL, 0, 3);

        assertThat(response.summary().period())
                .isEqualTo(new TransactionListResponse.Period(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)));
        assertThat(response.summary().spent()).isEqualTo(588_000L);
        assertThat(response.summary().income()).isEqualTo(30_000L);
        assertThat(response.summary().account()).isNull();
        assertThat(response.counts()).isEqualTo(new TransactionListResponse.Counts(2L, 1L));
        assertThat(response.days()).extracting(TransactionListResponse.Day::date)
                .containsExactly(LocalDate.of(2026, 10, 14), LocalDate.of(2026, 10, 10));
        assertThat(response.days()).extracting(TransactionListResponse.Day::dayTotal).containsExactly(15_300L, 30_800L);
        assertThat(response.days().getFirst().items()).extracting(item -> item.transaction().transactionId())
                .containsExactly(3L, 2L);
        assertThat(response.hasNext()).isTrue();
        assertThat(response.pendingQuestion()).isNull();
    }

    @Test
    @DisplayName("filter는 목록과 날짜별 합계에만 걸고 요약에는 걸지 않는다")
    void filterAppliesToListOnly() {
        Transaction review = at(1L, LocalDateTime.of(2026, 10, 10, 12, 0));
        when(transactionRepository.sumTotals(USER_ID, OCT_1, NOV_1, null, null))
                .thenReturn(new TransactionTotals(0L, 0L, 0L, 1L));
        when(transactionRepository.findPage(eq(USER_ID), eq(OCT_1), eq(NOV_1), isNull(), isNull(),
                eq(TransactionStatus.NEEDS_REVIEW), any()))
                .thenReturn(new SliceImpl<>(List.of(review), PageRequest.of(0, 30), false));
        when(transactionRepository.sumByDay(any(), any(), any(), any(), any(), eq(TransactionStatus.NEEDS_REVIEW)))
                .thenReturn(List.of());

        TransactionListResponse response = service.getTransactions(USER_ID, OCTOBER, null, null,
                TransactionListFilter.NEEDS_REVIEW, 0, 30);

        assertThat(response.days()).hasSize(1);
        assertThat(response.days().getFirst().dayTotal()).isZero();
    }

    @Test
    @DisplayName("거래가 없으면 날짜별 합계를 조회하지 않는다")
    void emptyPage() {
        when(transactionRepository.sumTotals(any(), any(), any(), any(), any()))
                .thenReturn(new TransactionTotals(0L, 0L, 0L, 0L));
        when(transactionRepository.findPage(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new SliceImpl<>(List.of(), PageRequest.of(0, 30), false));

        TransactionListResponse response = service.getTransactions(USER_ID, OCTOBER, null, null,
                TransactionListFilter.ALL, 0, 30);

        assertThat(response.days()).isEmpty();
        assertThat(response.hasNext()).isFalse();
        verify(transactionRepository, never()).sumByDay(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("계좌로 조회하면 요약에 계좌 정보를 넣는다")
    void byAccount() {
        LinkedAccount bank = LinkedAccount.builder().user(user(USER_ID)).institutionCode("0004")
                .type(LinkedAccountType.BANK).connectedId("cid-1").accountNumberLast4("5678").build();
        ReflectionTestUtils.setField(bank, "id", 3L);
        when(linkedAccountRepository.findById(3L)).thenReturn(Optional.of(bank));
        when(transactionRepository.sumTotals(USER_ID, OCT_1, NOV_1, 3L, null))
                .thenReturn(new TransactionTotals(10_000L, 0L, 0L, 0L));
        when(transactionRepository.findPage(any(), any(), any(), eq(3L), any(), any(), any()))
                .thenReturn(new SliceImpl<>(List.of(), PageRequest.of(0, 30), false));

        TransactionListResponse response = service.getTransactions(USER_ID, OCTOBER, 3L, null,
                TransactionListFilter.ALL, 0, 30);

        assertThat(response.summary().account()).isEqualTo(new TransactionResponse.AccountItem(3L, "국민은행", "5678"));
        assertThat(response.summary().spent()).isEqualTo(10_000L);
    }

    @Test
    @DisplayName("다른 사용자의 계좌로 조회하면 403")
    void byOthersAccount() {
        LinkedAccount others = LinkedAccount.builder().user(user(2L)).institutionCode("0004")
                .type(LinkedAccountType.BANK).connectedId("cid-2").build();
        when(linkedAccountRepository.findById(3L)).thenReturn(Optional.of(others));

        assertThatThrownBy(() -> service.getTransactions(USER_ID, OCTOBER, 3L, null, TransactionListFilter.ALL, 0, 30))
                .isInstanceOf(GeneralException.class)
                .extracting(e -> ((GeneralException) e).getCode())
                .isEqualTo(AccountErrorCode.ACCOUNT_FORBIDDEN);
        verify(transactionRepository, never()).findPage(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("카테고리를 고치면 사용자 분류로 바뀌고 불확실 표시가 사라진다")
    void changeCategory() {
        given(transaction(user(USER_ID)).category(living())
                .classifiedBy(ClassifiedBy.AUTO).confidence(new BigDecimal("0.400")).build());
        Category cafe = Category.builder().code("CAFE").name("카페/간식")
                .categoryType(CategoryType.EXPENSE).displayOrder(3).build();
        ReflectionTestUtils.setField(cafe, "id", 3L);
        when(categoryRepository.findById(3L)).thenReturn(Optional.of(cafe));

        TransactionResponse response = service.changeCategory(USER_ID, TRANSACTION_ID, 3L);

        assertThat(response.category()).isEqualTo(new TransactionResponse.CategoryItem(3L, "CAFE", "카페/간식"));
        assertThat(response.classifiedBy()).isEqualTo(ClassifiedBy.USER);
        assertThat(response.categoryUncertain()).isFalse();
    }

    @Test
    @DisplayName("없는 카테고리로 고치면 404")
    void changeCategoryNotFound() {
        given(transaction(user(USER_ID)).build());
        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.changeCategory(USER_ID, TRANSACTION_ID, 99L))
                .isInstanceOf(GeneralException.class)
                .extracting(e -> ((GeneralException) e).getCode())
                .isEqualTo(TransactionErrorCode.TRANSACTION_CATEGORY_NOT_FOUND);
    }

    @Test
    @DisplayName("다른 사용자의 거래는 카테고리를 고칠 수 없다")
    void changeCategoryForbidden() {
        given(transaction(user(2L)).build());

        assertThatThrownBy(() -> service.changeCategory(USER_ID, TRANSACTION_ID, 3L))
                .isInstanceOf(GeneralException.class)
                .extracting(e -> ((GeneralException) e).getCode())
                .isEqualTo(TransactionErrorCode.TRANSACTION_FORBIDDEN);
        verify(categoryRepository, never()).findById(any());
    }
}
