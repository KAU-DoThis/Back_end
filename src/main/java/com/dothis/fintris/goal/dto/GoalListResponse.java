package com.dothis.fintris.goal.dto;

import com.dothis.fintris.block.entity.BlockStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

@Schema(description = "목표 목록 (05 절약 · 진행 중 목표)")
public record GoalListResponse(List<Goal> goals) {

    @Schema(description = "목표 (목표형 블록)")
    public record Goal(
            @Schema(example = "4") Long blockId,
            @Schema(example = "여행") String name,
            @Schema(example = "320000") long balance,
            @Schema(example = "800000") long targetAmount,
            @Schema(example = "2026-12-31") LocalDate dueDate,
            @Schema(description = "달성률(%) = 잔액 ÷ 목표 금액, 0~100 내림", example = "40") int achievementRate,
            @Schema(description = "지금 속도로 모으면 다 모으는 날. 다 모았으면 오늘, 계산할 수 없으면 null",
                    example = "2027-01-15") LocalDate currentPaceDate,
            @Schema(description = "목표일보다 늦어지는 일수. 늦지 않으면 0, 계산할 수 없으면 null", example = "15") Long delayDays,
            @Schema(example = "CAUTION") BlockStatus status,
            @Schema(example = "주의") String statusLabel,
            @Schema(description = "상태 칩 옆 짧은 이유", example = "2주 늦어짐") String statusSubLabel,
            @Schema(description = "다음 수정·처리 요청에 보낸다", example = "5") Long version
    ) {
    }
}
