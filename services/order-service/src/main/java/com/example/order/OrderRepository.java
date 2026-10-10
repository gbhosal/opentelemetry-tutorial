package com.example.order;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Repository
public class OrderRepository {

    private final JdbcTemplate jdbc;

    public OrderRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insertPending(UUID id, String customerId, String sku, BigDecimal amount) {
        jdbc.update("""
                INSERT INTO orders (id, customer_id, sku, amount, status)
                VALUES (?, ?, ?, ?, 'PENDING')
                """, id, customerId, sku, amount);
    }

    public void markPaid(UUID id, UUID paymentId) {
        updateStatus(id, OrderStatus.PAID, paymentId);
    }

    public void markFailed(UUID id, UUID paymentId) {
        updateStatus(id, OrderStatus.FAILED, paymentId);
    }

    public Order get(UUID id) {
        return jdbc.query("""
                SELECT id, customer_id, sku, amount, status, payment_id, created_at
                FROM orders
                WHERE id = ?
                """, (rs, rowNum) -> new Order(
                rs.getObject("id", UUID.class),
                rs.getString("customer_id"),
                rs.getString("sku"),
                rs.getBigDecimal("amount"),
                OrderStatus.valueOf(rs.getString("status")),
                rs.getObject("payment_id", UUID.class),
                rs.getObject("created_at", OffsetDateTime.class)), id)
                .stream()
                .findFirst()
                .orElseThrow(() -> new OrderNotFoundException(id));
    }

    private void updateStatus(UUID id, OrderStatus status, UUID paymentId) {
        int updated = jdbc.update("""
                UPDATE orders
                SET status = ?, payment_id = ?
                WHERE id = ?
                """, status.name(), paymentId, id);
        if (updated != 1) {
            throw new OrderNotFoundException(id);
        }
    }
}
