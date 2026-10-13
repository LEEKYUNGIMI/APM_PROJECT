package com.leekyungmi.apm.simple.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final Map<Long, Map<String, Object>> store = new ConcurrentHashMap<>();
    private final AtomicLong idSeq = new AtomicLong(1);

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> list() {
        return ResponseEntity.ok(List.copyOf(store.values()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> get(@PathVariable Long id) {
        Map<String, Object> product = store.get(id);
        if (product == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(product);
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        long id = idSeq.getAndIncrement();
        body.put("id", id);
        store.put(id, body);
        return ResponseEntity.ok(body);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (store.remove(id) == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.noContent().build();
    }

    // SLOW_QUERY 시연: 2초 이상 걸리면 APM SDK가 SLOW_QUERY로 분류
    @GetMapping("/slow")
    public ResponseEntity<String> slow() throws InterruptedException {
        Thread.sleep(2500);
        return ResponseEntity.ok("느린 응답 완료");
    }

    // ERROR 시연: 예외 발생 시 APM SDK가 ERROR로 분류
    @GetMapping("/error")
    public ResponseEntity<String> error() {
        throw new RuntimeException("의도적으로 발생시킨 에러 - APM ERROR 이벤트 테스트");
    }
}