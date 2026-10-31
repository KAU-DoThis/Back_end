package com.dothis.fintris.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "동의 항목 목록")
public record ConsentItemListResponse(
        List<ConsentItemResponse> items,

        @Schema(description = "동의 화면 하단 안내 문구", example = "가져온 금융 정보는 블록 계산과 분석에만 써요.")
        String notice
) {
}
