package com.dothis.fintris.user.dto;

import com.dothis.fintris.user.entity.ConsentItem;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@Schema(description = "동의 변경·철회 결과")
public record ConsentChangeResponse(
        @Schema(example = "CODEF") ConsentItem item,

        @Schema(description = "처리 후 동의 상태. 재확인 전이라 저장하지 않았으면 기존 상태", example = "false")
        boolean agreed,

        @Schema(description = "CODEF 철회 시 영향. 그 밖에는 null") Impact impact
) {

    @Schema(description = "필수 동의 철회 영향")
    public record Impact(
            @Schema(description = "계좌·카드 연동 해제 여부", example = "true") boolean unlinkAccounts,
            @Schema(description = "수집 데이터 삭제 예정일", example = "2026-11-24") LocalDate dataDeletionDate
    ) {
    }
}
