# COD Manager

Cash-on-delivery order management API for Algerian sellers.

Sellers who take orders through Instagram and TikTok track them in spreadsheets,
retype every address into a courier dashboard, and have no reliable way to
answer the question that actually matters: **which delivered orders have I not
been paid for yet?**

This API models the full lifecycle as one system — order, carrier handover,
status sync, and payout reconciliation — so that question has an answer.

![CI](https://github.com/USERNAME/cod-manager/actions/workflows/ci.yml/badge.svg)

## Stack

Java 21 · Spring Boot 3.5 · Spring Security (JWT) · Spring Data JPA ·
PostgreSQL 17 · Flyway · Spring Retry · Testcontainers · WireMock ·
Docker Compose · GitHub Actions

## Running it

```bash
docker compose up -d db     # PostgreSQL
mvn spring-boot:run         # API on :8080
```

| | |
|---|---|
| Swagger UI | http://localhost:8080/swagger-ui.html |
| Health | http://localhost:8080/actuator/health |

Register through `/api/v1/auth/register`, paste the returned token into
**Authorize**, and the protected endpoints become available.

```bash
mvn test      # unit tests, no infrastructure
mvn verify    # adds integration tests on a real PostgreSQL container
```

## What it does

**Orders** move through an eleven-state lifecycle guarded by a transition
table. An order cannot jump from `PENDING` to `DELIVERED`; a shipped parcel can
no longer be cancelled. Every change is appended to an audit trail with its
reason and author.

**Stock** is committed at `CONFIRMED`, not at creation. A large share of COD
orders are never confirmed because the customer does not pick up, and reserving
earlier would show items as sold out while they sit on the shelf. Cancelling or
returning releases the stock automatically.

**Order lines snapshot** the product name and price at the time of sale.
Changing a price six months later does not rewrite what a customer agreed to
pay.

**Carriers** sit behind a `CarrierClient` interface with a Yalidine
implementation. Failures are classified as transient or permanent, and only
transient ones are retried — three attempts with exponential backoff.
Idempotency is enforced twice: the order number travels as the carrier's
reference, and a unique constraint on `order_id` prevents a second parcel.

**Status updates** arrive by signed webhook and are backed up by a poller for
the ones that never come. Payloads are stored before they are interpreted,
deduplicated by content hash, and applied only when the transition is legal.
An unmapped status is recorded and ignored: the carrier informs the system, it
does not command it.

**Payouts** are imported as the carrier's CSV export. Every row is reconciled
and recorded with an outcome — settled, amount mismatch, unknown tracking, not
delivered, already settled. A row that does not add up is a question for the
seller, never something silently accepted.

**Analytics** answer the questions a seller actually asks: which wilayas return
the most, whether Instagram or TikTok converts better, how much cash the carrier
is still holding.

## Design decisions

- **Flyway owns the schema.** Hibernate runs with `ddl-auto: validate` and
  refuses to start if a mapping drifts from the migrations.
- **Money is `BigDecimal`, scale 2.** Never a floating point type.
- **Time is stored as UTC `Instant`**, converted to `Africa/Algiers` only for
  display.
- **Packages are organised by feature**, not by layer: `order/`, `shipping/`,
  `finance/` each own their domain, service, repository and web code.
- **Errors are RFC 9457 `ProblemDetail`.** One shape for every failure.
- **Reference data lives in migrations**, not in Java. The 58 wilayas and their
  delivery tariffs are `V2`.
- **The state machine is a plain transition table**, not a framework. It fits on
  one screen, reads like the rules it encodes, and is tested exhaustively
  without Spring.

## Tests

Unit tests cover pure logic with no infrastructure: the transition table, the
CSV parser, the webhook signature verifier.

Integration tests run against a real PostgreSQL container via Testcontainers, so
every build also proves the migrations apply and the mappings match the schema.
Carrier calls are served by WireMock, which is what makes the interesting cases
testable at all — server errors, retry counts, duplicate parcels, malformed
payloads and statuses nobody has mapped yet.

## Roadmap

Done: project setup · authentication · reference data and catalog · order
lifecycle · carrier integration · webhooks and sync · payout reconciliation ·
analytics.

Next: full commune import, ZR Express and Noest implementations, per-seller
carrier credentials, PDF label merging for bulk printing.

## License

MIT
