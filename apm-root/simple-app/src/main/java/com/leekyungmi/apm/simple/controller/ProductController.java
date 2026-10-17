package com.leekyungmi.apm.simple.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.web.bind.annotation.*;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private static final RowMapper<Map<String, Object>> PRODUCT = (rs, rowNum) -> {
        Map<String, Object> product = new LinkedHashMap<>();
        product.put("id", rs.getLong("id"));
        product.put("name", rs.getString("name"));
        product.put("price", rs.getInt("price"));
        return product;
    };

    private final JdbcTemplate jdbcTemplate;

    public ProductController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // 일반 조회: 쿼리 1번 → DB_QUERY span 1개
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> list() {
        return ResponseEntity.ok(jdbcTemplate.query("SELECT id, name, price FROM products ORDER BY id", PRODUCT));
    }

    // N+1 느린 조회: id 목록 1번 + 상품마다 1번(각 110ms) → 20개면 약 2.2초, SLOW_REQUEST + DB_QUERY span 21개
    @GetMapping("/n-plus-one")
    public ResponseEntity<List<Map<String, Object>>> nPlusOne() {
        List<Long> ids = jdbcTemplate.queryForList("SELECT id FROM products ORDER BY id", Long.class);
        List<Map<String, Object>> products = new ArrayList<>();
        for (Long id : ids) {
            products.addAll(jdbcTemplate.query(
                    "SELECT id, name, price FROM products WHERE id = ? AND SLEEP(110) = 0", PRODUCT, id));
        }
        return ResponseEntity.ok(products);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> get(@PathVariable Long id) {
        List<Map<String, Object>> found = jdbcTemplate.query("SELECT id, name, price FROM products WHERE id = ?", PRODUCT, id);
        if (found.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(found.get(0));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement("INSERT INTO products (name, price) VALUES (?, ?)", Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, String.valueOf(body.get("name")));
            ps.setInt(2, body.get("price") instanceof Number n ? n.intValue() : 0);
            return ps;
        }, keyHolder);
        body.put("id", keyHolder.getKey().longValue());
        return ResponseEntity.ok(body);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (jdbcTemplate.update("DELETE FROM products WHERE id = ?", id) == 0) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.noContent().build();
    }

    // SLOW_REQUEST 시연: 2초 이상 걸리면 APM SDK가 SLOW_REQUEST로 분류
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
