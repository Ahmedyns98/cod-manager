package com.westy.codmanager.customer.domain;

import com.westy.codmanager.common.entity.BaseEntity;
import com.westy.codmanager.geo.domain.Wilaya;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * A customer is identified by phone number, because that is the only thing a
 * COD buyer reliably gives you. The delivered and returned counters are what
 * make the return rate visible before you ship to someone a second time.
 */
@Entity
@Table(name = "customer")
public class Customer extends BaseEntity {

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "full_name", nullable = false, length = 160)
    private String fullName;

    @Column(name = "phone", nullable = false, length = 24)
    private String phone;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "wilaya_code", nullable = false)
    private Wilaya wilaya;

    @Column(name = "commune", nullable = false, length = 120)
    private String commune;

    @Column(name = "address", length = 400)
    private String address;

    @Column(name = "delivered_count", nullable = false)
    private int deliveredCount;

    @Column(name = "returned_count", nullable = false)
    private int returnedCount;

    @Column(name = "blacklisted", nullable = false)
    private boolean blacklisted;

    protected Customer() {
    }

    public Customer(UUID ownerId, String fullName, String phone,
                    Wilaya wilaya, String commune, String address) {
        this.ownerId = ownerId;
        this.fullName = fullName;
        this.phone = phone;
        this.wilaya = wilaya;
        this.commune = commune;
        this.address = address;
    }

    public void recordDelivery() {
        this.deliveredCount++;
    }

    public void recordReturn() {
        this.returnedCount++;
    }

    /**
     * Share of past orders that came back. Meaningless below three orders, so
     * it returns zero there rather than a scary 100% after one bad delivery.
     */
    public double returnRate() {
        int total = deliveredCount + returnedCount;
        return total < 3 ? 0.0 : (double) returnedCount / total;
    }

    public void blacklist() {
        this.blacklisted = true;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public String getFullName() {
        return fullName;
    }

    public String getPhone() {
        return phone;
    }

    public Wilaya getWilaya() {
        return wilaya;
    }

    public String getCommune() {
        return commune;
    }

    public String getAddress() {
        return address;
    }

    public int getDeliveredCount() {
        return deliveredCount;
    }

    public int getReturnedCount() {
        return returnedCount;
    }

    public boolean isBlacklisted() {
        return blacklisted;
    }
}
