package com.example.repository;

import com.example.model.Transaction;

import java.util.List;
import java.util.UUID;

public interface TransactionRepository {

    Transaction save(Transaction transaction);

    /** Newest first. */
    List<Transaction> findByAccountId(UUID accountId);
}
