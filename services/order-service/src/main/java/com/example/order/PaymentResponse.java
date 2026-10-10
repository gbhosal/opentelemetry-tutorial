package com.example.order;

import java.util.UUID;

public record PaymentResponse(UUID id, String status) {
}
