package com.dothis.fintris.block.controller;

import com.dothis.fintris.block.dto.BlockCategoryRequest;
import com.dothis.fintris.block.dto.BlockCreateRequest;
import com.dothis.fintris.block.dto.BlockOrderRequest;
import com.dothis.fintris.block.dto.BlockResponse;
import com.dothis.fintris.block.dto.BlockUpdateRequest;
import com.dothis.fintris.block.entity.BlockType;
import com.dothis.fintris.block.service.BlockService;
import com.dothis.fintris.global.api.ApiResponse;
import com.dothis.fintris.global.api.code.GeneralSuccessCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// TODO: JWT 인증이 붙으면 X-User-Id 헤더 대신 로그인 사용자에서 userId를 꺼낸다
@Tag(name = "Block", description = "금융 블록 (F-04)")
@RestController
@RequestMapping("/blocks")
@RequiredArgsConstructor
public class BlockController {

    private static final String USER_HEADER = "X-User-Id";

    private final BlockService blockService;

    @Operation(summary = "블록 목록 조회", description = "display_order 순, 삭제된 블록 제외")
    @GetMapping
    public ApiResponse<List<BlockResponse>> getBlocks(
            @Parameter(description = "임시 사용자 ID") @RequestHeader(USER_HEADER) Long userId,
            @Parameter(description = "블록 유형 필터") @RequestParam(required = false) BlockType type) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, blockService.getBlocks(userId, type));
    }

    @Operation(summary = "블록 상세 조회")
    @GetMapping("/{blockId}")
    public ApiResponse<BlockResponse> getBlock(
            @Parameter(description = "임시 사용자 ID") @RequestHeader(USER_HEADER) Long userId,
            @PathVariable Long blockId) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, blockService.getBlock(userId, blockId));
    }

    @Operation(summary = "블록 생성",
            description = "목표형은 targetAmount·dueDate 필수, 고정지출형은 rule.pay_day·pay_amount 필수")
    @PostMapping
    public ApiResponse<BlockResponse> createBlock(
            @Parameter(description = "임시 사용자 ID") @RequestHeader(USER_HEADER) Long userId,
            @RequestBody @Valid BlockCreateRequest request) {
        return ApiResponse.onSuccess(GeneralSuccessCode.CREATED, blockService.createBlock(userId, request));
    }

    // /order가 /{blockId}보다 먼저 매칭된다 (고정 경로 우선)
    @Operation(summary = "블록 순서 변경", description = "삭제되지 않은 내 블록 전체 ID를 원하는 순서로 보낸다")
    @PatchMapping("/order")
    public ApiResponse<List<BlockResponse>> changeOrder(
            @Parameter(description = "임시 사용자 ID") @RequestHeader(USER_HEADER) Long userId,
            @RequestBody @Valid BlockOrderRequest request) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, blockService.changeOrder(userId, request));
    }

    @Operation(summary = "블록 수정", description = "보낸 필드만 바뀐다. 유형은 바꿀 수 없다")
    @PatchMapping("/{blockId}")
    public ApiResponse<BlockResponse> updateBlock(
            @Parameter(description = "임시 사용자 ID") @RequestHeader(USER_HEADER) Long userId,
            @PathVariable Long blockId,
            @RequestBody @Valid BlockUpdateRequest request) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, blockService.updateBlock(userId, blockId, request));
    }

    @Operation(summary = "블록 삭제", description = "소프트 삭제. 잔액은 미배분으로 돌아간다. 기본 블록은 삭제 불가")
    @DeleteMapping("/{blockId}")
    public ApiResponse<Void> deleteBlock(
            @Parameter(description = "임시 사용자 ID") @RequestHeader(USER_HEADER) Long userId,
            @PathVariable Long blockId) {
        blockService.deleteBlock(userId, blockId);
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, null);
    }

    @Operation(summary = "블록 카테고리 연결 설정", description = "보낸 목록으로 통째로 바꾼다")
    @PutMapping("/{blockId}/categories")
    public ApiResponse<BlockResponse> setCategories(
            @Parameter(description = "임시 사용자 ID") @RequestHeader(USER_HEADER) Long userId,
            @PathVariable Long blockId,
            @RequestBody @Valid BlockCategoryRequest request) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, blockService.setCategories(userId, blockId, request));
    }
}
