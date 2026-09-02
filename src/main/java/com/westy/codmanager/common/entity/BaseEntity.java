package com.westy.codmanager.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Shared identity and auditing for every persisted entity.
 * Timestamps are always stored as UTC Instants; conversion to Africa/Algiers
 * happens in the presentation layer only.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    /*
     * Assigned in Java rather than by @GeneratedValue.
     *
     * A generated id only exists once Hibernate decides to flush, so any code
     * that reads it before then — building a response, putting the entity in a
     * set — sees null. Generating it up front makes the object valid from the
     * moment it is constructed, and UUIDs need no database round trip to be
     * unique.
     */
    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id = UUID.randomUUID();

    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /*
     * Boxed on purpose. Spring Data decides whether an entity is new by looking
     * at the version when one is present, so a null version means "not yet
     * persisted" — which is what keeps save() issuing an INSERT now that the id
     * is populated before persisting.
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public UUID getId() {
        return id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getVersion() {
        return version;
    }

    /*
     * Identity is based on the persistent id only, so entities behave correctly
     * inside collections both before and after they are flushed.
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof BaseEntity that)) {
            return false;
        }
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getClass().getSimpleName());
    }
}
