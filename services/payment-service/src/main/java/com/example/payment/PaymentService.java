package com.example.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository repository;
    private final PaymentDecider decider;

    public PaymentService(PaymentRepository repository, PaymentDecider decider) {
        this.repository = repository;
        this.decider = decider;
    }

    public Payment authorize(CreatePaymentRequest request) {
        PaymentDecider.Decision decision = decider.decide();
        sleep(decision.delayMs());
        Payment payment = repository.insert(
                UUID.randomUUID(),
                request.orderId(),
                request.amount(),
                decision.status(),
                decision.delayMs());
        if (decision.status() == PaymentStatus.DECLINED) {
            log.info("payment declined orderId={} paymentId={} delayMs={}",
                    request.orderId(), payment.id(), decision.delayMs());
        } else {
            log.info("payment approved orderId={} paymentId={} delayMs={}",
                    request.orderId(), payment.id(), decision.delayMs());
        }
        return payment;
    }

    public Payment get(UUID id) {
        return repository.get(id);
    }

    private static void sleep(int delayMs) {
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("payment authorization interrupted", ex);
        }
    }
}
