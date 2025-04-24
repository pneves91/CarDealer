package com.cardealer.repositories;

import com.cardealer.models.EmailVerificationToken;
import com.cardealer.models.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {
    Optional<EmailVerificationToken> findByToken(String token);

    @Modifying
    @Query("UPDATE EmailVerificationToken t SET t.used = true WHERE t.user = :user AND t.used = false")
    void invalidateAllForUser(@Param("user") User user);

    List<EmailVerificationToken> findAllByUser(User user);
}
