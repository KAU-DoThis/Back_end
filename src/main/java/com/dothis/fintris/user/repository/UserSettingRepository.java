package com.dothis.fintris.user.repository;

import com.dothis.fintris.user.entity.UserSetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserSettingRepository extends JpaRepository<UserSetting, Long> {

    Optional<UserSetting> findByUser_Id(Long userId);
}
