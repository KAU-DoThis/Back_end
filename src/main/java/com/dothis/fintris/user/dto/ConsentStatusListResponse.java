package com.dothis.fintris.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "내 동의 현황")
public record ConsentStatusListResponse(
        @Schema(description = "항목별 가장 최근 상태. 한 번도 저장하지 않은 항목은 빠진다")
        List<ConsentStatusResponse> consents
) {
}
