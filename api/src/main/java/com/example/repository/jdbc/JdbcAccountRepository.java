package com.example.repository.jdbc;

import com.example.model.Account;
import com.example.repository.AccountRepository;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcAccountRepository implements AccountRepository {

    private static final String COLUMNS = "id, owner_name, balance, created_at";

    private static final RowMapper<Account> ROW_MAPPER = (rs, rowNum) -> new Account(
            rs.getObject("id", UUID.class),
            rs.getString("owner_name"),
            rs.getBigDecimal("balance"),
            rs.getObject("created_at", OffsetDateTime.class).toInstant());

    private final JdbcClient jdbc;

    public JdbcAccountRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Account save(Account account) {
        jdbc.sql("""
                        INSERT INTO accounts.account (id, owner_name, balance, created_at)
                        VALUES (:id, :ownerName, :balance, :createdAt)
                        ON CONFLICT (id) DO UPDATE SET balance = EXCLUDED.balance
                        """)
                .param("id", account.id())
                .param("ownerName", account.ownerName())
                .param("balance", account.balance())
                .param("createdAt", account.createdAt().atOffset(ZoneOffset.UTC))
                .update();
        return account;
    }

    @Override
    public Optional<Account> findById(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM accounts.account WHERE id = :id")
                .param("id", id)
                .query(ROW_MAPPER)
                .optional();
    }

    @Override
    public Optional<Account> findByIdForUpdate(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM accounts.account WHERE id = :id FOR UPDATE")
                .param("id", id)
                .query(ROW_MAPPER)
                .optional();
    }

    @Override
    public List<Account> findAll() {
        return jdbc.sql("SELECT " + COLUMNS + " FROM accounts.account ORDER BY created_at")
                .query(ROW_MAPPER)
                .list();
    }
}
