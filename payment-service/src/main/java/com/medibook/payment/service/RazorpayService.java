package com.medibook.payment.service;

import com.medibook.payment.dto.PaymentDto.RazorpayOrderRequest;
import com.medibook.payment.dto.PaymentDto.RazorpayOrderResponse;
import com.medibook.payment.dto.PaymentDto.RazorpayVerifyRequest;
import com.medibook.payment.entity.Payment;

public interface RazorpayService {

    /**
     * Creates a Razorpay order for the given amount and returns the order details
     * the frontend needs to open the checkout popup.
     */
    RazorpayOrderResponse createOrder(RazorpayOrderRequest request);

    /**
     * Verifies the HMAC-SHA256 signature returned by Razorpay after the user pays,
     * and — if valid — persists the payment record as PAID.
     *
     * @throws RuntimeException if the signature is invalid (possible fraud attempt)
     */
    Payment verifyAndCapture(RazorpayVerifyRequest request);

    /**
     * Issues a real refund via the Razorpay Refunds API for the given appointmentId.
     * Falls back to the local-only flow if the payment was not made via Razorpay.
     */
    Payment refundViaRazorpay(int appointmentId, String reason);
}