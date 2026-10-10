package com.example.payment;

import java.util.Random;

public class PaymentDecider {

    private static final int DECLINE_PERCENT = 10;
    private static final int SLOW_PERCENT = 20;

    private final Random random;

    public PaymentDecider(Random random) {
        this.random = random;
    }

    public Decision decide() {
        int roll = random.nextInt(100);
        if (roll < DECLINE_PERCENT) {
            return new Decision(PaymentStatus.DECLINED, fastDelay());
        }
        if (roll < DECLINE_PERCENT + SLOW_PERCENT) {
            return new Decision(PaymentStatus.APPROVED, 500 + random.nextInt(1501));
        }
        return new Decision(PaymentStatus.APPROVED, fastDelay());
    }

    private int fastDelay() {
        return 20 + random.nextInt(81);
    }

    public record Decision(PaymentStatus status, int delayMs) {
    }
}
