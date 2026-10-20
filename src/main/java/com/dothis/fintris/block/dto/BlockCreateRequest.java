package com.dothis.fintris.block.dto;

import com.dothis.fintris.block.entity.BlockRule;
import com.dothis.fintris.block.entity.BlockType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

// 금액·기한·납부일 검증은 블록 에러 코드로 응답하기 위해 서비스에서 한다
@Schema(description = "블록 생성 요청")
public record BlockCreateRequest(
        @Schema(description = "블록 유형", example = "GOAL")
        @NotNull BlockType type,

        @Schema(description = "블록 이름", example = "제주 여행")
        @NotBlank @Size(max = 50) String name,

        @Schema(description = "아이콘 키", example = "airplane")
        @Size(max = 50) String icon,

        @Schema(description = "목표 금액(원). 목표형 필수", example = "600000")
        Long targetAmount,

        @Schema(description = "시작일. 없으면 오늘", example = "2026-10-20")
        LocalDate startDate,

        @Schema(description = "사용형=기간 끝(없으면 이번 달 말일), 목표형=기한(필수)", example = "2027-02-28")
        LocalDate dueDate,

        @Schema(description = "규칙. 고정지출형은 pay_day·pay_amount 필수")
        BlockRule rule
) {
}
