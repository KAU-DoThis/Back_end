package com.dothis.fintris.global.config;

import com.dothis.fintris.auth.code.AuthErrorCode;
import com.dothis.fintris.global.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.nio.charset.StandardCharsets;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex.authenticationEntryPoint((request, response, e) -> {
                    // 전역 예외 핸들러 밖(필터 단계)이라 공통 응답 형식을 직접 쓴다
                    AuthErrorCode code = AuthErrorCode.AUTH_UNAUTHORIZED;
                    response.setStatus(code.getHttpStatus().value());
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                    response.getWriter().write(String.format(
                            "{\"isSuccess\":false,\"code\":\"%s\",\"message\":\"%s\",\"result\":null}",
                            code.getCode(), code.getMessage()));
                }))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(WebConfig.API_PREFIX + "/auth/**").permitAll()
                        // TODO: 블록 API가 X-User-Id 대신 로그인 사용자를 쓰도록 바뀌면 제거
                        .requestMatchers(WebConfig.API_PREFIX + "/blocks/**").permitAll()
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
