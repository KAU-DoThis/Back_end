package com.dothis.fintris.codef.client;

import com.dothis.fintris.codef.client.CodefApiClient.CodefResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// 카드사 한 곳의 승인내역을 카드 구분 없이 한 번에 가져온다. 어느 카드인지는 resCardNo로 나눈다
// TODO: 요청·응답 필드는 CODEF 문서 기준이다. 샌드박스·실제 응답으로 확인한 뒤 맞춘다
@Component
@RequiredArgsConstructor
public class CodefCardApprovalClient {

    private static final String APPROVAL_LIST_PATH = "/v1/kr/card/p/account/approval-list";
    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HHmmss");

    private final CodefApiClient codefApiClient;

    public CardApprovalsResult fetchApprovals(String connectedId, String organization,
                                              LocalDate startDate, LocalDate endDate) {
        CodefResponse response = codefApiClient.post(APPROVAL_LIST_PATH, Map.of(
                "connectedId", connectedId,
                "organization", organization,
                "startDate", startDate.format(DATE),
                "endDate", endDate.format(DATE),
                "orderBy", "1",               // 과거순
                "inquiryType", "1",           // 카드 전체
                "memberStoreInfoType", "1")); // 가맹점 업종을 받으려면 가맹점 정보를 포함해야 한다
        if (!response.isSuccess()) {
            return CardApprovalsResult.failure(response.result().code(), response.result().message());
        }

        List<CardApproval> approvals = new ArrayList<>();
        JsonNode data = response.data();
        // 승인이 하나면 배열이 아니라 객체로 온다
        if (data != null && data.isObject()) {
            addIfValid(approvals, data);
        } else if (data != null && data.isArray()) {
            for (JsonNode item : data) {
                addIfValid(approvals, item);
            }
        }
        return CardApprovalsResult.success(approvals);
    }

    private void addIfValid(List<CardApproval> approvals, JsonNode item) {
        LocalDateTime occurredAt = parseDateTime(text(item, "resUsedDate"), text(item, "resUsedTime"));
        Long amount = parseWon(text(item, "resUsedAmount"));
        // 일시·금액을 모르거나 0원인 행은 가계부에 쓸 수 없어 버린다
        if (occurredAt == null || amount == null || amount == 0) {
            return;
        }
        ApprovalStatus status = ApprovalStatus.fromCode(text(item, "resCancelYN"));
        // KB는 취소를 음수 금액의 취소전표로 보낸다
        if (amount < 0) {
            status = ApprovalStatus.CANCELED;
        }
        Long cancelAmount = parseWon(text(item, "resCancelAmount"));
        approvals.add(new CardApproval(occurredAt, text(item, "resCardNo"), text(item, "resApprovalNo"),
                Math.abs(amount), status, cancelAmount == null ? 0 : Math.abs(cancelAmount),
                text(item, "resMemberStoreName"), text(item, "resMemberStoreType")));
    }

    private LocalDateTime parseDateTime(String date, String time) {
        if (date == null) {
            return null;
        }
        try {
            LocalTime localTime = time == null ? LocalTime.MIDNIGHT : LocalTime.parse(time, TIME);
            return LocalDateTime.of(LocalDate.parse(date, DATE), localTime);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isString() && !value.stringValue().isBlank() ? value.stringValue().strip() : null;
    }

    // 금액은 쉼표가 섞인 문자열로 온다. 읽을 수 없으면 null
    private Long parseWon(String amount) {
        if (amount == null) {
            return null;
        }
        String digits = amount.replace(",", "");
        int dot = digits.indexOf('.');
        try {
            return Long.parseLong(dot < 0 ? digits : digits.substring(0, dot));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public enum ApprovalStatus {
        APPROVED,         // resCancelYN 0
        CANCELED,         // 1 또는 음수 금액
        PARTIAL_CANCELED, // 2. cancelAmount만큼 취소
        REJECTED;         // 3. 결제되지 않은 승인 거절

        static ApprovalStatus fromCode(String code) {
            return switch (code == null ? "0" : code) {
                case "1" -> CANCELED;
                case "2" -> PARTIAL_CANCELED;
                case "3" -> REJECTED;
                default -> APPROVED;
            };
        }
    }

    // cardNumber는 가운데가 가려진 카드번호다. amount·cancelAmount는 양수
    public record CardApproval(LocalDateTime occurredAt, String cardNumber, String approvalNo, long amount,
                               ApprovalStatus status, long cancelAmount, String merchantName, String merchantType) {
    }

    public record CardApprovalsResult(List<CardApproval> approvals, String failureCode, String failureMessage) {

        static CardApprovalsResult success(List<CardApproval> approvals) {
            return new CardApprovalsResult(approvals, null, null);
        }

        static CardApprovalsResult failure(String code, String message) {
            return new CardApprovalsResult(List.of(), code, message);
        }

        public boolean isSuccess() {
            return failureCode == null;
        }
    }
}
