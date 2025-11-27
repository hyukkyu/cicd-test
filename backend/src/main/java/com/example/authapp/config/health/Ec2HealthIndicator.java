package com.example.authapp.config.health;

import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.model.DescribeInstanceStatusRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.DescribeInstanceStatusResult;
import com.amazonaws.services.ec2.model.InstanceStatus;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.Filter;
import com.amazonaws.util.EC2MetadataUtils;
import com.example.authapp.config.AppProperties;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Optional;
import java.util.List;

@Component("ec2")
@ConditionalOnProperty(prefix = "app.monitoring", name = "ec2-health-enabled", havingValue = "true", matchIfMissing = true)
public class Ec2HealthIndicator implements HealthIndicator {

    private final AmazonEC2 amazonEC2;
    private final AppProperties appProperties;
    private volatile String cachedInstanceId;

    public Ec2HealthIndicator(AmazonEC2 amazonEC2, AppProperties appProperties) {
        this.amazonEC2 = amazonEC2;
        this.appProperties = appProperties;
    }

    @Override
    public Health health() {
        String instanceId = fetchInstanceId().orElse(null);
        if (instanceId == null) {
            return Health.unknown()
                    .withDetail("reason", "Instance metadata unavailable")
                    .build();
        }

        try {
            DescribeInstanceStatusResult result = amazonEC2.describeInstanceStatus(
                    new DescribeInstanceStatusRequest()
                            .withInstanceIds(instanceId)
                            .withIncludeAllInstances(true)
            );

            if (result.getInstanceStatuses().isEmpty()) {
                return Health.unknown()
                        .withDetail("instanceId", instanceId)
                        .withDetail("reason", "Instance status not returned by AWS")
                        .build();
            }

            InstanceStatus status = result.getInstanceStatuses().get(0);
            String systemStatus = status.getSystemStatus().getStatus();
            String instanceStatus = status.getInstanceStatus().getStatus();

            boolean healthy = "ok".equalsIgnoreCase(systemStatus) && "ok".equalsIgnoreCase(instanceStatus);

            return (healthy ? Health.up() : Health.down())
                    .withDetail("instanceId", instanceId)
                    .withDetail("systemStatus", systemStatus)
                    .withDetail("instanceStatus", instanceStatus)
                    .build();
        } catch (Exception ex) {
            return Health.down(ex)
                    .withDetail("instanceId", instanceId)
                    .build();
        }
    }

    private Optional<String> fetchInstanceId() {
        String configuredId = appProperties.getMonitoring().getEc2InstanceId();
        if (StringUtils.hasText(configuredId)) {
            return Optional.of(configuredId.trim());
        }
        String cached = cachedInstanceId;
        if (StringUtils.hasText(cached)) {
            return Optional.of(cached);
        }
        Optional<String> tagLookup = lookupInstanceIdByTag();
        if (tagLookup.isPresent()) {
            cachedInstanceId = tagLookup.get();
            return tagLookup;
        }
        try {
            return Optional.ofNullable(EC2MetadataUtils.getInstanceId());
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private Optional<String> lookupInstanceIdByTag() {
        String tagKey = appProperties.getMonitoring().getEc2InstanceTagKey();
        String tagValue = appProperties.getMonitoring().getEc2InstanceTagValue();
        if (!StringUtils.hasText(tagKey) || !StringUtils.hasText(tagValue)) {
            return Optional.empty();
        }
        try {
            DescribeInstancesRequest request = new DescribeInstancesRequest()
                    .withFilters(new Filter("tag:" + tagKey, List.of(tagValue)));
            DescribeInstancesResult result = amazonEC2.describeInstances(request);
            return result.getReservations().stream()
                    .map(Reservation::getInstances)
                    .flatMap(List::stream)
                    .filter(instance -> instance.getState() != null && !"terminated".equalsIgnoreCase(instance.getState().getName()))
                    .map(Instance::getInstanceId)
                    .findFirst();
        } catch (Exception ex) {
            return Optional.empty();
        }
    }
}
