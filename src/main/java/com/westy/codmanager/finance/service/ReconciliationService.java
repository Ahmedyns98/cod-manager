package com.westy.codmanager.finance.service;

import com.westy.codmanager.common.exception.BusinessRuleException;
import com.westy.codmanager.common.exception.NotFoundException;
import com.westy.codmanager.finance.domain.LineStatus;
import com.westy.codmanager.finance.domain.Remittance;
import com.westy.codmanager.finance.domain.RemittanceLine;
import com.westy.codmanager.finance.repository.RemittanceLineRepository;
import com.westy.codmanager.finance.repository.RemittanceRepository;
import com.westy.codmanager.geo.domain.Carrier;
import com.westy.codmanager.order.domain.Order;
import com.westy.codmanager.order.domain.OrderStatus;
import com.westy.codmanager.order.service.OrderService;
import com.westy.codmanager.shipping.domain.Shipment;
import com.westy.codmanager.shipping.repository.ShipmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Matches carrier payouts against delivered orders.
 *
 * This is the feature the system exists for. A delivered order is not money:
 * the courier holds the cash for a week or more, then settles it in one lump
 * transfer with a list of parcels attached. Sellers reconcile that list by hand
 * in a spreadsheet, and that is where the errors live.
 *
 * Every row is recorded with an outcome, including the ones that do not match.
 * A short payment or an unknown tracking number is a question for the seller,
 * never something accepted quietly — making that gap visible is the point.
 */
@Service
public class ReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);

    private final RemittanceRepository remittances;
    private final RemittanceLineRepository lines;
    private final ShipmentRepository shipments;
    private final OrderService orders;
    private final RemittanceCsvParser parser;

    public ReconciliationService(RemittanceRepository remittances, RemittanceLineRepository lines,
                                 ShipmentRepository shipments, OrderService orders,
                                 RemittanceCsvParser parser) {
        this.remittances = remittances;
        this.lines = lines;
        this.shipments = shipments;
        this.orders = orders;
        this.parser = parser;
    }

    /**
     * Imports a payout export and reconciles it against delivered orders.
     *
     * Every row is recorded whatever happens to it. A row that cannot be
     * matched is a question for the seller, not something to drop: the whole
     * point of this feature is making the gap between the carrier's figure and
     * the shop's own visible.
     */
    @Transactional
    public Remittance importPayout(UUID ownerId, Carrier carrier, String reference,
                                   BigDecimal declaredTotal, LocalDate receivedAt,
                                   MultipartFile file) {

        if (remittances.existsByOwnerIdAndCarrierAndReference(ownerId, carrier, reference)) {
            throw new BusinessRuleException("REMITTANCE_EXISTS",
                    "Payout " + reference + " has already been imported");
        }

        RemittanceCsvParser.Result parsed = read(file);

        Remittance remittance = remittances.save(new Remittance(ownerId, carrier, reference,
                declaredTotal, receivedAt, file.getOriginalFilename()));

        for (RemittanceCsvParser.Row row : parsed.rows()) {
            remittance.addLine(reconcile(ownerId, remittance, row));
        }

        parsed.errors().forEach(error ->
                log.warn("Payout {} row {} skipped: {}", reference, error.number(), error.message()));

        return remittance;
    }

    private RemittanceLine reconcile(UUID ownerId, Remittance remittance,
                                     RemittanceCsvParser.Row row) {

        Optional<Shipment> shipment = shipments.findByTrackingNumber(row.tracking());

        if (shipment.isEmpty() || !shipment.get().getOrder().getOwnerId().equals(ownerId)) {
            return line(remittance, null, row, null, LineStatus.UNKNOWN_TRACKING,
                    "No order in this account carries this tracking number");
        }

        Order order = shipment.get().getOrder();

        if (lines.existsByOrderIdAndStatus(order.getId(), LineStatus.SETTLED)) {
            return line(remittance, order.getId(), row, order.getTotal(),
                    LineStatus.ALREADY_SETTLED, "Settled by an earlier payout");
        }

        if (order.getStatus() != OrderStatus.DELIVERED) {
            return line(remittance, order.getId(), row, order.getTotal(),
                    LineStatus.NOT_DELIVERED,
                    "Order is " + order.getStatus() + ", the carrier says it was paid");
        }

        /*
         * The comparison is against what the customer owed, not the net. The
         * carrier's fee is its own line item and may legitimately differ from
         * the tariff we priced with.
         */
        if (order.getTotal().compareTo(row.collected()) != 0) {
            return line(remittance, order.getId(), row, order.getTotal(),
                    LineStatus.AMOUNT_MISMATCH,
                    "Expected %s, carrier collected %s".formatted(order.getTotal(), row.collected()));
        }

        orders.transition(ownerId, order.getId(), OrderStatus.SETTLED,
                "Paid in remittance " + remittance.getReference());

        return line(remittance, order.getId(), row, order.getTotal(), LineStatus.SETTLED, null);
    }

    private RemittanceLine line(Remittance remittance, UUID orderId,
                                RemittanceCsvParser.Row row, BigDecimal expected,
                                LineStatus status, String note) {
        return new RemittanceLine(remittance, orderId, row.tracking(), row.collected(),
                row.fee(), expected, status, note, row.number());
    }

    private RemittanceCsvParser.Result read(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessRuleException("EMPTY_FILE", "No file was uploaded");
        }

        try {
            return parser.parse(file.getInputStream(), file.getOriginalFilename());
        } catch (IOException ex) {
            throw new BusinessRuleException("UNREADABLE_FILE", "Could not read the uploaded file");
        }
    }

    @Transactional(readOnly = true)
    public Remittance get(UUID ownerId, UUID remittanceId) {
        return remittances.findByIdAndOwnerId(remittanceId, ownerId)
                .orElseThrow(() -> new NotFoundException("Remittance", remittanceId));
    }

    @Transactional(readOnly = true)
    public Page<Remittance> list(UUID ownerId, Pageable pageable) {
        return remittances.findByOwnerIdOrderByReceivedAtDesc(ownerId, pageable);
    }

    /** Delivered orders the carrier has not paid for yet: money still out there. */
    @Transactional(readOnly = true)
    public Page<Order> pendingPayout(UUID ownerId, Pageable pageable) {
        return orders.list(ownerId, OrderStatus.DELIVERED, pageable);
    }
}
