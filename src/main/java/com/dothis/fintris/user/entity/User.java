package com.dothis.fintris.user.entity;

import com.dothis.fintris.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

// 인증 로직(로그인·JWT)은 auth 패키지에서 다룬다
@Entity
@Table(name = "users")
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long id;

    @Column(nullable = false)
    private String email;

    private String password; // 소셜 가입은 null

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuthProvider authProvider;

    @Column(nullable = false)
    private boolean emailVerified;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Plan plan = Plan.FREE;

    private LocalDateTime lastLoginAt;

    @Column(nullable = false)
    private int failedLoginCount;

    private LocalDateTime lockedUntil;

    private LocalDateTime deletedAt;
}
