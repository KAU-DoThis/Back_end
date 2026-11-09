package com.dothis.fintris.user.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class UserSettingTest {

    @Test
    @DisplayName("초기 블록을 확정하면 확정 여부와 확정 시각을 남긴다")
    void confirmRecordsTime() {
        UserSetting setting = UserSetting.builder().build();

        setting.confirmBlocks();

        assertThat(setting.isBlocksConfirmed()).isTrue();
        assertThat(setting.getBlocksConfirmedAt()).isNotNull();
    }

    @Test
    @DisplayName("다시 확정해도 처음 확정 시각을 유지한다")
    void keepsFirstConfirmedTime() {
        UserSetting setting = UserSetting.builder().build();
        LocalDateTime first = LocalDateTime.of(2026, 11, 1, 10, 0);
        ReflectionTestUtils.setField(setting, "blocksConfirmedAt", first);

        setting.confirmBlocks();

        assertThat(setting.getBlocksConfirmedAt()).isEqualTo(first);
    }
}
