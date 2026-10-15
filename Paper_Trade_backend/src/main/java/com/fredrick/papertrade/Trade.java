package com.fredrick.papertrade;

import jakarta.persistence.*;
import java.time.Instant;

// Immutable trade history: one row is written for every accepted buy or sell.
@Entity
@Table(name = "trades")
public class Trade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    private String symbol;
    private String type;       // BUY or SELL
    private int quantity;
    private double price;      // live price used for this trade
    private double total;      // price * quantity
    private Instant createdAt = Instant.now();

    // Required by JPA for database reads.
    protected Trade() {}

    // Creates the audit record after the user's balance and holding have been updated.
    public Trade(Long userId, String symbol, String type, int quantity, double price, double total) {
        this.userId = userId;
        this.symbol = symbol;
        this.type = type;
        this.quantity = quantity;
        this.price = price;
        this.total = total;
    }
}
