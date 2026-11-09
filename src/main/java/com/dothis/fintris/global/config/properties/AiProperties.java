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
}
