package com.dothis.fintris.notification.entity;

import com.dothis.fintris.global.entity.BaseEntity;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalTime;

// 알림 설정 (v4 9장, F-22). 사용자당 한 행이고, 행이 없으면 기본값으로 본다
@Entity
@Table(name = "notification_settings")
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationSetting extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_setting_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Builder.Default
    private boolean pushEnabled = true;

    @Builder.Default
    private boolean contextEnabled = true;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ContextFrequency contextFrequency = ContextFrequency.MAX_3_PER_DAY;

    // 받는 시간 기본값은 기능명세서 F-22(07~23시). v4 13장에서 아직 TBD
    @Builder.Default
    @Column(nullable = false)
    private LocalTime quietStart = LocalTime.of(23, 0);

    @Builder.Default
    @Column(nullable = false)
    private LocalTime quietEnd = LocalTime.of(7, 0);

    @Builder.Default
    private boolean burnRateEnabled = true;

    @Builder.Default
    private boolean fixedShortageEnabled = true;

    @Builder.Default
    private boolean goalBehindEnabled = true;

    @Builder.Default
    private boolean savingReportEnabled = true;

    private boolean marketingEnabled;

    @Builder.Default
    @Column(nullable = false)
    private LocalTime dailyGuideTime = LocalTime.of(8, 0);

    private boolean lockScreenHideAmount;

    private boolean urgentOffConfirmed;

    public static NotificationSetting defaults(User user) {
        return NotificationSetting.builder().user(user).build();
    }

    public void update(boolean pushEnabled, boolean contextEnabled, ContextFrequency contextFrequency,
                       LocalTime quietStart, LocalTime quietEnd, boolean burnRateEnabled,
                       boolean fixedShortageEnabled, boolean goalBehindEnabled, boolean savingReportEnabled,
                       boolean marketingEnabled, LocalTime dailyGuideTime, boolean lockScreenHideAmount,
                       boolean urgentOffConfirmed) {
        this.pushEnabled = pushEnabled;
        this.contextEnabled = contextEnabled;
        this.contextFrequency = contextFrequency;
        this.quietStart = quietStart;
        this.quietEnd = quietEnd;
        this.burnRateEnabled = burnRateEnabled;
        this.fixedShortageEnabled = fixedShortageEnabled;
        this.goalBehindEnabled = goalBehindEnabled;
        this.savingReportEnabled = savingReportEnabled;
        this.marketingEnabled = marketingEnabled;
        this.dailyGuideTime = dailyGuideTime;
        this.lockScreenHideAmount = lockScreenHideAmount;
        this.urgentOffConfirmed = urgentOffConfirmed;
    }
}
