package com.orderaudit.inventory;

import com.orderaudit.audit.AuditService;
import com.orderaudit.common.NotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/raw-materials")
public class RawMaterialController {

    public record MaterialRequest(
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Pattern(regexp = "^(RAW|PACKAGING)$", message = "must be RAW or PACKAGING") String category,
            @NotBlank @Size(max = 10) String unit,
            @DecimalMin("0.000") BigDecimal reorderLevel) {}

    public record MaterialResponse(Long id, String name, String category, String unit,
                                   BigDecimal reorderLevel, boolean active) {
        static MaterialResponse of(RawMaterial m) {
            return new MaterialResponse(m.getId(), m.getName(), m.getCategory(), m.getUnit(),
                    m.getReorderLevel(), m.isActive());
        }
    }

    private final RawMaterialRepository materials;
    private final AuditService audit;

    public RawMaterialController(RawMaterialRepository materials, AuditService audit) {
        this.materials = materials;
        this.audit = audit;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','PRODUCTION_SUPERVISOR','ACCOUNTANT','OWNER')")
    public List<MaterialResponse> list(@RequestParam(defaultValue = "false") boolean includeInactive) {
        var rows = includeInactive ? materials.findAll() : materials.findByActiveTrueOrderByNameAsc();
        return rows.stream().map(MaterialResponse::of).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','PRODUCTION_SUPERVISOR','ACCOUNTANT','OWNER')")
    public MaterialResponse get(@PathVariable Long id) {
        return MaterialResponse.of(find(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public MaterialResponse create(@Valid @RequestBody MaterialRequest r) {
        RawMaterial m = materials.save(new RawMaterial(r.name(), r.category(), r.unit(), r.reorderLevel()));
        audit.log("CREATE", "RAW_MATERIAL", m.getId(), "name=" + m.getName());
        return MaterialResponse.of(m);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public MaterialResponse update(@PathVariable Long id, @Valid @RequestBody MaterialRequest r) {
        RawMaterial m = find(id);
        m.update(r.name(), r.category(), r.unit(), r.reorderLevel());
        materials.flush();
        audit.log("UPDATE", "RAW_MATERIAL", id, "name=" + m.getName());
        return MaterialResponse.of(m);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void deactivate(@PathVariable Long id) {
        RawMaterial m = find(id);
        m.setActive(false);
        audit.log("DEACTIVATE", "RAW_MATERIAL", id, "name=" + m.getName());
    }

    private RawMaterial find(Long id) {
        return materials.findById(id).orElseThrow(() -> new NotFoundException("Raw material", id));
    }
}
