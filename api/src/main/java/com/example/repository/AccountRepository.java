package com.example.repository;

import com.example.model.Account;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository {

    /** Inserts a new account or updates the balance of an existing one. */
    Account save(Account account);

    Optional<Account> findById(UUID id);

    /**
     * Same as findById, but the row stays locked until the surrounding transaction ends.
     * Use this for anything that reads a balance and then writes a new one.
     */
    Optional<Account> findByIdForUpdate(UUID id);

    List<Account> findAll();
}
