package com.cardealer.repositories;

import com.cardealer.models.Token;
import com.cardealer.models.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TokenRepository extends JpaRepository<Token, Long> {

    List<Token> findAllByUser(User user);

    Optional<Token> findByToken(String token);
}
