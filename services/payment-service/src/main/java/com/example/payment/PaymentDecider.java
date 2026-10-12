package com.example.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;

public class PaymentDecider {

    private static final Logger log = LoggerFactory.getLogger(PaymentDecider.class);

    private static final int DECLINE_PERCENT = 10;
    private static final int SLOW_PERCENT = 20;

    private final Random random;

    public PaymentDecider(Random random) {
        this.random = random;
    }

    public Decision decide() {
        int roll = random.nextInt(100);
        Decision decision;
        if (roll < DECLINE_PERCENT) {
            decision = new Decision(PaymentStatus.DECLINED, fastDelay());
            log.trace("payment roll declined roll={} delayMs={}", roll, decision.delayMs());
        } else if (roll < DECLINE_PERCENT + SLOW_PERCENT) {
            decision = new Decision(PaymentStatus.APPROVED, 500 + random.nextInt(1501));
            log.trace("payment roll slow approval roll={} delayMs={}", roll, decision.delayMs());
        } else {
            decision = new Decision(PaymentStatus.APPROVED, fastDelay());
            log.trace("payment roll fast approval roll={} delayMs={}", roll, decision.delayMs());
        }
        return decision;
    }

    private int fastDelay() {
        return 20 + random.nextInt(81);
    }

    public record Decision(PaymentStatus status, int delayMs) {
    }
}
