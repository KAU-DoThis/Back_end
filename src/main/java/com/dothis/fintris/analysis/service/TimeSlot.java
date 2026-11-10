package com.dothis.fintris.analysis.service;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalTime;

// 소비 분석 시간대 (API 명세 v4 8장). 새벽 0~6시는 밤에 넣는다
@Getter
@AllArgsConstructor
public enum TimeSlot {
    MORNING("아침 6~11시", "아침 6~11시"),
    LUNCH("점심 11~15시", "점심 11~15시"),
    EVENING("저녁 15~22시", "저녁 15~22시"),
    NIGHT("밤 22시~", "밤 10시 이후");

    private final String label;
    private final String summaryPhrase; // timeSummary 문장 "13번 중 9번이 {summaryPhrase}였어요"

    public static TimeSlot of(LocalTime time) {
        int hour = time.getHour();
        if (hour >= 6 && hour < 11) {
            return MORNING;
        }
        if (hour >= 11 && hour < 15) {
            return LUNCH;
        }
        if (hour >= 15 && hour < 22) {
            return EVENING;
        }
        return NIGHT;
    }
}
