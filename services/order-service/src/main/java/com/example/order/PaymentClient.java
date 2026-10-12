package com.example.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.UUID;

@Component
public class PaymentClient {

    private static final Logger log = LoggerFactory.getLogger(PaymentClient.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public PaymentClient(RestClient paymentRestClient, ObjectMapper objectMapper) {
        this.restClient = paymentRestClient;
        this.objectMapper = objectMapper;
    }

    public PaymentResponse charge(UUID orderId, BigDecimal amount) {
        log.trace("posting payment orderId={} amount={}", orderId, amount);
        try {
            PaymentResponse payment = restClient.post()
                    .uri("/payments")
                    .body(new ChargeRequest(orderId, amount))
                    .retrieve()
                    .body(PaymentResponse.class);
            if (payment == null || payment.id() == null) {
                log.trace("payment response missing id orderId={} httpStatus=200", orderId);
                throw new PaymentFailedException(null, 200);
            }
            log.trace("payment response orderId={} paymentId={} status={}", orderId, payment.id(), payment.status());
            return payment;
        } catch (RestClientResponseException ex) {
            UUID paymentId = readPaymentId(ex);
            log.trace("payment error response orderId={} paymentId={} httpStatus={} body={}",
                    orderId, paymentId, ex.getStatusCode().value(), ex.getResponseBodyAsString());
            throw new PaymentFailedException(paymentId, ex.getStatusCode().value());
        } catch (RestClientException ex) {
            log.trace("payment unreachable orderId={} reason={}", orderId, ex.toString());
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
