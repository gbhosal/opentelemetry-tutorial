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
        log.trace("placing order customerId={} sku={} amount={}",
                request.customerId(), request.sku(), request.amount());
        UUID orderId = UUID.randomUUID();
        repository.insertPending(orderId, request.customerId(), request.sku(), request.amount());
        log.trace("inserted pending order orderId={}", orderId);
        log.info("order pending orderId={} sku={} amount={}", orderId, request.sku(), request.amount());
        try {
            log.trace("charging payment orderId={} amount={}", orderId, request.amount());
            PaymentResponse payment = paymentClient.charge(orderId, request.amount());
            log.trace("payment charge succeeded orderId={} paymentId={} status={}",
                    orderId, payment.id(), payment.status());
            repository.markPaid(orderId, payment.id());
            log.trace("marked order paid orderId={} paymentId={}", orderId, payment.id());
            log.info("order paid orderId={} paymentId={}", orderId, payment.id());
        } catch (PaymentFailedException ex) {
            log.trace("payment charge failed orderId={} paymentId={} reason={}",
                    orderId, ex.paymentId(), ex.getMessage());
            repository.markFailed(orderId, ex.paymentId());
            log.trace("marked order failed orderId={} paymentId={}", orderId, ex.paymentId());
            log.warn("order failed orderId={} paymentId={} reason={}", orderId, ex.paymentId(), ex.getMessage());
        }
        Order order = repository.get(orderId);
        log.trace("returning order orderId={} status={} paymentId={}", order.id(), order.status(), order.paymentId());
        return order;
    }

    public Order get(UUID id) {
        log.trace("loading order orderId={}", id);
        Order order = repository.get(id);
        log.trace("loaded order orderId={} status={} paymentId={}", order.id(), order.status(), order.paymentId());
        return order;
    }
}
