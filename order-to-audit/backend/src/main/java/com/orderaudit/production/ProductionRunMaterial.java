package com.orderaudit.production;

import com.orderaudit.inventory.RawMaterial;
import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "production_run_materials")
public class ProductionRunMaterial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "run_id")
    private ProductionRun run;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "material_id")
    private RawMaterial material;

    @Column(name = "standard_qty", nullable = false, precision = 14, scale = 3)
    private BigDecimal standardQty;

    @Column(name = "actual_qty", nullable = false, precision = 14, scale = 3)
    private BigDecimal actualQty;

    @Column(name = "variance_pct", nullable = false, precision = 7, scale = 2)
    private BigDecimal variancePct;

    @Column(nullable = false)
    private boolean flagged;

    @Column(name = "unit_cost", nullable = false, precision = 14, scale = 4)
    private BigDecimal unitCost;

    protected ProductionRunMaterial() {}

    public ProductionRunMaterial(RawMaterial material, BigDecimal standardQty, BigDecimal actualQty,
                                 BigDecimal variancePct, boolean flagged, BigDecimal unitCost) {
        this.material = material;
        this.standardQty = standardQty;
        this.actualQty = actualQty;
        this.variancePct = variancePct;
        this.flagged = flagged;
        this.unitCost = unitCost;
    }

    void setRun(ProductionRun run) { this.run = run; }

    public Long getId() { return id; }
    public RawMaterial getMaterial() { return material; }
    public BigDecimal getStandardQty() { return standardQty; }
    public BigDecimal getActualQty() { return actualQty; }
    public BigDecimal getVariancePct() { return variancePct; }
    public boolean isFlagged() { return flagged; }
    public BigDecimal getUnitCost() { return unitCost; }
}
