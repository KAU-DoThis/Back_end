package com.dothis.fintris.goal.code;

import com.dothis.fintris.global.api.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum GoalErrorCode implements BaseErrorCode {
    GOAL_NOT_GOAL_BLOCK(HttpStatus.BAD_REQUEST, "GOAL_NOT_GOAL_BLOCK", "목표형 블록이 아닙니다."),
    GOAL_ACTION_NOT_ALLOWED(HttpStatus.CONFLICT, "GOAL_ACTION_NOT_ALLOWED", "지금 목표 상태에서는 할 수 없는 처리입니다."),
    GOAL_INVALID_RESOLUTION(HttpStatus.BAD_REQUEST, "GOAL_INVALID_RESOLUTION", "처리에 필요한 값이 없거나 올바르지 않습니다."),
    GOAL_NOTHING_SAVED(HttpStatus.BAD_REQUEST, "GOAL_NOTHING_SAVED", "모은 돈이 없어 이대로 끝낼 수 없습니다. 기한을 늘리거나 블록을 정리해 주세요.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
