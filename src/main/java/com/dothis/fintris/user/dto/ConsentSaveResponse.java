package com.dothis.fintris.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "동의 저장 결과")
public record ConsentSaveResponse(
        @Schema(description = "계좌 연동 가능 여부. 저장 후 CODEF·AI_PROCESSING 둘 다 동의 상태면 true", example = "true")
        boolean canLink
) {
}
