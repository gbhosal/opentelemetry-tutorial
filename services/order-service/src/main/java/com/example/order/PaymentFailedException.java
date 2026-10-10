package com.example.order;

import java.util.UUID;

public class PaymentFailedException extends RuntimeException {

    private final UUID paymentId;

    public PaymentFailedException(UUID paymentId, int statusCode) {
        super("payment call failed with status " + statusCode);
        this.paymentId = paymentId;
    }

    public UUID paymentId() {
        return paymentId;
    }
}
