# OrderMS — Order Microservice

Spring Boot microservice for managing **customers**, **orders**, and **order items**. Deployed on AWS with PostgreSQL (RDS), Cognito authentication, SQS event publishing, and optional ElastiCache Redis caching.

| | |
|---|---|
| **Stack** | Java 17 · Spring Boot 3.2 · Maven |
| **API base** | `http://localhost:8020/api` |
| **Region** | `ap-south-2` (configurable) |

---

## Documentation

| Guide | Description |
|-------|-------------|
| [**AWS Infrastructure Setup Guide**](docs/aws-infrastructure-setup-guide.md) | End-to-end AWS provisioning: EC2, RDS, Cognito, Lambda, Secrets Manager, SQS, ElastiCache, API Gateway — with CLI commands, verification, and troubleshooting |
| [**Developer Documentation**](docs/developer-documentation.md) | How the codebase works: Maven dependencies, annotations, classes, methods, `application.yml` properties, security, caching, and request flows |

Start with the **infrastructure guide** to provision AWS resources, then use the **developer guide** to understand and extend the application code.

---

## Features

- REST API for customers, orders, and order items
- PostgreSQL persistence (AWS RDS)
- OAuth2 resource server — Cognito JWT validation with scope-based authorization (`orderms/read`, `orderms/write`, `orderms/admin`)
- Async order events via AWS SQS (FIFO)
- Configuration from AWS Secrets Manager
- Optional Redis caching (ElastiCache)
- Swagger UI and Actuator health endpoints

---

## Quick start (local)

**Prerequisites:** Java 17, Maven 3.9+, PostgreSQL (or use test profile with H2)

```bash
git clone <repository-url>
cd orderms

# Database (local PostgreSQL example)
export DATABASE_HOST=localhost
export DATABASE_USERNAME=postgres
export DATABASE_PASSWORD=<your-password>

# Cognito (required for protected API endpoints)
export COGNITO_ISSUER_URI=https://cognito-idp.<region>.amazonaws.com/<user-pool-id>
export COGNITO_USER_POOL_ID=<user-pool-id>

mvn spring-boot:run
```

| Endpoint | URL |
|----------|-----|
| Health | http://localhost:8020/api/actuator/health |
| Swagger UI | http://localhost:8020/api/swagger-ui.html |
| OpenAPI JSON | http://localhost:8020/api/api-docs |

```bash
mvn test
mvn clean package -DskipTests
```

---

## Project layout

```
orderms/
├── src/main/java/com/winsoon/orderms/   # Application code
├── src/main/resources/application.yml   # Configuration
├── deploy/                              # EC2, Cognito, Redis, Lambda scripts
├── docs/
│   ├── aws-infrastructure-setup-guide.md
│   └── developer-documentation.md
└── postman/                             # API test collection
```

---

## AWS deployment (summary)

On EC2, the app uses the instance IAM role to read Secrets Manager and publish to SQS. Typical deploy flow:

```bash
./deploy/install-systemd.sh    # first time
~/redeploy-orderms.sh          # subsequent updates
```

Full setup steps, IAM policies, and CLI commands are in the [**AWS Infrastructure Setup Guide**](docs/aws-infrastructure-setup-guide.md).

---

## License

Apache 2.0 (see Swagger `OpenApiConfig` metadata).
