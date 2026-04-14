package com.interview.assistant.controller;

import com.interview.assistant.dto.payment.CreateDonationOrderRequest;
import com.interview.assistant.dto.payment.CreateDonationOrderResponse;
import com.interview.assistant.dto.payment.VerifyDonationPaymentRequest;
import com.interview.assistant.dto.payment.VerifyDonationPaymentResponse;
import com.interview.assistant.service.PaymentService;
import javax.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/donations/order")
    public ResponseEntity<CreateDonationOrderResponse> createDonationOrder(@Valid @RequestBody CreateDonationOrderRequest request) {
        return ResponseEntity.ok(paymentService.createOrder(request));
    }

    @PostMapping("/donations/verify")
    public ResponseEntity<VerifyDonationPaymentResponse> verifyDonation(@Valid @RequestBody VerifyDonationPaymentRequest request) {
        return ResponseEntity.ok(paymentService.verifyPayment(request));
    }
}