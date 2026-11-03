package com.dothis.fintris.auth.service;

import com.dothis.fintris.auth.client.KakaoApiClient;
import com.dothis.fintris.auth.code.AuthErrorCode;
import com.dothis.fintris.auth.dto.KakaoLoginResponse;
import com.dothis.fintris.auth.dto.LoginRequest;
import com.dothis.fintris.auth.dto.SignupRequest;
import com.dothis.fintris.auth.dto.TokenResponse;
import com.dothis.fintris.auth.entity.RefreshToken;
import com.dothis.fintris.auth.entity.SocialAccount;
import com.dothis.fintris.auth.repository.RefreshTokenRepository;
import com.dothis.fintris.auth.repository.SocialAccountRepository;
import com.dothis.fintris.global.exception.GeneralException;
import com.dothis.fintris.global.security.JwtTokenProvider;
import com.dothis.fintris.user.code.UserErrorCode;
import com.dothis.fintris.user.entity.AuthProvider;
import com.dothis.fintris.user.entity.User;
import com.dothis.fintris.user.repository.UserRepository;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private static final int MAX_LOGIN_FAILURES = 5;
    private static final long LOCK_MINUTES = 10;

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final SocialAccountRepository socialAccountRepository;
    private final KakaoApiClient kakaoApiClient;
    private final ObjectProvider<AuthService> self; // 프록시를 거쳐 트랜잭션을 적용하기 위해 사용
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional
    public TokenResponse signup(SignupRequest request) {
        String email = normalize(request.email());
        if (userRepository.findByEmail(email).isPresent()) {
            throw GeneralException.of(AuthErrorCode.AUTH_EMAIL_DUPLICATED);
        }
        User user = userRepository.save(User.builder()
                .email(email)
                .password(passwordEncoder.encode(request.password()))
                .authProvider(AuthProvider.EMAIL)
                .build());
        user.recordLoginSuccess(LocalDateTime.now());
        return issueTokens(user);
    }

    // 실패 횟수 기록이 예외로 롤백되지 않도록 GeneralException은 롤백에서 제외한다
    @Transactional(noRollbackFor = GeneralException.class)
    public TokenResponse login(LoginRequest request) {
        LocalDateTime now = LocalDateTime.now();
        // 이메일 존재 여부가 드러나지 않도록 없는 계정도 비밀번호 불일치와 같은 응답을 준다
        User user = userRepository.findByEmail(normalize(request.email()))
                .filter(u -> u.getDeletedAt() == null && u.getAuthProvider() == AuthProvider.EMAIL)
                .orElseThrow(() -> GeneralException.of(AuthErrorCode.AUTH_INVALID_CREDENTIALS));

        if (user.isLocked(now)) {
            throw GeneralException.of(AuthErrorCode.AUTH_ACCOUNT_LOCKED);
        }
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            user.recordLoginFailure(now, MAX_LOGIN_FAILURES, LOCK_MINUTES);
            throw GeneralException.of(AuthErrorCode.AUTH_INVALID_CREDENTIALS);
        }
        user.recordLoginSuccess(now);
        return issueTokens(user);
    }

    // 카카오 API 호출 동안 DB 트랜잭션을 잡고 있지 않도록 트랜잭션 밖에서 조회한 뒤 저장만 트랜잭션으로 처리한다
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public KakaoLoginResponse loginKakao(String kakaoAccessToken) {
        KakaoApiClient.KakaoUserInfo kakaoUser = kakaoApiClient.getUserInfo(kakaoAccessToken);
        return self.getObject().completeLoginKakao(kakaoUser);
    }

    @Transactional
    public KakaoLoginResponse completeLoginKakao(KakaoApiClient.KakaoUserInfo kakaoUser) {
        LocalDateTime now = LocalDateTime.now();
        SocialAccount existing = socialAccountRepository
                .findByProviderAndProviderUserId(AuthProvider.KAKAO, kakaoUser.providerUserId())
                .orElse(null);
        if (existing != null) {
            User user = existing.getUser();
            if (user.getDeletedAt() != null) {
                throw GeneralException.of(UserErrorCode.USER_NOT_FOUND);
            }
            user.recordLoginSuccess(now);
            return toKakaoResponse(issueTokens(user), false);
        }

        if (kakaoUser.email() == null || kakaoUser.email().isBlank()) {
            throw GeneralException.of(AuthErrorCode.AUTH_KAKAO_EMAIL_REQUIRED);
        }
        String email = normalize(kakaoUser.email());
        // 같은 이메일의 기존 계정과는 자동으로 합치지 않는다(계정 탈취 방지)
        if (userRepository.findByEmail(email).isPresent()) {
            throw GeneralException.of(AuthErrorCode.AUTH_EMAIL_DUPLICATED);
        }
        User user = userRepository.save(User.builder()
                .email(email)
                .authProvider(AuthProvider.KAKAO)
                .build());
        socialAccountRepository.save(SocialAccount.builder()
                .user(user)
                .provider(AuthProvider.KAKAO)
                .providerUserId(kakaoUser.providerUserId())
                .build());
        user.recordLoginSuccess(now);
        return toKakaoResponse(issueTokens(user), true);
    }

    // 사용한 리프레시 토큰은 폐기하고 새로 발급한다(재사용 방지)
    @Transactional
    public TokenResponse reissue(String refreshToken) {
        Claims claims = jwtTokenProvider.parseOrNull(refreshToken);
        if (claims == null || !jwtTokenProvider.isType(claims, JwtTokenProvider.TOKEN_TYPE_REFRESH)
                || refreshTokenRepository.deleteByToken(refreshToken) == 0) {
            throw GeneralException.of(AuthErrorCode.AUTH_INVALID_REFRESH_TOKEN);
        }
        User user = userRepository.findById(jwtTokenProvider.getUserId(claims))
                .filter(u -> u.getDeletedAt() == null)
                .orElseThrow(() -> GeneralException.of(UserErrorCode.USER_NOT_FOUND));
        return issueTokens(user);
    }

    // 이미 폐기됐거나 없는 토큰이어도 성공으로 처리한다
    @Transactional
    public void logout(String refreshToken) {
        refreshTokenRepository.deleteByToken(refreshToken);
    }

    private TokenResponse issueTokens(User user) {
        String accessToken = jwtTokenProvider.createAccessToken(user.getId());
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getId());
        refreshTokenRepository.save(RefreshToken.builder()
                .user(user)
                .token(refreshToken)
                .expiresAt(LocalDateTime.now().plusSeconds(jwtTokenProvider.getRefreshExpMs() / 1000))
                .build());
        return new TokenResponse(accessToken, refreshToken);
    }

    private static KakaoLoginResponse toKakaoResponse(TokenResponse tokens, boolean isNewUser) {
        return new KakaoLoginResponse(tokens.accessToken(), tokens.refreshToken(), isNewUser);
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
