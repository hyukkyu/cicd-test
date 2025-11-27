package com.example.authapp.admin;

import com.example.authapp.post.PostRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthComponent;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class AdminMetricsBinder implements MeterBinder {

    private final PostRepository postRepository;
    private final HealthEndpoint healthEndpoint;

    public AdminMetricsBinder(PostRepository postRepository,
                              HealthEndpoint healthEndpoint) {
        this.postRepository = postRepository;
        this.healthEndpoint = healthEndpoint;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder("community_harmful_posts_today", this, AdminMetricsBinder::countHarmfulPostsToday)
                .description("Number of harmful posts detected since the start of the day")
                .baseUnit("posts")
                .register(registry);

        Gauge.builder("community_ec2_status", this, binder -> binder.healthStatusValue("ec2"))
                .description("EC2 instance health indicator (1 = UP, 0 = DOWN)")
                .register(registry);

        Gauge.builder("community_rds_status", this, binder -> binder.healthStatusValue("db"))
                .description("RDS connectivity indicator (1 = UP, 0 = DOWN)")
                .register(registry);
    }

    double countHarmfulPostsToday() {
        return postRepository.countHarmfulSince(LocalDate.now().atStartOfDay());
    }

    double healthStatusValue(String component) {
        return "UP".equalsIgnoreCase(resolveHealthStatus(component)) ? 1.0 : 0.0;
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
