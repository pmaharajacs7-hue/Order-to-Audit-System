package com.orderaudit.purchase;

import com.orderaudit.supplier.Supplier;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "purchases")
public class Purchase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    @Column(name = "invoice_no", length = 40)
    private String invoiceNo;

    @Column(name = "purchase_date", nullable = false)
    private LocalDate purchaseDate;

    @Column(name = "total_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @OneToMany(mappedBy = "purchase", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<PurchaseItem> items = new ArrayList<>();

    protected Purchase() {}

    public Purchase(Supplier supplier, String invoiceNo, LocalDate purchaseDate, Long createdBy) {
        this.supplier = supplier;
        this.invoiceNo = invoiceNo;
        this.purchaseDate = purchaseDate;
        this.createdBy = createdBy;
    }

    public void addItem(PurchaseItem item) {
        item.setPurchase(this);
        items.add(item);
        totalAmount = totalAmount.add(item.getLineTotal());
    }

    public Long getId() { return id; }
    public Supplier getSupplier() { return supplier; }
    public String getInvoiceNo() { return invoiceNo; }
    public LocalDate getPurchaseDate() { return purchaseDate; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public Long getCreatedBy() { return createdBy; }
    public List<PurchaseItem> getItems() { return items; }
}
