package com.westy.codmanager.analytics.web;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public final class AnalyticsDtos {

    private AnalyticsDtos() {
    }

    public record OverviewResponse(
            int periodDays,
            long totalOrders,
            Map<String, Long> ordersByStatus,
            BigDecimal settledRevenue,
            BigDecimal outstandingWithCarriers,
            BigDecimal grossPipeline,
            double confirmationRate,
            double deliveryRate,
            double returnRate,
            Double hoursToConfirm) {
    }

    public record WilayaRow(
            Short wilayaCode,
            String wilayaName,
            long delivered,
            long returned,
            double returnRate,
            BigDecimal revenue) {
    }

    public record SourceRow(
            String source,
            long total,
            long confirmed,
            long delivered,
            long returned,
            double confirmationRate,
            double returnRate,
            BigDecimal revenue) {
    }

    public record ProductRow(String productName, long unitsSold, BigDecimal revenue) {
    }

    public record DailyRow(String day, long orders, BigDecimal revenue) {
    }

    public record BreakdownResponse(
            int periodDays,
            List<WilayaRow> byWilaya,
            List<SourceRow> bySource,
            List<ProductRow> topProducts,
            List<DailyRow> daily) {
    }
}
