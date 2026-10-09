# HealthCart — Enterprise Healthcare & Pharmaceutical Marketplace

HealthCart is a production-grade, enterprise healthcare e-commerce platform built strictly on a **pure Java stack** with **Spring Boot 3.3.4**, **Spring Security 6**, **Spring Data JPA**, and **Thymeleaf SSR**.

The platform is engineered for compliance, clinical accuracy, and high resilience, featuring multi-role access control, prescription verification queues, optimistic locking on inventory, an order state machine, audit trails, and server-side SVG analytics without client-side charting dependencies.

---

## Architecture Overview

```
                                      +-------------------------------+
                                      |  Browser Client / Webhooks    |
                                      +---------------+---------------+
                                                      |
                                             HTTP / HTTPS (CSRF)
                                                      |
                                                      v
                                      +-------------------------------+
                                      |   Spring Security 6 (RBAC)    |
                                      |   - Rate Limiting Filter      |
                                      |   - BCrypt Password Encoder   |
                                      +---------------+---------------+
                                                      |
                   +----------------------------------+----------------------------------+
                   |                                  |                                  |
                   v                                  v                                  v
+-------------------------------+  +-------------------------------+  +-------------------------------+
|      BuyerController          |  |      SellerController         |  |       AdminController         |
|  - Catalog & Product Detail   |  |  - Inventory Management       |  |  - User & Role Administration |
|  - Cart & Wishlist Ops        |  |  - Order Fulfillment Tracking |  |  - Rx Moderation Queue        |
|  - Idempotent Checkout        |  |  - SvgChartService (Pure Java)|  |  - Platform Audit Logs        |
|  - Prescription Upload        |  |  - Product Catalog CRUD       |  |  - Global Order Management    |
+---------------+---------------+  +---------------+---------------+  +---------------+---------------+
                |                                  |                                  |
                +----------------------------------+----------------------------------+
                                                   |
                                                   v
                                   +-------------------------------+
                                   |         Service Layer         |
                                   |  - OrderStateMachine          |
                                   |  - InventoryService (@Version)|
                                   |  - PrescriptionService        |
                                   |  - PaymentReconciliationService
                                   |  - AuditService               |
                                   +---------------+---------------+
                                                   |
                                                   v
                                   +-------------------------------+
                                   |     Spring Data JPA Repos     |
                                   +---------------+---------------+
                                                   |
                                                   v
                                   +-------------------------------+
                                   |  H2 (Dev) / PostgreSQL (Prod) |
                                   +-------------------------------+
```

---

## Key Features

### 1. Multi-Role RBAC (Role-Based Access Control)
- **`ROLE_BUYER`**: Browse catalog, add items to cart/wishlist, track browsing history, checkout with prescription upload, view order history, and simulate payments.
- **`ROLE_SELLER`**: Manage product listings, update stock levels, fulfill seller order items, and view server-side SVG sales charts.
- **`ROLE_ADMIN`**: Global user administration, account toggle/lockout, product catalog moderation, prescription verification queue, and platform-wide audit activity logging.

### 2. Clinical Prescription Verification Workflow
- Products requiring a doctor's prescription (`rxRequired = true`) enforce mandatory prescription upload at checkout.
- Orders remain guarded until an Administrator reviews and approves or rejects the prescription in `/admin/prescriptions`.

### 3. Order State Machine
- Strict order lifecycle transitions managed by `OrderStateMachine`:
  - `PLACED` &rarr; `PAID` / `PAYMENT_FAILED` / `CANCELLED`
  - `PAID` &rarr; `CONFIRMED` / `CANCELLED`
  - `CONFIRMED` &rarr; `PACKED` / `CANCELLED`
  - `PACKED` &rarr; `SHIPPED`
  - `SHIPPED` &rarr; `DELIVERED`
- Any illegal status jump is rejected with validation exceptions and logged to the audit log.

### 4. Concurrency & Inventory Control
- Optimistic locking via JPA `@Version` on `Product` to prevent race conditions during high-concurrency order placement.
- Atomic stock decrements and threshold-based low-stock indicators.

### 5. Server-Side SVG Chart Generation (Pure Java)
- `SvgChartService` generates responsive, styled SVG revenue trend bar charts and top-product performance graphs directly in Java.
- Zero client-side JavaScript graphing libraries required.

### 6. Resilience & Security
- **Idempotent Order Placement**: Deduplication via unique `idempotencyKey` prevents duplicate charges and orders.
- **In-Memory Rate Limiting**: `RateLimitingFilter` enforces sliding-window request throttling against malicious scraping and brute-force attacks.
- **Centralized Error Handling**: Custom error controller handling HTTP `403`, `404`, `429`, and `500` with dedicated templates.
- **Audit Logging**: Structured audit trail tracking critical events (logins, status changes, prescription reviews, and inventory edits).

---

## Pre-Seeded Demo Accounts (Development Profile)

When running under the default `dev` profile, `DataLoader` seeds the database with initial categories, products, and credentials:

| Role | Email | Password | Access Area |
| :--- | :--- | :--- | :--- |
| **Administrator** | `admin@healthcart.com` | `Admin@12345` | `/admin/dashboard` |
| **Seller** | `seller@healthcart.com` | `Seller@12345` | `/seller/dashboard` |
| **Buyer** | `buyer@healthcart.com` | `Buyer@12345` | `/`, `/cart`, `/checkout` |

---

## Getting Started

### Prerequisites
- **JDK**: Java 21 or Java 26+
- **Maven**: Standalone Apache Maven 3.9+ (included in `./apache-maven-3.9.6`)

### Memory Configuration Note (Windows / Low Virtual Memory)
To ensure optimal performance and avoid native heap allocation spikes during compilation or testing on Windows systems, use the lean JVM flags:
```powershell
$env:MAVEN_OPTS = "-Xmx256m -XX:+UseSerialGC -XX:TieredStopAtLevel=1"
```

### Running Automated Tests
Execute the entire test suite (57 tests across 9 test suites):
```powershell
$env:MAVEN_OPTS="-Xmx256m -XX:+UseSerialGC -XX:TieredStopAtLevel=1"
.\apache-maven-3.9.6\bin\mvn.cmd test
```

### Building the Production Executable JAR
```powershell
$env:MAVEN_OPTS="-Xmx256m -XX:+UseSerialGC -XX:TieredStopAtLevel=1"
.\apache-maven-3.9.6\bin\mvn.cmd package -DskipTests
```
The resulting executable JAR will be located at `target/healthcart-1.0.0.jar`.

### Running the Application
Launch the application using Java:
```powershell
java -Xmx256m -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -jar target\healthcart-1.0.0.jar
```
Or directly via Maven:
```powershell
$env:MAVEN_OPTS="-Xmx256m -XX:+UseSerialGC -XX:TieredStopAtLevel=1"
.\apache-maven-3.9.6\bin\mvn.cmd spring-boot:run
```

Once started, navigate to:
- **Storefront / Catalog**: [http://localhost:8080/](http://localhost:8080/)
- **Login**: [http://localhost:8080/login](http://localhost:8080/login)
- **Actuator Health**: [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health)

---

## Production Deployment Configuration

For production deployment with PostgreSQL, activate the `prod` Spring profile:
```powershell
java -jar target\healthcart-1.0.0.jar --spring.profiles.active=prod
```

Configure the following environment variables:
- `SPRING_DATASOURCE_URL`: `jdbc:postgresql://<host>:5432/<db_name>`
- `SPRING_DATASOURCE_USERNAME`: `<db_user>`
- `SPRING_DATASOURCE_PASSWORD`: `<db_password>`
- `APP_ADMIN_BOOTSTRAP_EMAIL`: `<admin_email>`
- `APP_ADMIN_BOOTSTRAP_PASSWORD`: `<admin_password>`
- `APP_STORAGE_DIR`: Path to persistent prescription and asset storage.
- `APP_WEBHOOK_SECRET`: Secret key for Razorpay / Stripe webhook validation.

---

## Test Suite Summary

The automated test suite verifies 57 specifications with zero failures:
1. `CustomErrorControllerTest`: Verification of 403, 404, 429, and 500 error view resolution.
2. `InputValidationMockMvcTest`: Form validation for buyer/seller registration and password reset.
3. `SecurityRoutingMockMvcTest`: Strict RBAC authorization enforcement across `/admin/**`, `/seller/**`, and `/buyer/**`.
4. `OrderStateMachineTest`: Strict state transition graph validations and illegal transition rejections.
5. `InventoryServiceTest`: Concurrent stock deduction and threshold alerts.
6. `PaymentServiceTest`: Idempotent payment processing and webhook reconciliation.
7. `PasswordResetTest`: Secure token generation, expiration, and password updates.
8. `FileStorageServiceTest`: Secure prescription file storage and directory sandboxing.
9. `OwnershipSecurityServiceTest`: Resource-level ownership checks preventing unauthorized access across buyers and sellers.
