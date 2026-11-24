package com.dothis.fintris.notification.service;

import com.dothis.fintris.global.exception.GeneralException;
import com.dothis.fintris.notification.code.NotificationErrorCode;
import com.dothis.fintris.notification.dto.NotificationListResponse;
import com.dothis.fintris.notification.entity.Notification;
import com.dothis.fintris.notification.entity.NotificationCategory;
import com.dothis.fintris.notification.entity.NotificationType;
import com.dothis.fintris.notification.repository.NotificationRepository;
import com.dothis.fintris.user.entity.AuthProvider;
import com.dothis.fintris.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.SliceImpl;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    private static final Long USER_ID = 1L;

    @Mock NotificationRepository notificationRepository;
    @InjectMocks NotificationService service;

    private User user(Long id) {
        User user = User.builder().email("user@example.com").authProvider(AuthProvider.EMAIL).build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Notification notification(long id, NotificationType type, String title, LocalDateTime createdAt) {
        Notification notification = Notification.builder().user(user(USER_ID)).type(type).title(title).build();
        ReflectionTestUtils.setField(notification, "id", id);
        ReflectionTestUtils.setField(notification, "createdAt", createdAt);
        return notification;
    }

    @Test
    @DisplayName("탭을 고르면 그 분류의 종류만 조회하고, 배지 수는 탭과 관계없이 분류별로 센다")
    void filtersByCategoryAndCountsUnread() {
        LocalDateTime now = LocalDateTime.now();
        when(notificationRepository.findByUser_IdAndTypeInOrderByCreatedAtDescIdDesc(eq(USER_ID), any(), any()))
                .thenReturn(new SliceImpl<>(List.of(
                        notification(301, NotificationType.BURN_RATE, "생활비가 평소보다 17% 빨리 줄고 있어요", now)),
                        PageRequest.of(0, 20), false));
        when(notificationRepository.findUnreadTypes(USER_ID)).thenReturn(List.of(
                NotificationType.BURN_RATE, NotificationType.FIXED_SHORTAGE, NotificationType.CONTEXT_QUESTION));

        NotificationListResponse response = service.getNotifications(USER_ID, NotificationCategory.WARNING, 0, 20);

        ArgumentCaptor<Collection<NotificationType>> types = ArgumentCaptor.captor();
        verify(notificationRepository).findByUser_IdAndTypeInOrderByCreatedAtDescIdDesc(eq(USER_ID), types.capture(), any());
        assertThat(types.getValue()).allMatch(type -> type.getCategory() == NotificationCategory.WARNING)
                .contains(NotificationType.BURN_RATE, NotificationType.FIXED_SHORTAGE);
        assertThat(response.counts()).containsEntry(NotificationCategory.WARNING, 2L)
                .containsEntry(NotificationCategory.QUESTION, 1L)
                .containsEntry(NotificationCategory.NEWS, 0L)
                .doesNotContainKey(NotificationCategory.ALL);
        NotificationListResponse.Item item = response.groups().getFirst().items().getFirst();
        assertThat(response.groups().getFirst().label()).isEqualTo("오늘");
        assertThat(item.badge()).isEqualTo("확인 필요");
        assertThat(item.read()).isFalse();
        assertThat(response.retentionDays()).isEqualTo(30);
    }

    @Test
    @DisplayName("전체 탭은 모든 종류를 조회한다")
    void allCategoryQueriesEveryType() {
        when(notificationRepository.findByUser_IdAndTypeInOrderByCreatedAtDescIdDesc(eq(USER_ID), any(), any()))
                .thenReturn(new SliceImpl<>(List.of(), PageRequest.of(0, 20), false));

        service.getNotifications(USER_ID, NotificationCategory.ALL, 0, 20);

        ArgumentCaptor<Collection<NotificationType>> types = ArgumentCaptor.captor();
        verify(notificationRepository).findByUser_IdAndTypeInOrderByCreatedAtDescIdDesc(eq(USER_ID), types.capture(), any());
        assertThat(types.getValue()).containsExactlyInAnyOrder(NotificationType.values());
    }

    @Test
    @DisplayName("오늘 · 이번 주(월요일부터) · 이전으로 묶는다")
    void groupLabel() {
        LocalDate wednesday = LocalDate.of(2026, 11, 25);
        assertThat(NotificationService.groupLabel(wednesday, wednesday)).isEqualTo("오늘");
        assertThat(NotificationService.groupLabel(LocalDate.of(2026, 11, 23), wednesday)).isEqualTo("이번 주");
        assertThat(NotificationService.groupLabel(LocalDate.of(2026, 11, 22), wednesday)).isEqualTo("이전");
    }

    @Test
    @DisplayName("읽음 처리는 처음 읽은 시각을 남긴다")
    void markRead() {
        Notification notification = notification(301, NotificationType.REPORT, "9월 리포트가 나왔어요", LocalDateTime.now());
        when(notificationRepository.findById(301L)).thenReturn(Optional.of(notification));

        service.markRead(USER_ID, 301L);

        assertThat(notification.isRead()).isTrue();
    }

    @Test
    @DisplayName("다른 사용자의 알림은 읽음 처리할 수 없다")
    void markReadForbidden() {
        Notification notification = Notification.builder().user(user(2L)).type(NotificationType.REPORT).title("리포트").build();
        when(notificationRepository.findById(301L)).thenReturn(Optional.of(notification));

        assertThatThrownBy(() -> service.markRead(USER_ID, 301L))
                .isInstanceOf(GeneralException.class)
                .extracting(e -> ((GeneralException) e).getCode())
                .isEqualTo(NotificationErrorCode.NOTIFICATION_FORBIDDEN);
    }
}
