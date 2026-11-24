package com.dothis.fintris.global.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "expo")
public class ExpoProperties {

    private String pushUrl = "https://exp.host/--/api/v2/push/send";
    private String accessToken; // Expo 푸시 보안을 켰을 때만. 없으면 헤더 없이 보낸다
}
