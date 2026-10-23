CREATE TABLE settlement_results (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    order_amount BIGINT NOT NULL,
    fee_amount BIGINT NOT NULL,
    payout_amount BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_settlement_order UNIQUE (order_id),
    CONSTRAINT fk_settlement_order FOREIGN KEY (order_id) REFERENCES purchase_orders(id),
    CONSTRAINT ck_settlement_amount CHECK (order_amount > 0),
    CONSTRAINT ck_settlement_fee CHECK (fee_amount >= 0 AND fee_amount <= order_amount),
    CONSTRAINT ck_settlement_payout CHECK (payout_amount = order_amount - fee_amount)
);
