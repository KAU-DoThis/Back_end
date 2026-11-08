package com.dothis.fintris.transaction.dto;

import com.dothis.fintris.transaction.dto.TransactionResponse.AccountItem;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

// API 명세 v4 4장 거래 목록. 날짜별로 묶고, 하루가 두 페이지에 걸치면 FE가 같은 날짜끼리 이어 붙인다
@Schema(description = "거래 목록 응답")
public record TransactionListResponse(
        Summary summary,
        // TODO: 맥락 질문(F-15)이 붙으면 가장 최근 미답변 질문 1개로 채운다
        @Schema(description = "가장 최근 미답변 맥락 질문. 없으면 null") PendingQuestion pendingQuestion,
        Counts counts,
        List<Day> days,
        boolean hasNext
) {

    public record Summary(
            Period period,
            @Schema(description = "기간 내 출금 합계(소비 제외·취소 거래 제외)", example = "588000") long spent,
            @Schema(example = "1") long provisionalCount,
            @Schema(description = "기간 내 입금 합계(취소·본인 계좌 이동 제외)", example = "0") long income,
            // TODO: 입금원 분석(v4 6-1장)이 붙으면 홈과 같은 값으로 채운다
            @Schema(description = "다음 입금 예정. 없으면 null") NextExpectedIncome nextExpectedIncome,
            @Schema(description = "linkedAccountId로 조회했을 때만 채운다") AccountItem account
    ) {
    }

    public record Period(
            @Schema(example = "2026-10-01") LocalDate from,
            @Schema(description = "이번 달이면 오늘, 지난달이면 말일", example = "2026-10-14") LocalDate to
    ) {
    }

    public record NextExpectedIncome(
            @Schema(example = "월급") String label,
            @Schema(example = "2026-10-25") LocalDate expectedDate
    ) {
    }

    public record PendingQuestion(
            @Schema(example = "55") Long questionId,
            @Schema(example = "네이버페이 38,000원, 무엇을 샀는지 알려줄래요?") String text,
            @Schema(example = "답해주면 소비 분석이 정확해져요") String subText
    ) {
    }

    public record Counts(
            @Schema(example = "2") long needsReview,
            @Schema(example = "1") long provisional
    ) {
    }

    public record Day(
            @Schema(example = "2026-10-14") LocalDate date,
            @Schema(description = "그날 쓴 돈(출금 합계, 소비 제외·취소 거래 제외)", example = "15300") long dayTotal,
            List<Item> items
    ) {
    }

    public record Item(
            @Schema(example = "TRANSACTION") ItemKind kind,
            @Schema(description = "kind가 TRANSACTION일 때") TransactionResponse transaction,
            // TODO: 결제 알림 임시 거래(F-25)·빠른 입력(F-26)·확인 필요 처리가 붙으면 안내 문장과 선택지를 채운다
            @Schema(description = "거래 아래 안내 문장. 없으면 null") String notice,
            @Schema(description = "확인 필요 거래의 선택지 (KEEP, DELETE). 없으면 null") List<String> reviewActions,
            @Schema(description = "kind가 ALLOCATION일 때") Allocation allocation
    ) {

        public static Item of(TransactionResponse transaction) {
            return new Item(ItemKind.TRANSACTION, transaction, null, null, null);
        }
    }

    // TODO: 대기 자금 배분(v4 6-1장)이 붙으면 배분 기록을 ALLOCATION 항목으로 날짜 사이에 넣는다
    public enum ItemKind {
        TRANSACTION, ALLOCATION
    }

    public record Allocation(
            @Schema(example = "10월 블록 배분") String title,
            @Schema(example = "5") int blockCount,
            @Schema(example = "앱 안에서만 나눈 돈이라 쓴 돈에 안 잡혀요") String note
    ) {
    }
}
