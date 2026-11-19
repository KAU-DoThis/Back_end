package com.dothis.fintris.global.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

// Swagger 오른쪽 위 Authorize 버튼. 로그인 응답의 accessToken을 넣으면 Try it out 요청에 Authorization: Bearer 헤더가 붙는다
// 기본은 모든 API에 토큰을 붙이고, 로그인 없이 쓰는 API(가입·로그인·약관 목록)는 컨트롤러에서 빈 @SecurityRequirements로 뺀다
@Configuration
@OpenAPIDefinition(
        info = @Info(title = "FINTRIS API", version = "v1",
                description = "응답은 { isSuccess, code, message, result } 형식. 로그인 후 Authorize에 accessToken을 넣는다"),
        security = @SecurityRequirement(name = OpenApiConfig.BEARER))
@SecurityScheme(name = OpenApiConfig.BEARER, type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class OpenApiConfig {

    static final String BEARER = "bearerAuth";
}
