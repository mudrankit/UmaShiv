package com.interview.assistant.repository;

import com.interview.assistant.model.DonationPayment;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DonationPaymentRepository extends JpaRepository<DonationPayment, String> {

    Optional<DonationPayment> findByProviderOrderId(String providerOrderId);
}