package com.example.payment;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Random;

@Configuration
public class PaymentConfig {

    @Bean
    PaymentDecider paymentDecider() {
        return new PaymentDecider(new Random());
    }
}
