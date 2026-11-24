package com.dothis.fintris.notification.repository;

import com.dothis.fintris.notification.entity.PushToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PushTokenRepository extends JpaRepository<PushToken, Long> {

    Optional<PushToken> findByToken(String token);
}
