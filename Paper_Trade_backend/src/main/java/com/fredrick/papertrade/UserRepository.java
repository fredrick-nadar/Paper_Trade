package com.fredrick.papertrade;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

// Spring Data generates SQL for these methods from their names and annotations.
public interface UserRepository extends JpaRepository<User, Long> {
    // Used by signup/login to locate a user.
    Optional<User> findByEmail(String email);

    // Locks the user row until the transaction ends, preventing concurrent trades from
    // spending the same virtual balance.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);
}
