package com.interview.assistant.repository;

import com.interview.assistant.model.UserAccount;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, String> {

    Optional<UserAccount> findByEmailIgnoreCase(String email);

    Optional<UserAccount> findByUsernameIgnoreCase(String username);

    Optional<UserAccount> findByProviderAndProviderSubject(String provider, String providerSubject);
}