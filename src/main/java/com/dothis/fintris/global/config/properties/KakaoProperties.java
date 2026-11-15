package com.dothis.fintris.global.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "kakao")
public class KakaoProperties {

    private String userInfoUri = "https://kapi.kakao.com/v2/user/me";
    private String tokenInfoUri = "https://kapi.kakao.com/v1/user/access_token_info";
    private Long appId; // 카카오 개발자 콘솔의 앱 ID. 다른 앱에서 발급된 토큰을 걸러낸다
}
