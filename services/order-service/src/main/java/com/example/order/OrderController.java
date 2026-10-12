package com.example.order;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<Order> create(@Valid @RequestBody CreateOrderRequest request) {
        log.trace("received order request customerId={} sku={} amount={}",
                request.customerId(), request.sku(), request.amount());
        Order order = orderService.place(request);
        HttpStatus status = order.status() == OrderStatus.PAID ? HttpStatus.OK : HttpStatus.BAD_GATEWAY;
        log.trace("returning order httpStatus={} orderId={} status={} paymentId={}",
                status.value(), order.id(), order.status(), order.paymentId());
        return ResponseEntity.status(status).body(order);
    }

    @GetMapping("/{id}")
    public Order get(@PathVariable UUID id) {
        log.trace("received order lookup orderId={}", id);
        return orderService.get(id);
    }
}
