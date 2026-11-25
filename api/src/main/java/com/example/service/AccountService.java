package com.example.service;

import com.example.model.Account;
import com.example.model.Transaction;
import com.example.repository.AccountRepository;
import com.example.repository.TransactionRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * Every banking rule lives here. Controllers and the CLI only move data in and out.
 */
@Service
public class AccountService {

    public static final String ACCOUNTS_CACHE = "accounts";

    private final AccountRepository accounts;
    private final TransactionRepository transactions;
    private final Clock clock;

    // Exposed at /actuator/prometheus as transfers_completed_total, etc.
    private final Counter transfersCompleted;
    private final Counter transfersFailed;
    private final Counter overdraftRejections;

    public AccountService(AccountRepository accounts,
                          TransactionRepository transactions,
                          Clock clock,
                          MeterRegistry meters) {
        this.accounts = accounts;
        this.transactions = transactions;
        this.clock = clock;
        this.transfersCompleted = Counter.builder("transfers.completed")
                .description("Transfers that moved money").register(meters);
        this.transfersFailed = Counter.builder("transfers.failed")
                .description("Transfers rejected for any reason").register(meters);
        this.overdraftRejections = Counter.builder("overdraft.rejections")
                .description("Withdrawals or transfers rejected for insufficient funds").register(meters);
    }

    @Transactional
    public Account openAccount(String ownerName) {
        if (ownerName == null || ownerName.isBlank()) {
            throw new BankingException.InvalidRequest("ownerName", "Owner name is required");
        }
        return accounts.save(Account.open(ownerName.strip(), clock.instant()));
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = ACCOUNTS_CACHE, key = "#id")
    public Account getAccount(UUID id) {
        return accounts.findById(id).orElseThrow(() -> new BankingException.AccountNotFound(id));
    }

    @Transactional(readOnly = true)
    public List<Account> listAccounts() {
        return accounts.findAll();
    }

    @Transactional(readOnly = true)
    public List<Transaction> history(UUID accountId) {
        if (accounts.findById(accountId).isEmpty()) {
            throw new BankingException.AccountNotFound(accountId);
        }
        return transactions.findByAccountId(accountId);
    }

    /**
     * TODO(Week 2): implement.
     * - Reject null, zero or negative amounts, and amounts with more than 2 decimal places
     *   (BankingException.InvalidAmount).
     * - Lock the account with findByIdForUpdate, add the amount, save it.
     * - Record a DEPOSIT transaction with the new balance.
     * - Return the updated account.
     */
    @Transactional
    @CacheEvict(cacheNames = ACCOUNTS_CACHE, key = "#accountId")
    public Account deposit(UUID accountId, BigDecimal amount) {
        throw new UnsupportedOperationException("Week 2: deposit not implemented yet");
    }

    /**
     * TODO(Week 2): implement.
     * - Same amount validation as deposit.
     * - If the balance would go below zero: increment overdraftRejections and throw
     *   BankingException.InsufficientFunds. Nothing is written.
     * - Otherwise save the new balance and record a WITHDRAWAL transaction.
     */
    @Transactional
    @CacheEvict(cacheNames = ACCOUNTS_CACHE, key = "#accountId")
    public Account withdraw(UUID accountId, BigDecimal amount) {
        throw new UnsupportedOperationException("Week 2: withdraw not implemented yet");
    }

    /**
     * TODO(Week 2): implement. A transfer either fully happens or not at all.
     * - Validate the amount. Reject from == to (BankingException.InvalidRequest).
     * - Lock both rows with findByIdForUpdate in a fixed order (lowest UUID first). Two
     *   concurrent transfers A->B and B->A would otherwise deadlock.
     * - Check funds, update both balances, record TRANSFER_OUT and TRANSFER_IN sharing one
     *   transferId.
     * - On success increment transfersCompleted. On any BankingException increment
     *   transfersFailed (and overdraftRejections if it was insufficient funds), then rethrow.
     *   @Transactional rolls back on RuntimeException, so a half-done transfer is undone.
     */
    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = ACCOUNTS_CACHE, key = "#fromId"),
            @CacheEvict(cacheNames = ACCOUNTS_CACHE, key = "#toId")
    })
    public TransferResult transfer(UUID fromId, UUID toId, BigDecimal amount) {
        throw new UnsupportedOperationException("Week 2: transfer not implemented yet");
    }

    public record TransferResult(UUID transferId, Account from, Account to) {
    }
}
