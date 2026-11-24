package com.dothis.fintris.notification.entity;

import com.dothis.fintris.global.entity.BaseCreatedEntity;
import com.dothis.fintris.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

// 알림함에 쌓이는 알림 (v4 9장)
@Entity
@Table(name = "notifications")
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationType type;

    @Column(nullable = false)
    private String title;

    @Column(length = 500)
    private String body;

    private Long refId; // 눌렀을 때 열 대상. 질문이면 질문 ID

    private LocalDateTime readAt;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private PushStatus pushStatus; // 아직 정하지 않았으면 null

    private LocalDateTime pushedAt;

    public boolean isOwnedBy(Long userId) {
        return user.getId().equals(userId);
    }

    public boolean isRead() {
        return readAt != null;
    }

    public void pushed(PushStatus status, LocalDateTime now) {
        this.pushStatus = status;
        if (status == PushStatus.SENT) {
            this.pushedAt = now;
        }
    }

    // 이미 읽었으면 처음 읽은 시각을 둔다
    public void markRead(LocalDateTime now) {
        if (readAt == null) {
            readAt = now;
        }
    }
}
