package com.dothis.fintris.notification.service;

import com.dothis.fintris.block.entity.Block;
import com.dothis.fintris.block.repository.BlockRepository;
import com.dothis.fintris.global.exception.GeneralException;
import com.dothis.fintris.notification.code.NotificationDetailErrorCode;
import com.dothis.fintris.notification.code.NotificationErrorCode;
import com.dothis.fintris.notification.dto.BurnRateDetailResponse;
import com.dothis.fintris.notification.dto.BurnRateDetailResponse.DayAmount;
import com.dothis.fintris.notification.entity.Notification;
import com.dothis.fintris.notification.entity.NotificationType;
import com.dothis.fintris.notification.repository.NotificationRepository;
import com.dothis.fintris.transaction.repository.CategorySpent;
import com.dothis.fintris.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// 이상 징후 상세 (v4 9장 13-1). 볼 때마다 지금 잔액·거래로 다시 계산한다(calculatedAt)
// TODO: FIXED_SHORTAGE 등 다른 경고 상세는 감지(F-09)를 붙일 때 추가한다
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationDetailService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final List<String> BURN_RATE_ACTIONS = List.of("FILL_FROM_OTHER", "CREATE_SAVING_GOAL");

    private final NotificationRepository notificationRepository;
    private final BlockRepository blockRepository;
    private final TransactionRepository transactionRepository;

    public BurnRateDetailResponse getDetail(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> GeneralException.of(NotificationErrorCode.NOTIFICATION_NOT_FOUND));
        if (!notification.isOwnedBy(userId)) {
            throw GeneralException.of(NotificationErrorCode.NOTIFICATION_FORBIDDEN);
        }
        if (notification.getType() != NotificationType.BURN_RATE || notification.getRefId() == null) {
            throw GeneralException.of(NotificationDetailErrorCode.NOTIFICATION_NO_DETAIL);
        }
        Block block = blockRepository.findByIdAndDeletedAtIsNull(notification.getRefId())
                .filter(found -> found.isOwnedBy(userId))
                .orElseThrow(() -> GeneralException.of(NotificationDetailErrorCode.NOTIFICATION_BLOCK_GONE));
        return burnRate(notification, block, LocalDate.now(KST));
    }

    private BurnRateDetailResponse burnRate(Notification notification, Block block, LocalDate today) {
        LocalDateTime recentFrom = today.minusDays(BurnRateCalculator.RECENT_DAYS - 1).atStartOfDay();
        LocalDateTime tomorrow = today.plusDays(1).atStartOfDay();
        LocalDateTime usualFrom = recentFrom.minusDays(BurnRateCalculator.USUAL_DAYS);

        Map<LocalDate, Long> spentByDate = new HashMap<>();
        transactionRepository.sumBlockSpentByDay(block.getId(), recentFrom, tomorrow)
                .forEach(day -> spentByDate.put(day.date(), day.total()));
        List<CategorySpent> recentInBlock = transactionRepository.sumBlockSpentByCategory(block.getId(), recentFrom, tomorrow);
        List<CategorySpent> usualAll = transactionRepository.sumSpentByCategory(block.getUser().getId(), usualFrom, recentFrom);

        Long usualDailyAvg = BurnRateCalculator.usualDailyAvg(recentInBlock, usualAll);
        List<DayAmount> series = BurnRateCalculator.series(spentByDate, today, usualDailyAvg);
        long dailyAvg = series.stream().mapToLong(DayAmount::amount).sum() / BurnRateCalculator.RECENT_DAYS;
        long remainingDays = block.getDueDate() == null ? 0 : Math.max(0, ChronoUnit.DAYS.between(today, block.getDueDate()) + 1);

        NotificationType type = notification.getType();
        return new BurnRateDetailResponse(
                notification.getId(), type, type.isUrgent(),
                new BurnRateDetailResponse.Block(block.getId(), block.getName(), block.getBalance(), remainingDays),
                BurnRateCalculator.headline(BurnRateCalculator.fasterRate(dailyAvg, usualDailyAvg), dailyAvg),
                new BurnRateDetailResponse.Recent7Days(dailyAvg, usualDailyAvg, series),
                BurnRateCalculator.depletion(block.getBalance(), dailyAvg, today, block.getDueDate()),
                BurnRateCalculator.safeDaily(block.getBalance(), remainingDays),
                BurnRateCalculator.topIncreases(recentInBlock, usualAll),
                BURN_RATE_ACTIONS,
                LocalDateTime.now(KST));
    }
}
