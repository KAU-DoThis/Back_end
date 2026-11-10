package com.dothis.fintris.block.service;

import com.dothis.fintris.block.entity.BlockStatus;
import com.dothis.fintris.block.simulation.ImpactCalculator;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

// 블록 상태 판정 (기능명세서 F-11). 날짜가 지나면 상태도 바뀌어서 저장하지 않고 조회할 때마다 계산한다
// 기준값은 기능명세서의 초기값이다. 사용자 테스트 후 조정한다
public final class BlockStatusJudge {

    static final int CAUTION_MARGIN = 10; // 사용형: 소진율이 경과율보다 10%p 넘게 앞서면 주의
    static final int DANGER_MARGIN = 25;  // 25%p 넘게 앞서면 위험
    static final int FIXED_DANGER_DAYS = 3; // 고정지출형: 납부 3일 전 이내에 모자라면 위험
    static final double GOAL_JUDGE_FROM = 0.25; // 목표형: 이번 달이 25% 지나기 전에는 월말 예상으로 판정하지 않는다
    static final double GOAL_CAUTION_RATE = 0.7; // 월말 예상 확보율 70% 이상이면 주의, 미만이면 위험

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

    // 목표형 (기능명세서 F-11, 팀 협의로 보완)
    // 1. 다 모았으면 안전, 기한이 지났는데 못 모았으면 위험
    // 2. 월 확보 금액대로 모아도 기한을 넘기면 주의 ("2주 늦어짐")
    // 3. 이번 달 확보액이 월 확보 금액 이상이면 안전
    // 4. 달 초(경과율 25% 전)는 아직 안 들어온 돈이 많아 월말 예상으로 판정하지 않는다
    // 5. 월말 예상 확보율(이번 달 확보액 ÷ 경과율 ÷ 월 확보 금액)이 70~100%면 주의, 70% 미만이면 위험
    public static Judgement goal(long balance, long targetAmount, Long monthlyTarget, LocalDate dueDate,
                                 LocalDate today, long securedThisMonth) {
        if (balance >= targetAmount) {
            return Judgement.safe("목표 달성");
        }
        if (dueDate != null && dueDate.isBefore(today)) {
            return new Judgement(BlockStatus.DANGER, "기한 지남");
        }
        if (monthlyTarget == null || monthlyTarget <= 0) {
            return Judgement.safe(null);
        }
        LocalDate paceDate = ImpactCalculator.expectedDate(targetAmount, balance, monthlyTarget, today);
        if (dueDate != null && paceDate != null && paceDate.isAfter(dueDate)) {
            return new Judgement(BlockStatus.CAUTION,
                    ImpactCalculator.span(ChronoUnit.DAYS.between(dueDate, paceDate)) + " 늦어짐");
        }
        if (securedThisMonth >= monthlyTarget) {
            return Judgement.safe(null);
        }
        double monthElapsed = (double) today.getDayOfMonth() / today.lengthOfMonth();
        if (monthElapsed < GOAL_JUDGE_FROM) {
            return Judgement.safe(null);
        }
        double projectedRate = Math.max(0, securedThisMonth) / monthElapsed / monthlyTarget;
        if (projectedRate >= 1) {
            return Judgement.safe(null);
        }
        return new Judgement(projectedRate >= GOAL_CAUTION_RATE ? BlockStatus.CAUTION : BlockStatus.DANGER,
                "이번 달 확보 부족");
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
