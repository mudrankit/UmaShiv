package com.interview.assistant.dto.payment;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;

public class CreateDonationOrderRequest {

    @NotBlank
    private String donorName;

    @Min(1)
    private int amount;

    public String getDonorName() {
        return donorName;
    }

    public void setDonorName(String donorName) {
        this.donorName = donorName;
    }

    public int getAmount() {
        return amount;
    }

    public void setAmount(int amount) {
        this.amount = amount;
    }
}