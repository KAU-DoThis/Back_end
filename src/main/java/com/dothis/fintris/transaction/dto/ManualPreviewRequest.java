package com.dothis.fintris.transaction.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "빠른 입력 미리보기 요청")
public record ManualPreviewRequest(
        @Schema(description = "입력한 문장", example = "붕어빵 3000")
        @NotBlank @Size(max = 100) String text
) {
}
