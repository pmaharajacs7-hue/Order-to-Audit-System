package com.orderaudit.auth;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Tiny endpoints that exist to demonstrate and test role-based access.
 * Real modules in later phases use the same @PreAuthorize pattern.
 */
@RestController
@RequestMapping("/api/rolecheck")
public class RoleCheckController {

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, String> adminOnly() {
        return Map.of("message", "admin area");
    }

    @GetMapping("/reports")
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT','OWNER')")
    public Map<String, String> reportsArea() {
        return Map.of("message", "reports area");
    }

    @GetMapping("/production")
    @PreAuthorize("hasAnyRole('ADMIN','PRODUCTION_SUPERVISOR')")
    public Map<String, String> productionArea() {
        return Map.of("message", "production area");
    }
}
