package com.orderaudit.sales;

import com.orderaudit.customer.Customer;
import com.orderaudit.user.User;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
public class SalesOrder {

    public static final String SUBMITTED = "SUBMITTED";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";
    public static final String INVOICED = "INVOICED";
    public static final String DISPATCHED = "DISPATCHED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "salesman_id")
    private User salesman;

    @Column(name = "order_date", nullable = false)
    private LocalDate orderDate;

    @Column(nullable = false, length = 20)
    private String status = SUBMITTED;

    @Column(name = "approved_by")
    private Long approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(length = 255)
    private String notes;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<SalesOrderItem> items = new ArrayList<>();

    protected SalesOrder() {}

    public SalesOrder(Customer customer, User salesman, LocalDate orderDate, String notes) {
        this.customer = customer;
        this.salesman = salesman;
        this.orderDate = orderDate;
        this.notes = notes;
    }

    public void addItem(SalesOrderItem item) {
        item.setOrder(this);
        items.add(item);
    }

    public void replaceItems(List<SalesOrderItem> replacement) {
        items.clear();
        replacement.forEach(this::addItem);
    }

    public void approve(Long userId) {
        this.status = APPROVED;
        this.approvedBy = userId;
        this.approvedAt = LocalDateTime.now();
    }

    public void reject(Long userId, String reason) {
        this.status = REJECTED;
        this.approvedBy = userId;
        this.approvedAt = LocalDateTime.now();
        if (reason != null && !reason.isBlank()) this.notes = reason;
    }

    public void setStatus(String status) { this.status = status; }

    public Long getId() { return id; }
    public Customer getCustomer() { return customer; }
    public User getSalesman() { return salesman; }
    public LocalDate getOrderDate() { return orderDate; }
    public String getStatus() { return status; }
    public Long getApprovedBy() { return approvedBy; }
    public LocalDateTime getApprovedAt() { return approvedAt; }
    public String getNotes() { return notes; }
    public List<SalesOrderItem> getItems() { return items; }
}
