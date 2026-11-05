package com.dothis.fintris.block.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;

// 색만으로 구분하지 않도록 상태 코드와 함께 라벨을 내려준다
@Getter
@AllArgsConstructor
public enum BlockStatus {
    SAFE("안전"),
    CAUTION("주의"),
    DANGER("위험");

    private final String label;
}
