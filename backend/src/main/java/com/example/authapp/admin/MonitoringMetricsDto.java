package com.example.authapp.admin;

public record MonitoringMetricsDto(
        double cpuUsage,
        double memoryUsageBytes,
        double requestCount,
        double errorRate,
        long harmfulPostsToday,
        String ec2Status,
        String rdsStatus
) {
}
