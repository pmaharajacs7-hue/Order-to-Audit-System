package com.orderaudit.purchase;

import com.orderaudit.audit.AuditService;
import com.orderaudit.common.NotFoundException;
import com.orderaudit.inventory.RawMaterialRepository;
import com.orderaudit.inventory.StockService;
import com.orderaudit.security.CurrentUser;
import com.orderaudit.supplier.SupplierRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/purchases")
public class PurchaseController {

    public record PurchaseItemRequest(
            @NotNull Long materialId,
            @NotNull @DecimalMin("0.001") @Digits(integer = 11, fraction = 3) BigDecimal quantity,
            @NotNull @DecimalMin("0.00") @Digits(integer = 12, fraction = 2) BigDecimal unitPrice) {}

    public record PurchaseRequest(
            @NotNull Long supplierId,
            @Size(max = 40) String invoiceNo,
            LocalDate purchaseDate,
            @NotEmpty @Valid List<PurchaseItemRequest> items) {}

    public record PurchaseItemResponse(Long materialId, String materialName, String unit,
                                       BigDecimal quantity, BigDecimal unitPrice, BigDecimal lineTotal) {}

    public record PurchaseResponse(Long id, Long supplierId, String supplierName, String invoiceNo,
                                   LocalDate purchaseDate, BigDecimal totalAmount, List<PurchaseItemResponse> items) {}

    private final PurchaseRepository purchases;
    private final SupplierRepository suppliers;
    private final RawMaterialRepository materials;
    private final StockService stock;
    private final CurrentUser currentUser;
    private final AuditService audit;

    public PurchaseController(PurchaseRepository purchases, SupplierRepository suppliers,
                              RawMaterialRepository materials, StockService stock,
                              CurrentUser currentUser, AuditService audit) {
        this.purchases = purchases;
        this.suppliers = suppliers;
        this.materials = materials;
        this.stock = stock;
        this.currentUser = currentUser;
        this.audit = audit;
    }

    /** Saving a purchase and adding it to stock happen in one transaction: both succeed or neither does. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public PurchaseResponse create(@Valid @RequestBody PurchaseRequest r) {
        var supplier = suppliers.findById(r.supplierId())
                .orElseThrow(() -> new NotFoundException("Supplier", r.supplierId()));
        if (!supplier.isActive()) throw new IllegalArgumentException("Supplier is inactive");

        Purchase purchase = new Purchase(supplier, r.invoiceNo(),
                r.purchaseDate() != null ? r.purchaseDate() : LocalDate.now(), currentUser.id());
        for (PurchaseItemRequest i : r.items()) {
            var material = materials.findById(i.materialId())
                    .orElseThrow(() -> new NotFoundException("Raw material", i.materialId()));
            if (!material.isActive()) throw new IllegalArgumentException(material.getName() + " is inactive");
            purchase.addItem(new PurchaseItem(material, i.quantity(), i.unitPrice()));
        }
        purchases.saveAndFlush(purchase);

        for (PurchaseItem item : purchase.getItems()) {
            stock.record(StockService.RAW_MATERIAL, item.getMaterial().getId(), "PURCHASE",
                    item.getQuantity(), item.getUnitPrice(), "PURCHASE", purchase.getId(), null);
        }
        audit.log("CREATE", "PURCHASE", purchase.getId(),
                "supplier=" + supplier.getName() + ", total=" + purchase.getTotalAmount());
        return toResponse(purchase);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT','OWNER')")
    @Transactional(readOnly = true)
    public List<PurchaseResponse> list() {
        return purchases.findAllByOrderByIdDesc().stream().map(this::toResponse).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT','OWNER')")
    @Transactional(readOnly = true)
    public PurchaseResponse get(@PathVariable Long id) {
        return toResponse(purchases.findById(id).orElseThrow(() -> new NotFoundException("Purchase", id)));
    }

    private PurchaseResponse toResponse(Purchase p) {
        return new PurchaseResponse(p.getId(), p.getSupplier().getId(), p.getSupplier().getName(), p.getInvoiceNo(),
                p.getPurchaseDate(), p.getTotalAmount(),
                p.getItems().stream().map(i -> new PurchaseItemResponse(i.getMaterial().getId(),
                        i.getMaterial().getName(), i.getMaterial().getUnit(), i.getQuantity(),
                        i.getUnitPrice(), i.getLineTotal())).toList());
    }
}
