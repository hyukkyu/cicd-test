package com.example.authapp.admin;

import com.example.authapp.post.PostRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthComponent;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Collection;

@Service
public class MonitoringService {

    private final MeterRegistry meterRegistry;
    private final PostRepository postRepository;
    private final HealthEndpoint healthEndpoint;

    public MonitoringService(MeterRegistry meterRegistry,
                             PostRepository postRepository,
                             HealthEndpoint healthEndpoint) {
        this.meterRegistry = meterRegistry;
        this.postRepository = postRepository;
        this.healthEndpoint = healthEndpoint;
    }

    public MonitoringMetricsDto getCurrentMetrics() {
        double cpuUsage = readGauge("system.cpu.usage");
        double memoryUsage = readGauge("jvm.memory.used", "area", "heap");
        double totalRequests = readTimerCount("http.server.requests");
        double errorRequests = readTimerCount("http.server.requests", "outcome", "SERVER_ERROR");
        double errorRate = totalRequests > 0 ? errorRequests / totalRequests : 0.0;

        long harmfulPostsToday = postRepository.countHarmfulSince(LocalDate.now().atStartOfDay());

        String ec2Status = resolveHealthStatus("ec2");
        String rdsStatus = resolveHealthStatus("db");

        return new MonitoringMetricsDto(
                cpuUsage,
                memoryUsage,
                totalRequests,
                errorRate,
                harmfulPostsToday,
                ec2Status,
                rdsStatus
        );
    }

    private double readGauge(String name, String... tags) {
        try {
            Gauge gauge = tags.length == 0
                    ? meterRegistry.find(name).gauge()
                    : meterRegistry.find(name).tags(Tags.of(tags)).gauge();
            return gauge != null ? gauge.value() : 0.0;
        } catch (IllegalArgumentException ex) {
            return 0.0;
        }
    }

    private double readTimerCount(String name, String... tags) {
        try {
            var search = tags.length == 0
                    ? meterRegistry.find(name)
                    : meterRegistry.find(name).tags(Tags.of(tags));
            Collection<Timer> timers = search.timers();
            if (timers.isEmpty()) {
                return 0.0;
            }
            return timers.stream()
                    .mapToDouble(Timer::count)
                    .sum();
        } catch (IllegalArgumentException ex) {
            return 0.0;
        }
    }

    private String resolveHealthStatus(String path) {
        try {
            HealthComponent component = healthEndpoint.healthForPath(path);
            if (component instanceof Health health) {
                return health.getStatus().getCode();
            }
            return component.getStatus().getCode();
        } catch (Exception ex) {
            return "UNKNOWN";
        }
    }
}
