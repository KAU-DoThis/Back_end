package com.dothis.fintris.user.service;

import com.dothis.fintris.user.repository.UserSettingRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BlocksConfirmedAtBackfillTest {

    @Mock UserSettingRepository userSettingRepository;
    @InjectMocks BlocksConfirmedAtBackfill backfill;

    @Test
    @DisplayName("앱이 뜰 때 블록 확정 시각이 없는 행을 한 번 채운다")
    void backfillsOnStartup() {
        backfill.run(null);

        verify(userSettingRepository).backfillBlocksConfirmedAt();
    }
}
