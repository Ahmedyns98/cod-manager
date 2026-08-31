package com.westy.codmanager.geo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Reference data with a meaningful natural key: the official wilaya code runs
 * 1 to 58 and is what carriers, forms and customers actually use. A surrogate
 * UUID here would only add a translation step.
 */
@Entity
@Table(name = "wilaya")
public class Wilaya {

    @Id
    @Column(name = "code")
    private Short code;

    @Column(name = "name_fr", nullable = false, length = 64)
    private String nameFr;

    @Column(name = "name_ar", nullable = false, length = 64)
    private String nameAr;

    @Column(name = "is_south", nullable = false)
    private boolean south;

    protected Wilaya() {
    }

    public Short getCode() {
        return code;
    }

    public String getNameFr() {
        return nameFr;
    }

    public String getNameAr() {
        return nameAr;
    }

    public boolean isSouth() {
        return south;
    }
}
