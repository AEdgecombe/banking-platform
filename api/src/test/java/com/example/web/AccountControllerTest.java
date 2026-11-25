package com.example.web;

import com.example.model.Account;
import com.example.service.AccountService;
import com.example.service.BankingException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AccountController.class)
class AccountControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    AccountService service;

    @Test
    void openAccount_returns201WithLocation() throws Exception {
        var account = new Account(UUID.randomUUID(), "Ada", BigDecimal.ZERO, Instant.parse("2026-01-01T09:00:00Z"));
        when(service.openAccount("Ada")).thenReturn(account);

        mvc.perform(post("/api/accounts").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ownerName\":\"Ada\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/accounts/" + account.id()))
                .andExpect(jsonPath("$.balance").value(0.00))
                .andExpect(jsonPath("$.createdAt").value("2026-01-01T09:00:00Z"));
    }

    @Test
    void missingOwnerName_returnsFieldError() throws Exception {
        mvc.perform(post("/api/accounts").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.field").value("ownerName"));
    }

    @Test
    void unknownAccount_returns404WithCode() throws Exception {
        var id = UUID.randomUUID();
        when(service.getAccount(id)).thenThrow(new BankingException.AccountNotFound(id));

        mvc.perform(get("/api/accounts/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
    }

    @Test
    void insufficientFunds_returns422() throws Exception {
        var id = UUID.randomUUID();
        when(service.withdraw(eq(id), any())).thenThrow(new BankingException.InsufficientFunds(id));

        mvc.perform(post("/api/accounts/{id}/withdrawals", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":\"10.00\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"))
                .andExpect(jsonPath("$.field").value("amount"));
    }

    @Test
    void malformedId_returnsJsonNotStackTrace() throws Exception {
        mvc.perform(get("/api/accounts/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void malformedBody_returnsJsonNotStackTrace() throws Exception {
        mvc.perform(post("/api/accounts").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }
}
