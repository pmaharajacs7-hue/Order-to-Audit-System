package com.orderaudit.bom;

import com.orderaudit.audit.AuditService;
import com.orderaudit.common.NotFoundException;
import com.orderaudit.inventory.RawMaterialRepository;
import com.orderaudit.product.Product;
import com.orderaudit.product.ProductRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/boms")
public class BomController {

    public record BomItemRequest(
            @NotNull Long materialId,
            @NotNull @DecimalMin("0.0001") @Digits(integer = 10, fraction = 4) BigDecimal quantity,
            @Pattern(regexp = "^(PER_BATCH|PER_PACKET)$", message = "must be PER_BATCH or PER_PACKET") String basis,
            @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal tolerancePct) {}

    public record BomRequest(
            @NotBlank @Size(max = 100) String name,
            @NotNull @DecimalMin("0.001") BigDecimal batchOutputKg,
            @NotEmpty @Valid List<BomItemRequest> items) {}

    public record BomItemResponse(Long materialId, String materialName, String unit, BigDecimal quantity,
                                  String basis, BigDecimal tolerancePct) {}

    public record BomResponse(Long id, String name, BigDecimal batchOutputKg, boolean active,
                              List<BomItemResponse> items) {}

    public record PlanResponse(Long bomId, Long productId, String productName, int packSizeG,
                               BigDecimal batches, int expectedPackets, List<BomService.PlanLine> materials) {}

    private final BomRepository boms;
    private final RawMaterialRepository materials;
    private final ProductRepository products;
    private final BomService bomService;
    private final AuditService audit;

    public BomController(BomRepository boms, RawMaterialRepository materials, ProductRepository products,
                         BomService bomService, AuditService audit) {
        this.boms = boms;
        this.materials = materials;
        this.products = products;
        this.bomService = bomService;
        this.audit = audit;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','PRODUCTION_SUPERVISOR','ACCOUNTANT','OWNER')")
    @Transactional(readOnly = true)
    public List<BomResponse> list() {
        return boms.findByActiveTrueOrderByNameAsc().stream().map(this::toResponse).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','PRODUCTION_SUPERVISOR','ACCOUNTANT','OWNER')")
    @Transactional(readOnly = true)
    public BomResponse get(@PathVariable Long id) {
        return toResponse(find(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public BomResponse create(@Valid @RequestBody BomRequest r) {
        Bom bom = new Bom(r.name(), r.batchOutputKg());
        fillItems(bom, r.items());
        boms.saveAndFlush(bom);
        audit.log("CREATE", "BOM", bom.getId(), "name=" + bom.getName() + ", items=" + bom.getItems().size());
        return toResponse(bom);
    }

    /** Replaces the whole recipe. The previous version stays visible in the audit trail. */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public BomResponse update(@PathVariable Long id, @Valid @RequestBody BomRequest r) {
        Bom bom = find(id);
        String before = describe(bom);
        bom.clearItems();
        boms.flush(); // delete old rows first, so re-adding the same material doesn't hit the unique key
        bom.update(r.name(), r.batchOutputKg());
        fillItems(bom, r.items());
        boms.saveAndFlush(bom);
        audit.log("UPDATE", "BOM", id, "before: " + before + " | after: " + describe(bom));
        return toResponse(bom);
    }

    /** What would one run of this product need? Expected packets plus required materials. */
    @GetMapping("/{id}/plan")
    @PreAuthorize("hasAnyRole('ADMIN','PRODUCTION_SUPERVISOR','ACCOUNTANT','OWNER')")
    @Transactional(readOnly = true)
    public PlanResponse plan(@PathVariable Long id, @RequestParam Long productId,
                             @RequestParam(defaultValue = "1") BigDecimal batches) {
        if (batches.signum() <= 0) throw new IllegalArgumentException("batches must be positive");
        Bom bom = find(id);
        Product product = products.findById(productId).orElseThrow(() -> new NotFoundException("Product", productId));
        BomService.Plan plan = bomService.plan(product, bom, batches);
        return new PlanResponse(bom.getId(), product.getId(), product.getName(), product.getPackSizeG(),
                batches, plan.expectedPackets(), plan.lines());
    }

    // ---------- helpers ----------

    private void fillItems(Bom bom, List<BomItemRequest> items) {
        Set<Long> seen = new HashSet<>();
        for (BomItemRequest i : items) {
            if (!seen.add(i.materialId())) {
                throw new IllegalArgumentException("Material " + i.materialId() + " appears twice in the BOM");
            }
            var material = materials.findById(i.materialId())
                    .orElseThrow(() -> new NotFoundException("Raw material", i.materialId()));
            bom.addItem(new BomItem(material, i.quantity(), i.basis(), i.tolerancePct()));
        }
    }

    private String describe(Bom bom) {
        StringBuilder sb = new StringBuilder(bom.getName()).append(" yields ").append(bom.getBatchOutputKg()).append("kg: ");
        bom.getItems().forEach(i -> sb.append(i.getMaterial().getName()).append("=").append(i.getQuantity())
                .append(" ").append(i.getBasis()).append("; "));
        return sb.toString();
    }

    private BomResponse toResponse(Bom b) {
        return new BomResponse(b.getId(), b.getName(), b.getBatchOutputKg(), b.isActive(),
                b.getItems().stream().map(i -> new BomItemResponse(i.getMaterial().getId(), i.getMaterial().getName(),
                        i.getMaterial().getUnit(), i.getQuantity(), i.getBasis(), i.getTolerancePct())).toList());
    }

    private Bom find(Long id) {
        return boms.findById(id).orElseThrow(() -> new NotFoundException("BOM", id));
    }
}
