package com.westy.codmanager.finance.web;

import com.westy.codmanager.finance.domain.LineStatus;
import com.westy.codmanager.finance.domain.Remittance;
import com.westy.codmanager.finance.domain.RemittanceLine;
import com.westy.codmanager.geo.domain.Carrier;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class RemittanceDtos {

    private RemittanceDtos() {
    }

    public record RemittanceLineResponse(
            String tracking,
            String orderId,
            BigDecimal collectedAmount,
            BigDecimal carrierFee,
            BigDecimal netAmount,
            BigDecimal expectedAmount,
            LineStatus status,
            String note,
            Integer sourceRow) {

        static RemittanceLineResponse from(RemittanceLine line) {
            return new RemittanceLineResponse(
                    line.getTracking(),
                    line.getOrderId() == null ? null : line.getOrderId().toString(),
                    line.getCollectedAmount(),
                    line.getCarrierFee(),
                    line.getNetAmount(),
                    line.getExpectedAmount(),
                    line.getStatus(),
                    line.getNote(),
                    line.getSourceRow());
        }
    }

    public record RemittanceResponse(
            String id,
            Carrier carrier,
            String reference,
            LocalDate receivedAt,
            BigDecimal declaredTotal,
            BigDecimal matchedTotal,
            BigDecimal unaccounted,
            int lineCount,
            int matchedCount,
            boolean fullyReconciled,
            Map<LineStatus, Long> breakdown,
            List<RemittanceLineResponse> lines) {

        public static RemittanceResponse from(Remittance remittance) {
            return new RemittanceResponse(
                    remittance.getId().toString(),
                    remittance.getCarrier(),
                    remittance.getReference(),
                    remittance.getReceivedAt(),
                    remittance.getDeclaredTotal(),
                    remittance.getMatchedTotal(),
                    remittance.unaccounted(),
                    remittance.getLineCount(),
                    remittance.getMatchedCount(),
                    remittance.isFullyReconciled(),
                    remittance.getLines().stream().collect(Collectors.groupingBy(
                            RemittanceLine::getStatus, Collectors.counting())),
                    remittance.getLines().stream().map(RemittanceLineResponse::from).toList());
        }

        /** Summary without the lines, for list views. */
        public static RemittanceResponse summary(Remittance remittance) {
            RemittanceResponse full = from(remittance);

            return new RemittanceResponse(full.id(), full.carrier(), full.reference(),
                    full.receivedAt(), full.declaredTotal(), full.matchedTotal(),
                    full.unaccounted(), full.lineCount(), full.matchedCount(),
                    full.fullyReconciled(), full.breakdown(), List.of());
        }
    }
}
