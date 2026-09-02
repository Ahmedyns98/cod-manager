package com.westy.codmanager.analytics.service;

import com.westy.codmanager.analytics.repository.AnalyticsProjections.StatusCount;
import com.westy.codmanager.analytics.repository.AnalyticsRepository;
import com.westy.codmanager.analytics.web.AnalyticsDtos.BreakdownResponse;
import com.westy.codmanager.analytics.web.AnalyticsDtos.DailyRow;
import com.westy.codmanager.analytics.web.AnalyticsDtos.OverviewResponse;
import com.westy.codmanager.analytics.web.AnalyticsDtos.ProductRow;
import com.westy.codmanager.analytics.web.AnalyticsDtos.SourceRow;
import com.westy.codmanager.analytics.web.AnalyticsDtos.WilayaRow;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The numbers a COD seller actually asks for.
 *
 * Return rate is measured against orders that shipped, not against every order
 * created. Dividing by the total would let a pile of unanswered phone calls
 * flatter a genuinely bad return problem in the south.
 *
 * Cash outstanding with carriers is tracked separately from settled revenue,
 * because until a remittance arrives those are two different things.
 */
@Service
public class AnalyticsService {

    private static final List<String> SETTLED_STATES = List.of("DELIVERED", "SETTLED");

    private final AnalyticsRepository analytics;

    public AnalyticsService(AnalyticsRepository analytics) {
        this.analytics = analytics;
    }

    @Transactional(readOnly = true)
    public OverviewResponse overview(UUID ownerId, int days) {
        Instant from = since(days);

        List<StatusCount> counts = analytics.countByStatus(ownerId, from);

        Map<String, Long> byStatus = new LinkedHashMap<>();
        counts.forEach(row -> byStatus.put(row.getStatus(), row.getCount()));

        long total = byStatus.values().stream().mapToLong(Long::longValue).sum();
        long delivered = sum(byStatus, SETTLED_STATES);
        long returned = byStatus.getOrDefault("RETURNED", 0L);
        long cancelled = byStatus.getOrDefault("CANCELLED", 0L);
        long confirmed = total - byStatus.getOrDefault("PENDING", 0L)
                - byStatus.getOrDefault("NO_ANSWER", 0L) - cancelled;

        BigDecimal settledRevenue = counts.stream()
                .filter(row -> row.getStatus().equals("SETTLED"))
                .map(StatusCount::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal pipeline = counts.stream()
                .filter(row -> !List.of("CANCELLED", "RETURNED").contains(row.getStatus()))
                .map(StatusCount::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Double seconds = analytics.averageSecondsToConfirm(ownerId, from);

        return new OverviewResponse(
                days,
                total,
                byStatus,
                settledRevenue,
                analytics.outstandingWithCarriers(ownerId),
                pipeline,
                rate(confirmed, total),
                rate(delivered, confirmed),
                /*
                 * Return rate is measured against orders that actually shipped.
                 * Dividing by all orders would let a pile of unanswered calls
                 * flatter a genuinely bad return problem.
                 */
                rate(returned, delivered + returned),
                seconds == null || seconds == 0 ? null
                        : BigDecimal.valueOf(seconds / 3600)
                        .setScale(1, RoundingMode.HALF_UP).doubleValue());
    }

    @Transactional(readOnly = true)
    public BreakdownResponse breakdown(UUID ownerId, int days, int productLimit) {
        Instant from = since(days);

        List<WilayaRow> byWilaya = analytics.performanceByWilaya(ownerId, from).stream()
                .map(row -> new WilayaRow(row.getWilayaCode(), row.getWilayaName(),
                        row.getDelivered(), row.getReturned(),
                        rate(row.getReturned(), row.getDelivered() + row.getReturned()),
                        row.getRevenue()))
                .toList();

        List<SourceRow> bySource = analytics.performanceBySource(ownerId, from).stream()
                .map(row -> new SourceRow(row.getSource(), row.getTotal(), row.getConfirmed(),
                        row.getDelivered(), row.getReturned(),
                        rate(row.getConfirmed(), row.getTotal()),
                        rate(row.getReturned(), row.getDelivered() + row.getReturned()),
                        row.getRevenue()))
                .toList();

        List<ProductRow> products = analytics.topProducts(ownerId, from, productLimit).stream()
                .map(row -> new ProductRow(row.getProductName(), row.getUnitsSold(), row.getRevenue()))
                .toList();

        List<DailyRow> daily = analytics.dailyVolume(ownerId, from).stream()
                .map(row -> new DailyRow(row.getDay(), row.getOrders(), row.getRevenue()))
                .toList();

        return new BreakdownResponse(days, byWilaya, bySource, products, daily);
    }

    private Instant since(int days) {
        return Instant.now().minus(days, ChronoUnit.DAYS);
    }

    private long sum(Map<String, Long> counts, List<String> statuses) {
        return statuses.stream().mapToLong(status -> counts.getOrDefault(status, 0L)).sum();
    }

    /** Returns 0 rather than NaN when there is nothing to divide by yet. */
    private double rate(long numerator, long denominator) {
        if (denominator <= 0) {
            return 0.0;
        }

        return BigDecimal.valueOf(numerator)
                .divide(BigDecimal.valueOf(denominator), 4, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
