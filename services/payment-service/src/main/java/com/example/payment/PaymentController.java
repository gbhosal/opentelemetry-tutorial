package com.example.payment;

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
@RequestMapping("/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    public ResponseEntity<Payment> create(@Valid @RequestBody CreatePaymentRequest request) {
        Payment payment = paymentService.authorize(request);
        // 5xx marks the server span as an error. The Java agent does not do that for 4xx.
        if (payment.status() == PaymentStatus.DECLINED) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(payment);
        }
        return ResponseEntity.ok(payment);
    }

    @GetMapping("/{id}")
    public Payment get(@PathVariable UUID id) {
        return paymentService.get(id);
    }
}
