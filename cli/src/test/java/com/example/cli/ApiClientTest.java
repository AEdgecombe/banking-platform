package com.example.cli;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Uses the JDK's built-in HTTP server as a stub API, so no running backend is needed. */
class ApiClientTest {

    private HttpServer server;
    private ApiClient client;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.start();
        client = new ApiClient(URI.create("http://localhost:" + server.getAddress().getPort()));
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void parsesAccount() {
        var id = UUID.randomUUID();
        stub("/api/accounts/" + id, 200, """
                {"id":"%s","ownerName":"Ada","balance":12.50,"createdAt":"2026-01-01T09:00:00Z"}
                """.formatted(id));

        var account = client.getAccount(id);

        assertThat(account.ownerName()).isEqualTo("Ada");
        assertThat(account.balance()).isEqualByComparingTo(new BigDecimal("12.50"));
    }

    @Test
    void turnsErrorBodyIntoApiException() {
        var id = UUID.randomUUID();
        stub("/api/accounts/" + id + "/withdrawals", 422, """
                {"code":"INSUFFICIENT_FUNDS","message":"Account has insufficient funds","field":"amount"}
                """);

        assertThatThrownBy(() -> client.withdraw(id, new BigDecimal("10.00")))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.status()).isEqualTo(422);
                    assertThat(e.code()).isEqualTo("INSUFFICIENT_FUNDS");
                    assertThat(e.field()).isEqualTo("amount");
                });
    }

    @Test
    void unreachableApiIsReportedNotThrownRaw() {
        server.stop(0);

        assertThatThrownBy(() -> client.listAccounts())
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("UNREACHABLE"));
    }

    private void stub(String path, int status, String body) {
        server.createContext(path, exchange -> {
            var bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (var os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
    }
}
