package com.example.service;

import com.example.model.Account;
import com.example.model.Money;
import com.example.model.Transaction;
import com.example.model.TransactionType;
import com.example.repository.InMemoryAccountRepository;
import com.example.repository.InMemoryTransactionRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountServiceTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T09:00:00Z"), ZoneOffset.UTC);

    private InMemoryAccountRepository accounts;
    private InMemoryTransactionRepository transactions;
    private SimpleMeterRegistry meters;
    private AccountService service;

    @BeforeEach
    void setUp() {
        accounts = new InMemoryAccountRepository();
        transactions = new InMemoryTransactionRepository();
        meters = new SimpleMeterRegistry();
        service = new AccountService(accounts, transactions, clock, meters);
    }

    @Test
    void openAccount_startsAtZero() {
        Account account = service.openAccount("  Ada Lovelace ");

        assertThat(account.ownerName()).isEqualTo("Ada Lovelace");
        assertThat(account.balance()).isEqualTo(Money.ZERO);
        assertThat(account.createdAt()).isEqualTo(clock.instant());
        assertThat(accounts.findById(account.id())).contains(account);
    }

    @Test
    void openAccount_rejectsBlankOwner() {
        assertThatThrownBy(() -> service.openAccount(" "))
                .isInstanceOf(BankingException.InvalidRequest.class);
    }

    @Test
    void getAccount_unknownId_throwsNotFound() {
        assertThatThrownBy(() -> service.getAccount(UUID.randomUUID()))
                .isInstanceOf(BankingException.AccountNotFound.class);
    }

    /**
     * The spec for the Week 2 rules. Implement deposit, withdraw and transfer in
     * AccountService, then delete the @Disabled line.
     */
    @Nested
    @Disabled("Week 2: implement deposit, withdraw and transfer in AccountService")
    class MoneyMovement {

        @Test
        void deposit_increasesBalanceAndRecordsTransaction() {
            var account = service.openAccount("Ada");

            var updated = service.deposit(account.id(), Money.of("100.00"));

            assertThat(updated.balance()).isEqualTo(Money.of("100.00"));
            assertThat(transactions.findByAccountId(account.id()))
                    .singleElement()
                    .satisfies(tx -> {
                        assertThat(tx.type()).isEqualTo(TransactionType.DEPOSIT);
                        assertThat(tx.balanceAfter()).isEqualTo(Money.of("100.00"));
                    });
        }

        @ParameterizedTest
        @ValueSource(strings = {"0", "-1.00", "0.001"})
        void deposit_rejectsInvalidAmounts(String amount) {
            var account = service.openAccount("Ada");

            assertThatThrownBy(() -> service.deposit(account.id(), new BigDecimal(amount)))
                    .isInstanceOf(BankingException.InvalidAmount.class);
            assertThat(service.getAccount(account.id()).balance()).isEqualTo(Money.ZERO);
        }

        @Test
        void withdraw_cannotOverdraw() {
            var account = service.openAccount("Ada");
            service.deposit(account.id(), Money.of("50.00"));

            assertThatThrownBy(() -> service.withdraw(account.id(), Money.of("50.01")))
                    .isInstanceOf(BankingException.InsufficientFunds.class);

            assertThat(service.getAccount(account.id()).balance()).isEqualTo(Money.of("50.00"));
            assertThat(meters.counter("overdraft.rejections").count()).isEqualTo(1.0);
        }

        @Test
        void withdraw_exactBalanceLeavesZero() {
            var account = service.openAccount("Ada");
            service.deposit(account.id(), Money.of("50.00"));

            assertThat(service.withdraw(account.id(), Money.of("50.00")).balance()).isEqualTo(Money.ZERO);
        }

        @Test
        void transfer_movesMoneyAndLinksBothSides() {
            var from = service.openAccount("Ada");
            var to = service.openAccount("Grace");
            service.deposit(from.id(), Money.of("100.00"));

            var result = service.transfer(from.id(), to.id(), Money.of("30.00"));

            assertThat(result.from().balance()).isEqualTo(Money.of("70.00"));
            assertThat(result.to().balance()).isEqualTo(Money.of("30.00"));
            assertThat(transactions.findByAccountId(from.id()))
                    .filteredOn(tx -> tx.type() == TransactionType.TRANSFER_OUT)
                    .singleElement()
                    .extracting(Transaction::transferId)
                    .isEqualTo(result.transferId());
            assertThat(transactions.findByAccountId(to.id()))
                    .filteredOn(tx -> tx.type() == TransactionType.TRANSFER_IN)
                    .singleElement()
                    .extracting(Transaction::transferId)
                    .isEqualTo(result.transferId());
            assertThat(meters.counter("transfers.completed").count()).isEqualTo(1.0);
        }

        @Test
        void transfer_insufficientFunds_changesNothing() {
            var from = service.openAccount("Ada");
            var to = service.openAccount("Grace");
            service.deposit(from.id(), Money.of("10.00"));

            assertThatThrownBy(() -> service.transfer(from.id(), to.id(), Money.of("10.01")))
                    .isInstanceOf(BankingException.InsufficientFunds.class);

            assertThat(service.getAccount(from.id()).balance()).isEqualTo(Money.of("10.00"));
            assertThat(service.getAccount(to.id()).balance()).isEqualTo(Money.ZERO);
            assertThat(transactions.findByAccountId(to.id())).isEmpty();
            assertThat(meters.counter("transfers.failed").count()).isEqualTo(1.0);
        }

        @Test
        void transfer_toSameAccount_isRejected() {
            var account = service.openAccount("Ada");
            service.deposit(account.id(), Money.of("10.00"));

            assertThatThrownBy(() -> service.transfer(account.id(), account.id(), Money.of("1.00")))
                    .isInstanceOf(BankingException.InvalidRequest.class);
        }

        @Test
        void transfer_unknownTarget_changesNothing() {
            var from = service.openAccount("Ada");
            service.deposit(from.id(), Money.of("10.00"));

            assertThatThrownBy(() -> service.transfer(from.id(), UUID.randomUUID(), Money.of("1.00")))
                    .isInstanceOf(BankingException.AccountNotFound.class);
            assertThat(service.getAccount(from.id()).balance()).isEqualTo(Money.of("10.00"));
        }
    }
}
