package com.dothis.fintris.notification.service;

import com.dothis.fintris.notification.entity.PushToken;
import com.dothis.fintris.notification.repository.PushTokenRepository;
import com.dothis.fintris.user.entity.User;
import com.dothis.fintris.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 앱 시작 때 등록하고 로그아웃 때 지운다 (v4 9장)
@Service
@RequiredArgsConstructor
@Transactional
public class PushTokenService {

    private final PushTokenRepository pushTokenRepository;
    private final UserRepository userRepository;

    // 앱이 켤 때마다 보내므로 이미 있으면 주인만 맞춘다
    public void register(Long userId, String token, String deviceId) {
        User user = userRepository.getReferenceById(userId);
        pushTokenRepository.findByToken(token).ifPresentOrElse(
                existing -> existing.assignTo(user, deviceId),
                () -> pushTokenRepository.save(PushToken.builder().user(user).token(token).deviceId(deviceId).build()));
    }

    // 내 토큰만 지운다. 없거나 남의 토큰이면 아무것도 하지 않는다 (로그아웃을 막지 않으려고)
    public void delete(Long userId, String token) {
        pushTokenRepository.findByToken(token)
                .filter(pushToken -> pushToken.getUser().getId().equals(userId))
                .ifPresent(pushTokenRepository::delete);
    }
}
