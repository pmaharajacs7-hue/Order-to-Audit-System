package com.orderaudit.bom;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "bom")
public class Bom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    /** Weight of finished snack one batch yields. Any pack size can share the recipe through this. */
    @Column(name = "batch_output_kg", nullable = false, precision = 14, scale = 3)
    private BigDecimal batchOutputKg;

    @Column(nullable = false)
    private boolean active = true;

    @OneToMany(mappedBy = "bom", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<BomItem> items = new ArrayList<>();

    protected Bom() {}

    public Bom(String name, BigDecimal batchOutputKg) {
        this.name = name;
        this.batchOutputKg = batchOutputKg;
    }

    public void update(String name, BigDecimal batchOutputKg) {
        this.name = name;
        this.batchOutputKg = batchOutputKg;
    }

    public void addItem(BomItem item) {
        item.setBom(this);
        items.add(item);
    }

    public void clearItems() {
        items.clear();
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public BigDecimal getBatchOutputKg() { return batchOutputKg; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public List<BomItem> getItems() { return items; }
}
