package com.dothis.fintris.transaction.dto;

import com.dothis.fintris.account.entity.LinkedAccount;
import com.dothis.fintris.account.entity.SupportedInstitution;
import com.dothis.fintris.block.entity.Block;
import com.dothis.fintris.category.entity.Category;
import com.dothis.fintris.transaction.entity.ClassifiedBy;
import com.dothis.fintris.transaction.entity.Transaction;
import com.dothis.fintris.transaction.entity.TransactionDirection;
import com.dothis.fintris.transaction.entity.TransactionSource;
import com.dothis.fintris.transaction.entity.TransactionStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// API 명세 v4 0장 거래 객체
@Schema(description = "거래 객체")
public record TransactionResponse(
        @Schema(example = "101") Long transactionId,
        @Schema(description = "현금 빠른 입력은 null") AccountItem linkedAccount,
        LocalDateTime occurredAt,
        @Schema(example = "38000") long amount,
        @Schema(example = "OUT") TransactionDirection direction,
        @Schema(example = "네이버페이") String merchantOrPayee,
        @Schema(example = "CODEF") TransactionSource source,
        @Schema(example = "NEEDS_REVIEW") TransactionStatus status,
        @Schema(description = "분류 전이면 null") CategoryItem category,
        @Schema(description = "true면 FE가 카테고리 이름 뒤에 물음표를 붙인다") boolean categoryUncertain,
        @Schema(example = "AUTO") ClassifiedBy classifiedBy,
        String contextTag,
        @Schema(description = "맥락 답변 표시용. 태그 목록(F-15)이 정해지면 채운다") String contextTagLabel,
        @Schema(description = "블록 반영 전이면 null") BlockItem block,
        boolean excluded,
        boolean isCanceled,
        @Schema(description = "빠른 입력이면 \"빠른 입력\"", example = "빠른 입력") String inputMethod
) {

    // 자동 분류 신뢰도가 이보다 낮으면 불확실로 본다. 기준은 아직 팀 결정 전
    private static final BigDecimal UNCERTAIN_CONFIDENCE = new BigDecimal("0.7");

    public static TransactionResponse from(Transaction transaction) {
        return new TransactionResponse(
                transaction.getId(),
                AccountItem.from(transaction.getLinkedAccount()),
                transaction.getOccurredAt(),
                transaction.getAmount(),
                transaction.getDirection(),
                transaction.getMerchantOrPayee(),
                transaction.getSource(),
                transaction.getStatus(),
                CategoryItem.from(transaction.getCategory()),
                isUncertain(transaction),
                transaction.getClassifiedBy(),
                transaction.getContextTag(),
                null,
                BlockItem.from(transaction.getBlock()),
                transaction.isExcluded(),
                transaction.isCanceled(),
                transaction.getSource() == TransactionSource.MANUAL ? "빠른 입력" : null
        );
    }

    private static boolean isUncertain(Transaction transaction) {
        return transaction.getCategory() != null
                && transaction.getClassifiedBy() == ClassifiedBy.AUTO
                && transaction.getConfidence() != null
                && transaction.getConfidence().compareTo(UNCERTAIN_CONFIDENCE) < 0;
    }

    public record AccountItem(
            @Schema(example = "3") Long linkedAccountId,
            @Schema(example = "신한카드") String institutionName,
            @Schema(description = "계좌·카드 번호 뒤 4자리", example = "1234") String last4
    ) {
        static AccountItem from(LinkedAccount account) {
            if (account == null) {
                return null;
            }
            String institutionName = SupportedInstitution.findByCode(account.getInstitutionCode())
                    .map(SupportedInstitution::getDisplayName)
                    .orElse(account.getInstitutionCode());
            return new AccountItem(account.getId(), institutionName, account.getAccountNumberLast4());
        }
    }

    public record CategoryItem(
            @Schema(example = "7") Long categoryId,
            @Schema(example = "LIVING") String code,
            @Schema(example = "생활/생필품") String name
    ) {
        static CategoryItem from(Category category) {
            return category == null ? null : new CategoryItem(category.getId(), category.getCode(), category.getName());
        }
    }

    public record BlockItem(
            @Schema(example = "1") Long blockId,
            @Schema(example = "생활비") String name
    ) {
        static BlockItem from(Block block) {
            return block == null ? null : new BlockItem(block.getId(), block.getName());
        }
    }
}
