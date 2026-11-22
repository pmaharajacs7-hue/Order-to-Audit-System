package com.orderaudit.product;

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
@RequestMapping("/api/products")
public class ProductController {

    public record ProductRequest(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Size(max = 40) String sku,
            @Positive int packSizeG,
            @NotNull @DecimalMin("0.00") @Digits(integer = 12, fraction = 2) BigDecimal unitPrice,
            @NotBlank @Pattern(regexp = "^[0-9]{4,8}$", message = "must be a 4 to 8 digit HSN code") String hsnCode,
            @NotNull @DecimalMin("0.00") @DecimalMax("100.00") @Digits(integer = 3, fraction = 2) BigDecimal gstRatePct,
            Long bomId) {}

    public record ProductResponse(Long id, String name, String sku, int packSizeG, BigDecimal unitPrice,
                                  String hsnCode, BigDecimal gstRatePct, Long bomId, boolean active) {
        static ProductResponse of(Product p) {
            return new ProductResponse(p.getId(), p.getName(), p.getSku(), p.getPackSizeG(), p.getUnitPrice(),
                    p.getHsnCode(), p.getGstRatePct(), p.getBomId(), p.isActive());
        }
    }

    private final ProductRepository products;
    private final AuditService audit;

    public ProductController(ProductRepository products, AuditService audit) {
        this.products = products;
        this.audit = audit;
    }

    @GetMapping
    public List<ProductResponse> list(@RequestParam(defaultValue = "false") boolean includeInactive) {
        var rows = includeInactive ? products.findAll() : products.findByActiveTrueOrderByNameAsc();
        return rows.stream().map(ProductResponse::of).toList();
    }

    @GetMapping("/{id}")
    public ProductResponse get(@PathVariable Long id) {
        return ProductResponse.of(find(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ProductResponse create(@Valid @RequestBody ProductRequest r) {
        Product p = products.save(new Product(r.name(), r.sku(), r.packSizeG(), r.unitPrice(),
                r.hsnCode(), r.gstRatePct(), r.bomId()));
        audit.log("CREATE", "PRODUCT", p.getId(), "sku=" + p.getSku() + ", price=" + p.getUnitPrice());
        return ProductResponse.of(p);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ProductResponse update(@PathVariable Long id, @Valid @RequestBody ProductRequest r) {
        Product p = find(id);
        BigDecimal oldPrice = p.getUnitPrice();
        BigDecimal oldGst = p.getGstRatePct();
        p.update(r.name(), r.sku(), r.packSizeG(), r.unitPrice(), r.hsnCode(), r.gstRatePct(), r.bomId());
        // Price and tax changes matter most to an auditor, so record old -> new.
        audit.log("UPDATE", "PRODUCT", id, "price " + oldPrice + "->" + p.getUnitPrice()
                + ", gst " + oldGst + "->" + p.getGstRatePct());
        products.flush(); // surface a duplicate-SKU error inside this request
        return ProductResponse.of(p);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void deactivate(@PathVariable Long id) {
        Product p = find(id);
        p.setActive(false);
        audit.log("DEACTIVATE", "PRODUCT", id, "sku=" + p.getSku());
    }

    private Product find(Long id) {
        return products.findById(id).orElseThrow(() -> new NotFoundException("Product", id));
    }
}
