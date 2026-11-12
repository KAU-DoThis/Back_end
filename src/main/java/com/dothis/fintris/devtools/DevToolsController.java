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
    private final DevBlocksService devBlocksService;
    private final DevOnboardingResetService devOnboardingResetService;

    @Operation(summary = "테스트 계좌 잔액 설정",
            description = "로그인한 계정에 가짜 은행 계좌를 만들거나 잔액을 바꾼다. CODEF를 부르지 않는다")
    @PutMapping("/test-account")
    public ApiResponse<DevTestAccountResponse> setTestAccount(@AuthenticationPrincipal Long userId,
                                                              @Valid @RequestBody DevTestAccountRequest request) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, devToolsService.setTestAccount(userId, request.balance()));
    }

    @Operation(summary = "온보딩(블록 구성) 초기화",
            description = "같은 계정으로 04 블록 구성부터 다시 시연할 수 있게 블록·이동 기록·나눌 돈·블록 확정 여부를 지운다. "
                    + "연동 계좌(잔액)·거래(시연 거래 포함)·동의·튜토리얼은 그대로 둔다. "
                    + "거래는 남기고 블록 반영만 푼다")
    @PostMapping("/onboarding/reset")
    public ApiResponse<DevOnboardingResetResponse> resetOnboarding(@AuthenticationPrincipal Long userId) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, devOnboardingResetService.reset(userId));
    }

    @Operation(summary = "블록 확정 시각 옮기기",
            description = "확정 시각을 과거로 옮기고, 그 뒤 출금을 블록에 바로 반영한다(늦추면 되돌린다). "
                    + "시연 거래를 넣고 블록을 확정한 뒤, 예: 2026-11-01T00:00:00으로 옮기면 11월 지출이 블록에서 빠진 모습이 된다. "
                    + "블록 확정 전이면 409 DEV_BLOCKS_NOT_CONFIRMED")
    @PutMapping("/blocks-confirmed-at")
    public ApiResponse<DevBlocksConfirmedAtResponse> moveBlocksConfirmedAt(
            @AuthenticationPrincipal Long userId, @Valid @RequestBody DevBlocksConfirmedAtRequest request) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK,
                devBlocksService.moveBlocksConfirmedAt(userId, request.confirmedAt()));
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
