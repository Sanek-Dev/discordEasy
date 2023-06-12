package net.towerester.deasy.utils;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class SimpleHttpClientTest {
    public SimpleHttpClient httpClient;

    @BeforeEach
    public void init() {
        httpClient = new SimpleHttpClient.Builder()
                .setBaseUrl("https://httpbin.org")
                .build();
    }

    @Test
    public void processGet() {
        SimpleHttpClient.Request request = new SimpleHttpClient.Request.Builder("/ip", "GET").build();
        String response = httpClient.execute(request);

        Assertions.assertFalse(response.isEmpty());
    }
}
