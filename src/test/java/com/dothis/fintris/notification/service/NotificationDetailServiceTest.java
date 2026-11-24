package com.dothis.fintris.notification.service;

import com.dothis.fintris.block.repository.BlockRepository;
import com.dothis.fintris.global.exception.GeneralException;
import com.dothis.fintris.notification.code.NotificationDetailErrorCode;
import com.dothis.fintris.notification.entity.Notification;
import com.dothis.fintris.notification.entity.NotificationType;
import com.dothis.fintris.notification.repository.NotificationRepository;
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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationDetailServiceTest {

    private static final Long USER_ID = 1L;

    @Mock NotificationRepository notificationRepository;
    @Mock BlockRepository blockRepository;
    @Mock TransactionRepository transactionRepository;
    @InjectMocks NotificationDetailService service;

    private void given(NotificationType type, Long refId) {
        User user = User.builder().email("user@example.com").authProvider(AuthProvider.EMAIL).build();
        ReflectionTestUtils.setField(user, "id", USER_ID);
        Notification notification = Notification.builder().user(user).type(type).title("알림").refId(refId).build();
        when(notificationRepository.findById(301L)).thenReturn(Optional.of(notification));
    }

    @Test
    @DisplayName("소진 속도가 아닌 알림은 상세가 없다")
    void noDetailForOtherTypes() {
        given(NotificationType.REPORT, null);

        assertThatThrownBy(() -> service.getDetail(USER_ID, 301L))
                .isInstanceOf(GeneralException.class)
                .extracting(e -> ((GeneralException) e).getCode())
                .isEqualTo(NotificationDetailErrorCode.NOTIFICATION_NO_DETAIL);
    }

    @Test
    @DisplayName("블록이 지워졌으면 404")
    void blockGone() {
        given(NotificationType.BURN_RATE, 10L);
        when(blockRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getDetail(USER_ID, 301L))
                .isInstanceOf(GeneralException.class)
                .extracting(e -> ((GeneralException) e).getCode())
                .isEqualTo(NotificationDetailErrorCode.NOTIFICATION_BLOCK_GONE);
    }
}
