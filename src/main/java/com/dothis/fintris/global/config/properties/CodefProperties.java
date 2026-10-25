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
}
