# OrderMS — Developer Documentation

A complete guide to how **OrderMS** (Order Microservice) is built: dependencies, annotations, classes, configuration, and how everything connects. Written for developers who are new to Spring Boot or this project.

**Stack:** Java 17 · Spring Boot 3.2 · PostgreSQL (RDS) · AWS (Cognito, SQS, Secrets Manager, ElastiCache Redis) · Maven

---

## Table of contents

1. [What this application does](#what-this-application-does)
2. [High-level architecture](#high-level-architecture)
3. [Project structure](#project-structure)
4. [Maven dependencies explained](#maven-dependencies-explained)
5. [Annotations reference](#annotations-reference)
6. [Configuration — `application.yml`](#configuration--applicationyml)
7. [Layer-by-layer walkthrough](#layer-by-layer-walkthrough)
8. [Class reference — every class and method](#class-reference--every-class-and-method)
9. [Request lifecycle](#request-lifecycle)
10. [Security flow (Cognito JWT)](#security-flow-cognito-jwt)
11. [Caching flow](#caching-flow)
12. [Event publishing (SQS)](#event-publishing-sqs)
13. [Database model](#database-model)
14. [REST API summary](#rest-api-summary)
15. [Local development and testing](#local-development-and-testing)

---

## What this application does

OrderMS is a **REST API microservice** that manages:

- **Customers** — create, read, update, delete
- **Orders** — create, list, search, update status, delete
- **Order items** — line items belonging to an order

It persists data in **PostgreSQL**, optionally caches reads in **Redis**, validates **Cognito JWT tokens** for every protected endpoint, and publishes **order events** to **SQS** when orders change.

Think of it as the “orders department” of a larger e-commerce system. Other services (inventory, shipping, notifications) can listen to SQS events without OrderMS knowing about them.

---

## High-level architecture

OrderMS follows the classic **layered architecture** used in Spring applications:

```
HTTP Request
     │
     ▼
┌─────────────────────────────────────────────────────────────┐
│  Security Filter Chain (Spring Security + OAuth2 JWT)        │  ← Cognito token checked first
└─────────────────────────────────────────────────────────────┘
     │
     ▼
┌─────────────┐     ┌─────────────┐     ┌──────────────────┐
│ Controller  │ ──▶ │  Service    │ ──▶ │  Repository      │ ──▶ PostgreSQL (RDS)
│ (REST API)  │     │ (business)  │     │  (data access)   │
└─────────────┘     └──────┬──────┘     └──────────────────┘
                           │
              ┌────────────┼────────────┐
              ▼            ▼            ▼
         Cache layer   SQS events   Exception handler
         (Redis/mem)   (async)      (error JSON)
```

| Layer | Package | Responsibility |
|-------|---------|----------------|
| **Entry point** | `OrderMicroserviceApplication` | Starts Spring, enables caching |
| **Web** | `controller` | Maps HTTP URLs to Java methods |
| **Business** | `service` | Rules, caching, events, DTO conversion |
| **Data** | `repository` + `entity` | Database read/write via JPA |
| **API contracts** | `dto` | JSON shape for requests/responses |
| **Cross-cutting** | `config`, `security`, `exception`, `event` | Security, AWS, cache, errors, messaging |

---

## Project structure

```
orderms/
├── pom.xml                          # Maven dependencies and build
├── src/main/java/com/winsoon/orderms/
│   ├── OrderMicroserviceApplication.java
│   ├── controller/                  # REST endpoints
│   │   ├── CustomerController.java
│   │   ├── OrderController.java
│   │   └── OrderItemController.java
│   ├── service/                     # Business logic
│   │   ├── CustomerService.java
│   │   ├── OrderService.java
│   │   └── OrderItemService.java
│   ├── repository/                  # Spring Data JPA interfaces
│   │   ├── CustomerRepository.java
│   │   ├── OrderRepository.java
│   │   └── OrderItemRepository.java
│   ├── entity/                      # Database table mappings
│   │   ├── Customer.java
│   │   ├── Order.java
│   │   ├── OrderItem.java
│   │   └── OrderStatus.java
│   ├── dto/                         # API data transfer objects
│   ├── config/                      # Spring @Configuration beans
│   ├── security/                    # JWT, scopes, Cognito groups
│   ├── event/                       # SQS order events
│   └── exception/                   # Global error handling
├── src/main/resources/
│   └── application.yml              # Main configuration
└── src/test/                        # Integration/unit tests
```

---

## Maven dependencies explained

Each dependency in `pom.xml` enables a specific capability. Spring Boot **starters** bundle related libraries so you add one artifact and get a working feature.

### Spring Boot core

| Dependency | What it brings | Features enabled |
|------------|----------------|------------------|
| `spring-boot-starter-parent` (parent POM) | Version alignment for all Spring libraries | Consistent Spring Boot 3.2 stack |
| `spring-boot-starter-web` | Spring MVC, embedded Tomcat, Jackson JSON | REST controllers, `@RestController`, HTTP server on port 8020 |
| `spring-boot-starter-data-jpa` | Hibernate ORM, Spring Data JPA, transactions | `@Entity`, `@Repository`, `JpaRepository`, `@Transactional` |
| `spring-boot-starter-validation` | Jakarta Bean Validation | `@Valid`, `@NotNull` (available for DTOs) |
| `spring-boot-starter-logging` | Logback | `@Slf4j` log output |
| `spring-boot-starter-actuator` | Health and metrics endpoints | `/actuator/health`, `/actuator/metrics` |
| `spring-boot-devtools` | Hot reload in dev | Faster local iteration (runtime only) |

### Database

| Dependency | What it brings | Features enabled |
|------------|----------------|------------------|
| `postgresql` | JDBC driver for PostgreSQL | Connects to AWS RDS |
| `h2` (test scope) | In-memory database | Unit/integration tests without RDS |

### Caching

| Dependency | What it brings | Features enabled |
|------------|----------------|------------------|
| `spring-boot-starter-cache` | Spring Cache abstraction | `@EnableCaching`, `@Cacheable`, `@CacheEvict` |
| `spring-boot-starter-data-redis` | Lettuce Redis client | `RedisCacheConfig`, ElastiCache connection |

### Security (Cognito / OAuth2)

| Dependency | What it brings | Features enabled |
|------------|----------------|------------------|
| `spring-boot-starter-security` | Spring Security filter chain | `@EnableWebSecurity`, `SecurityFilterChain`, 401/403 |
| `spring-boot-starter-oauth2-resource-server` | JWT validation | Validates Cognito tokens via `issuer-uri` |
| `spring-security-test` (test) | Security test helpers | `SecurityIntegrationTest`, `@WithMockUser` |

### API documentation

| Dependency | What it brings | Features enabled |
|------------|----------------|------------------|
| `springdoc-openapi-starter-webmvc-ui` | OpenAPI 3 + Swagger UI | `/swagger-ui.html`, `/api-docs` |

### AWS integration

| Dependency | What it brings | Features enabled |
|------------|----------------|------------------|
| `spring-cloud-aws-starter-secrets-manager` | Load secrets as Spring properties | `spring.config.import: aws-secretsmanager:...` |
| `software.amazon.awssdk:sqs` | AWS SDK v2 SQS client | `SqsClient`, `OrderEventPublisher` |
| `software.amazon.awssdk:secretsmanager` | Direct Secrets Manager API | Used by Spring Cloud AWS |
| `software.amazon.awssdk:cognitoidentityprovider` | Cognito admin API | `CognitoGroupService` (optional group lookup) |
| `software.amazon.awssdk:ssm` | Parameter Store | Available for future config |
| `software.amazon.awssdk:cloudwatch` | CloudWatch metrics API | Available for custom metrics |

### Observability

| Dependency | What it brings | Features enabled |
|------------|----------------|------------------|
| `micrometer-registry-cloudwatch` | Push metrics to CloudWatch | Production monitoring |
| `micrometer-registry-prometheus` | Prometheus scrape format | Optional metrics export |

### Developer productivity

| Dependency | What it brings | Features enabled |
|------------|----------------|------------------|
| `lombok` | Code generation at compile time | `@Data`, `@Builder`, `@Slf4j`, `@RequiredArgsConstructor` |
| `jackson-databind` | JSON serialization | REST request/response bodies, SQS message JSON |
| `spring-boot-starter-test` | JUnit 5, MockMvc, Spring Test | `mvn test` |

### BOMs (Bill of Materials)

| BOM | Purpose |
|-----|---------|
| `spring-cloud-dependencies` | Aligns Spring Cloud versions |
| `software.amazon.awssdk:bom` | Aligns all AWS SDK v2 module versions |
| `spring-cloud-aws-dependencies` | Aligns Spring Cloud AWS modules |

---

## Annotations reference

Annotations are **labels** Spring reads at startup to know what each class does. Below: **annotation → Maven dependency → plain-English purpose**.

### Application bootstrap

| Annotation | From dependency | Layman explanation |
|------------|-----------------|-------------------|
| `@SpringBootApplication` | `spring-boot-autoconfigure` | “This is the main app.” Turns on auto-configuration, component scanning, and property loading. Combines `@Configuration`, `@EnableAutoConfiguration`, and `@ComponentScan`. |
| `@EnableCaching` | `spring-context` (via `starter-cache`) | “Turn on the cache system.” Allows `@Cacheable` / `@CacheEvict` on service methods. |

### Web layer (Controllers)

| Annotation | From dependency | Layman explanation |
|------------|-----------------|-------------------|
| `@RestController` | `spring-web` | “This class answers HTTP requests and returns JSON.” Combines `@Controller` + `@ResponseBody`. |
| `@RequestMapping("/orders")` | `spring-web` | “All methods in this class live under `/orders`.” Base URL prefix. |
| `@GetMapping` | `spring-web` | “Handle HTTP GET.” Read data. |
| `@PostMapping` | `spring-web` | “Handle HTTP POST.” Create new resource. |
| `@PutMapping` | `spring-web` | “Handle HTTP PUT.” Update existing resource. |
| `@DeleteMapping` | `spring-web` | “Handle HTTP DELETE.” Remove resource. |
| `@PathVariable` | `spring-web` | “Take this value from the URL.” e.g. `/orders/{orderId}` → `orderId`. |
| `@RequestBody` | `spring-web` | “Parse JSON from request body into this Java object.” |
| `@RequestParam` | `spring-web` | “Take this value from query string.” e.g. `?status=SHIPPED`. |
| `@DateTimeFormat` | `spring-web` | “Parse date/time string in this format.” Used for date-range search. |
| `@CrossOrigin` | `spring-web` | “Allow browser apps on other domains to call this API.” CORS headers. |

### Dependency injection

| Annotation | From dependency | Layman explanation |
|------------|-----------------|-------------------|
| `@Autowired` | `spring-beans` | “Spring, please inject the matching bean here.” Wires `OrderService` into `OrderController` automatically. |
| `@Value("${property}")` | `spring-beans` | “Inject a config value from `application.yml` or environment.” |
| `@RequiredArgsConstructor` | `lombok` | “Generate a constructor for all `final` fields.” Lombok alternative to `@Autowired` on fields (used in `DataSeeder`). |

### Service layer

| Annotation | From dependency | Layman explanation |
|------------|-----------------|-------------------|
| `@Service` | `spring-context` | “This class contains business logic.” Spring registers it as a singleton bean. |
| `@Transactional` | `spring-tx` (via JPA starter) | “Wrap methods in a database transaction.” On failure, changes roll back. `readOnly = true` optimizes read-only queries. |
| `@Cacheable` | `spring-context` (via cache starter) | “Remember the return value; skip DB next time for same key.” |
| `@CacheEvict` | `spring-context` | “Clear cache when data changes.” Keeps cache consistent after create/update/delete. |

### Data layer (JPA / Hibernate)

| Annotation | From dependency | Layman explanation |
|------------|-----------------|-------------------|
| `@Entity` | `jakarta.persistence` (Hibernate) | “This Java class maps to a database table.” |
| `@Table(name = "orders")` | JPA | “Use this exact table name in PostgreSQL.” |
| `@Id` | JPA | “This field is the primary key.” |
| `@GeneratedValue(strategy = IDENTITY)` | JPA | “Database auto-generates IDs (SERIAL/BIGSERIAL).” |
| `@Column(...)` | JPA | “Map field to column; set nullability, uniqueness.” |
| `@Enumerated(EnumType.STRING)` | JPA | “Store enum as text (`PENDING`), not a number.” |
| `@ManyToOne` | JPA | “Many order items belong to one order.” Foreign key relationship. |
| `@JoinColumn(name = "order_id")` | JPA | “Foreign key column name in `order_items` table.” |
| `@PrePersist` | JPA | “Run this method automatically before first save.” Sets `createdAt`. |
| `@PreUpdate` | JPA | “Run before every update.” Sets `updatedAt`. |
| `@Repository` | `spring-data-jpa` | “This interface talks to the database.” Spring generates the implementation. |
| `@Query("SELECT ...")` | `spring-data-jpa` | “Custom JPQL query when method naming isn’t enough.” |
| `@Param` | `spring-data-jpa` | “Bind method parameter to `:name` in `@Query`.” |

### Configuration

| Annotation | From dependency | Layman explanation |
|------------|-----------------|-------------------|
| `@Configuration` | `spring-context` | “This class defines Spring beans.” |
| `@Bean` | `spring-context` | “Create and register this object in the Spring container.” |
| `@ConditionalOnProperty` | `spring-boot-autoconfigure` | “Only create this bean if a property exists.” Redis config loads only when `REDIS_HOST` is set. |
| `@ConditionalOnMissingBean` | `spring-boot-autoconfigure` | “Only create if no other bean of this type exists.” Fallback in-memory cache when Redis is off. |
| `@Component` | `spring-context` | “Generic Spring-managed component.” Used for `DataSeeder`. |

### Security

| Annotation | From dependency | Layman explanation |
|------------|-----------------|-------------------|
| `@EnableWebSecurity` | `spring-security-config` | “Activate Spring Security for this app.” |
| `@Bean SecurityFilterChain` | `spring-security-web` | “Define who can access which URLs.” JWT rules live here. |

### Exception handling

| Annotation | From dependency | Layman explanation |
|------------|-----------------|-------------------|
| `@RestControllerAdvice` | `spring-web` | “Catch exceptions from all controllers and return JSON errors.” |
| `@ExceptionHandler` | `spring-web` | “When this exception type is thrown, run this method.” |

### Lombok (compile-time code generation)

| Annotation | Purpose |
|------------|---------|
| `@Data` | Generates getters, setters, `equals`, `hashCode`, `toString` |
| `@Builder` | Fluent builder pattern: `Order.builder().customerId(1L).build()` |
| `@NoArgsConstructor` | Empty constructor (required by JPA) |
| `@AllArgsConstructor` | Constructor with all fields |
| `@Slf4j` | Creates `private static final Logger log = ...` for logging |

### Jackson (JSON)

| Annotation | From dependency | Layman explanation |
|------------|-----------------|-------------------|
| `@JsonProperty("event_id")` | `jackson-databind` | “Serialize this field as `event_id` in JSON.” Snake_case for SQS consumers. |

### Boot lifecycle

| Annotation | From dependency | Layman explanation |
|------------|-----------------|-------------------|
| `@CommandLineRunner` (interface) | `spring-boot` | “Run this code once after the app starts.” `DataSeeder` uses it. |

---

## Configuration — `application.yml`

Full file: `src/main/resources/application.yml`. Properties are grouped by feature.

### `spring.application`

```yaml
spring:
  application:
    name: orderms
```

| Property | Purpose |
|----------|---------|
| `name` | Logical service name in logs and service discovery. |

---

### `spring.autoconfigure.exclude`

```yaml
spring:
  autoconfigure:
    exclude:
      - RedisAutoConfiguration
      - RedisRepositoriesAutoConfiguration
```

| Property | Purpose |
|----------|---------|
| `exclude` | **Disables automatic Redis setup.** Redis is configured manually in `RedisCacheConfig` only when `REDIS_HOST` exists (from Secrets Manager). Prevents startup failure when Redis is not configured. |

**Linked classes:** `RedisCacheConfig`, `SimpleCacheConfig`

---

### `spring.config.import`

```yaml
spring:
  config:
    import: optional:aws-secretsmanager:winsoon/orderms/postgresql,...
```

| Property | Purpose |
|----------|---------|
| `optional:aws-secretsmanager:...` | Loads JSON secrets from AWS Secrets Manager as Spring properties. `optional:` means the app still starts locally without AWS. |

**Secrets loaded:**

| Secret path | Properties injected | Used by |
|-------------|---------------------|---------|
| `winsoon/orderms/postgresql` | `DATABASE_HOST`, `DATABASE_PORT`, `DATABASE_NAME`, `DATABASE_USERNAME`, `DATABASE_PASSWORD` | DataSource, JPA |
| `winsoon/orderms/sqs` | `ORDERMS_AWS_SQS_QUEUE_URL`, `ORDERMS_AWS_SQS_QUEUE_NAME` | `OrderEventPublisher` |
| `winsoon/orderms/cognito` | `COGNITO_ISSUER_URI`, `COGNITO_USER_POOL_ID`, etc. | Security, `CognitoGroupService` |
| `winsoon/orderms/redis` | `REDIS_HOST`, `REDIS_PORT`, `REDIS_SSL`, `REDIS_PASSWORD`, `REDIS_CACHE_TTL_SECONDS` | `RedisCacheConfig` |

**Dependency:** `spring-cloud-aws-starter-secrets-manager`

---

### `spring.cloud.aws.region`

```yaml
spring:
  cloud:
    aws:
      region:
        static: ${AWS_REGION:ap-south-2}
```

| Property | Purpose |
|----------|---------|
| `static` | AWS region for Spring Cloud AWS (Secrets Manager lookup). Defaults to `ap-south-2`. |

---

### `spring.datasource`

```yaml
spring:
  datasource:
    url: jdbc:postgresql://${DATABASE_HOST}:${DATABASE_PORT:5432}/${DATABASE_NAME:orderms_db}
    username: ${DATABASE_USERNAME}
    password: ${DATABASE_PASSWORD}
    driver-class-name: org.postgresql.Driver
    hikari:
      maximum-pool-size: 10
      minimum-idle: 2
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
```

| Property | Purpose |
|----------|---------|
| `url` | JDBC connection string to RDS PostgreSQL. |
| `username` / `password` | DB credentials (from secret or env vars). |
| `driver-class-name` | PostgreSQL JDBC driver class. |
| `hikari.maximum-pool-size` | Max concurrent DB connections (10). |
| `hikari.minimum-idle` | Minimum idle connections kept open (2). |
| `hikari.connection-timeout` | Wait up to 30s for a free connection. |
| `hikari.idle-timeout` | Close idle connections after 10 minutes. |
| `hikari.max-lifetime` | Recycle connections after 30 minutes. |

**Linked:** All `*Repository` interfaces, all `@Entity` classes, `@Transactional` services.

**Dependency:** `spring-boot-starter-data-jpa`, `postgresql`

---

### `spring.jpa`

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: update
    properties:
      hibernate:
        dialect: PostgreSQLDialect
        format_sql: true
        jdbc:
          batch_size: 20
          fetch_size: 50
        order_inserts: true
        order_updates: true
    show-sql: false
    open-in-view: false
```

| Property | Purpose |
|----------|---------|
| `ddl-auto: update` | Hibernate creates/updates tables from `@Entity` classes. **Use migrations in production.** |
| `dialect` | SQL dialect for PostgreSQL. |
| `format_sql` | Pretty-print SQL in logs (when `show-sql` is true). |
| `jdbc.batch_size` | Batch up to 20 INSERTs for performance. |
| `jdbc.fetch_size` | Fetch 50 rows per DB round-trip. |
| `order_inserts` / `order_updates` | Optimize batch ordering. |
| `show-sql` | Log SQL statements (`false` in prod). |
| `open-in-view: false` | **Best practice:** don’t keep DB session open during HTTP response rendering. |

---

### `spring.security.oauth2.resourceserver.jwt`

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${COGNITO_ISSUER_URI}
```

| Property | Purpose |
|----------|---------|
| `issuer-uri` | Cognito User Pool issuer URL. Spring fetches JWKS public keys from `{issuer}/.well-known/openid-configuration` and validates every JWT. |

**Linked:** `SecurityConfig`, `OrderMsJwtAuthenticationConverter`

**Dependency:** `spring-boot-starter-oauth2-resource-server`

---

### `orderms.security.cognito`

```yaml
orderms:
  security:
    cognito:
      user-pool-id: ${COGNITO_USER_POOL_ID}
      resolve-groups-via-api: false
      group-cache-ttl-minutes: 5
```

| Property | Purpose |
|----------|---------|
| `user-pool-id` | Cognito pool ID for optional API group lookup. |
| `resolve-groups-via-api` | `false` = read groups from JWT `cognito:groups` claim (set by Pre Token Lambda). `true` = call Cognito API as fallback. |
| `group-cache-ttl-minutes` | Cache Cognito API group lookups for 5 minutes. |

**Linked:** `CognitoGroupService`, `OrderMsJwtAuthenticationConverter`

---

### `orderms.cache.redis`

```yaml
orderms:
  cache:
    redis:
      ttl-seconds: ${REDIS_CACHE_TTL_SECONDS:300}
```

| Property | Purpose |
|----------|---------|
| `ttl-seconds` | Cache entries expire after 300 seconds (5 minutes) in Redis. |

**Linked:** `RedisCacheConfig`, `@Cacheable` in services

---

### `aws`

```yaml
aws:
  region: ${AWS_REGION:ap-south-2}
  sqs:
    queue-url: ${ORDERMS_AWS_SQS_QUEUE_URL:}
    queue-name: ${ORDERMS_AWS_SQS_QUEUE_NAME:orderms-events.fifo}
```

| Property | Purpose |
|----------|---------|
| `region` | AWS region for SDK clients (`SqsConfig`). |
| `sqs.queue-url` | Full SQS FIFO queue URL. Empty = event publishing disabled (warns in logs). |
| `sqs.queue-name` | Human-readable queue name for reference. |

**Linked:** `SqsConfig`, `OrderEventPublisher`

---

### `server`

```yaml
server:
  port: ${APPLICATION_PORT:8020}
  servlet:
    context-path: /api
  compression:
    enabled: true
```

| Property | Purpose |
|----------|---------|
| `port` | HTTP port (8020 on EC2). |
| `context-path` | All URLs prefixed with `/api`. Example: controller `/orders` → `http://host:8020/api/orders`. |
| `compression` | Gzip compress HTTP responses. |

---

### `springdoc` (Swagger)

```yaml
springdoc:
  api-docs:
    path: /api-docs
  swagger-ui:
    path: /swagger-ui.html
    enabled: true
```

| Property | Purpose |
|----------|---------|
| `api-docs.path` | OpenAPI JSON at `/api/api-docs`. |
| `swagger-ui.path` | Interactive API docs at `/api/swagger-ui.html`. |

**Linked:** `OpenApiConfig`

**Dependency:** `springdoc-openapi-starter-webmvc-ui`

---

### `management` (Actuator)

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,metrics,info
  endpoint:
    health:
      show-details: always
```

| Property | Purpose |
|----------|---------|
| `exposure.include` | Public actuator endpoints: health, metrics, info. |
| `health.show-details` | Show DB status, disk space, etc. in health response. |

**Dependency:** `spring-boot-starter-actuator`

**Note:** `/actuator/health` is public; other actuator endpoints require authentication (`SecurityConfig`).

---

### `logging`

```yaml
logging:
  level:
    root: INFO
    com.winsoon: DEBUG
    org.hibernate.SQL: DEBUG
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} - %msg%n"
```

| Property | Purpose |
|----------|---------|
| `level.root` | Default log level for all packages. |
| `level.com.winsoon` | Debug logs for application code. |
| `level.org.hibernate.SQL` | Log SQL statements (useful in dev). |
| `pattern.console` | Timestamp + message format. |

---

## Layer-by-layer walkthrough

### How classes connect (object graph)

```
OrderMicroserviceApplication
    │
    ├── SecurityConfig ──────────▶ OrderMsJwtAuthenticationConverter
    │                                    ├── CognitoGroupService
    │                                    │        └── CognitoIdentityProviderClient (CognitoConfig)
    │                                    └── CognitoGroupAuthorities
    │
    ├── CorsConfig ──────────────▶ CorsFilter
    ├── OpenApiConfig ───────────▶ OpenAPI bean
    ├── SqsConfig ───────────────▶ SqsClient
    ├── RedisCacheConfig (if REDIS_HOST) ──▶ RedisCacheManager
    ├── SimpleCacheConfig (if no Redis) ───▶ ConcurrentMapCacheManager
    ├── DataSeeder ──────────────▶ CustomerRepository, OrderRepository, OrderItemRepository
    │
    ├── OrderController ─────────▶ OrderService
    │                                    ├── OrderRepository
    │                                    └── OrderEventPublisher ──▶ SqsClient
    │
    ├── CustomerController ──────▶ CustomerService ──▶ CustomerRepository
    │
    ├── OrderItemController ─────▶ OrderItemService
    │                                    ├── OrderItemRepository
    │                                    └── OrderRepository
    │
    └── GlobalExceptionHandler ──▶ ErrorResponse
```

---

## Class reference — every class and method

### `OrderMicroserviceApplication`

**Package:** root  
**Role:** Application entry point.

| Method | Purpose |
|--------|---------|
| `main(String[] args)` | Starts Spring Boot; scans `com.winsoon.orderms` and all sub-packages. |

**Annotations:** `@SpringBootApplication`, `@EnableCaching`

---

### Controllers

Controllers are thin: they log the request, delegate to a service, and wrap the result in `ResponseEntity` with the correct HTTP status.

#### `OrderController` — base path `/api/orders`

| Method | HTTP | Path | Calls | Returns |
|--------|------|------|-------|---------|
| `createOrder` | POST | `/orders` | `orderService.createOrder` | 201 Created |
| `getOrderById` | GET | `/orders/{orderId}` | `orderService.getOrderById` | 200 OK |
| `getOrderByNumber` | GET | `/orders/number/{orderNumber}` | `orderService.getOrderByNumber` | 200 OK |
| `getAllOrders` | GET | `/orders` | `orderService.getAllOrders` | 200 OK |
| `getOrdersByCustomerId` | GET | `/orders/customer/{customerId}` | `orderService.getOrdersByCustomerId` | 200 OK |
| `getOrdersBetweenDates` | GET | `/orders/search/date-range` | `orderService.getOrdersBetweenDates` | 200 OK |
| `updateOrderStatus` | PUT | `/orders/{orderId}/status?status=` | `orderService.updateOrderStatus` | 200 OK |
| `deleteOrder` | DELETE | `/orders/{orderId}` | `orderService.deleteOrder` | 204 No Content |

#### `CustomerController` — base path `/api/customers`

| Method | HTTP | Path | Calls | Returns |
|--------|------|------|-------|---------|
| `createCustomer` | POST | `/customers` | `customerService.createCustomer` | 201 |
| `getCustomerById` | GET | `/customers/{customerId}` | `customerService.getCustomerById` | 200 |
| `getCustomerByEmail` | GET | `/customers/email/{email}` | `customerService.getCustomerByEmail` | 200 |
| `getAllCustomers` | GET | `/customers` | `customerService.getAllCustomers` | 200 |
| `updateCustomer` | PUT | `/customers/{customerId}` | `customerService.updateCustomer` | 200 |
| `deleteCustomer` | DELETE | `/customers/{customerId}` | `customerService.deleteCustomer` | 204 |

#### `OrderItemController` — base path `/api/orders/{orderId}/items`

| Method | HTTP | Path | Calls | Returns |
|--------|------|------|-------|---------|
| `addItemToOrder` | POST | `/orders/{orderId}/items` | `orderItemService.addItemToOrder` | 201 |
| `getItemsByOrderId` | GET | `/orders/{orderId}/items` | `orderItemService.getItemsByOrderId` | 200 |
| `getItemById` | GET | `/orders/{orderId}/items/{itemId}` | `orderItemService.getItemById` | 200 |
| `updateOrderItem` | PUT | `/orders/{orderId}/items/{itemId}` | `orderItemService.updateOrderItem` | 200 |
| `deleteOrderItem` | DELETE | `/orders/{orderId}/items/{itemId}` | `orderItemService.deleteOrderItem` | 204 |

---

### Services

Services contain **business logic**: validation (basic), entity ↔ DTO conversion, caching, transactions, and side effects (SQS).

#### `OrderService`

| Method | Cache | SQS event | Description |
|--------|-------|-----------|-------------|
| `createOrder` | Evict all `orders` | `ORDER_CREATED` | Builds `Order` with generated number, status `PENDING`, saves to DB |
| `getOrderById` | `@Cacheable` key=`orderId` | — | Load by primary key |
| `getOrderByNumber` | `@Cacheable` key=`number:{n}` | — | Load by unique order number |
| `getOrdersByCustomerId` | `@Cacheable` key=`customer:{id}` | — | List orders for one customer |
| `getAllOrders` | `@Cacheable` key=`all` | — | List every order |
| `updateOrderStatus` | Evict all | `ORDER_STATUS_CHANGED` | Parse status string to `OrderStatus` enum |
| `deleteOrder` | Evict all | `ORDER_DELETED` | Delete row; event sent before delete data is lost |
| `getOrdersBetweenDates` | No cache | — | Custom date range query |
| `generateOrderNumber()` (private) | — | — | `ORD-{timestamp}` |
| `publishOrderEvent()` (private) | — | — | Builds `OrderEvent`, calls `OrderEventPublisher` |
| `convertToDTO()` (private) | — | — | `Order` entity → `OrderDTO` |

#### `CustomerService`

| Method | Cache | Description |
|--------|-------|-------------|
| `createCustomer` | Evict all `customers` | Save new customer from DTO |
| `getCustomerById` | `@Cacheable` key=`customerId` | Load by ID |
| `getCustomerByEmail` | `@Cacheable` key=`email:{email}` | Load by unique email |
| `getAllCustomers` | `@Cacheable` key=`all` | List all |
| `updateCustomer` | Evict all | Update address fields (email not changed in update) |
| `deleteCustomer` | Evict all | Delete if exists |
| `convertToDTO()` (private) | — | Entity → DTO |

#### `OrderItemService`

| Method | Description |
|--------|-------------|
| `addItemToOrder` | Verify order exists; create `OrderItem` linked via `@ManyToOne` |
| `getItemsByOrderId` | List items for an order |
| `getItemById` | Load single item |
| `updateOrderItem` | Update quantity and unit price; `totalPrice` recalculated on persist via `@PrePersist` only on create — note: update does not recalc total in entity hook |
| `deleteOrderItem` | Remove item row |
| `convertToDTO()` (private) | Entity → DTO |

---

### Repositories

Spring Data JPA **generates implementations** at runtime. You only declare the interface.

#### `OrderRepository extends JpaRepository<Order, Long>`

| Method | Description |
|--------|-------------|
| `findByOrderNumber` | Derived query: `WHERE order_number = ?` |
| `findByCustomerId` | All orders for customer |
| `findByStatus` | Filter by status string |
| `findByCustomerIdAndStatus` | Custom `@Query` JPQL |
| `findOrdersBetweenDates` | Orders in date range |
| *(inherited)* `findById`, `findAll`, `save`, `deleteById`, `existsById`, `count` | Standard CRUD from `JpaRepository` |

#### `CustomerRepository extends JpaRepository<Customer, Long>`

| Method | Description |
|--------|-------------|
| `findByEmail` | Lookup by unique email |
| `findByPhoneNumber` | Lookup by phone |

#### `OrderItemRepository extends JpaRepository<OrderItem, Long>`

| Method | Description |
|--------|-------------|
| `findByOrderOrderId` | Items where `order.orderId = ?` (navigates `@ManyToOne`) |
| `findByProductId` | All items for a product across orders |

---

### Entities

#### `Customer` → table `customers`

Fields: `customerId`, `firstName`, `lastName`, `email` (unique), `phoneNumber`, address fields, `createdAt`, `updatedAt`.

Lifecycle: `@PrePersist` / `@PreUpdate` set timestamps.

#### `Order` → table `orders`

Fields: `orderId`, `orderNumber` (unique), `customerId` (logical FK, not JPA relation), `orderDate`, `totalAmount`, `status` (`OrderStatus` enum), `shippingAddress`, timestamps.

**Note:** `customerId` is a `Long` column, not `@ManyToOne Customer`. Orders reference customers by ID only (simpler microservice boundary).

#### `OrderItem` → table `order_items`

Fields: `orderItemId`, `order` (`@ManyToOne`), `productId`, `productName`, `quantity`, `unitPrice`, `totalPrice`, `createdAt`.

`@PrePersist`: sets `createdAt` and calculates `totalPrice = unitPrice × quantity` if not set.

#### `OrderStatus` (enum)

Values: `PENDING`, `CONFIRMED`, `SHIPPED`, `DELIVERED`, `CANCELLED`.

---

### DTOs

DTOs (**Data Transfer Objects**) are plain Java objects for JSON. They decouple the API contract from database schema.

| Class | Used by |
|-------|---------|
| `OrderDTO` | `OrderController`, `OrderService` |
| `CustomerDTO` | `CustomerController`, `CustomerService` |
| `OrderItemDTO` | `OrderItemController`, `OrderItemService` |

No JPA annotations on DTOs — they are not persisted directly.

---

### Configuration classes

#### `SecurityConfig`

| Bean / constant | Purpose |
|-----------------|---------|
| `PUBLIC_PATHS` | Health + Swagger — no JWT required |
| `API_READ_PATHS` | GET on orders/customers needs `orderms/read` (or write/admin) |
| `securityFilterChain` | CSRF off, stateless sessions, CORS on, OAuth2 JWT, scope rules |

#### `CorsConfig`

| Bean | Purpose |
|------|---------|
| `corsFilter` | Allow all origins/headers/methods (`*`); credentials enabled |

#### `RedisCacheConfig` (`@ConditionalOnProperty("REDIS_HOST")`)

| Bean | Purpose |
|------|---------|
| `redisConnectionFactory` | Connect to ElastiCache (host, port, password, SSL) |
| `cacheManager` | Redis-backed cache with JSON serialization and TTL |

#### `SimpleCacheConfig` (`@ConditionalOnMissingBean(RedisConnectionFactory)`)

| Bean | Purpose |
|------|---------|
| `cacheManager` | In-memory `ConcurrentMapCacheManager` for local dev/tests |

#### `SqsConfig`

| Bean | Purpose |
|------|---------|
| `sqsClient` | AWS SDK SQS client for the configured region |

#### `CognitoConfig`

| Bean | Purpose |
|------|---------|
| `cognitoIdentityProviderClient` | AWS Cognito admin API client |

#### `OpenApiConfig`

| Bean | Purpose |
|------|---------|
| `orderMsOpenAPI` | Swagger title, description, version |

#### `DataSeeder` (`CommandLineRunner`)

| Method | Purpose |
|--------|---------|
| `run` | If DB empty, inserts 3 customers, 3 orders, 4 order items (demo data) |

#### `CacheNames`

Constants: `ORDERS = "orders"`, `CUSTOMERS = "customers"`.

---

### Security classes

#### `OAuth2Scopes`

Constants mapping Cognito scope strings to Spring authorities:

| Constant | Value |
|----------|-------|
| `READ` | `orderms/read` → `SCOPE_orderms/read` |
| `WRITE` | `orderms/write` → `SCOPE_orderms/write` |
| `ADMIN` | `orderms/admin` → `SCOPE_orderms/admin` |

#### `CognitoGroupAuthorities`

Maps Cognito groups to the same authorities as scopes:

| Group | Authorities granted |
|-------|---------------------|
| `ADMIN` | read + write + admin |
| `SALES` | read + write |
| `CUSTOMER` | read only |

#### `OrderMsJwtAuthenticationConverter`

| Method | Purpose |
|--------|---------|
| `convert(Jwt)` | Entry point: delegates to Spring's `JwtAuthenticationConverter` |
| `resolveAuthorities(Jwt)` | Merge scope authorities + group authorities |
| `resolveGroups(Jwt)` | Read `cognito:groups` from JWT, or fallback to `CognitoGroupService` |

#### `CognitoGroupService`

| Method | Purpose |
|--------|---------|
| `resolveGroups(username)` | Call Cognito `AdminListGroupsForUser` (cached 5 min) when API fallback enabled |

---

### Event classes

#### `OrderEvent`

Serializable message payload for SQS. Constructor sets `eventId`, `eventTimestamp`, `sourceService = "orderms"`, `correlationId`.

Event types used: `ORDER_CREATED`, `ORDER_STATUS_CHANGED`, `ORDER_DELETED`.

#### `OrderEventPublisher`

| Method | Purpose |
|--------|---------|
| `publishEvent` | Serialize to JSON, send to FIFO queue with `messageGroupId` per customer |
| `publishBatch` | Loop publish |
| `buildMessageGroupId` | `order-customer-{customerId}` for FIFO ordering |
| `isHealthy` | Ping queue attributes for health checks |

---

### Exception handling

#### `GlobalExceptionHandler`

| Method | Handles | HTTP status |
|--------|---------|-------------|
| `handleRuntimeException` | `RuntimeException` (e.g. "Order not found") | 400 Bad Request |
| `handleGeneralException` | Any other `Exception` | 500 Internal Server Error |

#### `ErrorResponse`

JSON shape: `timestamp`, `status`, `error`, `message`, `path`.

---

## Request lifecycle

Example: **GET /api/orders/1** with a valid Bearer token.

```
1. Tomcat receives HTTP GET :8020/api/orders/1
2. CorsFilter adds CORS headers
3. Spring Security filter chain:
   a. Extract Bearer token from Authorization header
   b. Fetch Cognito JWKS (cached) and validate signature + expiry + issuer
   c. OrderMsJwtAuthenticationConverter builds authorities from scope + groups
   d. SecurityConfig: GET /orders/** requires SCOPE_orderms/read (or write/admin)
   e. If OK → continue; else 401 or 403
4. DispatcherServlet routes to OrderController.getOrderById(1)
5. OrderController calls orderService.getOrderById(1)
6. @Cacheable checks "orders" cache — hit → return cached DTO
7. On miss → OrderRepository.findById(1) → PostgreSQL
8. convertToDTO → return OrderDTO
9. Jackson serializes OrderDTO to JSON
10. HTTP 200 response
```

---

## Security flow (Cognito JWT)

```
                    ┌─────────────────┐
                    │  Client (Postman) │
                    └────────┬────────┘
                             │ Authorization: Bearer <JWT>
                             ▼
                    ┌─────────────────┐
                    │ SecurityConfig   │
                    │ Filter Chain     │
                    └────────┬────────┘
                             │
              ┌──────────────┼──────────────┐
              ▼              ▼              ▼
        Public paths?   Valid JWT?     Scope/group OK?
        (health,        (issuer,       (orderms/read
         swagger)         signature)      for GET)
              │              │              │
              ▼              ▼              ▼
           permit         401 if bad     403 if missing
```

| HTTP method | Path pattern | Required authority |
|-------------|--------------|-------------------|
| GET | `/orders/**`, `/customers/**` | `SCOPE_orderms/read` OR write OR admin |
| POST, PUT | `/orders/**`, `/customers/**` | write OR admin |
| DELETE | `/orders/**`, `/customers/**` | admin only |
| GET | `/actuator/health`, `/swagger-ui/**` | none (public) |
| * | `/actuator/**` (except health) | any authenticated JWT |

---

## Caching flow

```
@EnableCaching (main class)
        │
        ▼
┌───────────────────┐     REDIS_HOST set?     ┌──────────────────┐
│ CacheManager bean │ ──── yes ──────────────▶│ RedisCacheConfig │
│                   │                         │ (ElastiCache)    │
│                   │ ──── no ───────────────▶│ SimpleCacheConfig│
└───────────────────┘                         │ (in-memory map)  │
        │                                     └──────────────────┘
        ▼
@Service methods with @Cacheable / @CacheEvict
        │
        ├── CustomerService: cache "customers"
        └── OrderService: cache "orders"
```

| Operation | Cache effect |
|-----------|--------------|
| `getCustomerById(5)` | Store result under key `5` |
| `getAllCustomers()` | Store under key `all` |
| `createCustomer()` | `@CacheEvict(allEntries=true)` — clear entire customers cache |
| `createOrder()` | Evict all orders cache entries |

TTL: 300 seconds when using Redis (`orderms.cache.redis.ttl-seconds`).

---

## Event publishing (SQS)

Triggered only from `OrderService` (not customers or order items).

```
OrderService.createOrder / updateOrderStatus / deleteOrder
        │
        ▼
publishOrderEvent(eventType, order)
        │
        ▼
new OrderEvent(...)  ──▶  OrderEventPublisher.publishEvent()
                                │
                                ▼
                         ObjectMapper → JSON
                                │
                                ▼
                         SqsClient.sendMessage()
                                │
                                ▼
                         orderms-events.fifo (AWS)
```

If `aws.sqs.queue-url` is empty, publishing is skipped (logged warning) — the HTTP operation still succeeds.

FIFO fields:
- `messageGroupId`: `order-customer-{customerId}` — preserves order per customer
- `messageDeduplicationId`: `eventId` — prevents duplicate events

---

## Database model

```
┌─────────────────┐         ┌─────────────────┐
│    customers    │         │     orders      │
├─────────────────┤         ├─────────────────┤
│ customer_id PK  │◀──┐     │ order_id PK     │
│ first_name      │   │     │ order_number UK │
│ last_name       │   │     │ customer_id     │── (logical FK, no JPA relation)
│ email UK        │   │     │ order_date      │
│ phone_number    │   │     │ total_amount    │
│ address...      │   │     │ status          │
│ created_at      │   │     │ shipping_address│
│ updated_at      │   │     │ created_at      │
└─────────────────┘   │     │ updated_at      │
                      │     └────────┬────────┘
                      │              │ 1
                      │              │
                      │              │ *
                      │     ┌────────▼────────┐
                      │     │   order_items   │
                      │     ├─────────────────┤
                      │     │ order_item_id PK│
                      │     │ order_id FK     │── @ManyToOne Order
                      │     │ product_id      │
                      │     │ product_name    │
                      │     │ quantity        │
                      │     │ unit_price      │
                      │     │ total_price     │
                      │     │ created_at      │
                      └─────┴─────────────────┘
```

---

## REST API summary

Base URL: `http://localhost:8020/api` (or EC2 host).  
All protected endpoints require: `Authorization: Bearer <Cognito access token>`.

### Customers

| Method | Endpoint |
|--------|----------|
| POST | `/customers` |
| GET | `/customers` |
| GET | `/customers/{id}` |
| GET | `/customers/email/{email}` |
| PUT | `/customers/{id}` |
| DELETE | `/customers/{id}` |

### Orders

| Method | Endpoint |
|--------|----------|
| POST | `/orders` |
| GET | `/orders` |
| GET | `/orders/{id}` |
| GET | `/orders/number/{orderNumber}` |
| GET | `/orders/customer/{customerId}` |
| GET | `/orders/search/date-range?startDate=&endDate=` |
| PUT | `/orders/{id}/status?status=CONFIRMED` |
| DELETE | `/orders/{id}` |

### Order items

| Method | Endpoint |
|--------|----------|
| POST | `/orders/{orderId}/items` |
| GET | `/orders/{orderId}/items` |
| GET | `/orders/{orderId}/items/{itemId}` |
| PUT | `/orders/{orderId}/items/{itemId}` |
| DELETE | `/orders/{orderId}/items/{itemId}` |

### Utility endpoints

| Endpoint | Auth | Purpose |
|----------|------|---------|
| `GET /actuator/health` | Public | Health check |
| `GET /swagger-ui.html` | Public | API documentation UI |
| `GET /api-docs` | Public | OpenAPI JSON |

---

## Local development and testing

### Run locally

```bash
# Set database (or use H2 via test profile)
export DATABASE_HOST=localhost
export DATABASE_USERNAME=postgres
export DATABASE_PASSWORD=<your-password>
export COGNITO_ISSUER_URI=https://cognito-idp.<region>.amazonaws.com/<pool-id>

mvn spring-boot:run
```

Without Redis, `SimpleCacheConfig` provides in-memory caching automatically.

### Test configuration

`src/test/resources/application.yml`:
- Uses **H2 in-memory** database (`create-drop`)
- Disables Secrets Manager import
- Sets `cache.type: simple`
- Empty SQS queue URL
- Fixed Cognito issuer for security tests

### Run tests

```bash
mvn test
```

Key test classes: `SecurityIntegrationTest`, `ScopeSecurityIntegrationTest`.

### Build JAR for EC2

```bash
mvn clean package -DskipTests
java -jar target/orderms-1.0.0.jar
```

---

## Quick reference — annotation → file map

| Annotation | Example location |
|------------|------------------|
| `@SpringBootApplication` | `OrderMicroserviceApplication` |
| `@EnableCaching` | `OrderMicroserviceApplication` |
| `@RestController` | All controllers |
| `@Service` | All services, `OrderEventPublisher`, `CognitoGroupService` |
| `@Repository` | All repositories |
| `@Entity` | `Customer`, `Order`, `OrderItem` |
| `@Configuration` | All `config/*` classes |
| `@EnableWebSecurity` | `SecurityConfig` |
| `@Cacheable` / `@CacheEvict` | `OrderService`, `CustomerService` |
| `@Transactional` | All services, `DataSeeder` |
| `@RestControllerAdvice` | `GlobalExceptionHandler` |

---

*Developer documentation — OrderMS v1.0.0 — last updated 2026-06-24*
