package com.dothis.fintris.block.simulation;

import com.dothis.fintris.block.dto.TransferRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SimulateRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private SimulateRequest request(long amount) {
        return new SimulateRequest(List.of(new SimulateRequest.Change(3L, null, 1L, amount)));
    }

    @Test
    @DisplayName("미리보기는 0원을 받는다 (이동 시트를 열 때 [부족한 만큼]만 먼저 받으려고)")
    void zeroAllowed() {
        assertThat(validator.validate(request(0))).isEmpty();
        assertThat(validator.validate(request(-1))).isNotEmpty();
    }

    @Test
    @DisplayName("실제 이동 확정은 여전히 0원을 막는다")
    void transferStillRejectsZero() {
        TransferRequest transfer = new TransferRequest(3L, 1L, 0L, null, 1L, 1L, null);

        assertThat(validator.validate(transfer)).isNotEmpty();
    }
}
