package com.dothis.fintris.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "온보딩 상태 수정 요청 (바꿀 필드만 전송)")
public record UserSettingUpdateRequest(
        @Schema(example = "true") Boolean onboardingDone,

        @Schema(example = "true") Boolean linkSkipped,

        @Schema(example = "홍길동")
        @Size(max = 30) @Pattern(regexp = ".*\\S.*", message = "공백만 입력할 수 없습니다.") String nickname
) {
}
