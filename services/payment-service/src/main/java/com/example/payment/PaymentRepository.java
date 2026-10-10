package com.example.payment;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Repository
public class PaymentRepository {

    private final JdbcTemplate jdbc;

    public PaymentRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Payment insert(UUID id, UUID orderId, BigDecimal amount, PaymentStatus status, int delayMs) {
        return jdbc.queryForObject("""
                INSERT INTO payments (id, order_id, amount, status, delay_ms)
                VALUES (?, ?, ?, ?, ?)
                RETURNING id, order_id, amount, status, delay_ms, created_at
                """, (rs, rowNum) -> new Payment(
                rs.getObject("id", UUID.class),
                rs.getObject("order_id", UUID.class),
                rs.getBigDecimal("amount"),
                PaymentStatus.valueOf(rs.getString("status")),
                rs.getInt("delay_ms"),
                rs.getObject("created_at", OffsetDateTime.class)), id, orderId, amount, status.name(), delayMs);
    }

    public Payment get(UUID id) {
        return jdbc.query("""
                SELECT id, order_id, amount, status, delay_ms, created_at
                FROM payments
                WHERE id = ?
                """, (rs, rowNum) -> new Payment(
                rs.getObject("id", UUID.class),
                rs.getObject("order_id", UUID.class),
                rs.getBigDecimal("amount"),
                PaymentStatus.valueOf(rs.getString("status")),
                rs.getInt("delay_ms"),
                rs.getObject("created_at", OffsetDateTime.class)), id)
                .stream()
                .findFirst()
                .orElseThrow(() -> new PaymentNotFoundException(id));
    }
}
