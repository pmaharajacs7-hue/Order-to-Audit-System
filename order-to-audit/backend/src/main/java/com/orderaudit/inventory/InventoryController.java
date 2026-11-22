package com.orderaudit.inventory;

import com.orderaudit.audit.AuditService;
import com.orderaudit.common.BusinessException;
import com.orderaudit.common.NotFoundException;
import com.orderaudit.product.ProductRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    public record StockRow(String itemType, Long itemId, String name, String unit, String category,
                           BigDecimal balance, BigDecimal reorderLevel, boolean belowReorder) {}

    public record LedgerRow(Long id, LocalDateTime createdAt, String movementType, BigDecimal quantity,
                            BigDecimal unitCost, String referenceType, Long referenceId, String notes,
                            BigDecimal balanceAfter) {}

    public record AdjustmentRequest(
            @NotBlank @Pattern(regexp = "^(RAW_MATERIAL|PRODUCT)$") String itemType,
            @NotNull Long itemId,
            @NotBlank @Pattern(regexp = "^(OPENING|ADJUSTMENT)$", message = "must be OPENING or ADJUSTMENT") String movementType,
            @NotNull BigDecimal quantity,
            @DecimalMin("0.0000") BigDecimal unitCost,
            @Size(max = 255) String notes) {}

    private final StockService stock;
    private final RawMaterialRepository materials;
    private final ProductRepository products;
    private final StockMovementRepository movements;
    private final AuditService audit;

    public InventoryController(StockService stock, RawMaterialRepository materials, ProductRepository products,
                               StockMovementRepository movements, AuditService audit) {
        this.stock = stock;
        this.materials = materials;
        this.products = products;
        this.movements = movements;
        this.audit = audit;
    }

    /** Current stock of every active raw material, packaging item and finished product. */
    @GetMapping("/stock")
    @PreAuthorize("hasAnyRole('ADMIN','PRODUCTION_SUPERVISOR','ACCOUNTANT','OWNER')")
    @Transactional(readOnly = true)
    public List<StockRow> stock() {
        List<StockRow> rows = new ArrayList<>();
        Map<Long, BigDecimal> rawBalances = stock.balances(StockService.RAW_MATERIAL);
        for (RawMaterial m : materials.findByActiveTrueOrderByNameAsc()) {
            BigDecimal bal = rawBalances.getOrDefault(m.getId(), BigDecimal.ZERO);
            rows.add(new StockRow(StockService.RAW_MATERIAL, m.getId(), m.getName(), m.getUnit(), m.getCategory(),
                    bal, m.getReorderLevel(), bal.compareTo(m.getReorderLevel()) < 0));
        }
        Map<Long, BigDecimal> productBalances = stock.balances(StockService.PRODUCT);
        for (var p : products.findByActiveTrueOrderByNameAsc()) {
            BigDecimal bal = productBalances.getOrDefault(p.getId(), BigDecimal.ZERO);
            rows.add(new StockRow(StockService.PRODUCT, p.getId(), p.getName(), "pack", "FINISHED_GOOD",
                    bal, BigDecimal.ZERO, false));
        }
        return rows;
    }

    /** Full history for one item with a running balance, oldest first. */
    @GetMapping("/ledger")
    @PreAuthorize("hasAnyRole('ADMIN','PRODUCTION_SUPERVISOR','ACCOUNTANT','OWNER')")
    public List<LedgerRow> ledger(@RequestParam String itemType, @RequestParam Long itemId) {
        if (!itemType.equals(StockService.RAW_MATERIAL) && !itemType.equals(StockService.PRODUCT)) {
            throw new IllegalArgumentException("itemType must be RAW_MATERIAL or PRODUCT");
        }
        List<LedgerRow> rows = new ArrayList<>();
        BigDecimal running = BigDecimal.ZERO;
        for (StockMovement m : movements.findByItemTypeAndItemIdOrderByIdAsc(itemType, itemId)) {
            running = running.add(m.getQuantity());
            rows.add(new LedgerRow(m.getId(), m.getCreatedAt(), m.getMovementType(), m.getQuantity(),
                    m.getUnitCost(), m.getReferenceType(), m.getReferenceId(), m.getNotes(), running));
        }
        return rows;
    }

    /** Opening stock and manual corrections. Corrections are ledger rows too, never overwrites. */
    @PostMapping("/adjustments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public StockRow adjust(@Valid @RequestBody AdjustmentRequest r) {
        String name;
        String unit;
        String category;
        if (r.itemType().equals(StockService.RAW_MATERIAL)) {
            RawMaterial m = materials.findById(r.itemId()).orElseThrow(() -> new NotFoundException("Raw material", r.itemId()));
            name = m.getName();
            unit = m.getUnit();
            category = m.getCategory();
        } else {
            var p = products.findById(r.itemId()).orElseThrow(() -> new NotFoundException("Product", r.itemId()));
            name = p.getName();
            unit = "pack";
            category = "FINISHED_GOOD";
        }
        if (r.quantity().signum() == 0) {
            throw new IllegalArgumentException("quantity cannot be zero");
        }
        if (r.movementType().equals("OPENING") && (r.quantity().signum() < 0 || r.unitCost() == null)) {
            throw new IllegalArgumentException("OPENING needs a positive quantity and a unitCost");
        }
        BigDecimal after = stock.balance(r.itemType(), r.itemId()).add(r.quantity());
        if (after.signum() < 0) {
            throw new BusinessException("Adjustment would make stock of " + name + " negative");
        }
        StockMovement m = stock.record(r.itemType(), r.itemId(), r.movementType(), r.quantity(),
                r.unitCost(), "ADJUSTMENT", null, r.notes());
        audit.log(r.movementType(), "STOCK", m.getId(), name + " " + r.quantity() + " " + unit);
        return new StockRow(r.itemType(), r.itemId(), name, unit, category, after, BigDecimal.ZERO, false);
    }
}
