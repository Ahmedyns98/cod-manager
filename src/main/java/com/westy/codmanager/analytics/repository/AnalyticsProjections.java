package com.westy.codmanager.analytics.repository;

import java.math.BigDecimal;

/**
 * Interface projections for the reporting queries.
 *
 * Reporting reads whole tables and returns a handful of numbers. Loading
 * entities for that would pull every column and every association into memory
 * to throw almost all of it away, so these queries select exactly what they
 * need and Spring Data maps it straight onto these interfaces.
 */
public final class AnalyticsProjections {

    private AnalyticsProjections() {
    }

    public interface StatusCount {
        String getStatus();

        long getCount();

        BigDecimal getTotal();
    }

    public interface WilayaPerformance {
        Short getWilayaCode();

        String getWilayaName();

        long getDelivered();

        long getReturned();

        BigDecimal getRevenue();
    }

    public interface SourcePerformance {
        String getSource();

        long getTotal();

        long getConfirmed();

        long getDelivered();

        long getReturned();

        BigDecimal getRevenue();
    }

    public interface ProductPerformance {
        String getProductName();

        long getUnitsSold();

        BigDecimal getRevenue();
    }

    public interface DailyVolume {
        String getDay();

        long getOrders();

        BigDecimal getRevenue();
    }
}
