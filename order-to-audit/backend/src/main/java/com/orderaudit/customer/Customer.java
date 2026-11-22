package com.orderaudit.customer;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "customers")
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 15)
    private String gstin;

    @Column(length = 20)
    private String phone;

    @Column(length = 255)
    private String address;

    /** GST state code, e.g. "33" = Tamil Nadu. Decides CGST+SGST vs IGST on invoices. */
    @Column(name = "state_code", nullable = false, length = 2)
    private String stateCode = "33";

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    protected Customer() {}

    public Customer(String name, String gstin, String phone, String address, String stateCode) {
        update(name, gstin, phone, address, stateCode);
    }

    public void update(String name, String gstin, String phone, String address, String stateCode) {
        this.name = name;
        this.gstin = gstin;
        this.phone = phone;
        this.address = address;
        this.stateCode = stateCode;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getGstin() { return gstin; }
    public String getPhone() { return phone; }
    public String getAddress() { return address; }
    public String getStateCode() { return stateCode; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
