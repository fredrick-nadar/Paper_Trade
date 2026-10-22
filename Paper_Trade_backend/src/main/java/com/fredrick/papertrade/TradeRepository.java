package com.fredrick.papertrade;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TradeRepository extends JpaRepository<Trade, Long> {
    // JpaRepository already provides persistence methods for the trade-history table.
    boolean existsByUserId(Long userId);
}
