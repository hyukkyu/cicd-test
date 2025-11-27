package com.example.authapp.report;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;

@Converter
public class ReportStatusConverter implements AttributeConverter<ReportStatus, String> {

    @Override
    public String convertToDatabaseColumn(ReportStatus status) {
        return status != null ? status.name() : null;
    }

    @Override
    public ReportStatus convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return ReportStatus.PENDING;
        }
        try {
            return ReportStatus.valueOf(dbData);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unknown ReportStatus value: " + dbData, ex);
        }
    }
}
