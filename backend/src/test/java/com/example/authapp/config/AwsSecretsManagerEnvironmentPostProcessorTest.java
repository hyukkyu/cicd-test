package com.example.authapp.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AwsSecretsManagerEnvironmentPostProcessorTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void normalizeCopiesBucketFromNestedStructure() throws Exception {
        String payload = """
            {
              "cms": {
                "media": {
                  "bucket": "cms-community-media-c8e27a63"
                }
              }
            }
            """;

        Map<String, Object> raw = OBJECT_MAPPER.readValue(payload, new TypeReference<>() {});
        Map<String, Object> sanitized = invoke("sanitize", Map.class, raw);
        Map<String, Object> normalized = invoke("normalize", String.class, Map.class, "cms-community/s3", sanitized);

        assertTrue(sanitized.containsKey("cms.media.bucket"), "flattened key should exist");
        assertEquals("cms-community-media-c8e27a63", normalized.get("app.aws.bucket-name"));
        assertEquals("cms-community-media-c8e27a63", normalized.get("S3_BUCKET_NAME"));
    }

    @SuppressWarnings("unchecked")
    private static <T> T invoke(String methodName, Class<?> parameterType, Object arg) throws Exception {
        Method method = AwsSecretsManagerEnvironmentPostProcessor.class.getDeclaredMethod(methodName, parameterType);
        method.setAccessible(true);
        return (T) method.invoke(null, arg);
    }

    @SuppressWarnings("unchecked")
    private static <T> T invoke(String methodName, Class<?> paramType1, Class<?> paramType2, Object arg1, Object arg2) throws Exception {
        Method method = AwsSecretsManagerEnvironmentPostProcessor.class.getDeclaredMethod(methodName, paramType1, paramType2);
        method.setAccessible(true);
        return (T) method.invoke(null, arg1, arg2);
    }
}
