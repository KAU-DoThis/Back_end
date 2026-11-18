package com.dothis.fintris.transaction.controller;

import com.dothis.fintris.global.api.ApiResponse;
import com.dothis.fintris.global.api.code.GeneralSuccessCode;
import com.dothis.fintris.transaction.dto.ManualPreviewRequest;
import com.dothis.fintris.transaction.dto.ManualPreviewResponse;
import com.dothis.fintris.transaction.dto.ManualSaveRequest;
import com.dothis.fintris.transaction.dto.ManualSaveResponse;
import com.dothis.fintris.transaction.dto.TransactionCategoryRequest;
import com.dothis.fintris.transaction.dto.TransactionListFilter;
import com.dothis.fintris.transaction.dto.TransactionListResponse;
import com.dothis.fintris.transaction.dto.TransactionResponse;
import com.dothis.fintris.transaction.dto.TransactionReviewRequest;
import com.dothis.fintris.transaction.dto.TransactionReviewResponse;
import com.dothis.fintris.transaction.service.ManualTransactionService;
import com.dothis.fintris.transaction.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;

@Tag(name = "Transaction", description = "거래 조회 (F-19·F-21)")
@RestController
@RequestMapping("/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private static final int MAX_PAGE_SIZE = 100;

    private final TransactionService transactionService;
    private final ManualTransactionService manualTransactionService;

    @Operation(summary = "거래 목록 조회",
            description = "월 단위로 최신순 날짜별 묶음을 돌려준다. month를 빼면 이번 달, size는 최대 100")
    @GetMapping
    public ApiResponse<TransactionListResponse> getTransactions(
            @AuthenticationPrincipal Long userId,
            @Parameter(example = "2026-10") @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
            @RequestParam(required = false) Long linkedAccountId,
            @RequestParam(required = false) Long blockId,
            @RequestParam(defaultValue = "ALL") TransactionListFilter filter,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, transactionService.getTransactions(
                userId, month, linkedAccountId, blockId, filter, Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE)));
    }

    @Operation(summary = "거래 상세 조회", description = "다른 사용자의 거래면 403")
    @GetMapping("/{transactionId}")
    public ApiResponse<TransactionResponse> getTransaction(@AuthenticationPrincipal Long userId,
                                                           @PathVariable Long transactionId) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, transactionService.getTransaction(userId, transactionId));
    }

    @Operation(summary = "거래 카테고리 수정", description = "사용자가 고친 분류는 자동 분류보다 우선한다(classifiedBy=USER)")
    @PatchMapping("/{transactionId}/category")
    public ApiResponse<TransactionResponse> changeCategory(@AuthenticationPrincipal Long userId,
                                                           @PathVariable Long transactionId,
                                                           @RequestBody @Valid TransactionCategoryRequest request) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK,
                transactionService.changeCategory(userId, transactionId, request.categoryId()));
    }

    @Operation(summary = "빠른 입력 미리보기",
            description = "문장에서 금액·내용·날짜를 읽고 차감될 블록을 보여 준다. 저장하지 않는다. 금액을 못 읽으면 400 MANUAL_PARSE_FAILED. "
                    + "카테고리 추천(AI)은 아직 없어 null")
    @PostMapping("/manual/preview")
    public ApiResponse<ManualPreviewResponse> previewManual(@AuthenticationPrincipal Long userId,
                                                            @RequestBody @Valid ManualPreviewRequest request) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, manualTransactionService.preview(userId, request.text()));
    }

    @Operation(summary = "빠른 입력 저장",
            description = "확정 거래(source=MANUAL)로 저장하고 블록에서 바로 뺀다. 응답의 todayRemaining은 홈과 같은 값")
    @PostMapping("/manual")
    public ApiResponse<ManualSaveResponse> saveManual(@AuthenticationPrincipal Long userId,
                                                      @RequestBody @Valid ManualSaveRequest request) {
        return ApiResponse.onSuccess(GeneralSuccessCode.CREATED, manualTransactionService.save(userId, request));
    }

    @Operation(summary = "확인 필요 거래 처리",
            description = "KEEP: 확정, DELETE: 블록 잔액을 복구하고 삭제. 확인 필요 상태가 아니면 409 TRANSACTION_NOT_REVIEWABLE, "
                    + "은행·카드에서 가져온 거래를 지우려 하면 409 TRANSACTION_NOT_DELETABLE")
    @PatchMapping("/{transactionId}/review")
    public ApiResponse<TransactionReviewResponse> review(@AuthenticationPrincipal Long userId,
                                                         @PathVariable Long transactionId,
                                                         @RequestBody @Valid TransactionReviewRequest request) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK,
                transactionService.review(userId, transactionId, request.action()));
    }
}
