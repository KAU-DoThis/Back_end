package com.dothis.fintris.global.security;

import com.dothis.fintris.global.api.code.GeneralErrorCode;
import com.dothis.fintris.global.config.WebConfig;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

// 비밀번호 대입, 금융기관 로그인 반복 시도, AI 호출 남용을 막는다. JWT 필터 뒤에서 돌아 사용자 ID를 쓸 수 있다
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String AUTH_PATH = WebConfig.API_PREFIX + "/auth/";
    private static final String LINK_PATH = WebConfig.API_PREFIX + "/linked-accounts";
    private static final String COACH_MESSAGE_PATH = WebConfig.API_PREFIX + "/coach/room/messages";

    // 로그인·가입·토큰 갱신: IP당 1분에 120번 (같은 와이파이를 쓰는 사람들이 한도를 나눠 쓴다)
    private static final int AUTH_LIMIT = 120;
    private static final Duration AUTH_WINDOW = Duration.ofMinutes(1);
    // 금융기관 연동: 사용자당 10분에 10번
    private static final int LINK_LIMIT = 10;
    private static final Duration LINK_WINDOW = Duration.ofMinutes(10);
    // 초불이에게 말 걸기: 사용자당 1분에 60번
    private static final int COACH_LIMIT = 60;
    private static final Duration COACH_WINDOW = Duration.ofMinutes(1);

    private final RateLimiter rateLimiter = new RateLimiter(System::currentTimeMillis);

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (!allowed(request)) {
            // 전역 예외 핸들러 밖(필터 단계)이라 공통 응답 형식을 직접 쓴다
            GeneralErrorCode code = GeneralErrorCode.TOO_MANY_REQUESTS;
            response.setStatus(code.getHttpStatus().value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write(String.format(
                    "{\"isSuccess\":false,\"code\":\"%s\",\"message\":\"%s\",\"result\":null}",
                    code.getCode(), code.getMessage()));
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean allowed(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path.startsWith(AUTH_PATH)) {
            return rateLimiter.tryAcquire("auth:" + request.getRemoteAddr(), AUTH_LIMIT, AUTH_WINDOW);
        }
        if (!"POST".equals(request.getMethod())) {
            return true;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        // 로그인하지 않은 요청은 뒤에서 401로 끝난다
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            return true;
        }
        if (path.equals(LINK_PATH)) {
            return rateLimiter.tryAcquire("link:" + userId, LINK_LIMIT, LINK_WINDOW);
        }
        if (path.equals(COACH_MESSAGE_PATH)) {
            return rateLimiter.tryAcquire("coach:" + userId, COACH_LIMIT, COACH_WINDOW);
        }
        return true;
    }
}
