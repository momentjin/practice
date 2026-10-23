CREATE TABLE merchants (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(40) NOT NULL,
    name VARCHAR(100) NOT NULL,
    CONSTRAINT uk_merchant_code UNIQUE (code)
);

CREATE TABLE purchase_orders (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_number VARCHAR(80) NOT NULL,
    merchant_id BIGINT NOT NULL,
    amount BIGINT NOT NULL,
    channel VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    ordered_at DATETIME(6) NOT NULL,
    paid_at DATETIME(6),
    cancelled_at DATETIME(6),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_order_number UNIQUE (order_number),
    CONSTRAINT fk_order_merchant FOREIGN KEY (merchant_id) REFERENCES merchants(id),
    CONSTRAINT ck_order_amount CHECK (amount > 0),
    CONSTRAINT ck_order_channel CHECK (channel IN ('APP', 'WEB', 'OFFLINE')),
    CONSTRAINT ck_order_status CHECK (status IN ('CREATED', 'PAID', 'CANCELLED')),
    CONSTRAINT ck_order_timestamps CHECK (
        (status = 'CREATED' AND paid_at IS NULL AND cancelled_at IS NULL) OR
        (status = 'PAID' AND paid_at IS NOT NULL AND paid_at >= ordered_at AND cancelled_at IS NULL) OR
        (status = 'CANCELLED' AND paid_at IS NOT NULL AND paid_at >= ordered_at AND cancelled_at >= paid_at AND cancelled_at IS NOT NULL)
    )
);
CREATE INDEX idx_order_status_paid_id ON purchase_orders(status, paid_at, id);
CREATE INDEX idx_order_merchant_ordered ON purchase_orders(merchant_id, ordered_at);

CREATE TABLE payment_ledgers (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    entry_type VARCHAR(20) NOT NULL,
    amount BIGINT NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_ledger_order_type UNIQUE (order_id, entry_type),
    CONSTRAINT fk_ledger_order FOREIGN KEY (order_id) REFERENCES purchase_orders(id),
    CONSTRAINT ck_ledger_amount CHECK (amount > 0),
    CONSTRAINT ck_ledger_type CHECK (entry_type IN ('PAYMENT', 'CANCELLATION'))
);
CREATE INDEX idx_ledger_occurred_id ON payment_ledgers(occurred_at, id);
