package com.dothis.fintris.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "동의 변경·철회 요청")
public record ConsentChangeRequest(
        @Schema(example = "false") @NotNull Boolean agreed,

        @Schema(description = "동의 문서 버전", example = "2026-10-01") @NotBlank String docVersion,

        @Schema(description = "필수 항목 철회를 영향 안내 후 재확인했는지. 아니면 저장하지 않고 영향만 돌려준다",
                example = "false")
        Boolean confirmed
) {
}
