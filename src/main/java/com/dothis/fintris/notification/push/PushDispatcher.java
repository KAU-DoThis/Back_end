package com.dothis.fintris.notification.push;

import com.dothis.fintris.notification.entity.Notification;
import com.dothis.fintris.notification.entity.NotificationLog;
import com.dothis.fintris.notification.entity.NotificationSetting;
import com.dothis.fintris.notification.entity.NotificationType;
import com.dothis.fintris.notification.entity.PushStatus;
import com.dothis.fintris.notification.entity.PushToken;
import com.dothis.fintris.notification.push.ExpoPushClient.PushResult;
import com.dothis.fintris.notification.push.PushPolicy.Decision;
import com.dothis.fintris.notification.repository.NotificationLogRepository;
import com.dothis.fintris.notification.repository.NotificationRepository;
import com.dothis.fintris.notification.repository.NotificationSettingRepository;
import com.dothis.fintris.notification.repository.PushTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// 알림 한 건을 PushPolicy대로 보내거나 미루거나 건너뛴다 (F-22)
// 알림을 만든 트랜잭션과 따로 돈다. 푸시가 실패해도 알림함 저장은 그대로다
@Component
@RequiredArgsConstructor
public class PushDispatcher {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final NotificationRepository notificationRepository;
    private final NotificationSettingRepository notificationSettingRepository;
    private final PushTokenRepository pushTokenRepository;
    private final NotificationLogRepository notificationLogRepository;
    private final ExpoPushClient expoPushClient;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void dispatch(Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId).orElse(null);
        if (notification == null || (notification.getPushStatus() != null && notification.getPushStatus() != PushStatus.HELD)) {
            return;
        }
        Long userId = notification.getUser().getId();
        NotificationSetting setting = notificationSettingRepository.findByUser_Id(userId)
                .orElseGet(() -> NotificationSetting.defaults(notification.getUser()));
        LocalDateTime now = LocalDateTime.now(KST);
        LocalDateTime startOfToday = now.toLocalDate().atStartOfDay();
        int sentToday = (int) notificationRepository.countByUser_IdAndPushStatusAndPushedAtGreaterThanEqual(
                userId, PushStatus.SENT, startOfToday);
        int contextSentToday = (int) notificationRepository.countByUser_IdAndTypeAndPushStatusAndPushedAtGreaterThanEqual(
                userId, NotificationType.CONTEXT_QUESTION, PushStatus.SENT, startOfToday);

        Decision decision = PushPolicy.decide(notification.getType(), setting, now.toLocalTime(), sentToday, contextSentToday);
        if (decision == Decision.HOLD) {
            notification.pushed(PushStatus.HELD, now);
            return;
        }
        List<PushToken> tokens = decision == Decision.SEND ? pushTokenRepository.findAllByUser_Id(userId) : List.of();
        if (tokens.isEmpty()) {
            notification.pushed(PushStatus.SKIPPED, now);
            return;
        }
        notification.pushed(send(notification, setting, tokens, now), now);
    }

    private PushStatus send(Notification notification, NotificationSetting setting, List<PushToken> tokens,
                            LocalDateTime now) {
        Map<String, Object> data = new HashMap<>();
        data.put("notificationId", notification.getId());
        data.put("type", notification.getType().name());
        if (notification.getRefId() != null) {
            data.put("refId", notification.getRefId());
        }
        String title = PushPolicy.title(notification.getTitle(), setting);
        String body = PushPolicy.body(notification.getBody(), setting);
        boolean urgent = notification.getType().isUrgent();
        List<PushMessage> messages = tokens.stream()
                .map(token -> new PushMessage(token.getToken(), title, body, data, urgent))
                .toList();

        List<PushResult> results = expoPushClient.send(messages);
        List<NotificationLog> logs = new ArrayList<>();
        boolean anySent = false;
        for (int i = 0; i < tokens.size(); i++) {
            PushToken token = tokens.get(i);
            PushResult result = results.get(i);
            anySent |= result == PushResult.SENT;
            logs.add(NotificationLog.builder().user(notification.getUser()).notificationId(notification.getId())
                    .type(notification.getType()).sentAt(now).delivered(result == PushResult.SENT)
                    .pushToken(token.getToken()).build());
            // 앱을 지웠거나 토큰이 만료된 기기는 다음부터 보내지 않는다
            if (result == PushResult.UNREGISTERED) {
                pushTokenRepository.delete(token);
            }
        }
        notificationLogRepository.saveAll(logs);
        return anySent ? PushStatus.SENT : PushStatus.FAILED;
    }
}
