package com.dothis.fintris.home.controller;

import com.dothis.fintris.global.api.ApiResponse;
import com.dothis.fintris.global.api.code.GeneralSuccessCode;
import com.dothis.fintris.home.dto.HomeResponse;
import com.dothis.fintris.home.service.HomeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// TODO: JWT 인증이 붙으면 X-User-Id 헤더 대신 로그인 사용자에서 userId를 꺼낸다
@Tag(name = "Home", description = "홈 화면 (F-10, F-21)")
@RestController
@RequestMapping("/home")
@RequiredArgsConstructor
public class HomeController {

    private final HomeService homeService;

    @Operation(summary = "홈 조회",
            description = "API 명세 v4 5장. 총 자산, 오늘 쓸 수 있는 돈, 계좌별 카드, 나눌 돈, 블록 타일. "
                    + "suggestion·waitingFunds·알림·질문 수는 해당 기능이 붙기 전까지 비어 있다")
    @GetMapping
    public ApiResponse<HomeResponse> getHome(
            @Parameter(description = "임시 사용자 ID") @RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, homeService.getHome(userId));
    }
}
