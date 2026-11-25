package com.example.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

/** Immutable snapshot of an account. Storage concerns live in the repository layer. */
public record Account(UUID id, String ownerName, BigDecimal balance, Instant createdAt) {

    public Account {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(ownerName, "ownerName");
        Objects.requireNonNull(createdAt, "createdAt");
        balance = Money.of(balance);
    }

    public static Account open(String ownerName, Instant now) {
        // PostgreSQL stores microseconds. Truncating here means a saved account equals its reloaded copy.
        return new Account(UUID.randomUUID(), ownerName, Money.ZERO, now.truncatedTo(ChronoUnit.MICROS));
    }

    public Account withBalance(BigDecimal newBalance) {
        return new Account(id, ownerName, newBalance, createdAt);
    }
}
