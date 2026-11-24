package com.dothis.fintris.notification.service;

import com.dothis.fintris.global.exception.GeneralException;
import com.dothis.fintris.notification.code.NotificationErrorCode;
import com.dothis.fintris.notification.dto.NotificationListResponse;
import com.dothis.fintris.notification.dto.NotificationListResponse.Group;
import com.dothis.fintris.notification.dto.NotificationListResponse.Item;
import com.dothis.fintris.notification.entity.Notification;
import com.dothis.fintris.notification.entity.NotificationCategory;
import com.dothis.fintris.notification.entity.NotificationType;
import com.dothis.fintris.notification.repository.NotificationRepository;
import com.dothis.fintris.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// 알림함 (v4 9장, F-22)
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    static final int RETENTION_DAYS = 30;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final NotificationRepository notificationRepository;

    // 다른 기능이 알림을 남길 때 부른다. 알림함에 쌓기만 하고 푸시 발송은 따로 한다
    @Transactional
    public Notification notify(User user, NotificationType type, String title, String body, Long refId) {
        return notificationRepository.save(Notification.builder()
                .user(user).type(type).title(title).body(body).refId(refId).build());
    }

    public NotificationListResponse getNotifications(Long userId, NotificationCategory category, int page, int size) {
        Slice<Notification> slice = notificationRepository.findByUser_IdAndTypeInOrderByCreatedAtDescIdDesc(
                userId, typesOf(category), PageRequest.of(page, size));

        Map<NotificationCategory, Long> counts = new EnumMap<>(NotificationCategory.class);
        for (NotificationCategory each : NotificationCategory.values()) {
            if (each != NotificationCategory.ALL) {
                counts.put(each, 0L);
            }
        }
        notificationRepository.findUnreadTypes(userId).forEach(type -> counts.merge(type.getCategory(), 1L, Long::sum));

        LocalDate today = LocalDate.now(KST);
        Map<String, List<Item>> grouped = new LinkedHashMap<>();
        for (Notification notification : slice.getContent()) {
            grouped.computeIfAbsent(groupLabel(notification.getCreatedAt().toLocalDate(), today), label -> new ArrayList<>())
                    .add(Item.from(notification));
        }
        List<Group> groups = grouped.entrySet().stream().map(entry -> new Group(entry.getKey(), entry.getValue())).toList();
        return new NotificationListResponse(counts, groups, RETENTION_DAYS, slice.hasNext());
    }

    @Transactional
    public void markRead(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> GeneralException.of(NotificationErrorCode.NOTIFICATION_NOT_FOUND));
        if (!notification.isOwnedBy(userId)) {
            throw GeneralException.of(NotificationErrorCode.NOTIFICATION_FORBIDDEN);
        }
        notification.markRead(LocalDateTime.now(KST));
    }

    public long countUnread(Long userId) {
        return notificationRepository.countByUser_IdAndReadAtIsNull(userId);
    }

    private List<NotificationType> typesOf(NotificationCategory category) {
        return Arrays.stream(NotificationType.values())
                .filter(type -> category == NotificationCategory.ALL || type.getCategory() == category)
                .toList();
    }

    // 이번 주는 월요일부터 어제까지
    static String groupLabel(LocalDate date, LocalDate today) {
        if (date.equals(today)) {
            return "오늘";
        }
        LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return date.isBefore(monday) ? "이전" : "이번 주";
    }
}
