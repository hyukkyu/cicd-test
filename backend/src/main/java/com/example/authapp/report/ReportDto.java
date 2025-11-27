package com.example.authapp.report;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReportDto {
    private Long targetId;
    private ReportType type;
    private String reason;
}
