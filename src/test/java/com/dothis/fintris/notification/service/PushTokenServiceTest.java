package com.dothis.fintris.notification.service;

import com.dothis.fintris.notification.entity.PushToken;
import com.dothis.fintris.notification.repository.PushTokenRepository;
import com.dothis.fintris.user.entity.AuthProvider;
import com.dothis.fintris.user.entity.User;
import com.dothis.fintris.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PushTokenServiceTest {

    private static final String TOKEN = "ExponentPushToken[abc]";

    @Mock PushTokenRepository pushTokenRepository;
    @Mock UserRepository userRepository;
    @InjectMocks PushTokenService service;

    private User user(Long id) {
        User user = User.builder().email("user@example.com").authProvider(AuthProvider.EMAIL).build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    @Test
    @DisplayName("다른 계정이 쓰던 토큰이면 새로 로그인한 사용자로 옮긴다")
    void registerReassigns() {
        PushToken existing = PushToken.builder().user(user(2L)).token(TOKEN).build();
        when(userRepository.getReferenceById(1L)).thenReturn(user(1L));
        when(pushTokenRepository.findByToken(TOKEN)).thenReturn(Optional.of(existing));

        service.register(1L, TOKEN, "device-1");

        assertThat(existing.getUser().getId()).isEqualTo(1L);
        assertThat(existing.getDeviceId()).isEqualTo("device-1");
        verify(pushTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("처음 보는 토큰이면 저장한다")
    void registerNew() {
        when(userRepository.getReferenceById(1L)).thenReturn(user(1L));
        when(pushTokenRepository.findByToken(TOKEN)).thenReturn(Optional.empty());

        service.register(1L, TOKEN, null);

        verify(pushTokenRepository).save(any(PushToken.class));
    }

    @Test
    @DisplayName("남의 토큰은 지우지 않는다")
    void deleteOthersTokenIgnored() {
        when(pushTokenRepository.findByToken(TOKEN))
                .thenReturn(Optional.of(PushToken.builder().user(user(2L)).token(TOKEN).build()));

        service.delete(1L, TOKEN);

        verify(pushTokenRepository, never()).delete(any());
    }
}
