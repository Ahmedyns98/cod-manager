package com.westy.codmanager.finance.domain;

import com.westy.codmanager.common.entity.BaseEntity;
import com.westy.codmanager.geo.domain.Carrier;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "remittance")
public class Remittance extends BaseEntity {

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "carrier", nullable = false, length = 32)
    private Carrier carrier;

    @Column(name = "reference", nullable = false, length = 64)
    private String reference;

    /** What the carrier says it transferred, straight off the statement. */
    @Column(name = "declared_total", nullable = false, precision = 14, scale = 2)
    private BigDecimal declaredTotal;

    /** What this system could actually account for, line by line. */
    @Column(name = "matched_total", nullable = false, precision = 14, scale = 2)
    private BigDecimal matchedTotal = BigDecimal.ZERO;

    @Column(name = "line_count", nullable = false)
    private int lineCount;

    @Column(name = "matched_count", nullable = false)
    private int matchedCount;

    @Column(name = "received_at", nullable = false)
    private LocalDate receivedAt;

    @Column(name = "source_file", length = 255)
    private String sourceFile;

    @OneToMany(mappedBy = "remittance", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RemittanceLine> lines = new ArrayList<>();

    protected Remittance() {
    }

    public Remittance(UUID ownerId, Carrier carrier, String reference,
                      BigDecimal declaredTotal, LocalDate receivedAt, String sourceFile) {
        this.ownerId = ownerId;
        this.carrier = carrier;
        this.reference = reference;
        this.declaredTotal = declaredTotal;
        this.receivedAt = receivedAt;
        this.sourceFile = sourceFile;
    }

    public RemittanceLine addLine(RemittanceLine line) {
        lines.add(line);
        recount();
        return line;
    }

    private void recount() {
        this.lineCount = lines.size();

        this.matchedCount = (int) lines.stream()
                .filter(line -> line.getStatus() == LineStatus.SETTLED)
                .count();

        this.matchedTotal = lines.stream()
                .filter(line -> line.getStatus() == LineStatus.SETTLED)
                .map(RemittanceLine::getNetAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Difference between the carrier's figure and what we could account for.
     * Anything other than zero is worth a phone call.
     */
    public BigDecimal unaccounted() {
        return declaredTotal.subtract(matchedTotal);
    }

    public boolean isFullyReconciled() {
        return unaccounted().compareTo(BigDecimal.ZERO) == 0
                && lines.stream().noneMatch(line -> line.getStatus().needsAttention());
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public Carrier getCarrier() {
        return carrier;
    }

    public String getReference() {
        return reference;
    }

    public BigDecimal getDeclaredTotal() {
        return declaredTotal;
    }

    public BigDecimal getMatchedTotal() {
        return matchedTotal;
    }

    public int getLineCount() {
        return lineCount;
    }

    public int getMatchedCount() {
        return matchedCount;
    }

    public LocalDate getReceivedAt() {
        return receivedAt;
    }

    public String getSourceFile() {
        return sourceFile;
    }

    public List<RemittanceLine> getLines() {
        return List.copyOf(lines);
    }
}
