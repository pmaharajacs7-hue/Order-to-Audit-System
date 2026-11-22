package com.orderaudit.payment;

import com.orderaudit.customer.Customer;
import com.orderaudit.sales.Invoice;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "invoice_id")
    private Invoice invoice;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "payment_date", nullable = false)
    private LocalDate paymentDate;

    @Column(nullable = false, length = 20)
    private String mode;

    @Column(length = 60)
    private String reference;

    @Column(name = "recorded_by", nullable = false)
    private Long recordedBy;

    protected Payment() {}

    public Payment(Invoice invoice, Customer customer, BigDecimal amount, LocalDate paymentDate,
                   String mode, String reference, Long recordedBy) {
        this.invoice = invoice;
        this.customer = customer;
        this.amount = amount;
        this.paymentDate = paymentDate;
        this.mode = mode;
        this.reference = reference;
        this.recordedBy = recordedBy;
    }

    public Long getId() { return id; }
    public Invoice getInvoice() { return invoice; }
    public Customer getCustomer() { return customer; }
    public BigDecimal getAmount() { return amount; }
    public LocalDate getPaymentDate() { return paymentDate; }
    public String getMode() { return mode; }
    public String getReference() { return reference; }
    public Long getRecordedBy() { return recordedBy; }
}
