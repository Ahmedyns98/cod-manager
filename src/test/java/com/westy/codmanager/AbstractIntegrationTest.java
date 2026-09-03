package com.westy.codmanager;

import com.github.tomakehurst.wiremock.WireMockServer;
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

    /*
     * One carrier stub for the whole suite, deliberately.
     *
     * Spring caches the application context and reuses it across every test
     * class with the same configuration, so a per-class WireMock server would
     * leave the base URL pinned to whichever class happened to load first while
     * the others talked to a stranger. A single shared server, reset before
     * each test, removes the ordering problem entirely.
     */
    protected static final WireMockServer CARRIER =
            new WireMockServer(com.github.tomakehurst.wiremock.core.WireMockConfiguration
                    .options()
                    .dynamicPort()
                    /*
                     * HTTP/2 off. The JDK client negotiates it when offered,
                     * and the stub's implementation then cancels the stream
                     * mid-request — every POST comes back as RST_STREAM and
                     * looks like the carrier is unreachable. Nothing here needs
                     * HTTP/2; the real API is plain HTTP/1.1 anyway.
                     */
                    .http2PlainDisabled(true)
                    .http2TlsDisabled(true));

    static {
        POSTGRES.start();
        CARRIER.start();
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
        CARRIER.resetAll();

        jdbcTemplate.execute("""
                TRUNCATE TABLE
                    remittance_line, remittance,
                    carrier_event, shipment, webhook_event,
                    order_status_history, order_item, orders,
                    customer, product_variant, product, users
                RESTART IDENTITY CASCADE""");
    }

    @DynamicPropertySource
    static void testProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);

        /*
         * 127.0.0.1, not localhost. WireMock binds to IPv4, while localhost
         * resolves to ::1 first on most CI runners — so the client connects to
         * an address nothing is listening on and reports the carrier as
         * unreachable.
         */
        registry.add("app.carriers.yalidine.base-url",
                () -> "http://127.0.0.1:" + CARRIER.port());
        registry.add("app.carriers.yalidine.api-id", () -> "test-id");
        registry.add("app.carriers.yalidine.api-token", () -> "test-token");

        /* Push the poller past the life of the run so it never interferes. */
        registry.add("app.sync.interval", () -> "PT24H");
    }
}
