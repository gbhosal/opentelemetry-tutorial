package com.example.order;

import jakarta.validation.Valid;
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

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<Order> create(@Valid @RequestBody CreateOrderRequest request) {
        Order order = orderService.place(request);
        HttpStatus status = order.status() == OrderStatus.PAID ? HttpStatus.OK : HttpStatus.BAD_GATEWAY;
        return ResponseEntity.status(status).body(order);
    }

    @GetMapping("/{id}")
    public Order get(@PathVariable UUID id) {
        return orderService.get(id);
    }
}
