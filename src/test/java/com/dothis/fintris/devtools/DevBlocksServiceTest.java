package com.dothis.fintris.devtools;

import com.dothis.fintris.block.repository.BlockRepository;
import com.dothis.fintris.global.exception.GeneralException;
import com.dothis.fintris.transaction.repository.TransactionRepository;
import com.dothis.fintris.transaction.service.BlockReflectionService;
import com.dothis.fintris.user.entity.UserSetting;
import com.dothis.fintris.user.repository.UserSettingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DevBlocksServiceTest {

    private static final Long USER_ID = 1L;
    private static final LocalDateTime CONFIRMED = LocalDateTime.of(2026, 11, 10, 15, 0);
    private static final LocalDateTime NOV_1 = LocalDateTime.of(2026, 11, 1, 0, 0);

    @Mock UserSettingRepository userSettingRepository;
    @Mock BlockRepository blockRepository;
    @Mock TransactionRepository transactionRepository;
    @Mock BlockReflectionService blockReflectionService;
    @Mock TransactionTemplate transactionTemplate;
    @InjectMocks DevBlocksService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void runCallbackInline() {
        lenient().when(transactionTemplate.execute(any()))
                .thenAnswer(invocation -> ((TransactionCallback<Object>) invocation.getArgument(0)).doInTransaction(null));
    }

    private UserSetting confirmedSetting() {
        UserSetting setting = UserSetting.builder().blocksConfirmed(true).build();
        setting.moveBlocksConfirmedAt(CONFIRMED);
        return setting;
    }

    @Test
    @DisplayName("확정 시각을 앞당기면 옮긴 시각부터 출금을 다시 맞추고, 바뀐 것만 센다")
    void moveEarlier() {
        UserSetting setting = confirmedSetting();
        when(userSettingRepository.findByUser_Id(USER_ID)).thenReturn(Optional.of(setting));
        when(transactionRepository.findOutgoingIdsSince(USER_ID, NOV_1)).thenReturn(List.of(10L, 11L, 12L));
        when(blockReflectionService.reconcileIfNeeded(10L, NOV_1)).thenReturn(true);
        when(blockReflectionService.reconcileIfNeeded(11L, NOV_1)).thenReturn(false);
        when(blockReflectionService.reconcileIfNeeded(12L, NOV_1)).thenReturn(true);

        DevBlocksConfirmedAtResponse response = service.moveBlocksConfirmedAt(USER_ID, NOV_1);

        assertThat(setting.getBlocksConfirmedAt()).isEqualTo(NOV_1);
        assertThat(response).isEqualTo(new DevBlocksConfirmedAtResponse(NOV_1, 2, 0));
    }

    @Test
    @DisplayName("블록 충돌이 3번 이어지면 그 거래는 건너뛴다")
    void skipsAfterConflicts() {
        when(userSettingRepository.findByUser_Id(USER_ID)).thenReturn(Optional.of(confirmedSetting()));
        when(transactionRepository.findOutgoingIdsSince(USER_ID, NOV_1)).thenReturn(List.of(10L));
        when(blockReflectionService.reconcileIfNeeded(10L, NOV_1))
                .thenThrow(new OptimisticLockingFailureException("conflict"));

        assertThat(service.moveBlocksConfirmedAt(USER_ID, NOV_1).skipped()).isEqualTo(1);
    }

    @Test
    @DisplayName("블록 확정 전이거나 미래 시각이면 막는다")
    void rejects() {
        when(userSettingRepository.findByUser_Id(USER_ID))
                .thenReturn(Optional.of(UserSetting.builder().blocksConfirmed(false).build()));

        assertThatThrownBy(() -> service.moveBlocksConfirmedAt(USER_ID, NOV_1))
                .isInstanceOf(GeneralException.class)
                .extracting(e -> ((GeneralException) e).getCode())
                .isEqualTo(DevToolsErrorCode.DEV_BLOCKS_NOT_CONFIRMED);
        assertThatThrownBy(() -> service.moveBlocksConfirmedAt(USER_ID, LocalDateTime.now().plusDays(1)))
                .extracting(e -> ((GeneralException) e).getCode())
                .isEqualTo(DevToolsErrorCode.DEV_CONFIRMED_AT_IN_FUTURE);
        verify(transactionRepository, never()).findOutgoingIdsSince(any(), any());
    }
}
