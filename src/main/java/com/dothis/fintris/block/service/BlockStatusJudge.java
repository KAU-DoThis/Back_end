package com.dothis.fintris.block.service;

import com.dothis.fintris.block.entity.BlockStatus;
import com.dothis.fintris.block.simulation.ImpactCalculator;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

// 블록 상태 판정 (기능명세서 F-11). 날짜가 지나면 상태도 바뀌어서 저장하지 않고 조회할 때마다 계산한다
// 기준값은 기능명세서의 초기값이다. 사용자 테스트 후 조정한다
// 이유 문장(reason)은 템플릿이다. AI 문장은 AI 쪽 API가 생기면 이 문장을 대신한다 (실패하면 템플릿)
public final class BlockStatusJudge {

    static final int CAUTION_MARGIN = 10; // 사용형: 소진율이 경과율보다 10%p 넘게 앞서면 주의
    static final int DANGER_MARGIN = 25;  // 25%p 넘게 앞서면 위험
    static final int FIXED_DANGER_DAYS = 3; // 고정지출형: 납부 3일 전 이내에 모자라면 위험
    static final double GOAL_JUDGE_FROM = 0.25; // 목표형: 이번 달이 25% 지나기 전에는 월말 예상으로 판정하지 않는다
    static final double GOAL_CAUTION_RATE = 0.7; // 월말 예상 확보율 70% 이상이면 주의, 미만이면 위험

    private BlockStatusJudge() {
    }

    // subLabel: 상태 칩 옆 짧은 말, reason: 상세 화면 "왜 주의인가요?" 문장 (안전이면 null)
    public record Judgement(BlockStatus status, String subLabel, String reason) {

        static Judgement safe(String subLabel) {
            return new Judgement(BlockStatus.SAFE, subLabel, null);
        }

        public static Judgement unknown() {
            return safe(null);
        }
    }

    // 사용형: 소진율(이번 기간 쓴 돈 ÷ 기간 초 배분액)이 경과율(지난 날 ÷ 기간 일수)보다 얼마나 앞서는지
    // 월 배분액을 정하지 않았으면 기간 초 배분액을 지금 잔액 + 쓴 돈으로 본다
    public static Judgement spending(long balance, Long monthlyAmount, LocalDate start, LocalDate end,
                                     LocalDate today, long periodSpent) {
        if (balance <= 0) {
            return new Judgement(BlockStatus.DANGER, "잔액 없음",
                    "남은 돈이 없어요. 다른 블록이나 나눌 돈에서 채울 수 있어요.");
        }
        if (start == null || end == null || today.isBefore(start)) {
            return Judgement.safe(null);
        }
        long allocated = (monthlyAmount != null && monthlyAmount > 0) ? monthlyAmount : balance + periodSpent;
        long periodDays = ChronoUnit.DAYS.between(start, end) + 1;
        long elapsedDays = ChronoUnit.DAYS.between(start, today.isAfter(end) ? end : today) + 1;

        // 경계(정확히 10%p, 25%p)가 부동소수점 오차로 넘어가지 않게 정수 교차곱으로 비교한다
        // gap(%p) × 배분액 × 기간 일수 = 쓴 돈 × 100 × 기간 일수 − 지난 날 × 100 × 배분액
        long gapScaled = periodSpent * 100 * periodDays - elapsedDays * 100 * allocated;
        long unit = allocated * periodDays;
        if (gapScaled <= CAUTION_MARGIN * unit) {
            return Judgement.safe(null);
        }
        double burnRate = periodSpent * 100.0 / allocated;
        double elapsedRate = elapsedDays * 100.0 / periodDays;
        double gap = burnRate - elapsedRate;
        return new Judgement(gapScaled <= DANGER_MARGIN * unit ? BlockStatus.CAUTION : BlockStatus.DANGER, "소진 빠름",
                "기간의 %d%%가 지났는데 %d%%를 썼어요. 계획보다 %d%%p 빨라요."
                        .formatted(Math.round(elapsedRate), Math.round(burnRate), Math.round(gap)));
    }

    // 목표형 (기능명세서 F-11, 팀 협의로 보완)
    // 1. 다 모았으면 안전, 기한이 지났는데 못 모았으면 위험
    // 2. 이번 달 확보액이 월 확보 금액 이상이면 안전 (이슈 #58·F-11 안전 기준이라 늦어짐보다 먼저 본다)
    // 3. 월 확보 금액대로 모아도 기한을 넘기면 기한 내 달성 불가라 위험 ("2주 늦어짐", 이슈 #58·F-11 위험 기준)
    // 4. 달 초(경과율 25% 전)는 아직 안 들어온 돈이 많아 월말 예상으로 판정하지 않는다
    // 5. 월말 예상 확보율(이번 달 확보액 ÷ 경과율 ÷ 월 확보 금액)이 70~100%면 주의, 70% 미만이면 위험
    public static Judgement goal(long balance, long targetAmount, Long monthlyTarget, LocalDate dueDate,
                                 LocalDate today, long securedThisMonth) {
        if (balance >= targetAmount) {
            return Judgement.safe("목표 달성");
        }
        if (dueDate != null && dueDate.isBefore(today)) {
            return new Judgement(BlockStatus.DANGER, "기한 지남",
                    "목표일(%d월 %d일)이 지났어요. 기한을 늘리거나 모은 만큼으로 끝낼 수 있어요."
                            .formatted(dueDate.getMonthValue(), dueDate.getDayOfMonth()));
        }
        if (monthlyTarget == null || monthlyTarget <= 0) {
            return Judgement.safe(null);
        }
        if (securedThisMonth >= monthlyTarget) {
            return Judgement.safe(null);
        }
        LocalDate paceDate = ImpactCalculator.expectedDate(targetAmount, balance, monthlyTarget, today);
        if (dueDate != null && paceDate != null && paceDate.isAfter(dueDate)) {
            String late = ImpactCalculator.span(ChronoUnit.DAYS.between(dueDate, paceDate));
            return new Judgement(BlockStatus.DANGER, late + " 늦어짐",
                    "매달 %s원씩 모으면 목표일보다 %s 늦어져서 기한 안에 다 모으기 어려워요."
                            .formatted(ImpactCalculator.won(monthlyTarget), late));
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
                "이번 달 확보 부족",
                "이번 달 %s원 모았어요. 이대로면 월 목표 %s원의 %d%%만 모여요."
                        .formatted(ImpactCalculator.won(Math.max(0, securedThisMonth)),
                                ImpactCalculator.won(monthlyTarget), Math.round(projectedRate * 100)));
    }

    // 고정지출형: 다음 납부일에 낼 돈을 확보했는지. 모자라도 나눌 돈으로 메울 수 있으면 주의,
    // 메울 수 없는데 납부일이 3일 안이면 위험. 이번 달에 납부액만큼 다 냈으면 안전
    // 일부만 냈으면(금액 범위 조건) 남은 납부액 = 납부액 - 이번 달 낸 금액으로 부족액을 본다
    public static Judgement fixed(long balance, Long payAmount, Integer payDay, LocalDate today,
                                  long waitingFundTotal, long paidThisMonth) {
        if (payAmount == null || payAmount <= 0) {
            return Judgement.safe(null);
        }
        if (paidThisMonth >= payAmount) {
            return Judgement.safe("이번 달 납부 완료");
        }
        long shortage = payAmount - Math.max(0, paidThisMonth) - balance;
        if (shortage <= 0) {
            return Judgement.safe("납부 준비 끝");
        }
        boolean coverable = waitingFundTotal >= shortage;
        String fill = coverable ? " 나눌 돈으로 채울 수 있어요." : " 다른 블록에서 옮겨 와야 해요.";
        if (payDay == null) {
            return new Judgement(BlockStatus.CAUTION, "납부액 부족",
                    "납부액보다 %s원이 모자라요.".formatted(ImpactCalculator.won(shortage)) + fill);
        }
        LocalDate payDate = BlockCalculator.nextPayDate(today, payDay);
        String reason = "%d월 %d일 납부까지 %s원이 모자라요."
                .formatted(payDate.getMonthValue(), payDate.getDayOfMonth(), ImpactCalculator.won(shortage)) + fill;
        if (coverable) {
            return new Judgement(BlockStatus.CAUTION, "납부액 부족", reason);
        }
        long daysLeft = ChronoUnit.DAYS.between(today, payDate);
        return new Judgement(daysLeft <= FIXED_DANGER_DAYS ? BlockStatus.DANGER : BlockStatus.CAUTION, "납부액 부족",
                reason);
    }
}
