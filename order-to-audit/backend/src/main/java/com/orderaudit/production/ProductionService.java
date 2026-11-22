package com.orderaudit.production;

import com.orderaudit.audit.AuditService;
import com.orderaudit.bom.Bom;
import com.orderaudit.bom.BomRepository;
import com.orderaudit.bom.BomService;
import com.orderaudit.common.BusinessException;
import com.orderaudit.common.NotFoundException;
import com.orderaudit.inventory.RawMaterialRepository;
import com.orderaudit.inventory.StockService;
import com.orderaudit.product.Product;
import com.orderaudit.product.ProductRepository;
import com.orderaudit.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Records one production run end to end, in a single transaction:
 *   1. plan = what the BOM says this run should use and make
 *   2. compare actual material use and good output with the plan (variance %)
 *   3. issue materials from stock and add finished packets to stock (ledger rows)
 *   4. cost the run (weighted-average material cost + labour + utilities)
 */
@Service
public class ProductionService {

    public record MaterialUse(@NotNull Long materialId, @NotNull @DecimalMin("0.000") BigDecimal actualQty) {}

    public record ProductionRequest(
            @NotNull Long productId,
            @DecimalMin("0.01") BigDecimal batches,
            LocalDate runDate,
            @PositiveOrZero int actualOutput,
            @PositiveOrZero int goodOutput,
            @PositiveOrZero int damagedUnits,
            @DecimalMin("0.00") BigDecimal labourCost,
            @DecimalMin("0.00") BigDecimal utilitiesCost,
            @Valid List<MaterialUse> materials) {}

    public record MaterialResult(Long materialId, String materialName, String unit, BigDecimal standardQty,
                                 BigDecimal actualQty, BigDecimal variancePct, boolean flagged,
                                 BigDecimal unitCost) {}

    public record ProductionResponse(Long id, Long productId, String productName, LocalDate runDate,
                                     BigDecimal batches, int expectedOutput, int actualOutput, int goodOutput,
                                     int damagedUnits, BigDecimal yieldVariancePct, boolean yieldFlagged,
                                     BigDecimal materialCost, BigDecimal labourCost, BigDecimal utilitiesCost,
                                     BigDecimal totalCost, BigDecimal costPerPacket, boolean anyFlag,
                                     List<MaterialResult> materials) {}

    private final ProductRepository products;
    private final BomRepository boms;
    private final RawMaterialRepository materials;
    private final BomService bomService;
    private final StockService stock;
    private final ProductionRunRepository runs;
    private final CurrentUser currentUser;
    private final AuditService audit;
    private final BigDecimal yieldTolerance;

    public ProductionService(ProductRepository products, BomRepository boms, RawMaterialRepository materials,
                             BomService bomService, StockService stock, ProductionRunRepository runs,
                             CurrentUser currentUser, AuditService audit,
                             @Value("${app.production.yield-tolerance-pct:3}") BigDecimal yieldTolerance) {
        this.products = products;
        this.boms = boms;
        this.materials = materials;
        this.bomService = bomService;
        this.stock = stock;
        this.runs = runs;
        this.currentUser = currentUser;
        this.audit = audit;
        this.yieldTolerance = yieldTolerance;
    }

    @Transactional
    public ProductionResponse record(ProductionRequest r) {
        Product product = products.findById(r.productId())
                .orElseThrow(() -> new NotFoundException("Product", r.productId()));
        if (!product.isActive()) throw new IllegalArgumentException("Product is inactive");
        if (product.getBomId() == null) throw new IllegalArgumentException("Product has no BOM linked");
        Bom bom = boms.findById(product.getBomId()).orElseThrow(() -> new NotFoundException("BOM", product.getBomId()));

        if (r.goodOutput() > r.actualOutput()) {
            throw new IllegalArgumentException("goodOutput cannot be more than actualOutput");
        }
        BigDecimal batches = r.batches() == null ? BigDecimal.ONE : r.batches();
        BomService.Plan plan = bomService.plan(product, bom, batches);

        // actual material use by id; anything not listed is assumed to be used exactly as per the BOM
        Map<Long, BigDecimal> reported = new HashMap<>();
        if (r.materials() != null) {
            for (MaterialUse u : r.materials()) {
                boolean inBom = plan.lines().stream().anyMatch(l -> l.materialId().equals(u.materialId()));
                if (!inBom) throw new IllegalArgumentException("Material " + u.materialId() + " is not part of this BOM");
                if (reported.put(u.materialId(), u.actualQty()) != null) {
                    throw new IllegalArgumentException("Material " + u.materialId() + " listed twice");
                }
            }
        }

        // 1) check stock for every material before changing anything
        for (BomService.PlanLine line : plan.lines()) {
            BigDecimal actual = reported.getOrDefault(line.materialId(), line.standardQty());
            BigDecimal have = stock.balance(StockService.RAW_MATERIAL, line.materialId());
            if (actual.compareTo(have) > 0) {
                throw new BusinessException("Insufficient stock of " + line.materialName()
                        + ": need " + actual.stripTrailingZeros().toPlainString() + " " + line.unit()
                        + ", have " + have.stripTrailingZeros().toPlainString());
            }
        }

        // 2) variances and material cost
        BigDecimal expectedOutput = BigDecimal.valueOf(plan.expectedPackets());
        BigDecimal yieldVariance = variancePct(BigDecimal.valueOf(r.goodOutput()), expectedOutput);
        boolean yieldFlagged = yieldVariance.abs().compareTo(yieldTolerance) > 0;

        BigDecimal materialCost = BigDecimal.ZERO;
        List<ProductionRunMaterial> lines = new java.util.ArrayList<>();
        for (BomService.PlanLine line : plan.lines()) {
            BigDecimal actual = reported.getOrDefault(line.materialId(), line.standardQty());
            BigDecimal variance = variancePct(actual, line.standardQty());
            boolean flagged = variance.abs().compareTo(line.tolerancePct()) > 0;
            BigDecimal unitCost = stock.averageCost(StockService.RAW_MATERIAL, line.materialId());
            materialCost = materialCost.add(actual.multiply(unitCost));
            lines.add(new ProductionRunMaterial(materials.getReferenceById(line.materialId()),
                    line.standardQty(), actual.setScale(3, RoundingMode.HALF_UP), variance, flagged, unitCost));
        }
        materialCost = materialCost.setScale(2, RoundingMode.HALF_UP);

        BigDecimal labour = r.labourCost() == null ? BigDecimal.ZERO : r.labourCost();
        BigDecimal utilities = r.utilitiesCost() == null ? BigDecimal.ZERO : r.utilitiesCost();
        BigDecimal totalCost = materialCost.add(labour).add(utilities);
        BigDecimal costPerPacket = r.goodOutput() == 0 ? null
                : totalCost.divide(BigDecimal.valueOf(r.goodOutput()), 4, RoundingMode.HALF_UP);

        // 3) save the run, then write the ledger
        ProductionRun run = new ProductionRun(product.getId(), bom.getId(), batches,
                r.runDate() != null ? r.runDate() : LocalDate.now(), plan.expectedPackets(), r.actualOutput(),
                r.goodOutput(), r.damagedUnits(), yieldVariance, yieldFlagged, labour, utilities, materialCost,
                costPerPacket, currentUser.id());
        lines.forEach(run::addMaterial);
        runs.saveAndFlush(run);

        for (ProductionRunMaterial m : run.getMaterials()) {
            if (m.getActualQty().signum() > 0) {
                stock.record(StockService.RAW_MATERIAL, m.getMaterial().getId(), "PRODUCTION_ISSUE",
                        m.getActualQty().negate(), m.getUnitCost(), "PRODUCTION_RUN", run.getId(), null);
            }
        }
        if (r.goodOutput() > 0) {
            stock.record(StockService.PRODUCT, product.getId(), "PRODUCTION_OUTPUT",
                    BigDecimal.valueOf(r.goodOutput()), costPerPacket, "PRODUCTION_RUN", run.getId(), null);
        }

        audit.log("CREATE", "PRODUCTION_RUN", run.getId(), product.getName() + ": expected " + plan.expectedPackets()
                + ", good " + r.goodOutput() + ", yield variance " + yieldVariance + "%");
        return toResponse(run, product.getName());
    }

    @Transactional(readOnly = true)
    public ProductionResponse get(Long id) {
        ProductionRun run = runs.findById(id).orElseThrow(() -> new NotFoundException("Production run", id));
        return toResponse(run, productName(run.getProductId()));
    }

    @Transactional(readOnly = true)
    public List<ProductionResponse> list() {
        return runs.findAllByOrderByIdDesc().stream().map(run -> toResponse(run, productName(run.getProductId()))).toList();
    }

    private String productName(Long productId) {
        return products.findById(productId).map(Product::getName).orElse("?");
    }

    /** (actual - standard) / standard * 100, to 2 decimals. Zero standard means no meaningful variance. */
    static BigDecimal variancePct(BigDecimal actual, BigDecimal standard) {
        if (standard.signum() == 0) return BigDecimal.ZERO.setScale(2);
        return actual.subtract(standard).multiply(BigDecimal.valueOf(100))
                .divide(standard, 2, RoundingMode.HALF_UP);
    }

    private ProductionResponse toResponse(ProductionRun run, String productName) {
        BigDecimal total = run.getMaterialCost().add(run.getLabourCost()).add(run.getUtilitiesCost());
        List<MaterialResult> mats = run.getMaterials().stream().map(m -> new MaterialResult(
                m.getMaterial().getId(), m.getMaterial().getName(), m.getMaterial().getUnit(), m.getStandardQty(),
                m.getActualQty(), m.getVariancePct(), m.isFlagged(), m.getUnitCost())).toList();
        boolean anyFlag = run.isYieldFlagged() || run.getMaterials().stream().anyMatch(ProductionRunMaterial::isFlagged);
        return new ProductionResponse(run.getId(), run.getProductId(), productName, run.getRunDate(), run.getBatches(),
                run.getExpectedOutput(), run.getActualOutput(), run.getGoodOutput(), run.getDamagedUnits(),
                run.getYieldVariancePct(), run.isYieldFlagged(), run.getMaterialCost(), run.getLabourCost(),
                run.getUtilitiesCost(), total, run.getCostPerPacket(), anyFlag, mats);
    }
}
