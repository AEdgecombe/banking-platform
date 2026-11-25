package com.example.web;

import com.example.service.AccountService;
import com.example.web.ApiModels.AccountResponse;
import com.example.web.ApiModels.AmountRequest;
import com.example.web.ApiModels.OpenAccountRequest;
import com.example.web.ApiModels.TransactionResponse;
import com.example.web.ApiModels.TransferRequest;
import com.example.web.ApiModels.TransferResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class AccountController {

    private final AccountService service;

    public AccountController(AccountService service) {
        this.service = service;
    }

    @PostMapping("/accounts")
    public ResponseEntity<AccountResponse> open(@Valid @RequestBody OpenAccountRequest request) {
        var account = AccountResponse.from(service.openAccount(request.ownerName()));
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(account.id()).toUri();
        return ResponseEntity.created(location).body(account);
    }

    @GetMapping("/accounts")
    public List<AccountResponse> list() {
        return service.listAccounts().stream().map(AccountResponse::from).toList();
    }

    @GetMapping("/accounts/{id}")
    public AccountResponse get(@PathVariable UUID id) {
        return AccountResponse.from(service.getAccount(id));
    }

    @PostMapping("/accounts/{id}/deposits")
    public AccountResponse deposit(@PathVariable UUID id, @Valid @RequestBody AmountRequest request) {
        return AccountResponse.from(service.deposit(id, request.amount()));
    }

    @PostMapping("/accounts/{id}/withdrawals")
    public AccountResponse withdraw(@PathVariable UUID id, @Valid @RequestBody AmountRequest request) {
        return AccountResponse.from(service.withdraw(id, request.amount()));
    }

    @GetMapping("/accounts/{id}/transactions")
    public List<TransactionResponse> history(@PathVariable UUID id) {
        return service.history(id).stream().map(TransactionResponse::from).toList();
    }

    @PostMapping("/transfers")
    public TransferResponse transfer(@Valid @RequestBody TransferRequest request) {
        return TransferResponse.from(
                service.transfer(request.fromAccountId(), request.toAccountId(), request.amount()));
    }
}
