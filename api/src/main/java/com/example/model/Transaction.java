package com.example.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One line in an account's history. A transfer produces two of these (TRANSFER_OUT and
 * TRANSFER_IN) that share a transferId.
 */
public record Transaction(
        UUID id,
        UUID accountId,
        TransactionType type,
        BigDecimal amount,
        BigDecimal balanceAfter,
        UUID transferId,
        Instant createdAt) {

    public Transaction {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(createdAt, "createdAt");
        amount = Money.of(amount);
        balanceAfter = Money.of(balanceAfter);
    }

    public static Transaction of(UUID accountId, TransactionType type, BigDecimal amount,
                                 BigDecimal balanceAfter, UUID transferId, Instant now) {
        return new Transaction(UUID.randomUUID(), accountId, type, amount, balanceAfter, transferId, now);
    }
}
