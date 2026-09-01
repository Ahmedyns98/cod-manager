package com.westy.codmanager.analytics.repository;

import com.westy.codmanager.analytics.repository.AnalyticsProjections.DailyVolume;
import com.westy.codmanager.analytics.repository.AnalyticsProjections.ProductPerformance;
import com.westy.codmanager.analytics.repository.AnalyticsProjections.SourcePerformance;
import com.westy.codmanager.analytics.repository.AnalyticsProjections.StatusCount;
import com.westy.codmanager.analytics.repository.AnalyticsProjections.WilayaPerformance;
import com.westy.codmanager.order.domain.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Read-only reporting queries, written as native SQL.
 *
 * These are aggregations over the whole order table with grouping and filtered
 * counts. Expressing that in JPQL would be contorted, and the SQL here says
 * plainly what each number means.
 */
public interface AnalyticsRepository extends JpaRepository<Order, UUID> {

    @Query(value = """
            SELECT o.status         AS status,
                   COUNT(*)         AS count,
                   COALESCE(SUM(o.total), 0) AS total
            FROM orders o
            WHERE o.owner_id = :ownerId
              AND o.created_at >= :from
            GROUP BY o.status
            """, nativeQuery = true)
    List<StatusCount> countByStatus(@Param("ownerId") UUID ownerId, @Param("from") Instant from);

    @Query(value = """
            SELECT w.code                                             AS wilayaCode,
                   w.name_fr                                          AS wilayaName,
                   COUNT(*) FILTER (WHERE o.status IN ('DELIVERED', 'SETTLED')) AS delivered,
                   COUNT(*) FILTER (WHERE o.status = 'RETURNED')      AS returned,
                   COALESCE(SUM(o.total) FILTER (
                       WHERE o.status IN ('DELIVERED', 'SETTLED')), 0) AS revenue
            FROM orders o
            JOIN wilaya w ON w.code = o.wilaya_code
            WHERE o.owner_id = :ownerId
              AND o.created_at >= :from
              AND o.status IN ('DELIVERED', 'SETTLED', 'RETURNED')
            GROUP BY w.code, w.name_fr
            ORDER BY returned DESC, delivered DESC
            """, nativeQuery = true)
    List<WilayaPerformance> performanceByWilaya(@Param("ownerId") UUID ownerId,
                                                @Param("from") Instant from);

    @Query(value = """
            SELECT o.source                                           AS source,
                   COUNT(*)                                           AS total,
                   COUNT(*) FILTER (WHERE o.confirmed_at IS NOT NULL) AS confirmed,
                   COUNT(*) FILTER (WHERE o.status IN ('DELIVERED', 'SETTLED')) AS delivered,
                   COUNT(*) FILTER (WHERE o.status = 'RETURNED')      AS returned,
                   COALESCE(SUM(o.total) FILTER (
                       WHERE o.status IN ('DELIVERED', 'SETTLED')), 0) AS revenue
            FROM orders o
            WHERE o.owner_id = :ownerId
              AND o.created_at >= :from
            GROUP BY o.source
            ORDER BY total DESC
            """, nativeQuery = true)
    List<SourcePerformance> performanceBySource(@Param("ownerId") UUID ownerId,
                                                @Param("from") Instant from);

    @Query(value = """
            SELECT i.product_name                    AS productName,
                   SUM(i.quantity)                   AS unitsSold,
                   SUM(i.unit_price * i.quantity)    AS revenue
            FROM order_item i
            JOIN orders o ON o.id = i.order_id
            WHERE o.owner_id = :ownerId
              AND o.created_at >= :from
              AND o.status IN ('DELIVERED', 'SETTLED')
            GROUP BY i.product_name
            ORDER BY revenue DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<ProductPerformance> topProducts(@Param("ownerId") UUID ownerId,
                                         @Param("from") Instant from,
                                         @Param("limit") int limit);

    @Query(value = """
            SELECT TO_CHAR(o.created_at AT TIME ZONE 'Africa/Algiers', 'YYYY-MM-DD') AS day,
                   COUNT(*)                  AS orders,
                   COALESCE(SUM(o.total), 0) AS revenue
            FROM orders o
            WHERE o.owner_id = :ownerId
              AND o.created_at >= :from
            GROUP BY day
            ORDER BY day
            """, nativeQuery = true)
    List<DailyVolume> dailyVolume(@Param("ownerId") UUID ownerId, @Param("from") Instant from);

    /** Delivered but not yet paid out: the cash the carrier is still holding. */
    @Query(value = """
            SELECT COALESCE(SUM(o.total), 0)
            FROM orders o
            WHERE o.owner_id = :ownerId
              AND o.status = 'DELIVERED'
            """, nativeQuery = true)
    BigDecimal outstandingWithCarriers(@Param("ownerId") UUID ownerId);

    /** Median would be better than mean here, but mean is enough to spot drift. */
    @Query(value = """
            SELECT COALESCE(AVG(EXTRACT(EPOCH FROM (o.confirmed_at - o.created_at))), 0)
            FROM orders o
            WHERE o.owner_id = :ownerId
              AND o.confirmed_at IS NOT NULL
              AND o.created_at >= :from
            """, nativeQuery = true)
    Double averageSecondsToConfirm(@Param("ownerId") UUID ownerId, @Param("from") Instant from);
}
