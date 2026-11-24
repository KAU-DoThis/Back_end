package com.dothis.fintris.notification.push;

import com.dothis.fintris.notification.push.ExpoPushClient.PushResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class ExpoPushClientTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    @DisplayName("보낸 순서대로 성공·기기 없음·실패를 읽고, 응답이 모자라면 실패로 본다")
    void parsesTickets() {
        String response = """
                {"data": [
                  {"status": "ok", "id": "abc"},
                  {"status": "error", "message": "not registered", "details": {"error": "DeviceNotRegistered"}},
                  {"status": "error", "message": "too big", "details": {"error": "MessageTooBig"}}
                ]}
                """;

        assertThat(ExpoPushClient.parse(mapper.readTree(response), 4)).containsExactly(
                PushResult.SENT, PushResult.UNREGISTERED, PushResult.FAILED, PushResult.FAILED);
    }

    @Test
    @DisplayName("긴급 알림은 높은 우선순위로 보낸다")
    void urgentPriority() {
        assertThat(new PushMessage("ExponentPushToken[a]", "제목", null, null, true).toBody())
                .containsEntry("priority", "high").doesNotContainKey("body");
    }
}
