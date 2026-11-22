package com.orderaudit.supplier;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "suppliers")
public class Supplier {

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

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    protected Supplier() {}

    public Supplier(String name, String gstin, String phone, String address) {
        update(name, gstin, phone, address);
    }

    public void update(String name, String gstin, String phone, String address) {
        this.name = name;
        this.gstin = gstin;
        this.phone = phone;
        this.address = address;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getGstin() { return gstin; }
    public String getPhone() { return phone; }
    public String getAddress() { return address; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
