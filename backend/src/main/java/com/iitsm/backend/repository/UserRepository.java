package com.iitsm.backend.repository;

import com.iitsm.backend.domain.user.Administrator;
import com.iitsm.backend.domain.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

/**
 * <b>Repository pattern:</b> the domain asks for users by meaning ("find by email"), not by SQL. Spring Data
 * generates the implementation from the method names at startup.
 */
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    @Query("SELECT a FROM Administrator a WHERE a.active = TRUE")
    List<Administrator> findActiveAdministrators();
}
