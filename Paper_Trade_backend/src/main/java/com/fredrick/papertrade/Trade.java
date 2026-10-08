package com.fredrick.papertrade;

import jakarta.persistence.*;
import java.time.Instant;

// History: one row per buy or sell
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

    protected Trade() {}

    public Trade(Long userId, String symbol, String type, int quantity, double price, double total) {
        this.userId = userId;
        this.symbol = symbol;
        this.type = type;
        this.quantity = quantity;
        this.price = price;
        this.total = total;
    }
}
