package com.dothis.fintris.global.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "codef")
public class CodefProperties {

    private String baseUrl;
    private String oauthUrl = "https://oauth.codef.io/oauth/token";
    private String clientId;
    private String clientSecret;
    private String publicKey;
    private String connectedIdKey; // connectedId 암호화 키 (Base64, 32바이트)
}
