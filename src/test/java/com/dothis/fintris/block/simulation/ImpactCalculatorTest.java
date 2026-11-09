package com.dothis.fintris.block.simulation;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ImpactCalculatorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 14);

    @Test
    void 사용형에_돈이_들어오면_하루_쓸_돈이_늘어난다() {
        // 시안 03-2: 생활비 950,000 → 1,000,000, 남은 18일
        ImpactItem item = ImpactCalculator.dailyBudget(1L, "생활비", 950_000, 1_000_000,
                LocalDate.of(2026, 10, 31), TODAY).orElseThrow();

        assertThat(item.type()).isEqualTo(ImpactItem.Type.DAILY_BUDGET);
        assertThat(item.severity()).isEqualTo(ImpactItem.Severity.GOOD);
        assertThat(item.title()).isEqualTo("하루 쓸 돈 52,700원 → 55,500원");
    }

    @Test
    void 하루_쓸_돈이_같으면_항목을_만들지_않는다() {
        assertThat(ImpactCalculator.dailyBudget(1L, "생활비", 950_000, 950_050,
                LocalDate.of(2026, 10, 31), TODAY)).isEmpty();
    }

    @Test
    void 목표형에서_돈을_빼면_예상_달성일이_늦어진다() {
        // 시안 03-2: 여행 320,000 → 270,000, 목표 800,000, 월 200,000
        ImpactItem item = ImpactCalculator.goal(3L, "여행", 800_000, 200_000L,
                320_000, 270_000, 120_000, 70_000, TODAY).orElseThrow();

        assertThat(item.type()).isEqualTo(ImpactItem.Type.GOAL_DATE);
        assertThat(item.severity()).isEqualTo(ImpactItem.Severity.WARN);
        // 50,000 ÷ (200,000 / 30일) = 7.5일 → 8일 늦어짐
        assertThat(item.title()).isEqualTo("여행 목표가 약 8일 늦어져요");
        assertThat(item.detail()).isEqualTo("이번 달 확보액 120,000 → 70,000 (월 목표 200,000)");
    }

    @Test
    void 늦어지는_기간이_2주_이상이면_주_단위로_말한다() {
        ImpactItem item = ImpactCalculator.goal(3L, "여행", 800_000, 200_000L,
                320_000, 220_000, 120_000, 20_000, TODAY).orElseThrow();

        assertThat(item.title()).isEqualTo("여행 목표가 약 2주 늦어져요");
    }

    @Test
    void 이미_목표를_채웠으면_확보액_변화만_알려준다() {
        ImpactItem item = ImpactCalculator.goal(5L, "비상금", 500_000, 100_000L,
                600_000, 550_000, 100_000, 50_000, TODAY).orElseThrow();

        assertThat(item.type()).isEqualTo(ImpactItem.Type.MONTHLY_SECURED);
        assertThat(item.title()).isEqualTo("비상금 이번 달 확보액 100,000 → 50,000");
    }

    @Test
    void 고정지출은_납부액이_남아_있으면_영향_없음() {
        ImpactItem item = ImpactCalculator.payment(2L, "월세", 600_000, 25, 600_000, 600_000, TODAY);

        assertThat(item.severity()).isEqualTo(ImpactItem.Severity.GOOD);
        assertThat(item.title()).isEqualTo("월세 납부 영향 없음");
        assertThat(item.detail()).isEqualTo("10월 25일 600,000원 확보 유지");
    }

    @Test
    void 이번_이동으로_납부액이_모자라지면_danger() {
        ImpactItem item = ImpactCalculator.payment(2L, "월세", 600_000, 25, 600_000, 540_000, TODAY);

        assertThat(item.severity()).isEqualTo(ImpactItem.Severity.DANGER);
        assertThat(item.title()).isEqualTo("월세 10월 25일에 60,000원 모자라요");
    }

    @Test
    void 원래_모자랐고_이번_이동과_상관없으면_warn() {
        ImpactItem item = ImpactCalculator.payment(2L, "월세", 600_000, 25, 500_000, 500_000, TODAY);

        assertThat(item.severity()).isEqualTo(ImpactItem.Severity.WARN);
    }

    @Test
    void 결과는_danger_warn_good_info_순으로_정렬한다() {
        ImpactItem good = ImpactCalculator.payment(2L, "월세", 600_000, 25, 600_000, 600_000, TODAY);
        ImpactItem danger = ImpactCalculator.payment(4L, "통신비", 55_000, 15, 55_000, 0, TODAY);
        ImpactItem warn = ImpactCalculator.goal(3L, "여행", 800_000, 200_000L,
                320_000, 270_000, 120_000, 70_000, TODAY).orElseThrow();

        assertThat(ImpactCalculator.sorted(List.of(good, warn, danger)))
                .extracting(ImpactItem::severity)
                .containsExactly(ImpactItem.Severity.DANGER, ImpactItem.Severity.WARN, ImpactItem.Severity.GOOD);
    }
}
