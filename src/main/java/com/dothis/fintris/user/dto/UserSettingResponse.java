package com.dothis.fintris.user.dto;

import com.dothis.fintris.user.entity.Plan;
import com.dothis.fintris.user.entity.User;
import com.dothis.fintris.user.entity.UserSetting;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "온보딩 상태")
public record UserSettingResponse(
        @Schema(description = "첫 사용 안내 완료 여부. false면 다음 로그인 때 다시 보여 준다", example = "true")
        boolean onboardingDone,

        @Schema(description = "계좌 연동 나중에 하기(체험 모드) 여부", example = "false") boolean linkSkipped,

        @Schema(description = "연동 계좌 보유 여부", example = "false") boolean hasLinkedAccount,

        @Schema(example = "홍길동") String nickname,

        @Schema(example = "FREE") Plan plan
) {
    public static UserSettingResponse of(User user, UserSetting setting, boolean hasLinkedAccount) {
        return new UserSettingResponse(
                setting.isOnboardingDone(),
                setting.isLinkSkipped(),
                hasLinkedAccount,
                setting.getNickname(),
                user.getPlan()
        );
    }
}
