package com.example.authapp.admin;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Service
public class PrometheusService {

    @Value("${prometheus.server.url}")
    private String prometheusServerUrl;

    private final RestTemplate restTemplate;

    public PrometheusService() {
        this.restTemplate = new RestTemplate();
    }

    /**
     * Prometheus API를 통해 Instant Vector 또는 Range Vector 쿼리 결과를 가져옵니다.
     * @param query Prometheus 쿼리 문자열 (예: 'node_cpu_usage_seconds_total')
     * @param duration 조회 기간 (예: '5m' for 5 minutes, '1h' for 1 hour). Instant Vector 쿼리 시에는 null.
     * @return Prometheus API 응답을 Map 형태로 반환
     */
    public Map<String, Object> queryPrometheus(String query, String duration) {
        final String encodedQuery;
        try {
            encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.toString());
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to encode Prometheus query", ex);
        }
        String url;
        if (duration != null && !duration.isEmpty()) {
            // Range Vector Query
            long end = Instant.now().getEpochSecond();
            long start = Instant.now().minusSeconds(parseDuration(duration)).getEpochSecond();
            url = String.format("%s/api/v1/query_range?query=%s&start=%d&end=%d&step=15s", prometheusServerUrl, encodedQuery, start, end);
        } else {
            // Instant Vector Query
            url = String.format("%s/api/v1/query?query=%s", prometheusServerUrl, encodedQuery);
        }
        
        try {
            return restTemplate.getForObject(url, Map.class);
        } catch (Exception e) {
            // 에러 로깅 및 빈 결과 반환
            System.err.println("Error querying Prometheus: " + e.getMessage());
            return new HashMap<>();
        }
    }

    // Duration 문자열을 초 단위로 파싱
    private long parseDuration(String duration) {
        if (duration.endsWith("s")) return Long.parseLong(duration.substring(0, duration.length() - 1));
        if (duration.endsWith("m")) return Long.parseLong(duration.substring(0, duration.length() - 1)) * 60;
        if (duration.endsWith("h")) return Long.parseLong(duration.substring(0, duration.length() - 1)) * 3600;
        if (duration.endsWith("d")) return Long.parseLong(duration.substring(0, duration.length() - 1)) * 86400;
        return 0; // 기본값 또는 에러 처리
    }

    // --- 특정 메트릭을 위한 헬퍼 메소드 (예시) ---

    public Map<String, Object> getCpuUsage(String duration) {
        // 예시 쿼리: node_cpu_seconds_total의 변화율을 통해 CPU 사용률 계산
        // 실제 쿼리는 Prometheus 설정 및 exporter에 따라 달라질 수 있습니다.
        String query = "100 - (avg by (instance) (rate(node_cpu_seconds_total{mode=\"idle\"}[5m])) * 100)";
        return queryPrometheus(query, duration);
    }

    public Map<String, Object> getMemoryUsage(String duration) {
        // 예시 쿼리: 사용 중인 메모리 / 전체 메모리
        String query = "(node_memory_MemTotal_bytes - node_memory_MemFree_bytes) / node_memory_MemTotal_bytes * 100";
        return queryPrometheus(query, duration);
    }

    public Map<String, Object> getDiskIO(String duration) {
        // 예시 쿼리: 디스크 읽기/쓰기 바이트의 변화율
        String query = "rate(node_disk_bytes_read_total[5m]) + rate(node_disk_bytes_written_total[5m])";
        return queryPrometheus(query, duration);
    }

    public Map<String, Object> getNetworkTraffic(String duration) {
        // 예시 쿼리: 네트워크 수신/전송 바이트의 변화율
        String query = "rate(node_network_receive_bytes_total[5m]) + rate(node_network_transmit_bytes_total[5m])";
        return queryPrometheus(query, duration);
    }

    public Map<String, Object> getHttpRequestCount(String duration) {
        // Spring Boot Actuator의 http_server_requests_seconds_count 메트릭
        String query = "http_server_requests_seconds_count";
        return queryPrometheus(query, duration);
    }

    public Map<String, Object> getHttpRequestErrors(String duration) {
        // http_server_requests_seconds_count 중 status가 4xx 또는 5xx인 요청 수
        String query = "sum(rate(http_server_requests_seconds_count{status=~\"(4|5)xx\"}[5m]))";
        return queryPrometheus(query, duration);
    }

    public Map<String, Object> getHttpRequestLatency(String duration) {
        // http_server_requests_seconds_sum / http_server_requests_seconds_count
        String query = "rate(http_server_requests_seconds_sum[5m]) / rate(http_server_requests_seconds_count[5m])";
        return queryPrometheus(query, duration);
    }
}
