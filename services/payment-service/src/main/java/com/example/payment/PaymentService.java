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
        log.trace("authorizing payment orderId={} amount={}", request.orderId(), request.amount());
        PaymentDecider.Decision decision = decider.decide();
        log.trace("payment decision orderId={} status={} delayMs={}",
                request.orderId(), decision.status(), decision.delayMs());
        sleep(decision.delayMs());
        UUID paymentId = UUID.randomUUID();
        log.trace("inserting payment orderId={} paymentId={} status={}",
                request.orderId(), paymentId, decision.status());
        Payment payment = repository.insert(
                paymentId,
                request.orderId(),
                request.amount(),
                decision.status(),
                decision.delayMs());
        if (decision.status() == PaymentStatus.DECLINED) {
            log.trace("payment stored as declined orderId={} paymentId={} delayMs={}",
                    request.orderId(), payment.id(), decision.delayMs());
            log.info("payment declined orderId={} paymentId={} delayMs={}",
                    request.orderId(), payment.id(), decision.delayMs());
        } else {
            log.trace("payment stored as approved orderId={} paymentId={} delayMs={}",
                    request.orderId(), payment.id(), decision.delayMs());
            log.info("payment approved orderId={} paymentId={} delayMs={}",
                    request.orderId(), payment.id(), decision.delayMs());
        }
        return payment;
    }

    public Payment get(UUID id) {
        log.trace("loading payment paymentId={}", id);
        Payment payment = repository.get(id);
        log.trace("loaded payment paymentId={} orderId={} status={}", payment.id(), payment.orderId(), payment.status());
        return payment;
    }

    private static void sleep(int delayMs) {
        log.trace("waiting before payment insert delayMs={}", delayMs);
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.trace("payment wait interrupted delayMs={}", delayMs, ex);
            throw new IllegalStateException("payment authorization interrupted", ex);
        }
        log.trace("payment wait finished delayMs={}", delayMs);
    }
}
