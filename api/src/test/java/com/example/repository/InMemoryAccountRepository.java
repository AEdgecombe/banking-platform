package com.example.repository;

import com.example.model.Account;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Test double for service unit tests. No locking: findByIdForUpdate is just findById. */
public class InMemoryAccountRepository implements AccountRepository {

    private final Map<UUID, Account> store = new ConcurrentHashMap<>();

    @Override
    public Account save(Account account) {
        store.put(account.id(), account);
        return account;
    }

    @Override
    public Optional<Account> findById(UUID id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Optional<Account> findByIdForUpdate(UUID id) {
        return findById(id);
    }

    @Override
    public List<Account> findAll() {
        return store.values().stream().sorted(Comparator.comparing(Account::createdAt)).toList();
    }
}
