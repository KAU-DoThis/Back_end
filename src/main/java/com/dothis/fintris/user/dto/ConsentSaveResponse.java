package com.dothis.fintris.user.dto;

import com.dothis.fintris.user.entity.OnboardingStep;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "동의 저장 결과")
public record ConsentSaveResponse(
        @Schema(description = "저장 후 온보딩 단계", example = "LINK") OnboardingStep onboardingStep
) {
}
