package com.example.order;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record Order(
        UUID id,
        String customerId,
        String sku,
        BigDecimal amount,
        OrderStatus status,
        UUID paymentId,
        OffsetDateTime createdAt) {
}
