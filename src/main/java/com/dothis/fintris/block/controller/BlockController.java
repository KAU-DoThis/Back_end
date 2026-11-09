package com.dothis.fintris.block.controller;

import com.dothis.fintris.block.dto.BlockCategoryRequest;
import com.dothis.fintris.block.dto.BlockCreateRequest;
import com.dothis.fintris.block.dto.BlockDeleteResponse;
import com.dothis.fintris.block.dto.BlockDetailResponse;
import com.dothis.fintris.block.dto.BlockHistoryResponse;
import com.dothis.fintris.block.dto.BlockListResponse;
import com.dothis.fintris.block.dto.BlockOrderRequest;
import com.dothis.fintris.block.dto.BlockProposalConfirmRequest;
import com.dothis.fintris.block.dto.BlockProposalConfirmResponse;
import com.dothis.fintris.block.dto.BlockProposalResponse;
import com.dothis.fintris.block.dto.BlockResponse;
import com.dothis.fintris.block.dto.BlockUpdateRequest;
import com.dothis.fintris.block.entity.BlockType;
import com.dothis.fintris.block.service.BlockDetailService;
import com.dothis.fintris.block.service.BlockProposalService;
import com.dothis.fintris.block.service.BlockService;
import com.dothis.fintris.global.api.ApiResponse;
import com.dothis.fintris.global.api.code.GeneralSuccessCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Block", description = "금융 블록 (F-04)")
@RestController
@RequestMapping("/blocks")
@RequiredArgsConstructor
public class BlockController {

    private final BlockService blockService;
    private final BlockProposalService blockProposalService;
    private final BlockDetailService blockDetailService;

    @Operation(summary = "블록 목록 조회", description = "display_order 순, 삭제된 블록 제외")
    @GetMapping
    public ApiResponse<BlockListResponse> getBlocks(
            @AuthenticationPrincipal Long userId,
            @Parameter(description = "블록 유형 필터") @RequestParam(required = false) BlockType type) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK,
                new BlockListResponse(blockService.getBlocks(userId, type)));
    }

    @Operation(summary = "블록 상세 조회", description = "유형별 detail (사용형 02 / 목표형 02-1 / 고정지출형 02-2)")
    @GetMapping("/{blockId}")
    public ApiResponse<BlockDetailResponse> getBlock(@AuthenticationPrincipal Long userId, @PathVariable Long blockId) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, blockDetailService.getDetail(userId, blockId));
    }

    @Operation(summary = "블록 생성",
            description = "목표형은 targetAmount·dueDate 필수, 고정지출형은 rule.payDay·payAmount 필수")
    @PostMapping
    public ApiResponse<BlockResponse> createBlock(
            @AuthenticationPrincipal Long userId,
            @RequestBody @Valid BlockCreateRequest request) {
        return ApiResponse.onSuccess(GeneralSuccessCode.CREATED, blockService.createBlock(userId, request));
    }

    @Operation(summary = "블록 기록", description = "배분·이동·만듦을 최근 순으로. 들어오면 +, 나가면 -. 납부 기록은 거래 반영 후")
    @GetMapping("/{blockId}/history")
    public ApiResponse<BlockHistoryResponse> getHistory(@AuthenticationPrincipal Long userId,
                                                        @PathVariable Long blockId,
                                                        @RequestParam(defaultValue = "0") int page,
                                                        @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, blockDetailService.getHistory(userId, blockId, page, size));
    }

    @Operation(summary = "초기 블록 구성안 (온보딩 3/3)",
            description = "확정 전엔 아무것도 저장하지 않는다. 거래 분석 전이라 지금은 남은 잔액으로 기본 생활비 블록만 제안한다")
    @GetMapping("/proposals")
    public ApiResponse<BlockProposalResponse> getProposals(@AuthenticationPrincipal Long userId) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, blockProposalService.getProposals(userId));
    }

    @Operation(summary = "초기 블록 확정 (온보딩 3/3)",
            description = "고친 구성안을 잔액과 함께 한 번에 만든다. 보낸 순서가 표시 순서. "
                    + "블록 합계가 연동 잔액보다 크면 BLOCK_INSUFFICIENT_FUNDS, 남는 돈은 '남은 돈' 나눌 돈")
    @PostMapping("/proposals/confirm")
    public ApiResponse<BlockProposalConfirmResponse> confirmProposals(
            @AuthenticationPrincipal Long userId,
            @RequestBody @Valid BlockProposalConfirmRequest request) {
        return ApiResponse.onSuccess(GeneralSuccessCode.CREATED, blockProposalService.confirm(userId, request));
    }

    // /order가 /{blockId}보다 먼저 매칭된다 (고정 경로 우선)
    @Operation(summary = "블록 순서 변경", description = "삭제되지 않은 내 블록 전체 ID를 원하는 순서로 보낸다")
    @PatchMapping("/order")
    public ApiResponse<BlockListResponse> changeOrder(
            @AuthenticationPrincipal Long userId,
            @RequestBody @Valid BlockOrderRequest request) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK,
                new BlockListResponse(blockService.changeOrder(userId, request)));
    }

    @Operation(summary = "블록 수정", description = "보낸 필드만 바뀐다. 유형은 바꿀 수 없다")
    @PatchMapping("/{blockId}")
    public ApiResponse<BlockResponse> updateBlock(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long blockId,
            @RequestBody @Valid BlockUpdateRequest request) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, blockService.updateBlock(userId, blockId, request));
    }

    @Operation(summary = "블록 삭제", description = "소프트 삭제. 남은 잔액은 '남은 돈' 나눌 돈으로 돌아간다. 기본 블록은 삭제 불가")
    @DeleteMapping("/{blockId}")
    public ApiResponse<BlockDeleteResponse> deleteBlock(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long blockId) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, blockService.deleteBlock(userId, blockId));
    }

    @Operation(summary = "블록 카테고리 연결 설정", description = "보낸 목록으로 통째로 바꾼다")
    @PutMapping("/{blockId}/categories")
    public ApiResponse<BlockResponse> setCategories(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long blockId,
            @RequestBody @Valid BlockCategoryRequest request) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, blockService.setCategories(userId, blockId, request));
    }
}
