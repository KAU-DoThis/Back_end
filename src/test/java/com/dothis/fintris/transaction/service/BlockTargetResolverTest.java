package com.dothis.fintris.transaction.service;

import com.dothis.fintris.account.entity.LinkedAccount;
import com.dothis.fintris.account.entity.LinkedAccountType;
import com.dothis.fintris.block.entity.Block;
import com.dothis.fintris.block.entity.BlockRule;
import com.dothis.fintris.block.entity.BlockRule.AmountMode;
import com.dothis.fintris.block.entity.BlockRule.MatchCondition;
import com.dothis.fintris.block.entity.BlockType;
import com.dothis.fintris.transaction.entity.Transaction;
import com.dothis.fintris.transaction.entity.TransactionDirection;
import com.dothis.fintris.transaction.entity.TransactionSource;
import com.dothis.fintris.transaction.entity.TransactionStatus;
import com.dothis.fintris.user.entity.AuthProvider;
import com.dothis.fintris.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BlockTargetResolverTest {

    private final BlockTargetResolver resolver = new BlockTargetResolver();
    private final User user = User.builder().email("user@example.com").authProvider(AuthProvider.EMAIL).build();
    private final LinkedAccount bank = LinkedAccount.builder().user(user).institutionCode("0088")
            .type(LinkedAccountType.BANK).connectedId("cid-1").build();

    {
        ReflectionTestUtils.setField(bank, "id", 3L);
    }

    private final Block living = block(1L, BlockType.SPENDING, "생활비", true, null);
    private final Block travel = block(4L, BlockType.GOAL, "여행", false, null);

    private Block block(long id, BlockType type, String name, boolean isDefault, BlockRule rule) {
        Block block = Block.builder().user(user).type(type).name(name).balance(1_000_000L).isDefault(isDefault)
                .startDate(LocalDate.of(2026, 11, 1)).rule(rule).displayOrder((int) id).build();
        ReflectionTestUtils.setField(block, "id", id);
        return block;
    }

    private Block rent(MatchCondition condition) {
        return block(2L, BlockType.FIXED, "월세", false, new BlockRule(null, null, null, 25, 600_000L, condition, null));
    }

    private Transaction out(String payee, LocalDateTime at, long amount) {
        return Transaction.builder().user(user).linkedAccount(bank).occurredAt(at).amount(amount)
                .direction(TransactionDirection.OUT).merchantOrPayee(payee)
                .source(TransactionSource.CODEF).status(TransactionStatus.CONFIRMED).build();
    }

    @Test
    @DisplayName("고정지출 납부 조건(받는 분·금액·납부일)에 맞으면 그 고정지출 블록이다")
    void matchesFixed() {
        Block rent = rent(new MatchCondition("김*수", AmountMode.EXACT, null, null, 3, null));

        assertThat(resolver.resolve(out("김철수", LocalDateTime.of(2026, 11, 24, 10, 0), 600_000),
                List.of(living, rent, travel), null)).contains(rent);
    }

    @Test
    @DisplayName("금액·날짜·받는 분·계좌 중 하나라도 다르면 고정지출이 아니고 기본 생활비 블록이다")
    void fallsBackToDefault() {
        Block rent = rent(new MatchCondition("김*수", AmountMode.EXACT, null, null, 3, 3L));
        List<Block> blocks = List.of(living, rent);
        LocalDateTime payday = LocalDateTime.of(2026, 11, 25, 10, 0);

        assertThat(resolver.resolve(out("김철수", payday, 590_000), blocks, null)).contains(living);
        assertThat(resolver.resolve(out("김철수", payday.plusDays(4), 600_000), blocks, null)).contains(living);
        assertThat(resolver.resolve(out("이영희", payday, 600_000), blocks, null)).contains(living);
        LinkedAccount other = LinkedAccount.builder().user(user).institutionCode("0004")
                .type(LinkedAccountType.BANK).connectedId("cid-2").build();
        ReflectionTestUtils.setField(other, "id", 9L);
        Transaction fromOther = Transaction.builder().user(user).linkedAccount(other).occurredAt(payday)
                .amount(600_000).direction(TransactionDirection.OUT).merchantOrPayee("김철수")
                .source(TransactionSource.CODEF).status(TransactionStatus.CONFIRMED).build();
        assertThat(resolver.resolve(fromOther, blocks, null)).contains(living);
    }

    @Test
    @DisplayName("RANGE면 최소~최대 금액 안이면 맞다")
    void rangeAmount() {
        Block telecom = block(5L, BlockType.FIXED, "통신비", false, new BlockRule(null, null, null, 15, 55_000L,
                new MatchCondition("SKT", AmountMode.RANGE, 50_000L, 60_000L, null, null), null));

        assertThat(resolver.resolve(out("SKT", LocalDateTime.of(2026, 11, 16, 9, 0), 58_000),
                List.of(living, telecom), null)).contains(telecom);
        assertThat(resolver.resolve(out("SKT", LocalDateTime.of(2026, 11, 16, 9, 0), 61_000),
                List.of(living, telecom), null)).contains(living);
    }

    @Test
    @DisplayName("납부일 31일은 30일까지인 달에는 말일로 보고, 월초 경계도 가깝게 잰다")
    void payDayAtMonthEnd() {
        Block insurance = block(6L, BlockType.FIXED, "보험", false, new BlockRule(null, null, null, 31, 30_000L,
                new MatchCondition("보험", AmountMode.EXACT, null, null, 2, null), null));

        assertThat(resolver.resolve(out("보험", LocalDateTime.of(2026, 11, 30, 9, 0), 30_000),
                List.of(living, insurance), null)).contains(insurance);
        assertThat(resolver.resolve(out("보험", LocalDateTime.of(2026, 12, 1, 9, 0), 30_000),
                List.of(living, insurance), null)).contains(insurance);
    }

    @Test
    @DisplayName("고정지출이 아니면 카테고리에 연결된 블록, 그 블록이 삭제됐으면 기본 생활비 블록이다")
    void categoryBlock() {
        Transaction cafe = out("스타벅스", LocalDateTime.of(2026, 11, 7, 9, 0), 4_500);

        assertThat(resolver.resolve(cafe, List.of(living, travel), travel)).contains(travel);
        travel.delete(LocalDateTime.of(2026, 11, 8, 0, 0));
        assertThat(resolver.resolve(cafe, List.of(living), travel)).contains(living);
    }

    @Test
    @DisplayName("받는 분 조건이 없는 고정지출은 맞추지 않고, 기본 블록도 없으면 고르지 않는다")
    void noTarget() {
        Block noCondition = rent(null);

        assertThat(resolver.resolve(out("김철수", LocalDateTime.of(2026, 11, 25, 9, 0), 600_000),
                List.of(noCondition, travel), null)).isEmpty();
    }

    @Test
    @DisplayName("받는 분은 띄어쓰기를 무시하고 *를 아무 글자로 본다")
    void payeePattern() {
        assertThat(BlockTargetResolver.payeeMatches("김*수", "김 철 수")).isTrue();
        assertThat(BlockTargetResolver.payeeMatches("SKT 통신", "SKT통신")).isTrue();
        assertThat(BlockTargetResolver.payeeMatches("김*수", "박철수")).isFalse();
        assertThat(BlockTargetResolver.payeeMatches("(주)관리", "주관리")).isFalse();
    }
}
