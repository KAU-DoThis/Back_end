package com.dothis.fintris.transaction.service;

import com.dothis.fintris.block.entity.Block;
import com.dothis.fintris.block.repository.BlockRepository;
import com.dothis.fintris.category.repository.CategoryRepository;
import com.dothis.fintris.global.exception.GeneralException;
import com.dothis.fintris.home.service.HomeService;
import com.dothis.fintris.transaction.code.TransactionErrorCode;
import com.dothis.fintris.transaction.dto.ManualPreviewResponse;
import com.dothis.fintris.transaction.dto.ManualSaveRequest;
import com.dothis.fintris.transaction.dto.ManualSaveResponse;
import com.dothis.fintris.transaction.entity.Transaction;
import com.dothis.fintris.transaction.entity.TransactionDirection;
import com.dothis.fintris.transaction.entity.TransactionSource;
import com.dothis.fintris.transaction.entity.TransactionStatus;
import com.dothis.fintris.transaction.repository.TransactionRepository;
import com.dothis.fintris.user.entity.User;
import com.dothis.fintris.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ManualTransactionServiceTest {

    private static final Long USER_ID = 1L;

    @Mock TransactionRepository transactionRepository;
    @Mock BlockRepository blockRepository;
    @Mock CategoryRepository categoryRepository;
    @Mock UserRepository userRepository;
    @Mock BlockTargetResolver blockTargetResolver;
    @Mock BlockReflector blockReflector;
    @Mock HomeService homeService;
    @InjectMocks ManualTransactionService service;

    @Test
    @DisplayName("미리보기는 해석 결과와 차감될 블록을 보여 주고 저장하지 않는다")
    void preview() {
        Block living = Block.builder().id(1L).name("생활비").isDefault(true).build();
        when(userRepository.getReferenceById(USER_ID)).thenReturn(mock(User.class));
        when(blockRepository.findAllByUser_IdAndDeletedAtIsNullOrderByDisplayOrderAsc(USER_ID)).thenReturn(List.of(living));
        when(blockTargetResolver.resolve(any(), anyList(), isNull())).thenReturn(Optional.of(living));

        ManualPreviewResponse response = service.preview(USER_ID, "붕어빵 3000");

        assertThat(response.amount()).isEqualTo(3000);
        assertThat(response.description()).isEqualTo("붕어빵");
        assertThat(response.block()).isEqualTo(new ManualPreviewResponse.BlockItem(1L, "생활비", true));
        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("금액을 못 읽으면 MANUAL_PARSE_FAILED")
    void previewParseFailed() {
        assertThatThrownBy(() -> service.preview(USER_ID, "붕어빵"))
                .isInstanceOf(GeneralException.class)
                .extracting(e -> ((GeneralException) e).getCode())
                .isEqualTo(TransactionErrorCode.MANUAL_PARSE_FAILED);
    }

    @Test
    @DisplayName("저장하면 확정된 직접 입력 출금으로 남기고 블록 반영을 맞춘 뒤 오늘 남은 금액을 돌려준다")
    void save() {
        LocalDate yesterday = LocalDate.now(java.time.ZoneId.of("Asia/Seoul")).minusDays(1);
        LocalDateTime confirmedAt = LocalDateTime.of(2026, 1, 1, 0, 0);
        when(userRepository.getReferenceById(USER_ID)).thenReturn(mock(User.class));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(blockReflector.blocksConfirmedAt(USER_ID)).thenReturn(Optional.of(confirmedAt));
        when(homeService.todayRemaining(USER_ID)).thenReturn(40_400L);

        ManualSaveResponse response = service.save(USER_ID, new ManualSaveRequest(3000L, "붕어빵", yesterday, null, null));

        ArgumentCaptor<Transaction> saved = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(saved.capture());
        Transaction transaction = saved.getValue();
        assertThat(transaction.getSource()).isEqualTo(TransactionSource.MANUAL);
        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.CONFIRMED);
        assertThat(transaction.getDirection()).isEqualTo(TransactionDirection.OUT);
        assertThat(transaction.getLinkedAccount()).isNull();
        // 지난 날짜는 시각을 몰라 정오로 둔다
        assertThat(transaction.getOccurredAt()).isEqualTo(yesterday.atTime(LocalTime.NOON));
        verify(blockReflector).reconcile(transaction, confirmedAt);
        assertThat(response.todayRemaining()).isEqualTo(40_400L);
        assertThat(response.transaction().inputMethod()).isEqualTo("빠른 입력");
    }

    @Test
    @DisplayName("없는 카테고리로 저장하면 거래를 만들지 않는다")
    void saveUnknownCategory() {
        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.save(USER_ID,
                new ManualSaveRequest(3000L, "붕어빵", LocalDate.of(2026, 11, 1), 99L, null)))
                .isInstanceOf(GeneralException.class)
                .extracting(e -> ((GeneralException) e).getCode())
                .isEqualTo(TransactionErrorCode.TRANSACTION_CATEGORY_NOT_FOUND);
        verify(transactionRepository, never()).save(any());
    }
}
