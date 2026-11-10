package com.dothis.fintris.devtools;

import com.dothis.fintris.global.api.ApiResponse;
import com.dothis.fintris.global.api.code.GeneralSuccessCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// DEV_TOOLS_ENABLED=true일 때만 생긴다. 실서비스 서버에서는 켜지 않는다
@Tag(name = "Dev", description = "개발 서버 테스트용 (DEV_TOOLS_ENABLED=true일 때만)")
@RestController
@RequestMapping("/dev")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "dev-tools.enabled", havingValue = "true")
public class DevToolsController {

    private final DevToolsService devToolsService;

    @Operation(summary = "테스트 계좌 잔액 설정",
            description = "로그인한 계정에 가짜 은행 계좌를 만들거나 잔액을 바꾼다. CODEF를 부르지 않는다")
    @PutMapping("/test-account")
    public ApiResponse<DevTestAccountResponse> setTestAccount(@AuthenticationPrincipal Long userId,
                                                              @Valid @RequestBody DevTestAccountRequest request) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, devToolsService.setTestAccount(userId, request.balance()));
    }
}
