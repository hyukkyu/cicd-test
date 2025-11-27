package com.example.authapp.service.dto;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

public record DailySignupStats(LocalDate date, long count) {

    public DailySignupStats {
        if (date == null) {
            throw new IllegalArgumentException("date must not be null");
        }
    }

    public DailySignupStats(Date date, long count) {
        this(adapt(date), count);
    }

    private static LocalDate adapt(Date date) {
        if (date == null) {
            throw new IllegalArgumentException("date must not be null");
        }
        if (date instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }
}
