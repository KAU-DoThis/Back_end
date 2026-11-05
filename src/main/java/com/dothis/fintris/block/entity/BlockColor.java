package com.dothis.fintris.block.entity;

// 블록 색 코드 (API 명세 v4 블록 객체). 상태에 따른 채도 변화는 FE가 계산한다
public enum BlockColor {
    I, O, T, S, Z, J, L;

    // 색을 안 고르면 유형별 기본색을 쓴다 (시안 01 홈 기준)
    public static BlockColor defaultFor(BlockType type) {
        return switch (type) {
            case SPENDING -> I;
            case GOAL -> O;
            case FIXED -> L;
        };
    }
}
