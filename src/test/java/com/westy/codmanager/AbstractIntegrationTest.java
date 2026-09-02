package com.westy.codmanager;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * One real PostgreSQL container, started once and reused by every integration
 * test. Tests run against the same engine as production, and Flyway migrations
 * are exercised on every build.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine")
                    .withDatabaseName("coddb_test")
                    .withUsername("test")
                    .withPassword("test")
                    .withReuse(true);

    static {
        POSTGRES.start();
    }

    /*
     * The container is reused across the whole suite, so each test has to start
     * from a known state. TRUNCATE ... CASCADE clears every table in one
     * statement and sidesteps the foreign keys entirely — deleting table by
     * table means getting the order right and keeping it right forever.
     *
     * Reference data seeded by Flyway (wilaya, commune, delivery_fee) is
     * deliberately left alone.
     */
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetTransactionalTables() {
        jdbcTemplate.execute("""
                TRUNCATE TABLE
                    remittance_line, remittance,
                    carrier_event, shipment, webhook_event,
                    order_status_history, order_item, orders,
                    customer, product_variant, product, users
                RESTART IDENTITY CASCADE""");
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
