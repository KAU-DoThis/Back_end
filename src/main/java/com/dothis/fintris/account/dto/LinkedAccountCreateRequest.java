package com.dothis.fintris.account.dto;

import com.dothis.fintris.account.entity.LinkLoginType;
import com.dothis.fintris.account.entity.LinkedAccountType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "금융기관 연동 요청. 기관을 여러 곳 한 번에 보낼 수 있다")
public record LinkedAccountCreateRequest(
        @NotEmpty @Valid List<@NotNull Item> institutions
) {

    public record Item(
            @Schema(description = "CODEF 기관코드", example = "0004")
            @NotBlank @Size(max = 10) String institutionCode,

            @Schema(example = "BANK") @NotNull LinkedAccountType type,

            @Schema(example = "ID_PASSWORD") @NotNull LinkLoginType loginType,

            @Schema(description = "금융기관 로그인 아이디", example = "codef")
            @NotBlank @Size(max = 100) String loginId,

            @Schema(description = "CODEF 공개키로 프론트가 암호화한 비밀번호. 서버는 저장하지 않고 CODEF로만 전달한다")
            @NotBlank @Size(max = 1000) String encryptedPassword,

            @Schema(description = "생년월일 6자리(YYMMDD)", example = "980225")
            @NotBlank @Pattern(regexp = "\\d{6}", message = "생년월일은 숫자 6자리여야 합니다.") String birthDate
    ) {

        // 로그에 인증정보가 남지 않도록 막는다
        @Override
        public String toString() {
            return "Item[institutionCode=" + institutionCode + "]";
        }
    }
}
