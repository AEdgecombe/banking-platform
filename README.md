# Banking Platform

A small banking backend built as three tiers: a Java CLI, a Spring Boot API, and PostgreSQL with Redis. It grows over ten weeks into microservices on AWS, deployed with Terraform and TeamCity and monitored in Grafana. The full plan is in [docs/roadmap.md](docs/roadmap.md).

**Current state:** weeks 1 to 4 are scaffolded. Deposit, withdraw and transfer are not implemented yet. Their spec is the `MoneyMovement` tests in [`AccountServiceTest`](api/src/test/java/com/example/service/AccountServiceTest.java), which stay `@Disabled` until the rules are written. Until then those endpoints return `501 NOT_IMPLEMENTED`.

## Architecture

Each tier only talks to the tier next to it. The CLI never touches the database.

![Current architecture: the Java CLI calls the Spring Boot API over HTTP/JSON; the API uses PostgreSQL over JDBC and Redis as a read-through cache; Prometheus scrapes /actuator/prometheus](docs/images/architecture-now.png)

Where it is heading (weeks 7 to 9):

![Target AWS architecture: the CLI reaches an Application Load Balancer in public subnets over HTTPS; four Spring Boot services on ECS and Prometheus with Grafana run in private app subnets; RDS PostgreSQL and ElastiCache Redis sit in private data subnets; Grafana reads RDS, Redis and ALB metrics from CloudWatch](docs/images/architecture-aws.png)

Diagram sources (Mermaid) are in [docs/diagrams](docs/diagrams).

Grafana never connects to RDS or Redis directly. It reads their health from CloudWatch, so the data tier only accepts traffic from the services.

## Run it locally

Needs JDK 21, Maven 3.9 and Docker.

```bash
docker compose up -d                    # PostgreSQL and Redis
mvn -pl api spring-boot:run             # API on :8080, Flyway migrates on start

mvn -pl cli package
java -jar cli/target/banking-cli.jar    # set BANKING_API_URL to point elsewhere
```

- OpenAPI UI: http://localhost:8080/swagger-ui.html
- Metrics: http://localhost:8080/actuator/prometheus
- Prometheus (optional): `docker compose --profile monitoring up -d`, then http://localhost:9090

## Tests

| Command | Runs | Needs |
|---|---|---|
| `mvn test` | Service unit tests, controller slice tests, CLI client tests | Nothing |
| `mvn verify` | All of the above plus `*IT` tests against real PostgreSQL and Redis | Docker (Testcontainers) |

## API

| Method | Path | Body |
|---|---|---|
| `POST` | `/api/accounts` | `{"ownerName": "Ada"}` |
| `GET` | `/api/accounts` | |
| `GET` | `/api/accounts/{id}` | |
| `POST` | `/api/accounts/{id}/deposits` | `{"amount": "25.00"}` |
| `POST` | `/api/accounts/{id}/withdrawals` | `{"amount": "10.00"}` |
| `POST` | `/api/transfers` | `{"fromAccountId": "...", "toAccountId": "...", "amount": "5.00"}` |
| `GET` | `/api/accounts/{id}/transactions` | |

Every error has the same shape, and clients branch on `code`, not on the message:

```json
{ "code": "INSUFFICIENT_FUNDS", "message": "Account ... has insufficient funds", "field": "amount" }
```

| Code | Status |
|---|---|
| `ACCOUNT_NOT_FOUND` | 404 |
| `INVALID_AMOUNT`, `INVALID_REQUEST`, `VALIDATION_FAILED`, `BAD_REQUEST` | 400 |
| `INSUFFICIENT_FUNDS` | 422 |
| `INTERNAL_ERROR` | 500 |

## Layout

```
api/                      Spring Boot application (application tier)
  model/                  Account, Transaction, Money. No storage logic.
  service/                AccountService: every banking rule lives here
  repository/             Interfaces, plus jdbc/ implementations for PostgreSQL
  web/                    Controller, request/response records, error handling
  config/                 Cache, CORS, clock
  resources/db/migration  Flyway SQL
cli/                      Thin client (presentation tier). No dependency on api/.
.teamcity/                Pipeline as Kotlin DSL
ci/teamcity/              Local TeamCity server and agent
monitoring/               Prometheus config. Grafana dashboards arrive in Week 9.
docs/                     Roadmap
```

## Design decisions

- **Money is `BigDecimal` at scale 2.** Amounts with more decimal places are rejected, not rounded.
- **No overdrafts, enforced twice.** The service checks first. A `CHECK (balance >= 0)` constraint catches anything that slips past.
- **Transfers are all or nothing.** Both rows are locked with `SELECT ... FOR UPDATE` inside one transaction, lowest account ID first, so two opposite transfers cannot deadlock.
- **One schema per future service.** `accounts` and `transactions` have no foreign key between them, so the Week 6 split needs no data migration.
- **Cache eviction happens after commit.** Caching is ordered outside the transaction, so a concurrent read cannot put a stale balance back into Redis.
- **CORS is off by default.** A React client later is a config change (`BANKING_CORS_ALLOWED_ORIGINS`), not a code change.

## CI

```bash
docker compose -f ci/teamcity/docker-compose.yml up -d   # http://localhost:8111
```

Point a project at this repo and enable versioned settings. TeamCity then reads the pipeline from [`.teamcity/settings.kts`](.teamcity/settings.kts): `mvn clean verify` on every push, with both jars kept as build artefacts.
