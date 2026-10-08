package com.fredrick.papertrade;

import jakarta.persistence.*;

// One row per (user, stock): how many shares the user owns and the average price paid
@Entity
@Table(name = "holdings", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "symbol"}))
public class Holding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String symbol;

    private int quantity;
    private double avgPrice;

    protected Holding() {}

    public Holding(Long userId, String symbol) {
        this.userId = userId;
        this.symbol = symbol;
    }

    public String getSymbol() { return symbol; }
    public int getQuantity() { return quantity; }
    public double getAvgPrice() { return avgPrice; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public void setAvgPrice(double avgPrice) { this.avgPrice = avgPrice; }
}
