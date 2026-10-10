package com.example.order;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository repository;
    private final PaymentClient paymentClient;

    public OrderService(OrderRepository repository, PaymentClient paymentClient) {
        this.repository = repository;
        this.paymentClient = paymentClient;
    }

    public Order place(CreateOrderRequest request) {
        UUID orderId = UUID.randomUUID();
        repository.insertPending(orderId, request.customerId(), request.sku(), request.amount());
        log.info("order pending orderId={} sku={} amount={}", orderId, request.sku(), request.amount());
        try {
            PaymentResponse payment = paymentClient.charge(orderId, request.amount());
            repository.markPaid(orderId, payment.id());
            log.info("order paid orderId={} paymentId={}", orderId, payment.id());
        } catch (PaymentFailedException ex) {
            repository.markFailed(orderId, ex.paymentId());
            log.warn("order failed orderId={} paymentId={} reason={}", orderId, ex.paymentId(), ex.getMessage());
        }
        return repository.get(orderId);
    }

    public Order get(UUID id) {
        return repository.get(id);
    }
}
