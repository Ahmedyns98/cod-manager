package com.westy.codmanager.auth.domain;

/**
 * OWNER is the seller who owns the store. STAFF can process orders but cannot
 * touch finance or delete data. Persisted as a string so the database stays
 * readable and reordering the enum never corrupts existing rows.
 */
public enum Role {
    OWNER,
    STAFF
}
