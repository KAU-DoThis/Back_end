package com.dothis.fintris.account.dto;

import com.dothis.fintris.account.exception.AccountLinkRejectedException;
import com.dothis.fintris.global.exception.GeneralException;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

// connectedId와 전체 계좌번호는 응답에 담지 않는다
@Schema(description = "금융기관 연동 결과. 일부 기관만 실패하면 failed에 담는다")
public record LinkedAccountLinkResponse(
        List<LinkedAccountResponse> linkedAccounts,
        List<FailedInstitution> failed
) {

    public record FailedInstitution(
            @Schema(example = "0004") String institutionCode,
            @Schema(example = "LINK_INSTITUTION_AUTH_FAILED") String code,
            @Schema(description = "기관이 거절 사유를 줬으면 그 문구") String message
    ) {

        public static FailedInstitution of(String institutionCode, GeneralException e) {
            String message = e instanceof AccountLinkRejectedException rejected && rejected.getInstitutionMessage() != null
                    ? rejected.getInstitutionMessage()
                    : e.getCode().getMessage();
            return new FailedInstitution(institutionCode, e.getCode().getCode(), message);
        }
    }
}
