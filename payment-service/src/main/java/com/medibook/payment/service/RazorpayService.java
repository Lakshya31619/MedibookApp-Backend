package com.medibook.payment.service;

import com.medibook.payment.dto.PaymentDto.RazorpayOrderRequest;
import com.medibook.payment.dto.PaymentDto.RazorpayOrderResponse;
import com.medibook.payment.dto.PaymentDto.RazorpayVerifyRequest;
import com.medibook.payment.entity.Payment;

public interface RazorpayService {

    RazorpayOrderResponse createOrder(RazorpayOrderRequest request);

    Payment verifyAndCapture(RazorpayVerifyRequest request);

    Payment refundViaRazorpay(int appointmentId, String reason);
}