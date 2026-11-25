package com.example.repository.jdbc;

import com.example.model.Transaction;
import com.example.model.TransactionType;
import com.example.repository.TransactionRepository;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Repository
public class JdbcTransactionRepository implements TransactionRepository {

    private static final RowMapper<Transaction> ROW_MAPPER = (rs, rowNum) -> new Transaction(
            rs.getObject("id", UUID.class),
            rs.getObject("account_id", UUID.class),
            TransactionType.valueOf(rs.getString("type")),
            rs.getBigDecimal("amount"),
            rs.getBigDecimal("balance_after"),
            rs.getObject("transfer_id", UUID.class),
            rs.getObject("created_at", OffsetDateTime.class).toInstant());

    private final JdbcClient jdbc;

    public JdbcTransactionRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Transaction save(Transaction tx) {
        jdbc.sql("""
                        INSERT INTO transactions.account_transaction
                            (id, account_id, type, amount, balance_after, transfer_id, created_at)
                        VALUES (:id, :accountId, :type, :amount, :balanceAfter, :transferId, :createdAt)
                        """)
                .param("id", tx.id())
                .param("accountId", tx.accountId())
                .param("type", tx.type().name())
                .param("amount", tx.amount())
                .param("balanceAfter", tx.balanceAfter())
                .param("transferId", tx.transferId())
                .param("createdAt", tx.createdAt().atOffset(ZoneOffset.UTC))
                .update();
        return tx;
    }

    @Override
    public List<Transaction> findByAccountId(UUID accountId) {
        return jdbc.sql("""
                        SELECT id, account_id, type, amount, balance_after, transfer_id, created_at
                        FROM transactions.account_transaction
                        WHERE account_id = :accountId
                        ORDER BY created_at DESC, id
                        """)
                .param("accountId", accountId)
                .query(ROW_MAPPER)
                .list();
    }
}
