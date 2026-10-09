package com.healthcart.service;

import com.healthcart.domain.AuditLog;
import com.healthcart.repository.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;

@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional
    public void record(String actorEmail, String action, String entity, String entityId, String details) {
        String clientIp = resolveClientIp();
        AuditLog auditLog = new AuditLog(actorEmail, action, entity, entityId, clientIp, details);
        auditLogRepository.save(auditLog);
        log.info("AUDIT: actor={}, action={}, entity={}:{}, ip={}", actorEmail, action, entity, entityId, clientIp);
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> getLogs(String actor, String action, LocalDateTime fromDate, LocalDateTime toDate, Pageable pageable) {
        return auditLogRepository.searchLogs(actor, action, fromDate, toDate, pageable);
    }

    private String resolveClientIp() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                String xForwardedFor = request.getHeader("X-Forwarded-For");
                if (xForwardedFor != null && !xForwardedFor.isBlank()) {
                    return xForwardedFor.split(",")[0].trim();
                }
                return request.getRemoteAddr();
            }
        } catch (Exception ignored) {
        }
        return "127.0.0.1";
    }
}
