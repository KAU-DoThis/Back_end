package com.dothis.fintris.auth.repository;

import com.dothis.fintris.auth.entity.SocialAccount;
import com.dothis.fintris.user.entity.AuthProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SocialAccountRepository extends JpaRepository<SocialAccount, Long> {

    Optional<SocialAccount> findByProviderAndProviderUserId(AuthProvider provider, String providerUserId);
}
