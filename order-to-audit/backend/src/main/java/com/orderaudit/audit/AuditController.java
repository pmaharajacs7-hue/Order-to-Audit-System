package com.orderaudit.audit;

import com.orderaudit.user.User;
import com.orderaudit.user.UserRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Read-only view of the audit trail: the latest 200 changes, or the full history of one record. */
@RestController
@RequestMapping("/api/audit")
@PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT','OWNER')")
public class AuditController {

    public record AuditRow(Long id, LocalDateTime createdAt, String username, String action,
                           String entityType, Long entityId, String details) {}

    private final AuditLogRepository logs;
    private final UserRepository users;

    public AuditController(AuditLogRepository logs, UserRepository users) {
        this.logs = logs;
        this.users = users;
    }

    @GetMapping
    public List<AuditRow> list(@RequestParam(required = false) String entityType,
                               @RequestParam(required = false) Long entityId) {
        List<AuditLog> rows = (entityType != null && entityId != null)
                ? logs.findByEntityTypeAndEntityIdOrderByIdAsc(entityType, entityId)
                : logs.findTop200ByOrderByIdDesc();
        Map<Long, String> names = users.findAll().stream()
                .collect(Collectors.toMap(User::getId, User::getUsername));
        return rows.stream().map(l -> new AuditRow(l.getId(), l.getCreatedAt(),
                l.getUserId() == null ? null : names.get(l.getUserId()),
                l.getAction(), l.getEntityType(), l.getEntityId(), l.getDetails())).toList();
    }
}
