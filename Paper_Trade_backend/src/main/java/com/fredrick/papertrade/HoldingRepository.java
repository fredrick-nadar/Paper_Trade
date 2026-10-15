package com.fredrick.papertrade;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface HoldingRepository extends JpaRepository<Holding, Long> {
    // Loads the complete portfolio for one authenticated user.
    List<Holding> findByUserId(Long userId);

    // Finds the one position that can be updated by a buy or sell operation.
    Optional<Holding> findByUserIdAndSymbol(Long userId, String symbol);
}
