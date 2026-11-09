package com.dothis.fintris.coach.entity;

import com.dothis.fintris.block.entity.BlockType;

import java.util.List;

// 제안 카드에 보여 준 숫자. coach_proposals.preview(JSON)에 그대로 저장한다
// 날짜는 JSON 변환 설정에 기대지 않도록 ISO 문자열로 둔다
public record TransferPreview(
        long amount,
        Side from,
        Side to,
        GoalDelay goalDelay,
        DailyBudget dailyBudget,
        FixedImpact fixedImpact,
        boolean requiresConfirm,
        boolean blocked,
        String blockedReason
) {

    // version은 수락할 때 그사이 블록이 바뀌었는지 비교하는 값
    public record Side(Long blockId, String name, BlockType type, long balanceBefore, long balanceAfter, Long version) {
    }

    // delayDays가 음수면 빨라짐
    public record GoalDelay(Long blockId, String name, String before, String after, long delayDays) {
    }

    public record DailyBudget(long before, long after, long diff) {
    }

    // 고정지출형 블록 전부. 하나라도 모자라면 affected
    public record FixedImpact(boolean affected, List<FixedItem> items) {
    }

    public record FixedItem(Long blockId, String name, String payDate, long payAmount, long shortage) {
    }
}
