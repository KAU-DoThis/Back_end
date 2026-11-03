package com.dothis.fintris.user.dto;

import com.dothis.fintris.user.entity.ConsentItem;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

@Schema(description = "동의 저장 요청. 여러 항목을 한 번에 보낸다")
public record ConsentSaveRequest(
        @Schema(description = "항목별 동의 여부. 같은 항목은 한 번만 보낸다")
        @NotEmpty List<@Valid @NotNull ConsentEntry> consents
) {

    @Schema(description = "항목 하나의 동의 여부")
    public record ConsentEntry(
            @Schema(example = "TERMS") @NotNull ConsentItem item,
            @Schema(example = "true") @NotNull Boolean agreed,
            @Schema(description = "동의한 문서 버전", example = "2026-10-01") @NotBlank String docVersion
    ) {
    }
}
