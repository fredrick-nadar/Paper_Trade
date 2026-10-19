package com.fredrick.papertrade;

import jakarta.persistence.*;
import java.util.UUID;

// @Entity maps this class to the "users" table in PostgreSQL.
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Random public identifier shared with analytics instead of the sequential database id.
    // Nullable only so existing rows can be backfilled at startup (PublicIdBackfill).
    @Column(name = "public_id", unique = true, length = 36)
    private String publicId;

    private String name;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    // Every new account starts with $1,000 of virtual money
    @Column(nullable = false)
    private double balance = 1000.0;

    // Required by JPA when loading an existing user.
    protected User() {}

    // New accounts start with the virtual balance declared above.
    public User(String name, String email, String passwordHash) {
        this.publicId = UUID.randomUUID().toString();
        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
    }

    public Long getId() { return id; }
    public String getPublicId() { return publicId; }
    public void assignPublicId() { if (publicId == null) publicId = UUID.randomUUID().toString(); }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public double getBalance() { return balance; }
    public void setBalance(double balance) { this.balance = balance; }
}
