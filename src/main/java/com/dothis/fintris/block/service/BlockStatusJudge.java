package com.dothis.fintris.block.service;

import com.dothis.fintris.block.entity.BlockStatus;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

// 블록 상태 판정 (기능명세서 F-11). 날짜가 지나면 상태도 바뀌어서 저장하지 않고 조회할 때마다 계산한다
// 기준값은 기능명세서의 초기값이다. 사용자 테스트 후 조정한다
public final class BlockStatusJudge {

    static final int CAUTION_MARGIN = 10; // 사용형: 소진율이 경과율보다 10%p 넘게 앞서면 주의
    static final int DANGER_MARGIN = 25;  // 25%p 넘게 앞서면 위험
    static final int FIXED_DANGER_DAYS = 3; // 고정지출형: 납부 3일 전 이내에 모자라면 위험

    private BlockStatusJudge() {
    }

    public record Judgement(BlockStatus status, String subLabel) {

        static Judgement safe(String subLabel) {
            return new Judgement(BlockStatus.SAFE, subLabel);
        }
    }

    // 사용형: 소진율(이번 기간 쓴 돈 ÷ 기간 초 배분액)이 경과율(지난 날 ÷ 기간 일수)보다 얼마나 앞서는지
    // 월 배분액을 정하지 않았으면 기간 초 배분액을 지금 잔액 + 쓴 돈으로 본다
    public static Judgement spending(long balance, Long monthlyAmount, LocalDate start, LocalDate end,
                                     LocalDate today, long periodSpent) {
        if (balance <= 0) {
            return new Judgement(BlockStatus.DANGER, "잔액 없음");
        }
        if (start == null || end == null || today.isBefore(start)) {
            return Judgement.safe(null);
        }
        long allocated = (monthlyAmount != null && monthlyAmount > 0) ? monthlyAmount : balance + periodSpent;
        long periodDays = ChronoUnit.DAYS.between(start, end) + 1;
        long elapsedDays = ChronoUnit.DAYS.between(start, today.isAfter(end) ? end : today) + 1;

        double gap = periodSpent * 100.0 / allocated - elapsedDays * 100.0 / periodDays;
        if (gap <= CAUTION_MARGIN) {
            return Judgement.safe(null);
        }
        return new Judgement(gap <= DANGER_MARGIN ? BlockStatus.CAUTION : BlockStatus.DANGER, "소진 빠름");
    }

    // 고정지출형: 다음 납부일에 낼 돈을 확보했는지. 모자라도 나눌 돈으로 메울 수 있으면 주의,
    // 메울 수 없는데 납부일이 3일 안이면 위험. 이번 납부를 이미 냈으면 안전
    public static Judgement fixed(long balance, Long payAmount, Integer payDay, LocalDate today,
                                  long waitingFundTotal, boolean paidThisCycle) {
        if (payAmount == null || payAmount <= 0) {
            return Judgement.safe(null);
        }
        if (paidThisCycle) {
            return Judgement.safe("이번 달 납부 완료");
        }
        long shortage = payAmount - balance;
        if (shortage <= 0) {
            return Judgement.safe("납부 준비 끝");
        }
        if (waitingFundTotal >= shortage || payDay == null) {
            return new Judgement(BlockStatus.CAUTION, "납부액 부족");
        }
        long daysLeft = ChronoUnit.DAYS.between(today, BlockCalculator.nextPayDate(today, payDay));
        return new Judgement(daysLeft <= FIXED_DANGER_DAYS ? BlockStatus.DANGER : BlockStatus.CAUTION, "납부액 부족");
    }
}
