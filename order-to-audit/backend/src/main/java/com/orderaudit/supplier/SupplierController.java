package com.orderaudit.supplier;

import com.orderaudit.audit.AuditService;
import com.orderaudit.common.NotFoundException;
import com.orderaudit.customer.CustomerController;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/suppliers")
public class SupplierController {

    public record SupplierRequest(
            @NotBlank @Size(max = 120) String name,
            @Pattern(regexp = CustomerController.GSTIN_REGEX, message = "must be a valid 15-character GSTIN") String gstin,
            @Size(max = 20) String phone,
            @Size(max = 255) String address) {}

    public record SupplierResponse(Long id, String name, String gstin, String phone, String address, boolean active) {
        static SupplierResponse of(Supplier s) {
            return new SupplierResponse(s.getId(), s.getName(), s.getGstin(), s.getPhone(), s.getAddress(), s.isActive());
        }
    }

    private final SupplierRepository suppliers;
    private final AuditService audit;

    public SupplierController(SupplierRepository suppliers, AuditService audit) {
        this.suppliers = suppliers;
        this.audit = audit;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT','OWNER')")
    public List<SupplierResponse> list(@RequestParam(defaultValue = "false") boolean includeInactive) {
        var rows = includeInactive ? suppliers.findAll() : suppliers.findByActiveTrueOrderByNameAsc();
        return rows.stream().map(SupplierResponse::of).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT','OWNER')")
    public SupplierResponse get(@PathVariable Long id) {
        return SupplierResponse.of(find(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public SupplierResponse create(@Valid @RequestBody SupplierRequest r) {
        Supplier s = suppliers.save(new Supplier(r.name(), r.gstin(), r.phone(), r.address()));
        audit.log("CREATE", "SUPPLIER", s.getId(), "name=" + s.getName());
        return SupplierResponse.of(s);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public SupplierResponse update(@PathVariable Long id, @Valid @RequestBody SupplierRequest r) {
        Supplier s = find(id);
        s.update(r.name(), r.gstin(), r.phone(), r.address());
        audit.log("UPDATE", "SUPPLIER", id, "name=" + s.getName());
        return SupplierResponse.of(s);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void deactivate(@PathVariable Long id) {
        Supplier s = find(id);
        s.setActive(false);
        audit.log("DEACTIVATE", "SUPPLIER", id, "name=" + s.getName());
    }

    private Supplier find(Long id) {
        return suppliers.findById(id).orElseThrow(() -> new NotFoundException("Supplier", id));
    }
}
