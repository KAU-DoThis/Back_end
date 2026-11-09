package com.dothis.fintris.user.repository;

import com.dothis.fintris.user.entity.UserSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface UserSettingRepository extends JpaRepository<UserSetting, Long> {

    Optional<UserSetting> findByUser_Id(Long userId);

    // blocks_confirmed_at이 생기기 전에 블록을 확정한 행을 마지막 수정 시각으로 채운다
    // 확정이 온보딩의 마지막 단계라 updated_at이 확정 시각에 가장 가깝다. 네이티브라 updated_at은 바뀌지 않는다
    @Modifying
    @Query(value = """
            UPDATE user_settings SET blocks_confirmed_at = updated_at
            WHERE blocks_confirmed = 1 AND blocks_confirmed_at IS NULL
            """, nativeQuery = true)
    int backfillBlocksConfirmedAt();
}
