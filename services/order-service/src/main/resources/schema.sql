CREATE TABLE IF NOT EXISTS orders (
    id UUID PRIMARY KEY,
    customer_id VARCHAR(64) NOT NULL,
    sku VARCHAR(64) NOT NULL,
    amount NUMERIC(12, 2) NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('PENDING', 'PAID', 'FAILED')),
    payment_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
