package com.dothis.fintris.global.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

// 자체 AI 서버(FastAPI)
@Getter
@Setter
@ConfigurationProperties(prefix = "ai")
public class AiProperties {

    private String baseUrl;
    // main ↔ agent 내부 인증. 요청 헤더 X-Internal-Token으로 보낸다 (서버 .env FINTRIS_INTERNAL_TOKEN)
    private String internalToken;
}
