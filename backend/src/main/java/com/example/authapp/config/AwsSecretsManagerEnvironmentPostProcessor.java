package com.example.authapp.config;

import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import software.amazon.awssdk.services.secretsmanager.model.ResourceNotFoundException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Loads required configuration from AWS Secrets Manager before the Spring context completes.
 * This guarantees that database credentials and other sensitive settings are available
 * when the datasource initializes.
 */
@Slf4j
public class AwsSecretsManagerEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String PROPERTY_SOURCE_PREFIX = "aws-secrets-";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String DEFAULT_SECRETS = "cms-community/db,cms-community/s3,cms-community/cognito";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        List<String> secretIds = resolveSecretIds(environment);
        if (secretIds.isEmpty()) {
            log.debug("No AWS Secrets Manager ids configured (property 'app.aws.secrets.required' is empty).");
            return;
        }

        String region = environment.getProperty("cloud.aws.region.static", "ap-northeast-1");
        boolean failFast = environment.getProperty("app.aws.secrets.fail-fast", Boolean.class, Boolean.TRUE);

        AwsCredentialsProvider credentialsProvider = DefaultCredentialsProvider.create();
        SecretsManagerClient client = SecretsManagerClient.builder()
            .region(Region.of(region))
            .credentialsProvider(credentialsProvider)
            .build();

        try {
            for (String secretId : secretIds) {
                Map<String, Object> secretValues = fetchSecretValues(client, secretId, failFast);
                if (!secretValues.isEmpty()) {
                    Map<String, Object> sanitized = sanitize(secretValues);
                    Map<String, Object> normalized = normalize(secretId, sanitized);
                    validate(secretId, normalized, failFast);

                    String propertySourceName = PROPERTY_SOURCE_PREFIX + secretId;
                    environment.getPropertySources().addFirst(new MapPropertySource(propertySourceName, normalized));
                    log.info("Loaded {} properties from AWS Secrets Manager secret '{}'.", normalized.size(), secretId);
                }
            }
        } finally {
            client.close();
        }
    }

    private static List<String> resolveSecretIds(ConfigurableEnvironment environment) {
        String raw = environment.getProperty("app.aws.secrets.required");
        if (!StringUtils.hasText(raw)) {
            raw = DEFAULT_SECRETS;
        }

        return Arrays.stream(raw.split(","))
            .map(String::trim)
            .filter(StringUtils::hasText)
            .collect(Collectors.toList());
    }

    private static Map<String, Object> fetchSecretValues(SecretsManagerClient client, String secretId, boolean failFast) {
        try {
            GetSecretValueResponse result = client.getSecretValue(
                GetSecretValueRequest.builder().secretId(secretId).build()
            );

            if (!StringUtils.hasText(result.secretString())) {
                String message = String.format("Secret '%s' has no string payload.", secretId);
                if (failFast) {
                    throw new IllegalStateException(message);
                }
                log.warn(message);
                return Map.of();
            }

            return OBJECT_MAPPER.readValue(result.secretString(), new TypeReference<Map<String, Object>>() {});
        } catch (ResourceNotFoundException ex) {
            String message = String.format("Required secret '%s' was not found in AWS Secrets Manager.", secretId);
            if (failFast) {
                throw new IllegalStateException(message, ex);
            }
            log.warn(message);
            return Map.of();
        } catch (SdkClientException | IOException ex) {
            String message = String.format("Unable to load secret '%s' from AWS Secrets Manager.", secretId);
            if (failFast) {
                throw new IllegalStateException(message, ex);
            }
            log.warn(message, ex);
            return Map.of();
        }
    }

    private static Map<String, Object> sanitize(Map<String, Object> secretValues) {
        Map<String, Object> sanitized = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : secretValues.entrySet()) {
            flatten(entry.getKey(), entry.getValue(), sanitized);
        }
        return sanitized;
    }

    @SuppressWarnings("unchecked")
    private static void flatten(String prefix, Object value, Map<String, Object> target) {
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                Object key = entry.getKey();
                if (key == null) {
                    continue;
                }
                String childKey = (prefix == null || prefix.isBlank())
                    ? key.toString()
                    : prefix + "." + key;
                flatten(childKey, entry.getValue(), target);
            }
            return;
        }
        if (value instanceof List<?> list) {
            for (int i = 0; i < list.size(); i++) {
                String childKey = String.format("%s[%d]", prefix, i);
                flatten(childKey, list.get(i), target);
            }
            return;
        }
        target.put(prefix, value == null ? null : value.toString());
    }

    private static Map<String, Object> normalize(String secretId, Map<String, Object> sanitized) {
        Map<String, Object> normalized = new LinkedHashMap<>(sanitized);

        if (secretId.endsWith("/db")) {
            propagateAlias(normalized, Set.of("DB_URL", "jdbcUrl", "DB_JDBC_URL"), "spring.datasource.url");
            propagateAlias(normalized, Set.of("DB_USERNAME", "username"), "spring.datasource.username");
            propagateAlias(normalized, Set.of("DB_PASSWORD", "password"), "spring.datasource.password");
            propagateAlias(normalized, Set.of("DB_DDL_AUTO"), "DB_DDL_AUTO");
            propagateAlias(normalized, Set.of("HIBERNATE_DIALECT", "hibernate.dialect"), "HIBERNATE_DIALECT");
        }

        if (secretId.endsWith("/s3")) {
            Set<String> bucketKeys = Set.of(
                "bucket",
                "BUCKET_NAME",
                "cms.media.bucket",
                "media.bucket"
            );
            propagateAlias(normalized, bucketKeys, "S3_BUCKET_NAME");
            propagateAlias(normalized, bucketKeys, "app.aws.bucket-name");
        }

        if (secretId.endsWith("/cognito")) {
            propagateAlias(normalized, Set.of(
                "userPoolId",
                "user_pool_id",
                "USER_POOL_ID",
                "COGNITO_POOL_ID"
            ), "COGNITO_USER_POOL_ID");

            propagateAlias(normalized, Set.of(
                "clientId",
                "userClientId",
                "USER_CLIENT_ID",
                "COGNITO_CLIENT_ID"
            ), "COGNITO_CLIENT_ID");

            propagateAlias(normalized, Set.of(
                "adminClientId",
                "ADMIN_CLIENT_ID",
                "COGNITO_ADMIN_CLIENT_ID"
            ), "COGNITO_ADMIN_CLIENT_ID");
        }

        return normalized;
    }

    private static void propagateAlias(Map<String, Object> target, Set<String> sourceKeys, String targetKey) {
        if (target.containsKey(targetKey) && StringUtils.hasText(valueAsString(target.get(targetKey)))) {
            return;
        }
        for (String sourceKey : sourceKeys) {
            if (target.containsKey(sourceKey)) {
                String candidate = valueAsString(target.get(sourceKey));
                if (StringUtils.hasText(candidate)) {
                    target.put(targetKey, candidate);
                    return;
                }
            }
        }
    }

    private static void validate(String secretId, Map<String, Object> values, boolean failFast) {
        if (!failFast) {
            return;
        }

        if (secretId.endsWith("/db")) {
            ensurePresent(secretId, values, "spring.datasource.url");
            ensurePresent(secretId, values, "spring.datasource.username");
            ensurePresent(secretId, values, "spring.datasource.password");
        } else if (secretId.endsWith("/s3")) {
            ensurePresent(secretId, values, "app.aws.bucket-name");
        } else if (secretId.endsWith("/cognito")) {
            ensurePresent(secretId, values, "COGNITO_USER_POOL_ID");
            ensurePresent(secretId, values, "COGNITO_CLIENT_ID");
        }
    }

    private static void ensurePresent(String secretId, Map<String, Object> values, String key) {
        String value = valueAsString(values.get(key));
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(
                String.format("Secret '%s' is missing required key '%s'.", secretId, key)
            );
        }
    }

    private static String valueAsString(Object value) {
        return value == null ? null : value.toString();
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
