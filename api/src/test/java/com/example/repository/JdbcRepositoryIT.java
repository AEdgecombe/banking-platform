package com.example.repository;

import com.example.model.Account;
import com.example.model.Money;
import com.example.model.Transaction;
import com.example.model.TransactionType;
import com.example.repository.jdbc.JdbcAccountRepository;
import com.example.repository.jdbc.JdbcTransactionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Runs the real Flyway migrations against a throwaway PostgreSQL. Needs Docker. */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JdbcAccountRepository.class, JdbcTransactionRepository.class})
@Testcontainers
class JdbcRepositoryIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    // Postgres stores microseconds, so truncate before comparing round-tripped values
    private final Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);

    @Autowired
    JdbcAccountRepository accounts;

    @Autowired
    JdbcTransactionRepository transactions;

    @Test
    void savesAndReloadsAccount() {
        var account = accounts.save(Account.open("Ada", now));

        assertThat(accounts.findById(account.id())).contains(account);
    }

    @Test
    void saveTwiceUpdatesBalance() {
        var account = accounts.save(Account.open("Ada", now));

        accounts.save(account.withBalance(Money.of("12.34")));

        assertThat(accounts.findByIdForUpdate(account.id()))
                .hasValueSatisfying(a -> assertThat(a.balance()).isEqualTo(Money.of("12.34")));
    }

    @Test
    void databaseRejectsNegativeBalance() {
        var account = accounts.save(Account.open("Ada", now));

        assertThatThrownBy(() -> accounts.save(account.withBalance(Money.of("-0.01"))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void historyIsNewestFirst() {
        var account = accounts.save(Account.open("Ada", now));
        var older = Transaction.of(account.id(), TransactionType.DEPOSIT, Money.of("5.00"), Money.of("5.00"),
                null, now);
        var newer = Transaction.of(account.id(), TransactionType.WITHDRAWAL, Money.of("2.00"), Money.of("3.00"),
                null, now.plusSeconds(1));
        transactions.save(older);
        transactions.save(newer);

        assertThat(transactions.findByAccountId(account.id())).containsExactly(newer, older);
    }
}
