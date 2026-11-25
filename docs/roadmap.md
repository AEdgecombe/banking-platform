# Banking Platform: Three-Tier AWS Engineering Roadmap

### Roadmap Progress

| Tracker | Progress | Completion |
|---|---:|---:|
| Roadmap weeks | 2 / 10 | 20% |
| LeetCode problems | 95 / 50 | 190% |


## The Three Tiers

| Tier | Job | Technology | Where it lives on AWS |
|---|---|---|---|
| Presentation | What I touch as the user. Takes input, shows output, holds no business rules. | Java CLI client (thin, talks to the API over HTTPS). Later, optionally, a React single page app. | The CLI runs on my machine and reaches an Application Load Balancer in public subnets. React static files would sit in S3 behind CloudFront. |
| Application | All the banking rules: accounts, transfers, authentication, authorisation. | Spring Boot services (User, Account, Transaction, Notification), JWT | Docker containers on ECS or EC2 in private app subnets |
| Data | Stores everything and nothing else. | PostgreSQL, Redis | RDS (PostgreSQL) and ElastiCache (Redis) in private data subnets |

**A tier only talks to the tier directly next to it.** My CLI never touches the database, and the database never receives a connection from the internet.

### Supporting tools (not tiers)

These tools serve my three tiers. None of them is a fourth tier, and none of them gets to break the adjacency rule.

| Tool | Job | Where it lives |
|---|---|---|
| Terraform | Creates and changes every AWS resource from code | `infra/` folder. I run it from my laptop first, then from the TeamCity pipeline. |
| TeamCity | Builds, tests, packages and deploys | Server and agent in Docker on my machine. Pipeline config is stored in the repo. |
| Grafana (with Prometheus) | Dashboards and alerts | Containers in the private app subnets, alongside the services |

One rule I want written down now: Grafana never opens a connection to RDS or Redis. It reads database health from CloudWatch metrics through the AWS API, and it reads service metrics from the application tier. That keeps the data tier reachable only from the application security group.

```
                     Internet
                        |
         (stretch)      |
   CloudFront + S3      |
   React static site ---+
                        |
            +-----------v-----------+
 TIER 1     |  Application Load     |   public subnets (2 AZs)
 Presentn.  |  Balancer  (HTTPS)    |   CLI client connects here
            +-----------+-----------+
                        |
         +--------------v---------------+
 TIER 2  |  User   Account   Txn   Notif |   private app subnets (2 AZs)
 Applic. |  Spring Boot + JWT, Docker    |
         |  Prometheus + Grafana         |
         +--------------+---------------+
                        |
            +-----------v-----------+
 TIER 3     |  RDS PostgreSQL       |   private data subnets (2 AZs)
 Data       |  ElastiCache Redis    |   no route to the internet
            +-----------------------+

 Built and changed by:  Terraform (infra/)  <-  TeamCity pipeline
```

## Code Layout (set up in Week 2, extended as tools arrive)

Every Java package maps to a tier, so moving to a database in Week 3 or a REST API in Week 4 only swaps the outer layers.

```
banking-platform/
  pom.xml
  docker-compose.yml          <- Week 7: services, Postgres, Redis, Prometheus, Grafana
  src/main/java/com/example/
    cli/          <- Presentation tier: menus, Scanner input, printing
    service/      <- Application tier: Bank, deposit/withdraw/transfer rules
    model/        <- Account, Transaction (shared, no logic about storage)
    repository/   <- Data tier: AccountRepository, TransactionRepository
  .teamcity/      <- CI/CD pipeline as code (Kotlin DSL), from Week 3
  monitoring/     <- prometheus.yml and Grafana dashboards as JSON, from Week 4
  infra/          <- Terraform, from Week 7
    modules/      <- network, data, app, edge
    envs/dev/     <- the one environment I actually deploy
  frontend/       <- React app, stretch only
```

`repository/` starts as an interface with an in-memory implementation. In Week 3 I add a PostgreSQL implementation behind the same interface, and the `service/` code does not change. In Week 6 the Java code splits into one Maven module per service. `.teamcity/`, `monitoring/` and `infra/` stay at the root.

## Weekly Roadmap

### Week 1: Foundations

- I set up my development environment (JDK 21, Maven, VS Code) and the Git repository.
- I create an AWS account, set a billing alert, and avoid using the root user for day-to-day work.
- I install the Terraform CLI and the AWS CLI and set up a named AWS profile, so Week 7 is not also an installation week.
- I sketch the three tiers on paper and settle on the package layout above.

### Week 2: Java, Banking CLI (Presentation and Application tiers)

- I build the Banking CLI in Java.
- It lets me create accounts and make deposits, withdrawals and transfers.
- It records and displays transaction history.
- I keep the layers strict: `cli` only handles input and output, `service` holds every rule (no negative deposits, no overdrafts, a transfer either fully happens or not at all), and `repository` is an in-memory store behind an interface.
- I use `BigDecimal` for money and write JUnit tests against the `service` layer.
- I make sure the whole test suite runs with a single `mvn test` and needs nothing set up by hand. TeamCity will run exactly that command next week.

### Week 3: PostgreSQL + Redis (Data tier) and the first CI build

- I add a PostgreSQL implementation of the repository interfaces and persist accounts and transactions.
- The CLI workflows keep working unchanged.
- I add Redis caching for account lookups and session data.
- I design the schema with Week 6 in mind, grouping tables by future service (users, accounts, transactions) so they can later be split into separate schemas.
- I run a TeamCity server and one build agent in Docker on my machine. The free on-premises edition is plenty for a project this size.
- My first build configuration checks out from Git, runs `mvn clean verify` and publishes the test report. It triggers on every push.
- I write the pipeline as Kotlin DSL in `.teamcity/` so it lives in the repo and I can rebuild it from scratch.
- For tests that need PostgreSQL or Redis, I use Testcontainers so the build does not depend on a database I installed by hand.

### Week 4: Spring Boot, REST API (Application tier)

- I turn the application into a Spring Boot REST API with controller, service and repository layers.
- I implement CRUD endpoints for accounts and transactions.
- I keep the PostgreSQL persistence and Redis caching from Week 3.
- I rebuild the CLI as a thin client that calls the API over HTTP. This is what finally makes the Presentation tier a separate tier, because the CLI no longer imports any service or repository code.
- I add Spring Boot Actuator and the Micrometer Prometheus registry so `/actuator/prometheus` exposes metrics, plus a few business counters of my own, for example `transfers_completed_total`, `transfers_failed_total` and `overdraft_rejections_total`.
- I have the TeamCity build archive the runnable jar as a build artefact.
- I make three small decisions now so a React frontend stays cheap later:
  - I publish an OpenAPI spec with springdoc-openapi, so client types can be generated instead of written by hand.
  - I make the allowed CORS origins a configuration property, empty by default. Adding React then becomes a config change, not a code change.
  - I return every error as consistent JSON (a code, a message and the offending field), never a stack trace or a bare string. The CLI benefits from this too.

### Week 5: Security, Authentication and Authorisation (Application tier)

- I add user registration and login.
- I implement JWT authentication and role-based authorisation (for example CUSTOMER and ADMIN).
- I protect the existing API endpoints rather than building a separate security demo.
- I teach the CLI client to log in, store the token in memory and send it with each request.
- The JWT signing key stays out of the repo. In TeamCity it lives in a password parameter, which is masked in build logs.
- I add a dependency vulnerability scan (OWASP Dependency-Check) as a build step. It will probably flag something on the first run, which is the point.

### Week 6: Microservices, Service Boundaries (Application tier)

- I split the application tier into User, Account, Transaction and Notification services.
- I introduce inter-service communication over REST.
- Each service gets its own PostgreSQL schema, so services do not read each other's tables.
- The CLI still talks to a single entry point, so I need to decide how requests get routed to the right service.
- I document the service boundaries and the trade-offs of the split, including what happens to a transfer that spans two services.
- In TeamCity I move to one build configuration per service from a shared template, chained so a change to one service does not rebuild the other three.
- I tag every metric with a `service` label and pass a request ID header between services, so I can follow one transfer through all the logs.

### Week 7: Docker + Terraform + AWS, Deployment (all three tiers)

**Part 1: Docker locally**

- I containerise every service with Docker and describe the full stack in Docker Compose: the four services, PostgreSQL, Redis, Prometheus and Grafana.
- I get a working Grafana dashboard locally before touching AWS. Debugging a dashboard on my laptop is much faster than debugging it in a VPC.

**Part 2: Terraform**

- I create an S3 bucket for Terraform remote state (private, versioned, encrypted) with state locking turned on, then point the Terraform backend at it.
- I write the infrastructure as four modules in `infra/modules/`: `network`, `data`, `app` and `edge` (the load balancer). I build them in that order, and I read the `terraform plan` output properly before every `apply`.
- I create an ECR repository per service and push images by hand this week. Week 8 automates it.
- I do not commit `.tfvars` files that contain secrets.
- I let RDS manage its own master password in Secrets Manager (`manage_master_user_password`), so the database password never appears in my code. The state file can still hold sensitive values, which is why the state bucket is locked down.
- I run `terraform destroy` at the end of every working session. A fully built stack left running over a weekend costs real money.

**Part 3: the network and the tiers**

| Layer | Subnets (2 AZs) | Resources | Inbound allowed from |
|---|---|---|---|
| Presentation | 2 public | Application Load Balancer | Internet, port 443 |
| Application | 2 private | ECS or EC2 running the service containers | Load balancer security group only |
| Data | 2 private, no internet route | RDS PostgreSQL (port 5432), ElastiCache Redis (port 6379) | Application security group only |

- I use a VPC (for example 10.0.0.0/16) with six subnets, one pair per tier, spread across two Availability Zones.
- The private app subnets need a way out to pull images and ship logs. A NAT gateway does that but costs roughly $32 a month each at us-east-1 prices (I need to check my region), so I use a single one for this project and accept that it is a single point of failure. VPC endpoints for ECR, S3 and CloudWatch are the alternative.
- I chain the security groups so each tier only accepts traffic from the one above it, and I confirm the database cannot be reached from my laptop.
- I keep secrets (database passwords, JWT signing key) out of the code and images, using environment variables or AWS Secrets Manager.
- I document how to run the platform locally and how it is deployed, including a diagram of the tiers.

### Week 8: CI/CD with TeamCity


- I build the pipeline as a chain of TeamCity build configurations, all defined in `.teamcity/`:
  1. Build and test (already exists)
  2. Build the Docker image and push it to ECR, tagged with the Git commit SHA (never `latest`)
  3. Run `terraform plan` and publish the plan as a build artefact
  4. Manual approval step
  5. Run `terraform apply` and roll the ECS services onto the new image tags
- The pipeline gets its own AWS credentials with narrow permissions, stored as TeamCity password parameters. If I later run the agent on EC2, I switch to an instance role and delete the keys.
- I prove the safety net works by breaking a test on purpose and confirming nothing reaches AWS.
- I prove rollback works by redeploying the previous image tag and timing it. I am aiming for under 10 minutes.
- I add a post-deploy smoke test (log in, check a balance) so a deployment that starts but does not work is caught by the pipeline, not by me.

### Week 9: Observability with Grafana

- I run Grafana and Prometheus as containers in the private app subnets, deployed by Terraform. I reach Grafana through SSM port forwarding, or through a separate load balancer rule restricted to my own IP. It does not get exposed openly.
- Self-hosting is cheap for a learning project. Amazon Managed Grafana bills per active user and Amazon Managed Service for Prometheus bills by volume, so I only choose them if I would rather not run the containers.
- I add two data sources: Prometheus for service metrics, and CloudWatch for RDS, ElastiCache and load balancer health.
- I build four dashboards and store each as JSON in `monitoring/`, so they survive a teardown:
  - Service health per service: request rate, error rate and p95 latency
  - Banking business view: transfers per minute, failed transfers, overdraft rejections
  - Data tier: RDS CPU, connections and free storage, Redis hit rate and evictions
  - Load balancer: 5xx count and healthy target count
- I add three alerts: 5xx rate above 2% for 5 minutes, healthy targets below 1, and RDS CPU above 80% for 10 minutes. They go to an email address I actually read.

### Week 10: Hardening and Review

- I test the failure paths: stop one container, then one Availability Zone's worth of resources, and see what the CLI does. This time I watch it happen on the Grafana dashboards and confirm the right alert fires within the 5 minutes I configured.
- I run a full rebuild test: `terraform destroy`, then bring the whole platform back using only Terraform and the TeamCity pipeline. My target is under 30 minutes. Whatever I had to fix by hand is a gap in the automation, so I write each one down and fix it.
- I tidy the documentation and tear down anything I do not need, so AWS charges stop. I check the billing dashboard a few days later to confirm the charges really have stopped.

## Stretch: React Frontend (optional, after Week 10)

I only start this once Week 10 is done. It is a second Presentation tier client, not a rewrite of anything.

- I scope it to four screens: login, account list, transfer form and transaction history. If a screen needs something the CLI never needed, that is a gap in the API, so I fix the API rather than working around it in React.
- It calls the same REST API through the same load balancer. There is no new backend code beyond the CORS origin I already made configurable in Week 4.
- I host it as static files in S3 behind CloudFront. Terraform gets a new `frontend` module (bucket, CloudFront distribution and an ACM certificate, which must be created in us-east-1 for CloudFront).
- I decide how the browser holds the JWT. Keeping it in memory matches the CLI but is lost on refresh. An httpOnly cookie with a refresh token survives a refresh but needs backend changes. I pick one deliberately and write down why.
- I generate the API client types from the OpenAPI spec I published in Week 4.
- I add a TeamCity pipeline for it: `npm ci`, `npm test`, `npm run build`, sync to S3, invalidate the CloudFront cache.
