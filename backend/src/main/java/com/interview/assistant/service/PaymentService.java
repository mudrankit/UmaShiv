package com.interview.assistant.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.assistant.config.PaymentProperties;
import com.interview.assistant.dto.payment.CreateDonationOrderRequest;
import com.interview.assistant.dto.payment.CreateDonationOrderResponse;
import com.interview.assistant.dto.payment.VerifyDonationPaymentRequest;
import com.interview.assistant.dto.payment.VerifyDonationPaymentResponse;
import com.interview.assistant.model.DonationPayment;
import com.interview.assistant.repository.DonationPaymentRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class PaymentService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final PaymentProperties paymentProperties;
    private final DonationPaymentRepository donationPaymentRepository;

    public PaymentService(RestTemplate restTemplate,
                          ObjectMapper objectMapper,
                          PaymentProperties paymentProperties,
                          DonationPaymentRepository donationPaymentRepository) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.paymentProperties = paymentProperties;
        this.donationPaymentRepository = donationPaymentRepository;
    }

    @Transactional
    public CreateDonationOrderResponse createOrder(CreateDonationOrderRequest request) {
        ensureConfigured();
        String donorName = request.getDonorName().trim();
        if (donorName.length() < 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Please enter a valid donor name.");
        }
        if (request.getAmount() < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Please enter an amount of at least 1.");
        }

        String donationId = UUID.randomUUID().toString();
        String receipt = donationId.substring(0, 18).replace("-", "");
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("amount", request.getAmount() * 100);
        payload.put("currency", paymentProperties.getCurrency());
        payload.put("receipt", receipt);
        Map<String, String> notes = new LinkedHashMap<String, String>();
        notes.put("donation_id", donationId);
        notes.put("donor_name", donorName);
        payload.put("notes", notes);

        JsonNode orderNode = callCreateOrder(payload);
        DonationPayment donation = new DonationPayment();
        donation.setId(donationId);
        donation.setDonorName(donorName);
        donation.setAmount(request.getAmount());
        donation.setCurrency(paymentProperties.getCurrency());
        donation.setProvider(paymentProperties.getProvider());
        donation.setProviderOrderId(orderNode.path("id").asText());
        donation.setStatus("CREATED");
        donation.setCreatedAt(Instant.now().toString());
        donation.setUpdatedAt(Instant.now().toString());
        donationPaymentRepository.save(donation);

        CreateDonationOrderResponse response = new CreateDonationOrderResponse();
        response.setDonationId(donationId);
        response.setProvider(paymentProperties.getProvider());
        response.setKeyId(paymentProperties.getKeyId());
        response.setOrderId(donation.getProviderOrderId());
        response.setAmount(request.getAmount() * 100);
        response.setCurrency(paymentProperties.getCurrency());
        response.setBusinessName(paymentProperties.getBusinessName());
        response.setDescription(paymentProperties.getDescription());
        response.setDonorName(donorName);
        return response;
    }

    @Transactional
    public VerifyDonationPaymentResponse verifyPayment(VerifyDonationPaymentRequest request) {
        ensureConfigured();
        DonationPayment donation = donationPaymentRepository.findById(request.getDonationId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Donation record was not found."));

        if (!donation.getProviderOrderId().equals(request.getRazorpayOrderId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment order mismatch.");
        }

        if ("PAID".equals(donation.getStatus())) {
            VerifyDonationPaymentResponse response = new VerifyDonationPaymentResponse();
            response.setVerified(true);
            response.setMessage("Payment already verified.");
            response.setDonationId(donation.getId());
            response.setPaymentId(donation.getProviderPaymentId());
            return response;
        }

        String payload = request.getRazorpayOrderId() + "|" + request.getRazorpayPaymentId();
        String expectedSignature = hmacSha256(payload, paymentProperties.getKeySecret());
        if (!expectedSignature.equals(request.getRazorpaySignature())) {
            donation.setStatus("FAILED");
            donation.setUpdatedAt(Instant.now().toString());
            donationPaymentRepository.save(donation);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment signature verification failed.");
        }

        donation.setProviderPaymentId(request.getRazorpayPaymentId());
        donation.setProviderSignature(request.getRazorpaySignature());
        donation.setStatus("PAID");
        donation.setUpdatedAt(Instant.now().toString());
        donationPaymentRepository.save(donation);

        VerifyDonationPaymentResponse response = new VerifyDonationPaymentResponse();
        response.setVerified(true);
        response.setMessage("Thank you for supporting Umashiv.");
        response.setDonationId(donation.getId());
        response.setPaymentId(donation.getProviderPaymentId());
        return response;
    }

    private JsonNode callCreateOrder(Map<String, Object> payload) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set(HttpHeaders.AUTHORIZATION, basicAuthHeader(paymentProperties.getKeyId(), paymentProperties.getKeySecret()));
            HttpEntity<Map<String, Object>> entity = new HttpEntity<Map<String, Object>>(payload, headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    paymentProperties.getOrderBaseUrl(),
                    HttpMethod.POST,
                    entity,
                    String.class);
            return objectMapper.readTree(response.getBody());
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Unable to create a payment order right now.");
        }
    }

    private void ensureConfigured() {
        if (!paymentProperties.isEnabled() || !StringUtils.hasText(paymentProperties.getKeyId()) || !StringUtils.hasText(paymentProperties.getKeySecret())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payments are not configured yet. Add your Razorpay account details first.");
        }
    }

    private String basicAuthHeader(String keyId, String keySecret) {
        String value = keyId + ":" + keySecret;
        return "Basic " + Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String hmacSha256(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte item : digest) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to verify the payment signature.");
        }
    }
}