package com.dothis.fintris.saving.code;

import com.dothis.fintris.global.api.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum SavingGoalErrorCode implements BaseErrorCode {
    SAVING_GOAL_NOT_FOUND(HttpStatus.NOT_FOUND, "SAVING_GOAL_NOT_FOUND", "절약 목표를 찾을 수 없습니다."),
    SAVING_GOAL_FORBIDDEN(HttpStatus.FORBIDDEN, "SAVING_GOAL_FORBIDDEN", "다른 사용자의 절약 목표에는 접근할 수 없습니다."),
    SAVING_GOAL_CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "SAVING_GOAL_CATEGORY_NOT_FOUND", "카테고리를 찾을 수 없습니다."),
    SAVING_GOAL_NO_EXPECTED_SAVING(HttpStatus.BAD_REQUEST, "SAVING_GOAL_NO_EXPECTED_SAVING",
            "목표가 지난달보다 적어야 아낄 돈이 생깁니다."),
    SAVING_GOAL_INVALID_PERIOD(HttpStatus.BAD_REQUEST, "SAVING_GOAL_INVALID_PERIOD", "기간이 올바르지 않습니다."),
    SAVING_GOAL_INVALID_AMOUNT(HttpStatus.BAD_REQUEST, "SAVING_GOAL_INVALID_AMOUNT",
            "목표 횟수나 목표 지출액 중 하나를 0 이상으로 보내야 합니다."),
    SAVING_GOAL_DUPLICATE_CATEGORY(HttpStatus.CONFLICT, "SAVING_GOAL_DUPLICATE_CATEGORY",
            "이 카테고리에는 이미 진행 중인 절약 목표가 있습니다."),
    SAVING_GOAL_INVALID_LINKED_BLOCK(HttpStatus.BAD_REQUEST, "SAVING_GOAL_INVALID_LINKED_BLOCK",
            "절약액을 채울 블록은 내 목표형 블록이어야 합니다."),
    SAVING_GOAL_NOT_ACTIVE(HttpStatus.CONFLICT, "SAVING_GOAL_NOT_ACTIVE", "진행 중인 절약 목표만 취소할 수 있습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
