package com.dothis.fintris.notification.push;

import java.util.HashMap;
import java.util.Map;

// Expo 푸시 한 건. data는 앱이 눌렀을 때 열 화면을 고르는 데 쓴다
public record PushMessage(String to, String title, String body, Map<String, Object> data, boolean urgent) {

    Map<String, Object> toBody() {
        Map<String, Object> body = new HashMap<>();
        body.put("to", to);
        body.put("title", title);
        if (this.body != null) {
            body.put("body", this.body);
        }
        body.put("data", data == null ? Map.of() : data);
        body.put("sound", "default");
        body.put("priority", urgent ? "high" : "default");
        return body;
    }
}
