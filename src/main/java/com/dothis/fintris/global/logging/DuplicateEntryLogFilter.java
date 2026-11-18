package com.dothis.fintris.global.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.spi.FilterReply;
import org.slf4j.Marker;

// Hibernate가 남기는 DB 오류 문구 중 "Duplicate entry '<겹친 값>'" 줄만 막는다.
// 오류 코드·SQLState 줄과 다른 JDBC 오류는 그대로 남는다
public class DuplicateEntryLogFilter extends TurboFilter {

    static final String JDBC_ERROR_LOGGER = "org.hibernate.orm.jdbc.error";

    @Override
    public FilterReply decide(Marker marker, Logger logger, Level level, String format, Object[] params,
                              Throwable t) {
        if (format != null && format.startsWith("Duplicate entry")
                && JDBC_ERROR_LOGGER.equals(logger.getName())) {
            return FilterReply.DENY;
        }
        return FilterReply.NEUTRAL;
    }
}
