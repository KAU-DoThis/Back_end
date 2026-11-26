package com.dothis.fintris.block.service;

import com.dothis.fintris.block.entity.BlockStatus;
import com.dothis.fintris.block.service.BlockStatusJudge.Judgement;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class BlockStatusJudgeTest {

    private static final LocalDate START = LocalDate.of(2026, 11, 1);
    private static final LocalDate END = LocalDate.of(2026, 11, 30);
    // 10일째: 경과율 10 ÷ 30 = 33.3%
    private static final LocalDate DAY_10 = LocalDate.of(2026, 11, 10);

    @Nested
    @DisplayName("사용형")
    class Spending {

        @Test
        @DisplayName("소진율이 경과율 + 10%p 이내면 안전")
        void safe() {
            // 소진율 1,290,000 ÷ 3,000,000 = 43% (경과율 + 9.7%p)
            Judgement result = BlockStatusJudge.spending(1_710_000, 3_000_000L, START, END, DAY_10, 1_290_000);

            assertThat(result).extracting(Judgement::status, Judgement::subLabel).containsExactly(BlockStatus.SAFE, null);
        }

        @Test
        @DisplayName("경과율보다 10~25%p 앞서면 주의, 25%p 넘게 앞서면 위험")
        void cautionAndDanger() {
            // 소진율 50% → +16.7%p
            assertThat(BlockStatusJudge.spending(1_500_000, 3_000_000L, START, END, DAY_10, 1_500_000))
                    .extracting(Judgement::status, Judgement::subLabel).containsExactly(BlockStatus.CAUTION, "소진 빠름");
            // 소진율 60% → +26.7%p
            assertThat(BlockStatusJudge.spending(1_200_000, 3_000_000L, START, END, DAY_10, 1_800_000))
                    .extracting(Judgement::status, Judgement::subLabel).containsExactly(BlockStatus.DANGER, "소진 빠름");
        }

        @Test
        @DisplayName("정확히 10%p는 안전, 정확히 25%p는 주의 (부동소수점 오차로 넘어가지 않는다)")
        void exactBoundary() {
            LocalDate start = LocalDate.of(2026, 11, 1);
            LocalDate end = LocalDate.of(2026, 11, 30);
            LocalDate day7 = LocalDate.of(2026, 11, 7); // 경과율 7/30 = 23.33%

            // 100,000 ÷ 300,000 = 33.33% → 정확히 +10%p
            assertThat(BlockStatusJudge.spending(200_000, 300_000L, start, end, day7, 100_000).status())
                    .isEqualTo(BlockStatus.SAFE);
            assertThat(BlockStatusJudge.spending(199_999, 300_000L, start, end, day7, 100_001).status())
                    .isEqualTo(BlockStatus.CAUTION);
            // 145,000 ÷ 300,000 = 48.33% → 정확히 +25%p
            assertThat(BlockStatusJudge.spending(155_000, 300_000L, start, end, day7, 145_000).status())
                    .isEqualTo(BlockStatus.CAUTION);
            assertThat(BlockStatusJudge.spending(154_999, 300_000L, start, end, day7, 145_001).status())
                    .isEqualTo(BlockStatus.DANGER);
        }

        @Test
        @DisplayName("잔액이 0이면 쓴 속도와 상관없이 위험")
        void emptyBalance() {
            assertThat(BlockStatusJudge.spending(0, 3_000_000L, START, END, DAY_10, 0))
                    .extracting(Judgement::status, Judgement::subLabel).containsExactly(BlockStatus.DANGER, "잔액 없음");
        }

        @Test
        @DisplayName("월 배분액이 없으면 잔액 + 쓴 돈을 기간 초 배분액으로 본다")
        void withoutMonthlyAmount() {
            // 배분액 1,000,000 → 소진율 60%
            assertThat(BlockStatusJudge.spending(400_000, null, START, END, DAY_10, 600_000).status())
                    .isEqualTo(BlockStatus.DANGER);
        }

        @Test
        @DisplayName("기간을 모르거나 기간 시작 전이면 판정하지 않고 안전")
        void noPeriod() {
            assertThat(BlockStatusJudge.spending(100, 3_000_000L, null, null, DAY_10, 2_000_000).status())
                    .isEqualTo(BlockStatus.SAFE);
            assertThat(BlockStatusJudge.spending(100, 3_000_000L, START, END, START.minusDays(1), 0).status())
                    .isEqualTo(BlockStatus.SAFE);
        }
    }

    @Nested
    @DisplayName("목표형")
    class Goal {

        private static final long TARGET = 1_000_000;
        private static final long MONTHLY = 200_000;
        // 월 20만 원이면 남은 100만 원을 150일에 모은다 → 기한 안
        private static final LocalDate FAR_DUE = LocalDate.of(2027, 12, 31);

        private Judgement onDay(int day, long securedThisMonth) {
            return BlockStatusJudge.goal(0, TARGET, MONTHLY, FAR_DUE, LocalDate.of(2026, 11, day), securedThisMonth);
        }

        @Test
        @DisplayName("다 모았으면 안전, 기한이 지났는데 못 모았으면 위험")
        void achievedAndExpired() {
            assertThat(BlockStatusJudge.goal(TARGET, TARGET, MONTHLY, FAR_DUE, DAY_10, 0))
                    .extracting(Judgement::status, Judgement::subLabel).containsExactly(BlockStatus.SAFE, "목표 달성");
            assertThat(BlockStatusJudge.goal(500_000, TARGET, MONTHLY, START, DAY_10, 0))
                    .extracting(Judgement::status, Judgement::subLabel).containsExactly(BlockStatus.DANGER, "기한 지남");
        }

        @Test
        @DisplayName("월 확보 금액대로 모아도 기한을 넘기면 기한 내 달성 불가라 위험과 늦어지는 기간")
        void lateAtMonthlyPace() {
            // 월 10만 원이면 300일 뒤 → 2027-03-31 기한보다 23주 늦음
            Judgement result = BlockStatusJudge.goal(0, TARGET, 100_000L, LocalDate.of(2027, 3, 31), DAY_10, 0);

            assertThat(result).extracting(Judgement::status, Judgement::subLabel).containsExactly(BlockStatus.DANGER, "23주 늦어짐");
        }

        @Test
        @DisplayName("이번 달 몫을 다 채웠으면 월 확보 금액대로는 늦어지더라도 안전")
        void monthlyMetBeforeLate() {
            Judgement result = BlockStatusJudge.goal(0, TARGET, 100_000L, LocalDate.of(2027, 3, 31), DAY_10, 100_000);

            assertThat(result.status()).isEqualTo(BlockStatus.SAFE);
        }

        @Test
        @DisplayName("달 초(경과율 25% 전)는 아직 0원이어도 안전")
        void earlyMonth() {
            assertThat(onDay(5, 0).status()).isEqualTo(BlockStatus.SAFE);
        }

        @Test
        @DisplayName("이번 달 확보액이 월 확보 금액 이상이거나 월말 예상이 100% 이상이면 안전")
        void onTrack() {
            assertThat(onDay(20, 200_000).status()).isEqualTo(BlockStatus.SAFE);
            // 15일(50%)에 12만 원 → 월말 24만 원 예상
            assertThat(onDay(15, 120_000).status()).isEqualTo(BlockStatus.SAFE);
        }

        @Test
        @DisplayName("월말 예상 확보율 70~100%면 주의, 70% 미만이면 위험")
        void projected() {
            // 15일(50%)에 8만 원 → 월말 16만 원 = 80%
            assertThat(onDay(15, 80_000)).extracting(Judgement::status, Judgement::subLabel).containsExactly(BlockStatus.CAUTION, "이번 달 확보 부족");
            // 15일(50%)에 5만 원 → 월말 10만 원 = 50%
            assertThat(onDay(15, 50_000)).extracting(Judgement::status, Judgement::subLabel).containsExactly(BlockStatus.DANGER, "이번 달 확보 부족");
        }

        @Test
        @DisplayName("월 확보 금액을 정하지 않았으면 달성·기한만 본다")
        void withoutMonthlyTarget() {
            assertThat(BlockStatusJudge.goal(0, TARGET, null, FAR_DUE, LocalDate.of(2026, 11, 28), 0).status())
                    .isEqualTo(BlockStatus.SAFE);
        }
    }

    @Nested
    @DisplayName("고정지출형")
    class Fixed {

        // 납부일 25일
        private static final int PAY_DAY = 25;

        @Test
        @DisplayName("납부액을 다 모았으면 안전")
        void ready() {
            assertThat(BlockStatusJudge.fixed(600_000, 600_000L, PAY_DAY, DAY_10, 0, 0))
                    .extracting(Judgement::status, Judgement::subLabel).containsExactly(BlockStatus.SAFE, "납부 준비 끝");
        }

        @Test
        @DisplayName("이번 납부를 이미 냈으면 잔액이 비어도 안전")
        void alreadyPaid() {
            assertThat(BlockStatusJudge.fixed(0, 600_000L, PAY_DAY, DAY_10, 0, 600_000))
                    .extracting(Judgement::status, Judgement::subLabel).containsExactly(BlockStatus.SAFE, "이번 달 납부 완료");
        }

        @Test
        @DisplayName("일부만 냈으면 남은 납부액(납부액 - 낸 금액)으로 부족액을 본다")
        void partiallyPaid() {
            LocalDate twoDaysBefore = LocalDate.of(2026, 11, 23);
            // 600,000원 중 400,000원을 냈고 잔액 200,000원 → 남은 납부액을 잔액으로 채울 수 있다
            assertThat(BlockStatusJudge.fixed(200_000, 600_000L, PAY_DAY, twoDaysBefore, 0, 400_000))
                    .extracting(Judgement::status, Judgement::subLabel).containsExactly(BlockStatus.SAFE, "납부 준비 끝");
            // 잔액 100,000원이면 100,000원이 모자라고, 나눌 돈이 없으니 납부 3일 전 이내라 위험
            assertThat(BlockStatusJudge.fixed(100_000, 600_000L, PAY_DAY, twoDaysBefore, 0, 400_000))
                    .extracting(Judgement::status, Judgement::reason)
                    .containsExactly(BlockStatus.DANGER, "11월 25일 납부까지 100,000원이 모자라요. 다른 블록에서 옮겨 와야 해요.");
        }

        @Test
        @DisplayName("모자라도 나눌 돈으로 메울 수 있으면 납부일이 가까워도 주의")
        void coverable() {
            LocalDate twoDaysBefore = LocalDate.of(2026, 11, 23);
            assertThat(BlockStatusJudge.fixed(500_000, 600_000L, PAY_DAY, twoDaysBefore, 100_000, 0))
                    .extracting(Judgement::status, Judgement::subLabel).containsExactly(BlockStatus.CAUTION, "납부액 부족");
        }

        @Test
        @DisplayName("메울 수 없으면 납부 3일 전부터 위험, 그 전에는 주의")
        void notCoverable() {
            assertThat(BlockStatusJudge.fixed(500_000, 600_000L, PAY_DAY, LocalDate.of(2026, 11, 22), 0, 0).status())
                    .isEqualTo(BlockStatus.DANGER);
            assertThat(BlockStatusJudge.fixed(500_000, 600_000L, PAY_DAY, LocalDate.of(2026, 11, 21), 0, 0).status())
                    .isEqualTo(BlockStatus.CAUTION);
        }

        @Test
        @DisplayName("납부일이 그 달에 없으면 말일 기준으로 3일을 센다")
        void payDayMissingInMonth() {
            // 2월은 28일까지 → 31일 납부는 2/28
            assertThat(BlockStatusJudge.fixed(0, 600_000L, 31, LocalDate.of(2027, 2, 25), 0, 0).status())
                    .isEqualTo(BlockStatus.DANGER);
        }
    }

    @Nested
    @DisplayName("상태 이유 문장")
    class Reason {

        @Test
        @DisplayName("안전이면 이유 문장이 없다")
        void safeHasNoReason() {
            assertThat(BlockStatusJudge.spending(1_710_000, 3_000_000L, START, END, DAY_10, 1_290_000).reason()).isNull();
        }

        @Test
        @DisplayName("사용형은 지난 기간과 쓴 비율을 숫자로 알려준다")
        void spending() {
            // 10일째(33%)에 50%를 씀 → 17%p 빠름
            assertThat(BlockStatusJudge.spending(1_500_000, 3_000_000L, START, END, DAY_10, 1_500_000).reason())
                    .isEqualTo("기간의 33%가 지났는데 50%를 썼어요. 계획보다 17%p 빨라요.");
        }

        @Test
        @DisplayName("고정지출형은 납부일과 모자란 금액, 채울 방법을 알려준다")
        void fixed() {
            assertThat(BlockStatusJudge.fixed(500_000, 600_000L, 25, DAY_10, 100_000, 0).reason())
                    .isEqualTo("11월 25일 납부까지 100,000원이 모자라요. 나눌 돈으로 채울 수 있어요.");
            assertThat(BlockStatusJudge.fixed(500_000, 600_000L, 25, DAY_10, 0, 0).reason())
                    .endsWith("다른 블록에서 옮겨 와야 해요.");
        }

        @Test
        @DisplayName("목표형은 늦어지는 기간이나 이번 달 예상 확보율을 알려준다")
        void goal() {
            assertThat(BlockStatusJudge.goal(0, 1_000_000, 100_000L, LocalDate.of(2027, 3, 31), DAY_10, 0).reason())
                    .isEqualTo("매달 100,000원씩 모으면 목표일보다 23주 늦어져서 기한 안에 다 모으기 어려워요.");
            assertThat(BlockStatusJudge.goal(0, 1_000_000, 200_000L, LocalDate.of(2027, 12, 31),
                    LocalDate.of(2026, 11, 15), 80_000).reason())
                    .isEqualTo("이번 달 80,000원 모았어요. 이대로면 월 목표 200,000원의 80%만 모여요.");
        }
    }
}
