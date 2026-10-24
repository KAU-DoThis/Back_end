package com.dothis.fintris.user.entity;

public enum ConsentRequirement {
    ALWAYS,   // 필수. 거부하면 가입 불가
    ON_LINK,  // 계좌 연동 시 필수
    OPTIONAL  // 선택
}
