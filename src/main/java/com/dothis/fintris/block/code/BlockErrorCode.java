package com.dothis.fintris.block.code;

import com.dothis.fintris.global.api.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum BlockErrorCode implements BaseErrorCode {
    BLOCK_NOT_FOUND(HttpStatus.NOT_FOUND, "BLOCK_NOT_FOUND", "블록을 찾을 수 없습니다."),
    BLOCK_DEFAULT_NOT_DELETABLE(HttpStatus.BAD_REQUEST, "BLOCK_DEFAULT_NOT_DELETABLE", "기본 블록은 삭제할 수 없습니다."),
    BLOCK_INVALID_TARGET_AMOUNT(HttpStatus.BAD_REQUEST, "BLOCK_INVALID_TARGET_AMOUNT", "목표 금액은 0보다 커야 합니다."),
    BLOCK_INVALID_DUE_DATE(HttpStatus.BAD_REQUEST, "BLOCK_INVALID_DUE_DATE", "기한은 오늘 이후여야 합니다."),
    BLOCK_INVALID_PAY_DAY(HttpStatus.BAD_REQUEST, "BLOCK_INVALID_PAY_DAY", "납부일은 1~31 사이여야 합니다."),
    BLOCK_INVALID_ORDER(HttpStatus.BAD_REQUEST, "BLOCK_INVALID_ORDER", "블록 순서 목록이 현재 블록과 일치하지 않습니다."),
    BLOCK_CATEGORY_ALREADY_MAPPED(HttpStatus.CONFLICT, "BLOCK_CATEGORY_ALREADY_MAPPED", "이미 다른 블록에 연결된 카테고리입니다."),
    BLOCK_INSUFFICIENT_AMOUNT(HttpStatus.BAD_REQUEST, "BLOCK_INSUFFICIENT_AMOUNT", "이동할 금액이 블록 잔액보다 큽니다."),
    BLOCK_CONCURRENT_MODIFICATION(HttpStatus.CONFLICT, "BLOCK_CONCURRENT_MODIFICATION", "다른 곳에서 블록이 변경되었습니다. 다시 시도해 주세요.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
