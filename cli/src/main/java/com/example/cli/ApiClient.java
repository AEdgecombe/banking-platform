package com.example.cli;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * HTTP client for the banking API. This is the only class in the CLI that knows about HTTP.
 * It shares no code with the api module: the JSON contract is the only coupling.
 */
public class ApiClient {

    private final URI baseUri;
    private final HttpClient http;
    private final ObjectMapper json = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public ApiClient(URI baseUri) {
        this.baseUri = baseUri;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    public AccountView openAccount(String ownerName) {
        return post("/api/accounts", Map.of("ownerName", ownerName), type(AccountView.class));
    }

    public List<AccountView> listAccounts() {
        return get("/api/accounts", listOf(AccountView.class));
    }

    public AccountView getAccount(UUID id) {
        return get("/api/accounts/" + id, type(AccountView.class));
    }

    public AccountView deposit(UUID id, BigDecimal amount) {
        return post("/api/accounts/" + id + "/deposits", Map.of("amount", amount), type(AccountView.class));
    }

    public AccountView withdraw(UUID id, BigDecimal amount) {
        return post("/api/accounts/" + id + "/withdrawals", Map.of("amount", amount), type(AccountView.class));
    }

    public TransferView transfer(UUID from, UUID to, BigDecimal amount) {
        return post("/api/transfers",
                Map.of("fromAccountId", from, "toAccountId", to, "amount", amount), type(TransferView.class));
    }

    public List<TransactionView> history(UUID id) {
        return get("/api/accounts/" + id + "/transactions", listOf(TransactionView.class));
    }

    private <T> T get(String path, JavaType type) {
        var request = HttpRequest.newBuilder(baseUri.resolve(path)).GET().build();
        return readBody(send(request), type);
    }

    private <T> T post(String path, Object body, JavaType type) {
        try {
            var request = HttpRequest.newBuilder(baseUri.resolve(path))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                    .build();
            return readBody(send(request), type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialise request", e);
        }
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw toApiException(response);
            }
            return response;
        } catch (IOException e) {
            throw new ApiException(0, "UNREACHABLE", "Could not reach the API at " + baseUri, null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(0, "INTERRUPTED", "Request interrupted", null);
        }
    }

    private ApiException toApiException(HttpResponse<String> response) {
        try {
            var error = json.readValue(response.body(), ErrorBody.class);
            return new ApiException(response.statusCode(), error.code(), error.message(), error.field());
        } catch (JsonProcessingException e) {
            return new ApiException(response.statusCode(), "HTTP_" + response.statusCode(), response.body(), null);
        }
    }

    private JavaType type(Class<?> type) {
        return json.getTypeFactory().constructType(type);
    }

    private JavaType listOf(Class<?> elementType) {
        return json.getTypeFactory().constructCollectionType(List.class, elementType);
    }

    private <T> T readBody(HttpResponse<String> response, JavaType type) {
        try {
            return json.readValue(response.body(), type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unexpected response from API: " + response.body(), e);
        }
    }

    public record AccountView(UUID id, String ownerName, BigDecimal balance, Instant createdAt) {
    }

    public record TransactionView(UUID id, String type, BigDecimal amount, BigDecimal balanceAfter,
                                  UUID transferId, Instant createdAt) {
    }

    public record TransferView(UUID transferId, AccountView from, AccountView to) {
    }

    record ErrorBody(String code, String message, String field) {
    }
}
