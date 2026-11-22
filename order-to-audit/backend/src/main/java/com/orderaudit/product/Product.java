package com.orderaudit.product;

import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, unique = true, length = 40)
    private String sku;

    @Column(name = "pack_size_g", nullable = false)
    private int packSizeG;

    @Column(name = "unit_price", nullable = false, precision = 14, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "hsn_code", nullable = false, length = 8)
    private String hsnCode;

    /** GST percentage in total (e.g. 5.00 = 2.5% CGST + 2.5% SGST). Stored per product, never hardcoded. */
    @Column(name = "gst_rate_pct", nullable = false, precision = 5, scale = 2)
    private BigDecimal gstRatePct;

    /** Recipe used to make this product. Linked in Phase 3 (BOM module). */
    @Column(name = "bom_id")
    private Long bomId;

    @Column(nullable = false)
    private boolean active = true;

    protected Product() {}

    public Product(String name, String sku, int packSizeG, BigDecimal unitPrice, String hsnCode,
                   BigDecimal gstRatePct, Long bomId) {
        update(name, sku, packSizeG, unitPrice, hsnCode, gstRatePct, bomId);
    }

    public void update(String name, String sku, int packSizeG, BigDecimal unitPrice, String hsnCode,
                       BigDecimal gstRatePct, Long bomId) {
        this.name = name;
        this.sku = sku;
        this.packSizeG = packSizeG;
        this.unitPrice = unitPrice;
        this.hsnCode = hsnCode;
        this.gstRatePct = gstRatePct;
        this.bomId = bomId;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getSku() { return sku; }
    public int getPackSizeG() { return packSizeG; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public String getHsnCode() { return hsnCode; }
    public BigDecimal getGstRatePct() { return gstRatePct; }
    public Long getBomId() { return bomId; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
