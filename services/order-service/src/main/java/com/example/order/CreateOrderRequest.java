package com.example.order;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CreateOrderRequest(
        @NotBlank String customerId,
        @NotBlank String sku,
        @NotNull @DecimalMin("0.01") BigDecimal amount) {
}
