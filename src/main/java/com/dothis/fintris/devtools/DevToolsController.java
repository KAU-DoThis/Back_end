package com.dothis.fintris.devtools;

import com.dothis.fintris.global.api.ApiResponse;
import com.dothis.fintris.global.api.code.GeneralSuccessCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
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
    private final DevDemoService devDemoService;

    @Operation(summary = "테스트 계좌 잔액 설정",
            description = "로그인한 계정에 가짜 은행 계좌를 만들거나 잔액을 바꾼다. CODEF를 부르지 않는다")
    @PutMapping("/test-account")
    public ApiResponse<DevTestAccountResponse> setTestAccount(@AuthenticationPrincipal Long userId,
                                                              @Valid @RequestBody DevTestAccountRequest request) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, devToolsService.setTestAccount(userId, request.balance()));
    }

    @Operation(summary = "시연 거래 3개월치 넣기",
            description = "대학생 1명의 가짜 거래 197건(2026-08-12~11-11, 분류 결과 포함)을 테스트 계좌에 넣는다. "
                    + "블록 확정 전에 넣어야 구성안에 고정지출(월세·통신비·멜론·넷플릭스)이 잡힌다. "
                    + "블록에는 반영하지 않고, 여러 번 눌러도 중복되지 않는다")
    @PostMapping("/demo-transactions")
    public ApiResponse<DevDemoTransactionsResponse> loadDemoTransactions(@AuthenticationPrincipal Long userId) {
        return ApiResponse.onSuccess(GeneralSuccessCode.CREATED, devDemoService.loadDemoTransactions(userId));
    }
}
