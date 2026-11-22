-- Order-to-Audit schema (portable across MySQL 8 and H2 in MySQL mode).
-- Quantities: DECIMAL(14,3). Money: DECIMAL(14,2).
-- Stock is NEVER stored as an editable number: it is the SUM of stock_movements.

-- ---------- Access control ----------
CREATE TABLE roles (
    id   BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(30) NOT NULL UNIQUE
);

CREATE TABLE users (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    username      VARCHAR(50)  NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    full_name     VARCHAR(100) NOT NULL,
    email         VARCHAR(120),
    role_id       BIGINT       NOT NULL,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_users_role FOREIGN KEY (role_id) REFERENCES roles (id)
);

-- ---------- Master data ----------
CREATE TABLE customers (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    name       VARCHAR(120) NOT NULL,
    gstin      VARCHAR(15),
    phone      VARCHAR(20),
    address    VARCHAR(255),
    state_code VARCHAR(2)   NOT NULL DEFAULT '33',  -- decides CGST+SGST vs IGST
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE suppliers (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    name       VARCHAR(120) NOT NULL,
    gstin      VARCHAR(15),
    phone      VARCHAR(20),
    address    VARCHAR(255),
    active     BOOLEAN   NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Raw materials and packaging (category RAW | PACKAGING)
CREATE TABLE raw_materials (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    name          VARCHAR(100)  NOT NULL UNIQUE,
    category      VARCHAR(20)   NOT NULL,
    unit          VARCHAR(10)   NOT NULL,           -- kg, litre, piece
    reorder_level DECIMAL(14,3) NOT NULL DEFAULT 0,
    active        BOOLEAN       NOT NULL DEFAULT TRUE
);

-- A BOM is a reusable recipe. batch_output_kg is the weight of finished snack
-- one batch yields, so ANY pack size can share it:
--   expected packets = batch_output_kg * 1000 / products.pack_size_g
CREATE TABLE bom (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(100)  NOT NULL UNIQUE,
    batch_output_kg DECIMAL(14,3) NOT NULL,
    active          BOOLEAN       NOT NULL DEFAULT TRUE
);

-- basis PER_BATCH: qty used per batch (flour, oil, spice)
-- basis PER_PACKET: qty used per finished packet (pouch, carton share)
CREATE TABLE bom_items (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    bom_id        BIGINT        NOT NULL,
    material_id   BIGINT        NOT NULL,
    quantity      DECIMAL(14,4) NOT NULL,
    basis         VARCHAR(12)   NOT NULL DEFAULT 'PER_BATCH',
    tolerance_pct DECIMAL(5,2)  NOT NULL DEFAULT 3.00,
    CONSTRAINT fk_bomitems_bom      FOREIGN KEY (bom_id)      REFERENCES bom (id),
    CONSTRAINT fk_bomitems_material FOREIGN KEY (material_id) REFERENCES raw_materials (id),
    CONSTRAINT uq_bom_material UNIQUE (bom_id, material_id)
);

CREATE TABLE products (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    name         VARCHAR(120)  NOT NULL,
    sku          VARCHAR(40)   NOT NULL UNIQUE,
    pack_size_g  INT           NOT NULL,
    unit_price   DECIMAL(14,2) NOT NULL,
    hsn_code     VARCHAR(8)    NOT NULL,
    gst_rate_pct DECIMAL(5,2)  NOT NULL,            -- configurable, never hardcoded
    bom_id       BIGINT,
    active       BOOLEAN       NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_products_bom FOREIGN KEY (bom_id) REFERENCES bom (id)
);

-- ---------- Purchasing ----------
CREATE TABLE purchases (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    supplier_id   BIGINT        NOT NULL,
    invoice_no    VARCHAR(40),
    purchase_date DATE          NOT NULL,
    total_amount  DECIMAL(14,2) NOT NULL DEFAULT 0,
    created_by    BIGINT        NOT NULL,
    created_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_purchases_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers (id),
    CONSTRAINT fk_purchases_user     FOREIGN KEY (created_by)  REFERENCES users (id)
);

CREATE TABLE purchase_items (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    purchase_id BIGINT        NOT NULL,
    material_id BIGINT        NOT NULL,
    quantity    DECIMAL(14,3) NOT NULL,
    unit_price  DECIMAL(14,2) NOT NULL,
    line_total  DECIMAL(14,2) NOT NULL,
    CONSTRAINT fk_pitems_purchase FOREIGN KEY (purchase_id) REFERENCES purchases (id),
    CONSTRAINT fk_pitems_material FOREIGN KEY (material_id) REFERENCES raw_materials (id)
);

-- ---------- Production ----------
CREATE TABLE production_runs (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id      BIGINT        NOT NULL,
    bom_id          BIGINT        NOT NULL,
    batches         DECIMAL(8,2)  NOT NULL DEFAULT 1,
    run_date        DATE          NOT NULL,
    expected_output INT           NOT NULL,
    actual_output   INT           NOT NULL,
    good_output     INT           NOT NULL,
    damaged_units   INT           NOT NULL DEFAULT 0,
    yield_variance_pct DECIMAL(7,2),
    yield_flagged   BOOLEAN       NOT NULL DEFAULT FALSE,
    labour_cost     DECIMAL(14,2) NOT NULL DEFAULT 0,
    utilities_cost  DECIMAL(14,2) NOT NULL DEFAULT 0,
    material_cost   DECIMAL(14,2) NOT NULL DEFAULT 0,
    cost_per_packet DECIMAL(14,4),
    supervisor_id   BIGINT        NOT NULL,
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_runs_product    FOREIGN KEY (product_id)    REFERENCES products (id),
    CONSTRAINT fk_runs_bom        FOREIGN KEY (bom_id)        REFERENCES bom (id),
    CONSTRAINT fk_runs_supervisor FOREIGN KEY (supervisor_id) REFERENCES users (id)
);

CREATE TABLE production_run_materials (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    run_id       BIGINT        NOT NULL,
    material_id  BIGINT        NOT NULL,
    standard_qty DECIMAL(14,3) NOT NULL,
    actual_qty   DECIMAL(14,3) NOT NULL,
    variance_pct DECIMAL(7,2)  NOT NULL,
    flagged      BOOLEAN       NOT NULL DEFAULT FALSE,
    unit_cost    DECIMAL(14,4) NOT NULL DEFAULT 0,
    CONSTRAINT fk_prm_run      FOREIGN KEY (run_id)      REFERENCES production_runs (id),
    CONSTRAINT fk_prm_material FOREIGN KEY (material_id) REFERENCES raw_materials (id)
);

-- ---------- Sales ----------
CREATE TABLE orders (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id BIGINT      NOT NULL,
    salesman_id BIGINT      NOT NULL,
    order_date  DATE        NOT NULL,
    status      VARCHAR(20) NOT NULL DEFAULT 'SUBMITTED',  -- SUBMITTED|APPROVED|REJECTED|INVOICED|DISPATCHED
    approved_by BIGINT,
    approved_at TIMESTAMP NULL,
    notes       VARCHAR(255),
    created_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_orders_customer FOREIGN KEY (customer_id) REFERENCES customers (id),
    CONSTRAINT fk_orders_salesman FOREIGN KEY (salesman_id) REFERENCES users (id),
    CONSTRAINT fk_orders_approver FOREIGN KEY (approved_by) REFERENCES users (id)
);

CREATE TABLE order_items (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id   BIGINT        NOT NULL,
    product_id BIGINT        NOT NULL,
    quantity   INT           NOT NULL,
    unit_price DECIMAL(14,2) NOT NULL,
    CONSTRAINT fk_oitems_order   FOREIGN KEY (order_id)   REFERENCES orders (id),
    CONSTRAINT fk_oitems_product FOREIGN KEY (product_id) REFERENCES products (id)
);

CREATE TABLE invoices (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id      BIGINT        NOT NULL UNIQUE,
    invoice_no    VARCHAR(30)   NOT NULL UNIQUE,
    invoice_date  DATE          NOT NULL,
    subtotal      DECIMAL(14,2) NOT NULL,
    cgst          DECIMAL(14,2) NOT NULL DEFAULT 0,
    sgst          DECIMAL(14,2) NOT NULL DEFAULT 0,
    igst          DECIMAL(14,2) NOT NULL DEFAULT 0,
    total         DECIMAL(14,2) NOT NULL,
    status        VARCHAR(20)   NOT NULL DEFAULT 'ISSUED',  -- ISSUED|DISPATCHED|CANCELLED
    dispatched_at TIMESTAMP NULL,
    created_by    BIGINT        NOT NULL,
    created_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_invoices_order FOREIGN KEY (order_id)   REFERENCES orders (id),
    CONSTRAINT fk_invoices_user  FOREIGN KEY (created_by) REFERENCES users (id)
);

CREATE TABLE invoice_items (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    invoice_id    BIGINT        NOT NULL,
    product_id    BIGINT        NOT NULL,
    hsn_code      VARCHAR(8)    NOT NULL,
    quantity      INT           NOT NULL,
    unit_price    DECIMAL(14,2) NOT NULL,
    gst_rate_pct  DECIMAL(5,2)  NOT NULL,
    taxable_value DECIMAL(14,2) NOT NULL,
    cgst          DECIMAL(14,2) NOT NULL DEFAULT 0,
    sgst          DECIMAL(14,2) NOT NULL DEFAULT 0,
    igst          DECIMAL(14,2) NOT NULL DEFAULT 0,
    CONSTRAINT fk_iitems_invoice FOREIGN KEY (invoice_id) REFERENCES invoices (id),
    CONSTRAINT fk_iitems_product FOREIGN KEY (product_id) REFERENCES products (id)
);

CREATE TABLE payments (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    invoice_id   BIGINT        NOT NULL,
    customer_id  BIGINT        NOT NULL,
    amount       DECIMAL(14,2) NOT NULL,
    payment_date DATE          NOT NULL,
    mode         VARCHAR(20)   NOT NULL DEFAULT 'CASH',
    reference    VARCHAR(60),
    recorded_by  BIGINT        NOT NULL,
    created_at   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_payments_invoice  FOREIGN KEY (invoice_id)  REFERENCES invoices (id),
    CONSTRAINT fk_payments_customer FOREIGN KEY (customer_id) REFERENCES customers (id),
    CONSTRAINT fk_payments_user     FOREIGN KEY (recorded_by) REFERENCES users (id)
);

-- ---------- Inventory ledger ----------
-- item_type: RAW_MATERIAL (raw_materials.id) | PRODUCT (products.id, finished goods)
-- quantity is SIGNED: + receipts, - issues. Stock = SUM(quantity).
-- movement_type: OPENING | PURCHASE | PRODUCTION_ISSUE | PRODUCTION_OUTPUT |
--                DISPATCH | ADJUSTMENT | WASTAGE
CREATE TABLE stock_movements (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    item_type      VARCHAR(20)   NOT NULL,
    item_id        BIGINT        NOT NULL,
    movement_type  VARCHAR(20)   NOT NULL,
    quantity       DECIMAL(14,3) NOT NULL,
    unit_cost      DECIMAL(14,4),
    reference_type VARCHAR(30),
    reference_id   BIGINT,
    notes          VARCHAR(255),
    created_by     BIGINT        NOT NULL,
    created_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_stock_user FOREIGN KEY (created_by) REFERENCES users (id)
);
CREATE INDEX idx_stock_item ON stock_movements (item_type, item_id);

-- ---------- Audit trail ----------
CREATE TABLE audit_logs (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT,
    action      VARCHAR(40)  NOT NULL,
    entity_type VARCHAR(40)  NOT NULL,
    entity_id   BIGINT,
    details     VARCHAR(1000),
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_audit_entity ON audit_logs (entity_type, entity_id);
