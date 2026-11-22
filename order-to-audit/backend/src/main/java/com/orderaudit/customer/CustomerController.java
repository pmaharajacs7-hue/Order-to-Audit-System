package com.orderaudit.customer;

import com.orderaudit.audit.AuditService;
import com.orderaudit.common.NotFoundException;
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
@RequestMapping("/api/customers")
public class CustomerController {

    public static final String GSTIN_REGEX = "^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$";

    public record CustomerRequest(
            @NotBlank @Size(max = 120) String name,
            @Pattern(regexp = GSTIN_REGEX, message = "must be a valid 15-character GSTIN") String gstin,
            @Size(max = 20) String phone,
            @Size(max = 255) String address,
            @NotBlank @Pattern(regexp = "^[0-9]{2}$", message = "must be a 2-digit GST state code") String stateCode) {}

    public record CustomerResponse(Long id, String name, String gstin, String phone, String address,
                                   String stateCode, boolean active) {
        static CustomerResponse of(Customer c) {
            return new CustomerResponse(c.getId(), c.getName(), c.getGstin(), c.getPhone(),
                    c.getAddress(), c.getStateCode(), c.isActive());
        }
    }

    private final CustomerRepository customers;
    private final AuditService audit;

    public CustomerController(CustomerRepository customers, AuditService audit) {
        this.customers = customers;
        this.audit = audit;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SALESMAN','ACCOUNTANT','OWNER')")
    public List<CustomerResponse> list(@RequestParam(defaultValue = "false") boolean includeInactive) {
        var rows = includeInactive ? customers.findAll() : customers.findByActiveTrueOrderByNameAsc();
        return rows.stream().map(CustomerResponse::of).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SALESMAN','ACCOUNTANT','OWNER')")
    public CustomerResponse get(@PathVariable Long id) {
        return CustomerResponse.of(find(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public CustomerResponse create(@Valid @RequestBody CustomerRequest r) {
        Customer c = customers.save(new Customer(r.name(), r.gstin(), r.phone(), r.address(), r.stateCode()));
        audit.log("CREATE", "CUSTOMER", c.getId(), "name=" + c.getName());
        return CustomerResponse.of(c);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public CustomerResponse update(@PathVariable Long id, @Valid @RequestBody CustomerRequest r) {
        Customer c = find(id);
        c.update(r.name(), r.gstin(), r.phone(), r.address(), r.stateCode());
        audit.log("UPDATE", "CUSTOMER", id, "name=" + c.getName());
        return CustomerResponse.of(c);
    }

    /** Soft delete: customers are referenced by orders/invoices, so we deactivate instead. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void deactivate(@PathVariable Long id) {
        Customer c = find(id);
        c.setActive(false);
        audit.log("DEACTIVATE", "CUSTOMER", id, "name=" + c.getName());
    }

    private Customer find(Long id) {
        return customers.findById(id).orElseThrow(() -> new NotFoundException("Customer", id));
    }
}
