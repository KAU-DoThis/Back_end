package com.dothis.fintris.notification.service;

import com.dothis.fintris.block.entity.Block;
import com.dothis.fintris.block.entity.BlockType;
import com.dothis.fintris.block.repository.BlockRepository;
import com.dothis.fintris.notification.dto.BurnRateDetailResponse.Depletion;
import com.dothis.fintris.notification.entity.NotificationSetting;
import com.dothis.fintris.notification.entity.NotificationType;
import com.dothis.fintris.notification.repository.NotificationRepository;
import com.dothis.fintris.notification.repository.NotificationSettingRepository;
import com.dothis.fintris.transaction.repository.CategorySpent;
import com.dothis.fintris.transaction.repository.DayTotal;
import com.dothis.fintris.transaction.repository.TransactionRepository;
import com.dothis.fintris.user.entity.AuthProvider;
import com.dothis.fintris.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BurnRateDetectorTest {

    private static final Long USER_ID = 1L;

    @Mock BlockRepository blockRepository;
    @Mock TransactionRepository transactionRepository;
    @Mock NotificationRepository notificationRepository;
    @Mock NotificationSettingRepository notificationSettingRepository;
    @Mock NotificationService notificationService;
    @InjectMocks BurnRateDetector detector;

    private final LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
    private User user;
    private Block living;

    @BeforeEach
    void setUp() {
        user = User.builder().email("user@example.com").authProvider(AuthProvider.EMAIL).build();
        ReflectionTestUtils.setField(user, "id", USER_ID);
        living = Block.builder().user(user).type(BlockType.SPENDING).name("생활비").balance(950_000L)
                .startDate(today.minusDays(10)).dueDate(today.plusDays(17)).build();
        ReflectionTestUtils.setField(living, "id", 10L);
    }

    // 최근 7일 하루 70,000원(오늘 490,000원 한 번), 평소 하루 56,000원
    private void givenSpending(long recentTotal, long usualFourWeeks) {
        when(blockRepository.findAllByUser_IdAndTypeAndDeletedAtIsNullOrderByDisplayOrderAsc(USER_ID, BlockType.SPENDING))
                .thenReturn(List.of(living));
        when(transactionRepository.sumBlockSpentByCategory(eq(10L), any(), any()))
                .thenReturn(List.of(new CategorySpent(2L, "DELIVERY_DINING", "배달/외식", 7L, recentTotal)));
        when(transactionRepository.sumSpentByCategory(eq(USER_ID), any(), any()))
                .thenReturn(List.of(new CategorySpent(2L, "DELIVERY_DINING", "배달/외식", 20L, usualFourWeeks)));
        when(transactionRepository.sumBlockSpentByDay(eq(10L), any(), any()))
                .thenReturn(List.of(new DayTotal(today, recentTotal)));
    }

    @Test
    @DisplayName("평소보다 15% 이상 빠르면 블록을 대상으로 알림을 남긴다")
    void notifiesWhenFaster() {
        givenSpending(490_000L, 1_568_000L);

        detector.detect(USER_ID);

        verify(notificationService).notify(eq(user), eq(NotificationType.BURN_RATE),
                eq("생활비가 평소보다 25% 빨리 줄고 있어요"), any(), eq(10L));
    }

    @Test
    @DisplayName("평소보다 15% 미만이면 남기지 않는다")
    void skipsWhenSlightlyFaster() {
        givenSpending(441_000L, 1_568_000L); // 하루 63,000원 → 12.5%

        detector.detect(USER_ID);

        verify(notificationService, never()).notify(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("이번 블록 기간에 이미 남겼으면 다시 남기지 않는다")
    void skipsWhenAlreadyNotified() {
        when(blockRepository.findAllByUser_IdAndTypeAndDeletedAtIsNullOrderByDisplayOrderAsc(USER_ID, BlockType.SPENDING))
                .thenReturn(List.of(living));
        when(notificationRepository.existsByUser_IdAndTypeAndRefIdAndCreatedAtGreaterThanEqual(
                USER_ID, NotificationType.BURN_RATE, 10L, living.getStartDate().atStartOfDay())).thenReturn(true);

        detector.detect(USER_ID);

        verify(transactionRepository, never()).sumBlockSpentByCategory(anyLong(), any(), any());
    }

    @Test
    @DisplayName("소진 속도 경고를 꺼 두었으면 보지 않는다")
    void skipsWhenTurnedOff() {
        NotificationSetting setting = NotificationSetting.defaults(user);
        setting.update(true, true, setting.getContextFrequency(), LocalTime.of(23, 0), LocalTime.of(7, 0),
                false, true, true, true, false, LocalTime.of(8, 0), false, false);
        when(notificationSettingRepository.findByUser_Id(USER_ID)).thenReturn(Optional.of(setting));

        detector.detect(USER_ID);

        verify(blockRepository, never()).findAllByUser_IdAndTypeAndDeletedAtIsNullOrderByDisplayOrderAsc(any(), any());
    }

    @Test
    @DisplayName("제목은 블록 이름 받침에 맞춰 이/가를 붙이고, 본문은 바닥나는 날이나 평소 금액을 말한다")
    void texts() {
        assertThat(BurnRateDetector.title("생활비", 17)).isEqualTo("생활비가 평소보다 17% 빨리 줄고 있어요");
        assertThat(BurnRateDetector.title("용돈 통장", 20)).isEqualTo("용돈 통장이 평소보다 20% 빨리 줄고 있어요");
        assertThat(BurnRateDetector.body(68_000, 58_000, new Depletion(LocalDate.of(2026, 10, 28), 3)))
                .isEqualTo("최근 7일 하루 평균 68,000원. 이 속도면 28일쯤 바닥나요");
        assertThat(BurnRateDetector.body(68_000, 58_000, null))
                .isEqualTo("최근 7일 하루 평균 68,000원. 평소에는 58,000원이에요");
    }
}
