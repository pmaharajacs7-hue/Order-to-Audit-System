package com.orderaudit.sales;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "invoices")
public class Invoice {

    public static final String ISSUED = "ISSUED";
    public static final String DISPATCHED = "DISPATCHED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "order_id", unique = true)
    private SalesOrder order;

    @Column(name = "invoice_no", nullable = false, unique = true, length = 30)
    private String invoiceNo;

    @Column(name = "invoice_date", nullable = false)
    private LocalDate invoiceDate;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal subtotal;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal cgst;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal sgst;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal igst;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal total;

    @Column(nullable = false, length = 20)
    private String status = ISSUED;

    @Column(name = "dispatched_at")
    private LocalDateTime dispatchedAt;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<InvoiceItem> items = new ArrayList<>();

    protected Invoice() {}

    public Invoice(SalesOrder order, String invoiceNo, LocalDate invoiceDate, Long createdBy) {
        this.order = order;
        this.invoiceNo = invoiceNo;
        this.invoiceDate = invoiceDate;
        this.createdBy = createdBy;
        this.subtotal = BigDecimal.ZERO;
        this.cgst = BigDecimal.ZERO;
        this.sgst = BigDecimal.ZERO;
        this.igst = BigDecimal.ZERO;
        this.total = BigDecimal.ZERO;
    }

    /** Adds a line and keeps the invoice totals equal to the sum of its lines. */
    public void addItem(InvoiceItem item) {
        item.setInvoice(this);
        items.add(item);
        subtotal = subtotal.add(item.getTaxableValue());
        cgst = cgst.add(item.getCgst());
        sgst = sgst.add(item.getSgst());
        igst = igst.add(item.getIgst());
        total = subtotal.add(cgst).add(sgst).add(igst);
    }

    public void setInvoiceNo(String invoiceNo) { this.invoiceNo = invoiceNo; }

    public void markDispatched() {
        this.status = DISPATCHED;
        this.dispatchedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public SalesOrder getOrder() { return order; }
    public String getInvoiceNo() { return invoiceNo; }
    public LocalDate getInvoiceDate() { return invoiceDate; }
    public BigDecimal getSubtotal() { return subtotal; }
    public BigDecimal getCgst() { return cgst; }
    public BigDecimal getSgst() { return sgst; }
    public BigDecimal getIgst() { return igst; }
    public BigDecimal getTotal() { return total; }
    public String getStatus() { return status; }
    public LocalDateTime getDispatchedAt() { return dispatchedAt; }
    public Long getCreatedBy() { return createdBy; }
    public List<InvoiceItem> getItems() { return items; }
}
