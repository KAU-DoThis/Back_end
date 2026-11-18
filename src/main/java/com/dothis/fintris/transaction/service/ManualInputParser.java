package com.dothis.fintris.transaction.service;

import java.time.LocalDate;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// 빠른 입력 문장("붕어빵 3000", "어제 택시 1만2천원")에서 금액·내용·날짜를 꺼낸다 (F-26)
// TODO: AI 해석(카테고리 포함)이 붙으면 이 규칙 해석은 AI 실패 시 대체용으로 남긴다
public final class ManualInputParser {

    static final long MAX_AMOUNT = 100_000_000L;

    // 1만2천 / 1.5만 / 3천 / 3,000(원) 중 하나. 문장 안 마지막 금액을 쓴다
    private static final Pattern AMOUNT = Pattern.compile(
            "(\\d+(?:\\.\\d+)?)\\s*만(?:\\s*(\\d+(?:\\.\\d+)?)\\s*천)?\\s*원?"
                    + "|(\\d+(?:\\.\\d+)?)\\s*천\\s*원?"
                    + "|(\\d{1,3}(?:,\\d{3})+|\\d+)\\s*원?");
    // 억 단위는 한도(1억 원)를 넘으므로 숫자만 떼어 읽지 않고 실패로 본다
    private static final Pattern HUNDRED_MILLION = Pattern.compile("\\d\\s*억");
    private static final Pattern YESTERDAY = Pattern.compile("어제");
    private static final Pattern TWO_DAYS_AGO = Pattern.compile("그저께|그제");

    private ManualInputParser() {
    }

    public record Parsed(long amount, String description, LocalDate occurredDate) {
    }

    // 금액을 못 찾거나 0원·1억 원 초과면 비운다
    public static Optional<Parsed> parse(String text, LocalDate today) {
        if (text == null || text.isBlank() || HUNDRED_MILLION.matcher(text).find()) {
            return Optional.empty();
        }
        String rest = text.strip();

        LocalDate date = today;
        if (TWO_DAYS_AGO.matcher(rest).find()) {
            date = today.minusDays(2);
            rest = TWO_DAYS_AGO.matcher(rest).replaceAll(" ");
        } else if (YESTERDAY.matcher(rest).find()) {
            date = today.minusDays(1);
            rest = YESTERDAY.matcher(rest).replaceAll(" ");
        }

        Matcher matcher = AMOUNT.matcher(rest);
        Long amount = null;
        int start = -1;
        int end = -1;
        while (matcher.find()) {
            amount = amountOf(matcher);
            start = matcher.start();
            end = matcher.end();
        }
        if (amount == null || amount <= 0 || amount > MAX_AMOUNT) {
            return Optional.empty();
        }

        String description = (rest.substring(0, start) + " " + rest.substring(end)).replaceAll("\\s+", " ").strip();
        return Optional.of(new Parsed(amount, description.isEmpty() ? null : description, date));
    }

    private static long amountOf(Matcher matcher) {
        if (matcher.group(1) != null) {
            long man = Math.round(Double.parseDouble(matcher.group(1)) * 10_000);
            long cheon = matcher.group(2) == null ? 0 : Math.round(Double.parseDouble(matcher.group(2)) * 1_000);
            return man + cheon;
        }
        if (matcher.group(3) != null) {
            return Math.round(Double.parseDouble(matcher.group(3)) * 1_000);
        }
        return Long.parseLong(matcher.group(4).replace(",", ""));
    }
}
