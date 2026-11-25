package com.example.service;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Rule violations the caller can fix. Each has a stable code that ends up in the JSON error
 * body, so clients branch on the code, never on the message text.
 */
public abstract sealed class BankingException extends RuntimeException {

    private final String code;
    private final String field;

    protected BankingException(String code, String field, String message) {
        super(message);
        this.code = code;
        this.field = field;
    }

    public String code() {
        return code;
    }

    /** The request field at fault, or null if it is not about one field. */
    public String field() {
        return field;
    }

    public static final class AccountNotFound extends BankingException {
        public AccountNotFound(UUID id) {
            super("ACCOUNT_NOT_FOUND", null, "Account " + id + " does not exist");
        }
    }

    public static final class InvalidAmount extends BankingException {
        public InvalidAmount(BigDecimal amount) {
            super("INVALID_AMOUNT", "amount",
                    "Amount must be greater than zero with at most 2 decimal places, got " + amount);
        }
    }

    public static final class InsufficientFunds extends BankingException {
        public InsufficientFunds(UUID accountId) {
            super("INSUFFICIENT_FUNDS", "amount", "Account " + accountId + " has insufficient funds");
        }
    }

    public static final class InvalidRequest extends BankingException {
        public InvalidRequest(String field, String message) {
            super("INVALID_REQUEST", field, message);
        }
    }
}
