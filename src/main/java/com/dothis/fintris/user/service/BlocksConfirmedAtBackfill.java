package com.dothis.fintris.user.service;

import com.dothis.fintris.user.repository.UserSettingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// 블록 확정 시각이 없으면 거래가 블록에 반영되지 않는다 (F-21).
// 서버 DB가 update 모드라 컬럼만 생기고 값은 비어 있어서, 앱이 뜰 때 한 번 채운다. 이미 채운 행은 건드리지 않는다
// 같은 처리를 SQL로 하려면 db/upgrade/2026-11-10_block-reflection.sql
@Slf4j
@Component
@RequiredArgsConstructor
public class BlocksConfirmedAtBackfill implements ApplicationRunner {

    private final UserSettingRepository userSettingRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int filled = userSettingRepository.backfillBlocksConfirmedAt();
        if (filled > 0) {
            log.info("블록 확정 시각이 없던 사용자 설정을 채웠습니다: {}건", filled);
        }
    }
}
