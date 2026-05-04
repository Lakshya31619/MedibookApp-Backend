package com.medibook.payment.config;

import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RazorpayConfig {

    @Value("${razorpay.key.id}")
    private String keyId;

    @Value("${razorpay.key.secret}")
    private String keySecret;

    /**
     * FIX: @Bean methods cannot declare checked exceptions.
     * RazorpayException is checked — Spring cannot proxy the bean if the factory
     * method declares it in its throws clause. Wrap it so the app fails fast on
     * bad credentials with a clear message instead of a cryptic wiring error.
     */
    @Bean
    public RazorpayClient razorpayClient() {
        try {
            return new RazorpayClient(keyId, keySecret);
        } catch (RazorpayException e) {
            throw new IllegalStateException(
                "Failed to initialise Razorpay client — check razorpay.key.id and " +
                "razorpay.key.secret in your properties: " + e.getMessage(), e);
        }
    }

    /**
     * Exposes the key ID so the frontend can initialise the Razorpay checkout.
     * The secret is NEVER sent to the client.
     */
    public String getKeyId() {
        return keyId;
    }

    public String getKeySecret() {
        return keySecret;
    }
}