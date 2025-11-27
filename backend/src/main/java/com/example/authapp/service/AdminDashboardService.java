package com.example.authapp.service;

import com.example.authapp.admin.AdminReviewItemRepository;
import com.example.authapp.admin.AwsService;
import com.example.authapp.admin.MonitoringMetricsDto;
import com.example.authapp.admin.MonitoringService;
import com.example.authapp.admin.S3ObjectDto;
import com.example.authapp.post.PostRepository;
import com.example.authapp.service.dto.AdminDashboardData;
import com.example.authapp.service.dto.CategoryDistribution;
import com.example.authapp.service.dto.DailySignupStats;
import com.example.authapp.service.dto.DetectionCount;
import com.example.authapp.service.dto.SystemMetricPoint;
import com.example.authapp.user.UserRepository;
import com.example.authapp.user.UserStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AdminDashboardService {

    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final MonitoringService monitoringService;
    private final AwsService awsService;
    private final AdminReviewItemRepository adminReviewItemRepository;

    public AdminDashboardService(UserRepository userRepository,
                                 PostRepository postRepository,
                                 MonitoringService monitoringService,
                                 AwsService awsService,
                                 AdminReviewItemRepository adminReviewItemRepository) {
        this.userRepository = userRepository;
        this.postRepository = postRepository;
        this.monitoringService = monitoringService;
        this.awsService = awsService;
        this.adminReviewItemRepository = adminReviewItemRepository;
    }

    public AdminDashboardData fetchDashboardData() {
        long totalUsers = userRepository.count();
        long activeUsers = userRepository.countByStatus(UserStatus.ACTIVE);
        long blockedUsers = userRepository.countByStatus(UserStatus.BLOCKED);
        long harmfulPosts = adminReviewItemRepository.countByInappropriateDetectedTrue();

        LocalDate today = LocalDate.now();
        LocalDateTime start = today.minusDays(6).atStartOfDay();
        LocalDateTime end = today.plusDays(1).atStartOfDay();
        List<DailySignupStats> rawSignups = userRepository.countDailySignups(start, end);
        List<DailySignupStats> dailySignups = fillMissingDates(rawSignups, today.minusDays(6), today);

        List<CategoryDistribution> categoryDistribution = postRepository.countPostsByMainBoard();
        List<DetectionCount> detectionCounts = ensureDetectionBuckets(postRepository.countHarmfulBreakdown());

        MonitoringMetricsDto latestMetrics = monitoringService.getCurrentMetrics();
        List<SystemMetricPoint> metricSeries = buildSystemMetricSeries(latestMetrics);
        List<S3ObjectDto> s3Objects = safeListBucketObjects();

        return new AdminDashboardData(
                totalUsers,
                activeUsers,
                blockedUsers,
                harmfulPosts,
                dailySignups,
                categoryDistribution,
                detectionCounts,
                metricSeries,
                latestMetrics,
                s3Objects
        );
    }

    private List<DailySignupStats> fillMissingDates(List<DailySignupStats> raw,
                                                    LocalDate from,
                                                    LocalDate to) {
        Map<LocalDate, Long> mapped = raw.stream()
                .collect(Collectors.toMap(DailySignupStats::date, DailySignupStats::count));

        long days = ChronoUnit.DAYS.between(from, to);
        List<DailySignupStats> result = new ArrayList<>();
        for (int i = 0; i <= days; i++) {
            LocalDate day = from.plusDays(i);
            long count = mapped.getOrDefault(day, 0L);
            result.add(new DailySignupStats(day, count));
        }

        result.sort(Comparator.comparing(DailySignupStats::date));
        return result;
    }

    private List<DetectionCount> ensureDetectionBuckets(List<DetectionCount> raw) {
        Map<String, Long> map = new LinkedHashMap<>();
        map.put("Clean", 0L);
        map.put("Harmful Detected", 0L);
        raw.forEach(entry -> map.put(entry.label(), entry.count()));

        return map.entrySet().stream()
                .map(e -> new DetectionCount(e.getKey(), e.getValue()))
                .collect(Collectors.toList());
    }

    private List<SystemMetricPoint> buildSystemMetricSeries(MonitoringMetricsDto metrics) {
        List<SystemMetricPoint> points = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        double baseCpu = metrics.cpuUsage();
        double baseMemoryMb = metrics.memoryUsageBytes() / (1024.0 * 1024.0);
        double baseRequests = metrics.requestCount();
        double baseError = metrics.errorRate();

        for (int i = 4; i >= 0; i--) {
            double cpuFactor = 1 - (i * 0.05);
            double memFactor = 1 - (i * 0.04);
            double reqFactor = 1 - (i * 0.08);
            double errorFactor = 1 + (i * 0.06);

            double cpuPercent = percentClamp(baseCpu * cpuFactor);
            double memoryMb = Math.max(baseMemoryMb * memFactor, 0);
            double requests = Math.max(baseRequests * reqFactor, 0);
            double errorPercent = percentClamp(baseError * errorFactor);

            points.add(new SystemMetricPoint(
                    now.minusMinutes(i * 5L),
                    cpuPercent,
                    memoryMb,
                    requests,
                    errorPercent
            ));
        }
        return points;
    }

    private double percentClamp(double ratio) {
        double percent = ratio * 100;
        if (percent < 0) {
            return 0;
        }
        if (percent > 100) {
            return 100;
        }
        return percent;
    }

    private List<S3ObjectDto> safeListBucketObjects() {
        try {
            return awsService.listBucketObjects();
        } catch (Exception ex) {
            return List.of();
        }
    }
}
