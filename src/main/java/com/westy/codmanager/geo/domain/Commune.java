package com.westy.codmanager.geo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "commune")
public class Commune {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "wilaya_code", nullable = false)
    private Wilaya wilaya;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "has_stopdesk", nullable = false)
    private boolean hasStopdesk;

    protected Commune() {
    }

    public Long getId() {
        return id;
    }

    public Wilaya getWilaya() {
        return wilaya;
    }

    public String getName() {
        return name;
    }

    public boolean hasStopdesk() {
        return hasStopdesk;
    }
}
