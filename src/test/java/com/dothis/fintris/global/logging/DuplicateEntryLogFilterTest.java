package com.dothis.fintris.global.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.core.spi.FilterReply;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DuplicateEntryLogFilterTest {

    private final LoggerContext context = new LoggerContext();
    private final DuplicateEntryLogFilter filter = new DuplicateEntryLogFilter();

    @Test
    @DisplayName("Hibernate JDBC 오류 로그의 중복 키 문구는 막는다")
    void deniesDuplicateEntry() {
        FilterReply reply = filter.decide(null, context.getLogger(DuplicateEntryLogFilter.JDBC_ERROR_LOGGER),
                Level.WARN, "Duplicate entry 'a@b.com' for key 'users.uk_email'", null, null);

        assertThat(reply).isEqualTo(FilterReply.DENY);
    }

    @Test
    @DisplayName("오류 코드 줄과 다른 JDBC 오류는 그대로 둔다")
    void keepsOtherErrors() {
        var jdbcLogger = context.getLogger(DuplicateEntryLogFilter.JDBC_ERROR_LOGGER);

        assertThat(filter.decide(null, jdbcLogger, Level.WARN, "ErrorCode: 1062, SQLState: 23000", null, null))
                .isEqualTo(FilterReply.NEUTRAL);
        assertThat(filter.decide(null, jdbcLogger, Level.WARN, "Column 'name' cannot be null", null, null))
                .isEqualTo(FilterReply.NEUTRAL);
    }

    @Test
    @DisplayName("다른 로거는 같은 문구라도 막지 않는다")
    void ignoresOtherLoggers() {
        FilterReply reply = filter.decide(null, context.getLogger("com.dothis.fintris"),
                Level.WARN, "Duplicate entry", null, null);

        assertThat(reply).isEqualTo(FilterReply.NEUTRAL);
    }
}
