package com.orderaudit.production;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "production_runs")
public class ProductionRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "bom_id", nullable = false)
    private Long bomId;

    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal batches;

    @Column(name = "run_date", nullable = false)
    private LocalDate runDate;

    @Column(name = "expected_output", nullable = false)
    private int expectedOutput;

    @Column(name = "actual_output", nullable = false)
    private int actualOutput;

    @Column(name = "good_output", nullable = false)
    private int goodOutput;

    @Column(name = "damaged_units", nullable = false)
    private int damagedUnits;

    @Column(name = "yield_variance_pct", precision = 7, scale = 2)
    private BigDecimal yieldVariancePct;

    @Column(name = "yield_flagged", nullable = false)
    private boolean yieldFlagged;

    @Column(name = "labour_cost", nullable = false, precision = 14, scale = 2)
    private BigDecimal labourCost;

    @Column(name = "utilities_cost", nullable = false, precision = 14, scale = 2)
    private BigDecimal utilitiesCost;

    @Column(name = "material_cost", nullable = false, precision = 14, scale = 2)
    private BigDecimal materialCost;

    @Column(name = "cost_per_packet", precision = 14, scale = 4)
    private BigDecimal costPerPacket;

    @Column(name = "supervisor_id", nullable = false)
    private Long supervisorId;

    @OneToMany(mappedBy = "run", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<ProductionRunMaterial> materials = new ArrayList<>();

    protected ProductionRun() {}

    public ProductionRun(Long productId, Long bomId, BigDecimal batches, LocalDate runDate, int expectedOutput,
                         int actualOutput, int goodOutput, int damagedUnits, BigDecimal yieldVariancePct,
                         boolean yieldFlagged, BigDecimal labourCost, BigDecimal utilitiesCost,
                         BigDecimal materialCost, BigDecimal costPerPacket, Long supervisorId) {
        this.productId = productId;
        this.bomId = bomId;
        this.batches = batches;
        this.runDate = runDate;
        this.expectedOutput = expectedOutput;
        this.actualOutput = actualOutput;
        this.goodOutput = goodOutput;
        this.damagedUnits = damagedUnits;
        this.yieldVariancePct = yieldVariancePct;
        this.yieldFlagged = yieldFlagged;
        this.labourCost = labourCost;
        this.utilitiesCost = utilitiesCost;
        this.materialCost = materialCost;
        this.costPerPacket = costPerPacket;
        this.supervisorId = supervisorId;
    }

    public void addMaterial(ProductionRunMaterial m) {
        m.setRun(this);
        materials.add(m);
    }

    public Long getId() { return id; }
    public Long getProductId() { return productId; }
    public Long getBomId() { return bomId; }
    public BigDecimal getBatches() { return batches; }
    public LocalDate getRunDate() { return runDate; }
    public int getExpectedOutput() { return expectedOutput; }
    public int getActualOutput() { return actualOutput; }
    public int getGoodOutput() { return goodOutput; }
    public int getDamagedUnits() { return damagedUnits; }
    public BigDecimal getYieldVariancePct() { return yieldVariancePct; }
    public boolean isYieldFlagged() { return yieldFlagged; }
    public BigDecimal getLabourCost() { return labourCost; }
    public BigDecimal getUtilitiesCost() { return utilitiesCost; }
    public BigDecimal getMaterialCost() { return materialCost; }
    public BigDecimal getCostPerPacket() { return costPerPacket; }
    public Long getSupervisorId() { return supervisorId; }
    public List<ProductionRunMaterial> getMaterials() { return materials; }
}
