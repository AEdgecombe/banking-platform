package com.example.repository;

import com.example.model.Transaction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/** Test double for service unit tests. */
public class InMemoryTransactionRepository implements TransactionRepository {

    private final List<Transaction> store = new CopyOnWriteArrayList<>();

    @Override
    public Transaction save(Transaction transaction) {
        store.add(transaction);
        return transaction;
    }

    @Override
    public List<Transaction> findByAccountId(UUID accountId) {
        var result = new ArrayList<Transaction>();
        for (var tx : store) {
            if (tx.accountId().equals(accountId)) {
                result.add(tx);
            }
        }
        result.sort(Comparator.comparing(Transaction::createdAt).reversed());
        return result;
    }
}
