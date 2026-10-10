CREATE TABLE IF NOT EXISTS payments (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL,
    amount NUMERIC(12, 2) NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('APPROVED', 'DECLINED')),
    delay_ms INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
