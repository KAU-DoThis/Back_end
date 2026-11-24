package com.dothis.fintris.notification.dto;

import com.dothis.fintris.notification.entity.ContextFrequency;
import com.dothis.fintris.notification.entity.NotificationSetting;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

// API 명세 v4 9장 알림 설정. 조회 응답과 수정 요청이 같은 모양이다 (수정은 전체를 보낸다)
@Schema(description = "알림 설정")
public record NotificationSettingsBody(
        @Schema(example = "true") @NotNull Boolean pushEnabled,
        @NotNull @Valid ContextQuestion contextQuestion,
        @NotNull @Valid QuietHours quietHours,
        @NotNull @Valid BlockWarnings blockWarnings,
        @NotNull @Valid News news,
        @Schema(type = "string", example = "08:00") @JsonFormat(pattern = "HH:mm") @NotNull LocalTime dailyGuideTime,
        @Schema(description = "잠금 화면에서 금액 숨기기", example = "false") @NotNull Boolean lockScreenHideAmount,
        @Schema(description = "blockWarnings.FIXED_SHORTAGE를 끌 때 true로 보낸다", example = "false") @NotNull Boolean urgentOffConfirmed
) {

    public record ContextQuestion(
            @Schema(example = "true") @NotNull Boolean enabled,
            @Schema(description = "EACH_SYNC(가져올 때마다) · MAX_3_PER_DAY(하루 3번까지) · DAILY_DIGEST(하루 1번 저녁 9시에 모아서)",
                    example = "MAX_3_PER_DAY") @NotNull ContextFrequency frequency
    ) {
    }

    @Schema(description = "이 시간에는 푸시를 보내지 않고 알림함에만 쌓는다 (긴급 알림 제외)")
    public record QuietHours(
            @Schema(type = "string", example = "23:00") @JsonFormat(pattern = "HH:mm") @NotNull LocalTime start,
            @Schema(type = "string", example = "07:00") @JsonFormat(pattern = "HH:mm") @NotNull LocalTime end
    ) {
    }

    public record BlockWarnings(
            @JsonProperty("BURN_RATE") @Schema(example = "true") @NotNull Boolean burnRate,
            @JsonProperty("FIXED_SHORTAGE") @Schema(description = "긴급", example = "true") @NotNull Boolean fixedShortage,
            @JsonProperty("GOAL_BEHIND") @Schema(example = "true") @NotNull Boolean goalBehind
    ) {
    }

    public record News(
            @JsonProperty("SAVING_AND_REPORT") @Schema(example = "true") @NotNull Boolean savingAndReport,
            @JsonProperty("MARKETING") @Schema(description = "MARKETING 동의가 없으면 켤 수 없다", example = "false") @NotNull Boolean marketing
    ) {
    }

    // 마케팅은 동의를 철회했으면 켜 둔 설정이 있어도 꺼진 것으로 보여 준다
    public static NotificationSettingsBody of(NotificationSetting setting, boolean marketingAgreed) {
        return new NotificationSettingsBody(
                setting.isPushEnabled(),
                new ContextQuestion(setting.isContextEnabled(), setting.getContextFrequency()),
                new QuietHours(setting.getQuietStart(), setting.getQuietEnd()),
                new BlockWarnings(setting.isBurnRateEnabled(), setting.isFixedShortageEnabled(), setting.isGoalBehindEnabled()),
                new News(setting.isSavingReportEnabled(), setting.isMarketingEnabled() && marketingAgreed),
                setting.getDailyGuideTime(),
                setting.isLockScreenHideAmount(),
                setting.isUrgentOffConfirmed());
    }
}
