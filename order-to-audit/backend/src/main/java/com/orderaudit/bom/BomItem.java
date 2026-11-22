package com.orderaudit.bom;

import com.orderaudit.inventory.RawMaterial;
import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "bom_items")
public class BomItem {

    public static final String PER_BATCH = "PER_BATCH";
    public static final String PER_PACKET = "PER_PACKET";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bom_id")
    private Bom bom;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "material_id")
    private RawMaterial material;

    @Column(nullable = false, precision = 14, scale = 4)
    private BigDecimal quantity;

    /** PER_BATCH: quantity per batch. PER_PACKET: quantity per finished packet. */
    @Column(nullable = false, length = 12)
    private String basis = PER_BATCH;

    @Column(name = "tolerance_pct", nullable = false, precision = 5, scale = 2)
    private BigDecimal tolerancePct = new BigDecimal("3.00");

    protected BomItem() {}

    public BomItem(RawMaterial material, BigDecimal quantity, String basis, BigDecimal tolerancePct) {
        this.material = material;
        this.quantity = quantity;
        if (basis != null) this.basis = basis;
        if (tolerancePct != null) this.tolerancePct = tolerancePct;
    }

    void setBom(Bom bom) { this.bom = bom; }

    public Long getId() { return id; }
    public Bom getBom() { return bom; }
    public RawMaterial getMaterial() { return material; }
    public BigDecimal getQuantity() { return quantity; }
    public String getBasis() { return basis; }
    public BigDecimal getTolerancePct() { return tolerancePct; }
}
