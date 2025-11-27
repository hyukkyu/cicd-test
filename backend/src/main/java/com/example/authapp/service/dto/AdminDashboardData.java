package com.example.authapp.service.dto;

import com.example.authapp.admin.MonitoringMetricsDto;
import com.example.authapp.admin.S3ObjectDto;

import java.util.List;

public record AdminDashboardData(
        long totalUsers,
        long activeUsers,
        long blockedUsers,
        long harmfulPosts,
        List<DailySignupStats> dailySignups,
        List<CategoryDistribution> categoryDistribution,
        List<DetectionCount> detectionCounts,
        List<SystemMetricPoint> systemMetrics,
        MonitoringMetricsDto latestMetrics,
        List<S3ObjectDto> s3Objects
) {
}
