package com.dothis.fintris.contextquestion.service;

import com.dothis.fintris.account.entity.LinkedAccount;
import com.dothis.fintris.block.entity.Block;
import com.dothis.fintris.contextquestion.entity.QuestionType;
import com.dothis.fintris.transaction.entity.Transaction;
import com.dothis.fintris.transaction.entity.TransactionSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ContextQuestionTextTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 14);
    private static final LocalDateTime YESTERDAY_EVENING = LocalDateTime.of(2026, 10, 13, 19, 22);

    private Transaction.TransactionBuilder<?, ?> transaction(String merchant, long amount, LocalDateTime occurredAt) {
        return Transaction.builder()
                .merchantOrPayee(merchant)
                .amount(amount)
                .occurredAt(occurredAt)
                .source(TransactionSource.CODEF);
    }

    @Test
    @DisplayName("어제 동기화된 간편결제는 '어제 결제한 ...'으로 묻는다")
    void unknownItemYesterday() {
        Transaction transaction = transaction("네이버페이", 38000, YESTERDAY_EVENING).build();

        assertThat(ContextQuestionText.text(QuestionType.UNKNOWN_ITEM, transaction, TODAY))
                .isEqualTo("어제 결제한 네이버페이 38,000원, 무엇을 샀어요?");
    }

    @Test
    @DisplayName("결제 알림으로 잡힌 거래는 '방금'으로 묻는다")
    void notificationSaysJustNow() {
        Transaction transaction = transaction("배달의민족", 23000, YESTERDAY_EVENING)
                .source(TransactionSource.NOTIFICATION).build();

        assertThat(ContextQuestionText.text(QuestionType.LATE_NIGHT_FOOD, transaction, TODAY))
                .isEqualTo("방금 결제한 배달의민족 23,000원, 무슨 일 있었어요?");
    }

    @Test
    @DisplayName("개인 송금은 받는 사람 없이 금액만으로 묻는다")
    void personalTransfer() {
        Transaction transaction = transaction("김철수", 30000, TODAY.atTime(12, 0)).build();

        assertThat(ContextQuestionText.text(QuestionType.PERSONAL_TRANSFER, transaction, TODAY))
                .isEqualTo("오늘 보낸 30,000원은 어떤 돈이었어요?");
    }

    @Test
    @DisplayName("이틀 넘게 지난 거래는 날짜로 말하고, 가맹점 이름이 없으면 금액만 쓴다")
    void olderWithoutMerchant() {
        Transaction transaction = transaction(null, 15000, LocalDateTime.of(2026, 10, 3, 9, 0)).build();

        assertThat(ContextQuestionText.text(QuestionType.LOW_CONFIDENCE, transaction, TODAY))
                .isEqualTo("10월 3일에 결제한 15,000원, 어떤 지출이었어요?");
    }

    @Test
    @DisplayName("거래 정보 줄은 날짜·요일·시각 뒤에 기관 이름과 번호 끝 두 자리를 붙인다")
    void meta() {
        LinkedAccount account = LinkedAccount.builder().institutionCode("0088").accountNumberLast4("3412").build();
        Transaction transaction = transaction("네이버페이", 38000, YESTERDAY_EVENING).linkedAccount(account).build();

        assertThat(ContextQuestionText.meta(transaction)).isEqualTo("10월 13일 화 19:22 · 신한은행 ••12");
    }

    @Test
    @DisplayName("연결 계좌가 없는 거래는 날짜만 준다")
    void metaWithoutAccount() {
        Transaction transaction = transaction("붕어빵", 3000, YESTERDAY_EVENING).build();

        assertThat(ContextQuestionText.meta(transaction)).isEqualTo("10월 13일 화 19:22");
    }

    private Block block(long id, String name) {
        Block block = Block.builder().name(name).build();
        ReflectionTestUtils.setField(block, "id", id);
        return block;
    }

    @Test
    @DisplayName("반영 블록이 그대로면 '그대로 둬요'라고 말한다")
    void categoryChangedSameBlock() {
        assertThat(ContextQuestionText.categoryChanged("생활/생필품", block(1, "생활비"), block(1, "생활비")))
                .isEqualTo("생활/생필품으로 바꾸고 생활비 블록에 그대로 둬요");
    }

    @Test
    @DisplayName("다른 블록으로 옮겨졌으면 옮겼다고 말한다")
    void categoryChangedMovedBlock() {
        assertThat(ContextQuestionText.categoryChanged("교통", block(1, "생활비"), block(2, "교통비")))
                .isEqualTo("교통으로 바꾸고 교통비 블록으로 옮겼어요");
    }

    @Test
    @DisplayName("블록에서 빠졌으면 뺀 돈을 돌려놨다고 말한다")
    void categoryChangedReleasedBlock() {
        assertThat(ContextQuestionText.categoryChanged("더치페이", block(1, "생활비"), null))
                .isEqualTo("더치페이로 바꾸고 생활비 블록에서 뺀 돈은 돌려놨어요");
    }

    @Test
    @DisplayName("블록과 상관없는 거래는 바꾼 카테고리만 말하고, 받침 ㄹ 뒤에는 '로'를 쓴다")
    void categoryChangedNoBlock() {
        assertThat(ContextQuestionText.categoryChanged("문화/여가", null, null)).isEqualTo("문화/여가로 바꿨어요");
        assertThat(ContextQuestionText.categoryChanged("의료/건강", null, null)).isEqualTo("의료/건강으로 바꿨어요");
        assertThat(ContextQuestionText.categoryChanged("지하철", null, null)).isEqualTo("지하철로 바꿨어요");
    }
}
