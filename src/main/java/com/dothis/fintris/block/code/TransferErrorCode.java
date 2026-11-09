package com.dothis.fintris.block.code;

import com.dothis.fintris.global.api.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

// 블록 간 이동·나눌 돈 배분 (API 명세 v4 6장 이동 확정)
@Getter
@AllArgsConstructor
public enum TransferErrorCode implements BaseErrorCode {
    TRANSFER_INVALID_SOURCE(HttpStatus.BAD_REQUEST, "TRANSFER_INVALID_SOURCE", "보내는 쪽은 블록이나 나눌 돈 중 하나만 정해야 합니다."),
    TRANSFER_SAME_BLOCK(HttpStatus.BAD_REQUEST, "TRANSFER_SAME_BLOCK", "같은 블록으로는 옮길 수 없습니다."),
    TRANSFER_INSUFFICIENT_BALANCE(HttpStatus.BAD_REQUEST, "TRANSFER_INSUFFICIENT_BALANCE", "보내는 블록의 잔액보다 큰 금액입니다."),
    TRANSFER_CONFIRM_REQUIRED(HttpStatus.BAD_REQUEST, "TRANSFER_CONFIRM_REQUIRED", "규칙이 깨지는 이동입니다. 확인한 뒤 confirmed를 보내 주세요.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
