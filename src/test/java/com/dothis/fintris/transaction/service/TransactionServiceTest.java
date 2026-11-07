package com.dothis.fintris.transaction.service;

import com.dothis.fintris.account.entity.LinkedAccount;
import com.dothis.fintris.account.entity.LinkedAccountType;
import com.dothis.fintris.category.entity.Category;
import com.dothis.fintris.category.entity.CategoryType;
import com.dothis.fintris.global.exception.GeneralException;
import com.dothis.fintris.transaction.code.TransactionErrorCode;
import com.dothis.fintris.transaction.dto.TransactionResponse;
import com.dothis.fintris.transaction.entity.ClassifiedBy;
import com.dothis.fintris.transaction.entity.Transaction;
import com.dothis.fintris.transaction.entity.TransactionDirection;
import com.dothis.fintris.transaction.entity.TransactionSource;
import com.dothis.fintris.transaction.entity.TransactionStatus;
import com.dothis.fintris.transaction.repository.TransactionRepository;
import com.dothis.fintris.user.entity.AuthProvider;
import com.dothis.fintris.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long TRANSACTION_ID = 101L;

    @Mock TransactionRepository transactionRepository;
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
}
