package com.interview.assistant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.payment")
public class PaymentProperties {

    private boolean enabled;
    private String provider = "razorpay";
    private String keyId;
    private String keySecret;
    private String businessName = "Umashiv";
    private String description = "Support Umashiv";
    private String currency = "INR";
    private String orderBaseUrl = "https://api.razorpay.com/v1/orders";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getKeyId() {
        return keyId;
    }

    public void setKeyId(String keyId) {
        this.keyId = keyId;
    }

    public String getKeySecret() {
        return keySecret;
    }

    public void setKeySecret(String keySecret) {
        this.keySecret = keySecret;
    }

    public String getBusinessName() {
        return businessName;
    }

    public void setBusinessName(String businessName) {
        this.businessName = businessName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getOrderBaseUrl() {
        return orderBaseUrl;
    }

    public void setOrderBaseUrl(String orderBaseUrl) {
        this.orderBaseUrl = orderBaseUrl;
    }
}