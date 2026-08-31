# COD Manager

Cash-on-delivery order management API for Algerian sellers.

Sellers who take orders through Instagram and TikTok currently track them in
spreadsheets, retype every address into a courier dashboard, and have no
reliable way to answer the one question that matters: *which delivered orders
have I not been paid for yet?* This API models the full lifecycle — order,
shipment, courier sync, and remittance reconciliation — as one system.

## Status

Phase 1 of 8: project skeleton, database, migrations, error handling, CI.

## Stack

Java 21, Spring Boot 4.1, Spring Data JPA, PostgreSQL 17, Flyway, MapStruct,
Testcontainers, Docker Compose, GitHub Actions.

## Running locally

Requirements: JDK 21, Docker, Maven (or the bundled wrapper).

```bash
# database only, app runs from your IDE
docker compose up -d db
mvn spring-boot:run

# or everything in containers
docker compose up --build
```

| What | Where |
|---|---|
| API | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| Health | http://localhost:8080/actuator/health |

## Tests

```bash
mvn test      # unit tests
mvn verify    # unit + integration tests (spins up PostgreSQL via Testcontainers)
```

Integration tests run against a real PostgreSQL container, so every build also
verifies that the Flyway migrations apply cleanly and that the Hibernate
mapping matches the actual schema.

## Design notes

- **Flyway owns the schema.** Hibernate is set to `ddl-auto: validate` and will
  refuse to start if a mapping drifts from the migrations.
- **Money is `BigDecimal`, scale 2.** Never a floating point type.
- **Time is stored as UTC `Instant`.** Conversion to Africa/Algiers happens in
  the presentation layer only.
- **Errors are RFC 9457 ProblemDetail.** One error shape for every failure.
- **Packages are organised by feature, not by layer.** `order/`, `shipping/`,
  `finance/` — each owning its own domain, service, repository and web code.

## Roadmap

1. Project setup, database, migrations, CI ← *current*
2. Authentication: registration, login, JWT, method security
3. Reference data (58 wilayas, communes, delivery fees) and product catalog
4. Orders: pricing, status state machine, audit history
5. Shipping: `CarrierClient` abstraction with a Yalidine implementation
6. Courier status sync and inbound webhooks
7. Remittance reconciliation
8. Analytics, hardening, documentation

## License

MIT
