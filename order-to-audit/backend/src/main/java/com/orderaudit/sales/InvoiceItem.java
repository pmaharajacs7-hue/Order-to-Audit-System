package com.orderaudit.sales;

import com.orderaudit.product.Product;
import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "invoice_items")
public class InvoiceItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id")
    private Invoice invoice;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "product_id")
    private Product product;

    /** HSN code and GST rate are copied onto the invoice so it never changes if the product is edited later. */
    @Column(name = "hsn_code", nullable = false, length = 8)
    private String hsnCode;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_price", nullable = false, precision = 14, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "gst_rate_pct", nullable = false, precision = 5, scale = 2)
    private BigDecimal gstRatePct;

    @Column(name = "taxable_value", nullable = false, precision = 14, scale = 2)
    private BigDecimal taxableValue;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal cgst;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal sgst;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal igst;

    protected InvoiceItem() {}

    public InvoiceItem(Product product, String hsnCode, int quantity, BigDecimal unitPrice, BigDecimal gstRatePct,
                       BigDecimal taxableValue, BigDecimal cgst, BigDecimal sgst, BigDecimal igst) {
        this.product = product;
        this.hsnCode = hsnCode;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.gstRatePct = gstRatePct;
        this.taxableValue = taxableValue;
        this.cgst = cgst;
        this.sgst = sgst;
        this.igst = igst;
    }

    void setInvoice(Invoice invoice) { this.invoice = invoice; }

    public Long getId() { return id; }
    public Product getProduct() { return product; }
    public String getHsnCode() { return hsnCode; }
    public int getQuantity() { return quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public BigDecimal getGstRatePct() { return gstRatePct; }
    public BigDecimal getTaxableValue() { return taxableValue; }
    public BigDecimal getCgst() { return cgst; }
    public BigDecimal getSgst() { return sgst; }
    public BigDecimal getIgst() { return igst; }
}
