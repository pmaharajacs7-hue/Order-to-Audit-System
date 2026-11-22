package com.orderaudit.audit;

import com.orderaudit.user.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes one audit row per business change: who, what, which record.
 * Joins the caller's transaction, so if the change rolls back, so does its audit row.
 */
@Service
public class AuditService {

    private final AuditLogRepository logs;
    private final UserRepository users;

    public AuditService(AuditLogRepository logs, UserRepository users) {
        this.logs = logs;
        this.users = users;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void log(String action, String entityType, Long entityId, String details) {
        Long userId = null;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            userId = users.findByUsername(auth.getName()).map(u -> u.getId()).orElse(null);
        }
        String safe = details != null && details.length() > 1000 ? details.substring(0, 1000) : details;
        logs.save(new AuditLog(userId, action, entityType, entityId, safe));
    }
}
