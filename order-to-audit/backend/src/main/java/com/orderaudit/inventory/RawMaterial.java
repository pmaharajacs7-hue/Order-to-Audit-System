package com.orderaudit.inventory;

import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "raw_materials")
public class RawMaterial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    /** RAW (flour, oil, spices) or PACKAGING (pouches, cartons) */
    @Column(nullable = false, length = 20)
    private String category;

    @Column(nullable = false, length = 10)
    private String unit;

    @Column(name = "reorder_level", nullable = false, precision = 14, scale = 3)
    private BigDecimal reorderLevel = BigDecimal.ZERO;

    @Column(nullable = false)
    private boolean active = true;

    protected RawMaterial() {}

    public RawMaterial(String name, String category, String unit, BigDecimal reorderLevel) {
        update(name, category, unit, reorderLevel);
    }

    public void update(String name, String category, String unit, BigDecimal reorderLevel) {
        this.name = name;
        this.category = category;
        this.unit = unit;
        this.reorderLevel = reorderLevel == null ? BigDecimal.ZERO : reorderLevel;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getCategory() { return category; }
    public String getUnit() { return unit; }
    public BigDecimal getReorderLevel() { return reorderLevel; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
