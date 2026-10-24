package com.dothis.fintris.user.dto;

import com.dothis.fintris.user.entity.ConsentItem;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@Schema(description = "동의 변경·철회 결과")
public record ConsentChangeResponse(
        @Schema(example = "CODEF") ConsentItem item,

        @Schema(description = "처리 후 동의 상태. 저장하지 않았으면 기존 상태", example = "true") boolean agreed,

        @Schema(description = "저장 여부. 필수 항목 철회를 재확인하지 않았으면 false", example = "false") boolean saved,

        @Schema(description = "필수 항목 철회 시 영향. 철회가 아니거나 선택 항목이면 null") Impact impact
) {

    @Schema(description = "필수 동의 철회 영향")
    public record Impact(
            @Schema(description = "계좌·카드 연동 해제 여부", example = "true") boolean unlinkAccounts,
            @Schema(description = "수집 데이터 삭제 예정일. 삭제하지 않으면 null", example = "2026-11-24")
            LocalDate dataDeletionDate
    ) {
    }
}
