package com.dothis.fintris.user.dto;

import com.dothis.fintris.user.entity.OnboardingStep;
import com.dothis.fintris.user.entity.Plan;
import com.dothis.fintris.user.entity.User;
import com.dothis.fintris.user.entity.UserSetting;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "온보딩 상태")
public record UserSettingResponse(
        @Schema(description = "CONSENT → LINK → TUTORIAL → BLOCKS → DONE. 앱 시작 시 이 단계 화면으로 보낸다",
                example = "TUTORIAL")
        OnboardingStep onboardingStep,

        @Schema(description = "연동 계좌 보유 여부", example = "true") boolean hasLinkedAccount,

        @Schema(description = "튜토리얼 완료 여부", example = "false") boolean tutorialDone,

        @Schema(description = "초기 블록 확정 여부. 초기 블록 확정 API가 바꾼다", example = "false") boolean blocksConfirmed,

        @Schema(description = "닉네임 (TBD: 필요 여부)", example = "홍길동") String nickname,

        @Schema(example = "FREE") Plan plan
) {
    public static UserSettingResponse of(User user, UserSetting setting,
                                         OnboardingStep onboardingStep, boolean hasLinkedAccount) {
        return new UserSettingResponse(
                onboardingStep,
                hasLinkedAccount,
                setting.isTutorialDone(),
                setting.isBlocksConfirmed(),
                setting.getNickname(),
                user.getPlan()
        );
    }
}
