# Order-to-Audit System (small food manufacturer)

A mini ERP that connects the whole flow: **purchase → BOM-based production → stock ledger → sales order → GST invoice → dispatch → payment → audit reports.**

**Stack:** Java 21, Spring Boot 3.3, Spring Security + JWT, Spring Data JPA, Flyway, MySQL 8 (H2 for no-install runs), React 18 + Vite, OpenPDF for invoices.

## Prerequisites

| Tool | Version | Needed for |
|---|---|---|
| JDK | 21 | backend |
| Maven | 3.9+ | backend build and tests |
| Node.js | 20+ | frontend |
| MySQL | 8 (or Docker) | only for the default profile; the `dev` profile needs no database |
| Postman | any recent | running the API benchmark collection |

## Database setup

- **Quick run (no install):** the `dev` profile uses an in-memory H2 database in MySQL mode. Data is reset on every restart.
- **MySQL:** start one with `docker compose up -d mysql` (database `order_to_audit`, user `root`, password `root`), or use your own server and set `DB_URL`, `DB_USER`, `DB_PASSWORD`.
- **Schema:** Flyway creates every table on first start from `backend/src/main/resources/db/migration/V1__schema.sql`. Nothing to run by hand.

## Run

**Backend** (API on http://localhost:8080)
```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev     # H2, no database install
mvn spring-boot:run                                    # MySQL (see Database setup)
```
PowerShell: put the option in quotes, `mvn spring-boot:run "-Dspring-boot.run.profiles=dev"`.

**Frontend** (UI on http://localhost:5173, proxies `/api` to port 8080)
```bash
cd frontend
npm install
npm run dev
```

**Whole stack in containers:** `docker compose up --build`, then open http://localhost:8081. After changing backend code, rebuild with `--build`.

**Tests:** `cd backend && mvn test` runs the integration tests, including `BenchmarkScenarioTest`.

## Demo logins (password `Passw0rd!`)

| Username | Role | Can do |
|---|---|---|
| admin | ADMIN | master data, purchases, approve orders, invoices, dispatch, payments, stock corrections |
| salesman | SALESMAN | place orders, see own orders |
| supervisor | PRODUCTION_SUPERVISOR | record production, view stock |
| accountant | ACCOUNTANT | read-only reports, invoice PDFs, audit trail |
| owner | OWNER | dashboard, stock, reports (read-only) |

For anything real: `APP_SEED_DEMO_USERS=false` and `APP_JWT_SECRET=<32+ random characters>`. Seller name, GSTIN, address and state code for invoices are in `application.properties` (`app.company.*`).

## Verifying the benchmark

1. **API (Postman):** import `docs/postman_collection.json`, start the backend on a fresh `dev` database, and run the whole collection from the top. Every check should pass.
2. **UI:** follow `docs/DEMO_GUIDE.md`, which walks the same scenario through the screens and lists the numbers to expect.

| Step | Result |
|---|---|
| Flour 100 kg @ ₹42 + 60 kg @ ₹45 − 40 kg used | 120 kg |
| 188 good packets of 200 expected | yield −6.00% (flagged) |
| Oil 30 L − 10.5 L used | 19.5 L closing; +5.00% variance (flagged) |
| Materials 4,055 + labour 1,200 + power 600 over 188 good packets | ₹31.14 per packet (weighted-average costing) |
| 150 × ₹50 + 2.5% CGST + 2.5% SGST | ₹7,875 invoice |
| Finished stock: 50 opening + 188 produced − 150 dispatched | 88 packets closing |
| Pouches: 500 − 194 used; cartons: 30 − 9 used | 306 pouches; 21 cartons closing |
| Distributor B orders 120 after Retailer A dispatch | 88 available; 32 short; approval blocked |
| Pay ₹4,000 | outstanding ₹3,875 |

## API overview

| Area | Endpoints | Writers |
|---|---|---|
| Auth | `POST /api/auth/login`, `GET /api/auth/me` | all |
| Master data | `/api/customers`, `/api/suppliers`, `/api/products`, `/api/raw-materials`, `/api/boms` (+ `/{id}/plan`) | admin |
| Purchases | `/api/purchases` | admin |
| Inventory | `GET /api/inventory/stock`, `GET /api/inventory/ledger`, `POST /api/inventory/adjustments` | admin |
| Production | `/api/production` | admin, supervisor |
| Orders | `/api/orders`, `/{id}` (admin edit while submitted), `/{id}/approve`, `/{id}/reject`, `/{id}/invoice` | salesman/admin create; admin edits and approves |
| Invoices | `/api/invoices`, `/{id}/dispatch`, `/{id}/pdf` | dispatch: admin; read and PDF: admin, accountant, owner |
| Payments | `/api/payments` | write: admin; read: admin, accountant, owner |
| Reports | `/api/reports/{dashboard,outstanding,gst,sales,purchases,production,profitability}` (`?format=csv`) | read: admin, accountant, owner |
| Audit | `/api/audit` | read: admin, accountant, owner |

Errors are JSON `{"error": "..."}`: 400 invalid input, 401 not signed in (missing or invalid token), 403 signed in but wrong role, 404 not found, 409 business rule broken (insufficient stock, wrong status, overpayment, duplicate).

## Layout

```
backend/   Spring Boot API (packages: user, security, auth, audit, customer, supplier, product,
           inventory, bom, purchase, production, sales, payment, report, common)
frontend/  React UI: one page per module under src/pages
docs/      DESIGN_NOTE.md / .pdf (architecture, ER diagram, calculations),
           er-diagram.png, postman_collection.json
```

## Design decisions (details in `docs/DESIGN_NOTE.md`)

1. Stock is a ledger: `stock_movements` holds signed quantities and stock is their sum. Only `StockService` writes it.
2. A BOM stores batch output in kg, so one recipe serves every pack size.
3. GST rate and HSN are stored on the product and copied onto each invoice line. CGST+SGST within the seller's state, IGST outside it; invoice numbers use Indian financial-year format.
4. Production, purchase, dispatch and the matching stock entries each commit in one transaction.
5. Roles are enforced per endpoint with `@PreAuthorize`; every change writes an audit row. An authenticated user with the wrong role gets 403; only a missing or invalid token gets 401.

## Known limits

No returns or credit notes, e-invoicing, or e-way bill. Work-in-progress is not a separate stage. No row locking, so two simultaneous dispatches of the last stock could both pass the check. Verify GST rates and HSN codes before real use.
