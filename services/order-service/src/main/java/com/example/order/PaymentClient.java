package com.example.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.UUID;

@Component
public class PaymentClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public PaymentClient(RestClient paymentRestClient, ObjectMapper objectMapper) {
        this.restClient = paymentRestClient;
        this.objectMapper = objectMapper;
    }

    public PaymentResponse charge(UUID orderId, BigDecimal amount) {
        try {
            PaymentResponse payment = restClient.post()
                    .uri("/payments")
                    .body(new ChargeRequest(orderId, amount))
                    .retrieve()
                    .body(PaymentResponse.class);
            if (payment == null || payment.id() == null) {
                throw new PaymentFailedException(null, 200);
            }
            return payment;
        } catch (RestClientResponseException ex) {
            throw new PaymentFailedException(readPaymentId(ex), ex.getStatusCode().value());
        } catch (RestClientException ex) {
            throw new PaymentFailedException(null, 0);
        }
    }

    private UUID readPaymentId(RestClientResponseException ex) {
        byte[] body = ex.getResponseBodyAsByteArray();
        if (body.length == 0) {
            return null;
        }
        try {
            PaymentResponse payment = objectMapper.readValue(body, PaymentResponse.class);
            return payment == null ? null : payment.id();
        } catch (IOException parseError) {
            return null;
        }
    }

    private record ChargeRequest(UUID orderId, BigDecimal amount) {
    }
}
