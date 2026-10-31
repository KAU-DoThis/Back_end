package com.dothis.fintris.block.dto;

import com.dothis.fintris.block.entity.BlockRule;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

// 보낸 필드만 바꾼다. 유형(type)은 바꿀 수 없다
@Schema(description = "블록 수정 요청 (바꿀 필드만 전송)")
public record BlockUpdateRequest(
        @Schema(description = "블록 이름", example = "제주 여행")
        @Size(max = 50) @Pattern(regexp = ".*\\S.*", message = "공백만 입력할 수 없습니다.") String name,

        @Schema(description = "아이콘 키", example = "airplane")
        @Size(max = 50) String icon,

        @Schema(description = "목표 금액(원)", example = "800000")
        Long targetAmount,

        @Schema(description = "시작일", example = "2026-10-20")
        LocalDate startDate,

        @Schema(description = "기간 끝 또는 기한", example = "2027-03-31")
        LocalDate dueDate,

        @Schema(description = "규칙. 보내면 통째로 바뀐다")
        BlockRule rule
) {
}
