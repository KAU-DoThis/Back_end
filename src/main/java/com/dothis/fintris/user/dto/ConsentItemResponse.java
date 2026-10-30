package com.dothis.fintris.user.dto;

import com.dothis.fintris.user.entity.ConsentItem;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "동의 항목")
public record ConsentItemResponse(
        @Schema(example = "CODEF") ConsentItem item,
        @Schema(example = "금융거래정보 조회") String title,
        @Schema(description = "보조 설명. 없으면 null", example = "CODEF를 통해 연동한 계좌·카드의 잔액과 거래 내역")
        String description,
        @Schema(description = "필수 여부", example = "true") boolean required,
        @Schema(description = "동의 문서 버전", example = "2026-10-01") String docVersion,
        @Schema(description = "동의 문서 주소. 없으면 null", example = "/docs/consents/codef") String docUrl
) {
    public static ConsentItemResponse from(ConsentItem item) {
        return new ConsentItemResponse(
                item,
                item.getTitle(),
                item.getDescription(),
                item.isRequired(),
                ConsentItem.CURRENT_DOC_VERSION,
                item.getDocUrl()
        );
    }
}
