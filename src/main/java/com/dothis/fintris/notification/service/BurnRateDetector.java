package com.dothis.fintris.notification.service;

import com.dothis.fintris.block.entity.Block;
import com.dothis.fintris.block.entity.BlockType;
import com.dothis.fintris.block.repository.BlockRepository;
import com.dothis.fintris.notification.dto.BurnRateDetailResponse.DayAmount;
import com.dothis.fintris.notification.dto.BurnRateDetailResponse.Depletion;
import com.dothis.fintris.notification.entity.NotificationSetting;
import com.dothis.fintris.notification.entity.NotificationType;
import com.dothis.fintris.notification.repository.NotificationRepository;
import com.dothis.fintris.notification.repository.NotificationSettingRepository;
import com.dothis.fintris.transaction.repository.CategorySpent;
import com.dothis.fintris.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// 사용형 블록 소진 속도 감지 (F-09). 동기화가 끝날 때마다 돌린다
// 최근 7일 하루 평균이 평소보다 15% 이상 빠르면 알림함에 남긴다. 같은 블록은 블록 기간마다 한 번만
// TODO: 블록 상태(F-11 주의·위험)가 계산되면 "상태가 나빠질 때만 다시 보낸다"로 바꾼다
@Component
@RequiredArgsConstructor
public class BurnRateDetector {

    static final int THRESHOLD_RATE = 15; // v4 1장 제안 카드 기준
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d일");

    private final BlockRepository blockRepository;
    private final TransactionRepository transactionRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationSettingRepository notificationSettingRepository;
    private final NotificationService notificationService;

    @Transactional
    public void detect(Long userId) {
        boolean enabled = notificationSettingRepository.findByUser_Id(userId)
                .map(NotificationSetting::isBurnRateEnabled)
                .orElse(true);
        if (!enabled) {
            return;
        }
        LocalDate today = LocalDate.now(KST);
        for (Block block : blockRepository.findAllByUser_IdAndTypeAndDeletedAtIsNullOrderByDisplayOrderAsc(
                userId, BlockType.SPENDING)) {
            if (inPeriod(block, today) && !alreadyNotified(userId, block)) {
                check(block, today);
            }
        }
    }

    private boolean inPeriod(Block block, LocalDate today) {
        return block.getStartDate() != null && block.getDueDate() != null
                && !today.isBefore(block.getStartDate()) && !today.isAfter(block.getDueDate());
    }

    private boolean alreadyNotified(Long userId, Block block) {
        return notificationRepository.existsByUser_IdAndTypeAndRefIdAndCreatedAtGreaterThanEqual(
                userId, NotificationType.BURN_RATE, block.getId(), block.getStartDate().atStartOfDay());
    }

    private void check(Block block, LocalDate today) {
        LocalDateTime recentFrom = today.minusDays(BurnRateCalculator.RECENT_DAYS - 1).atStartOfDay();
        LocalDateTime tomorrow = today.plusDays(1).atStartOfDay();
        List<CategorySpent> recentInBlock = transactionRepository.sumBlockSpentByCategory(block.getId(), recentFrom, tomorrow);
        if (recentInBlock.isEmpty()) {
            return;
        }
        List<CategorySpent> usualAll = transactionRepository.sumSpentByCategory(block.getUser().getId(),
                recentFrom.minusDays(BurnRateCalculator.USUAL_DAYS), recentFrom);
        Long usualDailyAvg = BurnRateCalculator.usualDailyAvg(recentInBlock, usualAll);

        Map<LocalDate, Long> spentByDate = new HashMap<>();
        transactionRepository.sumBlockSpentByDay(block.getId(), recentFrom, tomorrow)
                .forEach(day -> spentByDate.put(day.date(), day.total()));
        long dailyAvg = BurnRateCalculator.series(spentByDate, today, usualDailyAvg).stream()
                .mapToLong(DayAmount::amount).sum() / BurnRateCalculator.RECENT_DAYS;

        Integer fasterRate = BurnRateCalculator.fasterRate(dailyAvg, usualDailyAvg);
        if (fasterRate == null || fasterRate < THRESHOLD_RATE) {
            return;
        }
        Depletion depletion = BurnRateCalculator.depletion(block.getBalance(), dailyAvg, today, block.getDueDate());
        notificationService.notify(block.getUser(), NotificationType.BURN_RATE,
                title(block.getName(), fasterRate), body(dailyAvg, usualDailyAvg, depletion), block.getId());
    }

    // 예: "생활비가 평소보다 17% 빨리 줄고 있어요"
    static String title(String blockName, int fasterRate) {
        return blockName + (hasBatchim(blockName) ? "이" : "가") + " 평소보다 " + fasterRate + "% 빨리 줄고 있어요";
    }

    // 예: "최근 7일 하루 평균 68,000원. 이 속도면 28일쯤 바닥나요"
    static String body(long dailyAvg, long usualDailyAvg, Depletion depletion) {
        String recent = "최근 7일 하루 평균 %,d원".formatted(dailyAvg);
        if (depletion == null) {
            return recent + ". 평소에는 %,d원이에요".formatted(usualDailyAvg);
        }
        return recent + ". 이 속도면 " + depletion.date().format(DAY) + "쯤 바닥나요";
    }

    private static boolean hasBatchim(String word) {
        char last = word.charAt(word.length() - 1);
        return last >= '가' && last <= '힣' && (last - '가') % 28 != 0;
    }
}
