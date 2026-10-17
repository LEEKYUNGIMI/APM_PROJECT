package com.leekyungmi.apm.simple.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * 분산 추적 시연: 자기 자신의 /api/products를 RestClient로 호출한다.
 * trace 1개 = 서버 span(/api/orders) → HTTP_CLIENT span → 서버 span(/api/products) → DB_QUERY span
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final RestClient restClient;

    // 자동설정된 RestClient.Builder를 써야 SDK의 HTTP_CLIENT 계측(traceparent 주입)이 붙는다
    public OrderController(RestClient.Builder builder, @Value("${server.port:8080}") int port) {
        this.restClient = builder.baseUrl("http://localhost:" + port).build();
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> order() {
        List<Map<String, Object>> products = restClient.get()
                .uri("/api/products")
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        return ResponseEntity.ok(Map.of("orderId", System.currentTimeMillis(), "products", products));
    }
}
