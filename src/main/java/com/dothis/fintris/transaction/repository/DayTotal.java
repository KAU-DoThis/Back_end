package com.dothis.fintris.transaction.repository;

import java.time.LocalDate;

public record DayTotal(LocalDate date, Long total) {
}
