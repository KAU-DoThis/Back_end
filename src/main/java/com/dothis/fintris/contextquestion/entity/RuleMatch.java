package com.dothis.fintris.contextquestion.entity;

import com.dothis.fintris.analysis.service.TimeSlot;
import com.dothis.fintris.transaction.entity.Transaction;

// 규칙이 맞는 거래 (기능명세서 F-15): 개인 송금은 받는 사람, 나머지는 가맹점 + 시간대
public record RuleMatch(String merchant, TimeSlot timeSlot, String payee) {

    // 가맹점·받는 사람 이름이 없으면 같은 거래인지 알 수 없어 null
    public static RuleMatch of(QuestionType type, Transaction transaction) {
        String name = transaction.getMerchantOrPayee();
        if (name == null || name.isBlank()) {
            return null;
        }
        if (type == QuestionType.PERSONAL_TRANSFER) {
            return new RuleMatch(null, null, name);
        }
        return new RuleMatch(name, TimeSlot.of(transaction.getOccurredAt().toLocalTime()), null);
    }
}
