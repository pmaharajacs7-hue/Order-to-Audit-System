package com.orderaudit.production;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/production")
public class ProductionController {

    private final ProductionService service;

    public ProductionController(ProductionService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','PRODUCTION_SUPERVISOR')")
    public ProductionService.ProductionResponse record(@Valid @RequestBody ProductionService.ProductionRequest r) {
        return service.record(r);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','PRODUCTION_SUPERVISOR','ACCOUNTANT','OWNER')")
    public List<ProductionService.ProductionResponse> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','PRODUCTION_SUPERVISOR','ACCOUNTANT','OWNER')")
    public ProductionService.ProductionResponse get(@PathVariable Long id) {
        return service.get(id);
    }
}
