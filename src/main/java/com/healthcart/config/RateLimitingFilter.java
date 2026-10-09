package com.healthcart.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class RateLimitingFilter extends OncePerRequestFilter {

    private final Map<String, Bucket> ipBuckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String uri = request.getRequestURI();

        // Exempt static resources, favicon, and payment webhooks from client IP rate-limiting (PRD 9.7)
        if (uri.startsWith("/css/") || uri.startsWith("/images/") || uri.startsWith("/files/products/") ||
            uri.startsWith("/webjars/") || uri.startsWith("/api/webhooks/") || "/favicon.ico".equals(uri)) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = resolveClientIp(request);
        String bucketKey = resolveBucketKey(clientIp, uri);
        Bucket bucket = ipBuckets.computeIfAbsent(bucketKey, k -> createNewBucket(uri));

        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
        } else {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", "60");
            response.setContentType("text/html;charset=UTF-8");
            response.getWriter().write(
                "<!DOCTYPE html><html><head><title>Too Many Requests</title>" +
                "<link rel='stylesheet' href='https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css'>" +
                "</head><body class='bg-light d-flex align-items-center justify-content-center' style='min-height: 100vh;'>" +
                "<div class='card shadow-sm p-4 text-center' style='max-width: 480px;'>" +
                "<h3 class='text-danger mb-3'>Rate Limit Exceeded (429)</h3>" +
                "<p class='text-muted'>Too many requests have been made from your connection. Please wait a moment before trying again.</p>" +
                "<a href='/' class='btn btn-outline-primary mt-2'>Return to HealthCart</a>" +
                "</div></body></html>"
            );
        }
    }

    private String resolveBucketKey(String clientIp, String uri) {
        if (uri.startsWith("/login") || uri.startsWith("/register") ||
            uri.startsWith("/forgot-password") || uri.startsWith("/reset-password")) {
            return clientIp + ":auth";
        }
        if (uri.startsWith("/checkout")) {
            return clientIp + ":checkout";
        }
        return clientIp + ":general";
    }

    private Bucket createNewBucket(String uri) {
        if (uri.startsWith("/login") || uri.startsWith("/register") ||
            uri.startsWith("/forgot-password") || uri.startsWith("/reset-password")) {
            // 15 requests per minute for auth routes
            return Bucket.builder()
                    .addLimit(Bandwidth.builder().capacity(15).refillIntervally(15, Duration.ofMinutes(1)).build())
                    .build();
        } else if (uri.startsWith("/checkout")) {
            // 20 requests per minute for checkout routes
            return Bucket.builder()
                    .addLimit(Bandwidth.builder().capacity(20).refillIntervally(20, Duration.ofMinutes(1)).build())
                    .build();
        } else {
            // 200 requests per minute for general navigation
            return Bucket.builder()
                    .addLimit(Bandwidth.builder().capacity(200).refillIntervally(200, Duration.ofMinutes(1)).build())
                    .build();
        }
    }

    private String resolveClientIp(HttpServletRequest request) {
        String xf = request.getHeader("X-Forwarded-For");
        if (xf != null && !xf.isBlank()) {
            return xf.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
