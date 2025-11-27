package com.example.authapp.service.dto;

import java.time.LocalDateTime;

public record SystemMetricPoint(
        LocalDateTime timestamp,
        double cpuUsagePercent,
        double memoryUsageMb,
        double requestCount,
        double errorRatePercent
) {
}
