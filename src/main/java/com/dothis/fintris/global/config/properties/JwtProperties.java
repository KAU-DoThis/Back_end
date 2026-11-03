package com.dothis.fintris.global.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    private String secret;
    private long accessExpMs = 3_600_000L;
    private long refreshExpMs = 1_209_600_000L;
}
