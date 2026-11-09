package com.dothis.fintris.block.exception;

import com.dothis.fintris.block.code.BlockErrorCode;
import com.dothis.fintris.global.api.ApiResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// @Version이 있는 엔티티는 블록뿐이라, 저장 시점의 낙관적 잠금 충돌은 블록 충돌로 응답한다
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class BlockExceptionHandler {

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Void>> onVersionConflict(OptimisticLockingFailureException e) {
        BlockErrorCode code = BlockErrorCode.BLOCK_VERSION_CONFLICT;
        return ResponseEntity.status(code.getHttpStatus()).body(ApiResponse.onFailure(code));
    }
}
