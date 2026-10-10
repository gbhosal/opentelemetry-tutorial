package com.example.payment;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record Payment(
        UUID id,
        UUID orderId,
        BigDecimal amount,
        PaymentStatus status,
        int delayMs,
        OffsetDateTime createdAt) {
}
