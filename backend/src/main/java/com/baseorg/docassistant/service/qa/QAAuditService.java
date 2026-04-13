package com.baseorg.docassistant.service.qa;

import com.baseorg.docassistant.entity.AuditLog;
import com.baseorg.docassistant.mapper.AuditLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class QAAuditService {

    private final AuditLogMapper auditLogMapper;

    public void record(Long userId,
                       Long sessionId,
                       String promptHash,
                       long processingTimeMs,
                       boolean degraded,
                       String rewrittenQuery,
                       String intent,
                       int retrievalHitCount,
                       String fallbackMode,
                       Map<String, Object> extraDetails) {
        try {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("processingTimeMs", processingTimeMs);
            details.put("degraded", degraded);
            details.put("rewrittenQuery", rewrittenQuery);
            details.put("intent", intent);
            details.put("retrievalHitCount", retrievalHitCount);
            details.put("fallbackMode", fallbackMode == null ? "NONE" : fallbackMode);
            if (extraDetails != null && !extraDetails.isEmpty()) {
                details.putAll(extraDetails);
            }
            AuditLog log = AuditLog.builder()
                    .userId(userId)
                    .action(AuditLog.Action.QA_QUERY)
                    .resourceType(AuditLog.ResourceType.QA_SESSION)
                    .resourceId(sessionId == null ? null : String.valueOf(sessionId))
                    .promptHash(promptHash)
                    .details(details)
                    .build();
            auditLogMapper.insert(log);
        } catch (Exception e) {
            log.warn("审计日志写入失败，不影响主流程: sessionId={}, reason={}", sessionId, e.getMessage());
        }
    }
}
