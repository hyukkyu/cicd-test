package com.example.authapp.admin;

import com.example.authapp.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.util.UriComponentsBuilder;

import javax.servlet.http.HttpServletRequest;
import java.net.URI;
import java.net.Proxy;
import java.time.Duration;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Set;

@RestController
@RequestMapping("/api/admin/monitoring/grafana")
public class GrafanaProxyController {

    private static final Logger logger = LoggerFactory.getLogger(GrafanaProxyController.class);
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();
    private static final Set<String> FORWARDED_HEADERS = Set.of(
            HttpHeaders.ACCEPT.toLowerCase(Locale.ROOT),
            HttpHeaders.ACCEPT_LANGUAGE.toLowerCase(Locale.ROOT),
            HttpHeaders.ACCEPT_ENCODING.toLowerCase(Locale.ROOT),
            HttpHeaders.USER_AGENT.toLowerCase(Locale.ROOT),
            HttpHeaders.CACHE_CONTROL.toLowerCase(Locale.ROOT),
            HttpHeaders.CONTENT_TYPE.toLowerCase(Locale.ROOT),
            HttpHeaders.CONTENT_LENGTH.toLowerCase(Locale.ROOT)
    );
    private static final Set<String> RESPONSE_HEADERS = Set.of(
            HttpHeaders.CONTENT_TYPE,
            HttpHeaders.CACHE_CONTROL,
            HttpHeaders.ETAG,
            HttpHeaders.LAST_MODIFIED,
            HttpHeaders.EXPIRES
    );

    private final RestTemplate restTemplate;
    private final URI grafanaBaseUri;
    private final boolean proxyEnabled;
    private final String grafanaHostHeader;

    public GrafanaProxyController(AppProperties appProperties, RestTemplateBuilder restTemplateBuilder) {
        String baseUrl = appProperties.getMonitoring().getGrafanaUrl();
        this.proxyEnabled = StringUtils.hasText(baseUrl);
        this.grafanaBaseUri = proxyEnabled ? URI.create(baseUrl) : null;
        if (this.grafanaBaseUri != null) {
            String host = grafanaBaseUri.getHost();
            int port = grafanaBaseUri.getPort();
            if (port > 0) {
                this.grafanaHostHeader = host + ":" + port;
            } else {
                this.grafanaHostHeader = host;
            }
        } else {
            this.grafanaHostHeader = null;
        }
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(25))
                .requestFactory(() -> {
                    var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
                    factory.setProxy(Proxy.NO_PROXY);
                    return factory;
                })
                .build();
    }

    @RequestMapping(value = "/proxy/**", method = {
            RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT,
            RequestMethod.DELETE, RequestMethod.PATCH, RequestMethod.OPTIONS, RequestMethod.HEAD
    })
    public ResponseEntity<byte[]> proxy(HttpServletRequest request,
                                        @RequestBody(required = false) byte[] body) {
        if (!proxyEnabled) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Grafana URL is not configured");
        }
        URI target = buildTargetUri(request);
        HttpHeaders forwardHeaders = collectForwardHeaders(request);
        HttpEntity<byte[]> entity = new HttpEntity<>(body, forwardHeaders);
        HttpMethod method = HttpMethod.resolve(request.getMethod());
        if (method == null) {
            throw new ResponseStatusException(HttpStatus.METHOD_NOT_ALLOWED, "Unsupported method");
        }
        try {
            String originalHost = request.getHeader(HttpHeaders.HOST);
            ResponseEntity<byte[]> response = followRedirects(target, entity, 3, originalHost, method);
            HttpHeaders filteredHeaders = filterResponseHeaders(response.getHeaders());
            return new ResponseEntity<>(response.getBody(), filteredHeaders, response.getStatusCode());
        } catch (HttpStatusCodeException httpEx) {
            // Preserve upstream status/body to make troubleshooting easier (e.g., 404/401).
            logger.warn("Grafana responded with error status. target={}, status={}", target, httpEx.getStatusCode());
            HttpHeaders filteredHeaders = filterResponseHeaders(httpEx.getResponseHeaders());
            return new ResponseEntity<>(httpEx.getResponseBodyAsByteArray(), filteredHeaders, httpEx.getStatusCode());
        } catch (RestClientException ex) {
            logger.error("Grafana proxy failed for target={}", target, ex);
            String rootMessage = ex.getMessage();
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Grafana에 연결할 수 없습니다. target=" + target + (rootMessage != null ? " reason=" + rootMessage : ""),
                    ex
            );
        }
    }

    private ResponseEntity<byte[]> followRedirects(URI initialTarget, HttpEntity<byte[]> entity, int maxRedirects, String originalHost, HttpMethod method) {
        URI current = initialTarget;
        for (int i = 0; i <= maxRedirects; i++) {
            ResponseEntity<byte[]> response = restTemplate.exchange(current, method, entity, byte[].class);
            if (!response.getStatusCode().is3xxRedirection()) {
                return response;
            }
            if (i == maxRedirects) {
                return response;
            }
            URI location = response.getHeaders().getLocation();
            if (location == null) {
                return response;
            }
            if (!location.isAbsolute()) {
                location = current.resolve(location);
            }
            // If Grafana redirects to the public API host (root_url), rewrite to hit Grafana directly to avoid proxy loops.
            if (grafanaBaseUri != null && location.getHost() != null && originalHost != null
                    && location.getHost().equalsIgnoreCase(originalHost)) {
                location = UriComponentsBuilder.fromUri(grafanaBaseUri)
                        .replacePath(location.getPath())
                        .replaceQuery(location.getQuery())
                        .build(true)
                        .toUri();
            }
            current = location;
        }
        throw new IllegalStateException("Unreachable redirect handler flow");
    }

    private URI buildTargetUri(HttpServletRequest request) {
        String path = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        String pattern = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String remaining = PATH_MATCHER.extractPathWithinPattern(pattern, path);
        String sanitized = sanitizePath(remaining);

        UriComponentsBuilder builder = UriComponentsBuilder.fromUri(grafanaBaseUri)
                .replaceQuery(null);

        if (StringUtils.hasText(sanitized)) {
            builder.path(sanitized.startsWith("/") ? sanitized : "/" + sanitized);
        }

        String query = request.getQueryString();
        if (StringUtils.hasText(query)) {
            builder.replaceQuery(query);
        }

        return builder.build(true).toUri();
    }

    private String sanitizePath(String path) {
        if (!StringUtils.hasText(path)) {
            return "";
        }
        String cleaned = path.replace("\\", "/");
        while (cleaned.contains("../")) {
            cleaned = cleaned.replace("../", "");
        }
        return cleaned;
    }

    private HttpHeaders collectForwardHeaders(HttpServletRequest request) {
        HttpHeaders headers = new HttpHeaders();
        Enumeration<String> headerNames = request.getHeaderNames();
        if (headerNames != null) {
            while (headerNames.hasMoreElements()) {
                String header = headerNames.nextElement();
                if (header == null) continue;
                String normalized = header.toLowerCase(Locale.ROOT);
                if (FORWARDED_HEADERS.contains(normalized)) {
                    headers.put(header, Collections.list(request.getHeaders(header)));
                }
            }
        }
        headers.set(HttpHeaders.ACCEPT_ENCODING, "identity");

        String originalHost = request.getHeader(HttpHeaders.HOST);
        if (StringUtils.hasText(originalHost)) {
            headers.set("X-Forwarded-Host", originalHost);
        }

        // Use Grafana's own host so it doesn't redirect back to the proxy URL (avoids loops).
        if (StringUtils.hasText(grafanaHostHeader)) {
            headers.set(HttpHeaders.HOST, grafanaHostHeader);
        } else if (StringUtils.hasText(originalHost)) {
            headers.set(HttpHeaders.HOST, originalHost);
        }

        String forwardedProto = request.getHeader("X-Forwarded-Proto");
        if (!StringUtils.hasText(forwardedProto)) {
            forwardedProto = request.getScheme();
        }
        if (StringUtils.hasText(forwardedProto)) {
            headers.set("X-Forwarded-Proto", forwardedProto);
        }

        String forwardedPort = request.getHeader("X-Forwarded-Port");
        if (!StringUtils.hasText(forwardedPort) && request.getServerPort() > 0) {
            forwardedPort = String.valueOf(request.getServerPort());
        }
        if (StringUtils.hasText(forwardedPort)) {
            headers.set("X-Forwarded-Port", forwardedPort);
        }

        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (!StringUtils.hasText(forwardedFor) && StringUtils.hasText(request.getRemoteAddr())) {
            forwardedFor = request.getRemoteAddr();
        }
        if (StringUtils.hasText(forwardedFor)) {
            headers.set("X-Forwarded-For", forwardedFor);
        }

        String forwardedUri = request.getHeader("X-Forwarded-Uri");
        if (!StringUtils.hasText(forwardedUri)) {
            forwardedUri = request.getRequestURI();
        }
        if (StringUtils.hasText(forwardedUri)) {
            headers.set("X-Forwarded-Uri", forwardedUri);
        }

        return headers;
    }

    private HttpHeaders filterResponseHeaders(HttpHeaders source) {
        HttpHeaders headers = new HttpHeaders();
        if (source == null) {
            headers.set("X-Frame-Options", "ALLOWALL");
            return headers;
        }
        RESPONSE_HEADERS.forEach((name) -> {
            if (source.containsKey(name)) {
                headers.put(name, source.get(name));
            }
        });
        headers.set("X-Frame-Options", "ALLOWALL");
        return headers;
    }
}
