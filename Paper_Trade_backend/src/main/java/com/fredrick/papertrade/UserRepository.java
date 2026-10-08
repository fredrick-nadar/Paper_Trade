package com.fredrick.papertrade;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

// Spring generates the SQL for these methods from their names
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
}
