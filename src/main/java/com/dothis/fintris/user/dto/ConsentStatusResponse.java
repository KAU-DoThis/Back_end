package com.dothis.fintris.user.dto;

import com.dothis.fintris.user.entity.Consent;
import com.dothis.fintris.user.entity.ConsentItem;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "항목별 최신 동의 상태")
public record ConsentStatusResponse(
        @Schema(example = "PUSH") ConsentItem item,
        @Schema(example = "false") boolean agreed,
        @Schema(example = "2026-10-01") String docVersion,
        @Schema(description = "마지막으로 바뀐 시각", example = "2026-10-20T14:30:00") LocalDateTime changedAt
) {
    public static ConsentStatusResponse from(Consent consent) {
        return new ConsentStatusResponse(
                consent.getItem(),
                consent.isAgreed(),
                consent.getDocVersion(),
                consent.getCreatedAt()
        );
    }
}
