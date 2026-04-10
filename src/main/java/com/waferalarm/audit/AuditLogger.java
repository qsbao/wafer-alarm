package com.waferalarm.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waferalarm.domain.AuditLogEntity;
import com.waferalarm.domain.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class AuditLogger {

    private static final Logger log = LoggerFactory.getLogger(AuditLogger.class);

    private final AuditLogRepository repo;
    private final ObjectMapper objectMapper;

    public AuditLogger(AuditLogRepository repo, ObjectMapper objectMapper) {
        this.repo = repo;
        this.objectMapper = objectMapper;
    }

    public void log(String entityType, Long entityId, String action,
                    Object before, Object after) {
        String beforeJson = toJson(before);
        String afterJson = toJson(after);
        String sourceIp = resolveSourceIp();

        repo.save(new AuditLogEntity(
                entityType, entityId, action,
                "system", sourceIp,
                beforeJson, afterJson));
    }

    private String toJson(Object obj) {
        if (obj == null) return null;
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("Failed to serialize audit snapshot", e);
            return obj.toString();
        }
    }

    private String resolveSourceIp() {
        try {
            var attrs = RequestContextHolder.getRequestAttributes();
            if (attrs instanceof ServletRequestAttributes sra) {
                HttpServletRequest request = sra.getRequest();
                return request.getRemoteAddr();
            }
        } catch (Exception ignored) {}
        return null;
    }
}
