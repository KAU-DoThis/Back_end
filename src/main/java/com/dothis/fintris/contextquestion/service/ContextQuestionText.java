package com.dothis.fintris.contextquestion.service;

import com.dothis.fintris.account.entity.LinkedAccount;
import com.dothis.fintris.account.entity.SupportedInstitution;
import com.dothis.fintris.contextquestion.entity.QuestionType;
import com.dothis.fintris.transaction.entity.Transaction;
import com.dothis.fintris.transaction.entity.TransactionSource;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

// 질문 문장 틀 (기능명세서 F-15 표). AI 문장 생성이 붙기 전까지 이 틀을 쓴다
public final class ContextQuestionText {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("M월 d일");
    private static final DateTimeFormatter META = DateTimeFormatter.ofPattern("M월 d일 E HH:mm", Locale.KOREAN);

    private ContextQuestionText() {
    }

    public static String text(QuestionType type, Transaction transaction, LocalDate today) {
        String when = when(transaction, today);
        String amount = "%,d원".formatted(transaction.getAmount());
        if (type == QuestionType.PERSONAL_TRANSFER) {
            return "%s 보낸 %s은 어떤 돈이었어요?".formatted(when, amount);
        }
        String merchant = transaction.getMerchantOrPayee();
        String target = merchant == null || merchant.isBlank() ? amount : merchant + " " + amount;
        String ask = switch (type) {
            case UNKNOWN_ITEM -> "무엇을 샀어요?";
            case LATE_NIGHT_FOOD -> "무슨 일 있었어요?";
            default -> "어떤 지출이었어요?";
        };
        return "%s 결제한 %s, %s".formatted(when, target, ask);
    }

    // 예: "10월 13일 화 19:22 · 신한카드 ••12". 현금 거래는 날짜만
    public static String meta(Transaction transaction) {
        String date = transaction.getOccurredAt().format(META);
        LinkedAccount account = transaction.getLinkedAccount();
        if (account == null) {
            return date;
        }
        String institution = SupportedInstitution.findByCode(account.getInstitutionCode())
                .map(SupportedInstitution::getDisplayName)
                .orElse(account.getInstitutionCode());
        String last4 = account.getAccountNumberLast4();
        if (last4 == null || last4.length() < 2) {
            return date + " · " + institution;
        }
        return "%s · %s ••%s".formatted(date, institution, last4.substring(last4.length() - 2));
    }

    // 결제 알림으로 잡힌 거래는 "방금", 동기화로 들어온 거래는 날짜로 말한다
    private static String when(Transaction transaction, LocalDate today) {
        if (transaction.getSource() == TransactionSource.NOTIFICATION) {
            return "방금";
        }
        LocalDate date = transaction.getOccurredAt().toLocalDate();
        if (date.equals(today)) {
            return "오늘";
        }
        if (date.equals(today.minusDays(1))) {
            return "어제";
        }
        return date.format(DAY) + "에";
    }
}
