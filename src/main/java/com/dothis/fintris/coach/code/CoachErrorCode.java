package com.dothis.fintris.coach.code;

import com.dothis.fintris.global.api.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

// 초불이의 방 이동 제안 (F-16)
@Getter
@AllArgsConstructor
public enum CoachErrorCode implements BaseErrorCode {
    COACH_PROPOSAL_NOT_FOUND(HttpStatus.NOT_FOUND, "COACH_PROPOSAL_NOT_FOUND", "제안을 찾을 수 없습니다."),
    COACH_PROPOSAL_FORBIDDEN(HttpStatus.FORBIDDEN, "COACH_PROPOSAL_FORBIDDEN", "다른 사용자의 제안에는 접근할 수 없습니다."),
    COACH_PROPOSAL_EXPIRED(HttpStatus.CONFLICT, "COACH_PROPOSAL_EXPIRED", "시간이 지나 만료된 제안입니다. 다시 요청해 주세요."),
    COACH_PROPOSAL_ALREADY_HANDLED(HttpStatus.CONFLICT, "COACH_PROPOSAL_ALREADY_HANDLED", "이미 수락했거나 거절한 제안입니다."),
    COACH_PROPOSAL_STALE(HttpStatus.CONFLICT, "COACH_PROPOSAL_STALE", "제안 뒤에 블록이 바뀌었습니다. 다시 요청해 주세요."),
    COACH_PROPOSAL_NOT_UNDOABLE(HttpStatus.CONFLICT, "COACH_PROPOSAL_NOT_UNDOABLE", "되돌릴 수 없는 제안입니다."),
    COACH_UNDO_INSUFFICIENT_BALANCE(HttpStatus.BAD_REQUEST, "COACH_UNDO_INSUFFICIENT_BALANCE", "받은 블록의 잔액이 옮긴 금액보다 적어 되돌릴 수 없습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
