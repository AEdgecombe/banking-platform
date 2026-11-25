package com.example;

import com.example.web.ApiModels.AccountResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full stack: HTTP -> service -> PostgreSQL, with Redis caching. Needs Docker.
 * Spring Boot turns metrics export off in tests by default, which would hide
 * /actuator/prometheus, so this test switches it back on.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureObservability(tracing = false)
@Testcontainers
class BankingApplicationIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Container
    @ServiceConnection(name = "redis")
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @Autowired
    TestRestTemplate http;

    @Test
    void openThenFetchAccount_twiceSoTheSecondReadComesFromRedis() {
        var created = http.postForEntity("/api/accounts", Map.of("ownerName", "Ada"), AccountResponse.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        var id = created.getBody().id();

        var first = http.getForObject("/api/accounts/{id}", AccountResponse.class, id);
        var second = http.getForObject("/api/accounts/{id}", AccountResponse.class, id);

        assertThat(first).isEqualTo(created.getBody());
        assertThat(second).isEqualTo(first);
    }

    @Test
    void prometheusEndpointExposesBusinessCounters() {
        var body = http.getForObject("/actuator/prometheus", String.class);

        assertThat(body)
                .contains("transfers_completed_total")
                .contains("transfers_failed_total")
                .contains("overdraft_rejections_total");
    }

    @Test
    void openApiSpecIsPublished() {
        var response = http.getForEntity("/v3/api-docs", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("/api/transfers");
    }
}
