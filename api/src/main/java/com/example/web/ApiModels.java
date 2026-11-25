package com.example.web;

import com.example.model.Account;
import com.example.model.Transaction;
import com.example.model.TransactionType;
import com.example.service.AccountService.TransferResult;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Request and response shapes for the REST API. Kept separate from the domain model so the
 * wire format can stay stable while the model changes.
 *
 * Validation here only checks shape (present, right type). Business rules such as
 * "amount must be positive" belong to AccountService so the API and any other caller get
 * the same behaviour.
 */
public final class ApiModels {

    private ApiModels() {
    }

    public record OpenAccountRequest(@NotBlank @Size(max = 100) String ownerName) {
    }

    public record AmountRequest(@NotNull BigDecimal amount) {
    }

    public record TransferRequest(@NotNull UUID fromAccountId,
                                  @NotNull UUID toAccountId,
                                  @NotNull BigDecimal amount) {
    }

    public record AccountResponse(UUID id, String ownerName, BigDecimal balance, Instant createdAt) {
        static AccountResponse from(Account a) {
            return new AccountResponse(a.id(), a.ownerName(), a.balance(), a.createdAt());
        }
    }

    public record TransactionResponse(UUID id, TransactionType type, BigDecimal amount,
                                      BigDecimal balanceAfter, UUID transferId, Instant createdAt) {
        static TransactionResponse from(Transaction t) {
            return new TransactionResponse(t.id(), t.type(), t.amount(), t.balanceAfter(),
                    t.transferId(), t.createdAt());
        }
    }

    public record TransferResponse(UUID transferId, AccountResponse from, AccountResponse to) {
        static TransferResponse from(TransferResult r) {
            return new TransferResponse(r.transferId(), AccountResponse.from(r.from()),
                    AccountResponse.from(r.to()));
        }
    }

    /** Every error the API returns has this shape. field is null when no single field is at fault. */
    public record ErrorResponse(String code, String message, String field) {
    }
}
