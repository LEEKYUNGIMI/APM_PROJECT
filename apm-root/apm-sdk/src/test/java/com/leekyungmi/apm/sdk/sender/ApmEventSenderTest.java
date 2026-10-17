package com.leekyungmi.apm.sdk.sender;

import com.leekyungmi.apm.sdk.trace.SpanContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** 이벤트 본문의 μs 필드와 거기서 파생한 ms 필드(계약 §6)를 검증한다. */
class ApmEventSenderTest {

    private static final SpanContext SPAN = new SpanContext("4bf92f3577b34da6a3ce929d0e0e4736", "00f067aa0ba902b7", null);

    private final ApmEventSender sender = new ApmEventSender("http://127.0.0.1:1", "test-app");

    @AfterEach
    void tearDown() {
        sender.shutdown();
    }

    @Test
    void μs_필드를_채우고_ms_필드는_μs에서_내림으로_파생한다() {
        Map<String, Object> event = sender.toEvent(SPAN, "DB_QUERY", "SELECT 1", "SQL", 0,
                1_760_000_000_123_999L, 1_999L, null);

        assertEquals(1_760_000_000_123_999L, event.get("startTimeUs"));
        assertEquals(1_999L, event.get("durationUs"));
        assertEquals(1_760_000_000_123L, event.get("startTimeMs"));
        assertEquals(1, event.get("durationMs"));
    }

    @Test
    void 밀리초_미만_소요는_durationMs_0이지만_durationUs는_보존된다() {
        Map<String, Object> event = sender.toEvent(SPAN, "DB_QUERY", "SELECT 1", "SQL", 0,
                1_760_000_000_000_000L, 340L, null);

        assertEquals(340L, event.get("durationUs"));
        assertEquals(0, event.get("durationMs"));
    }

    @Test
    void nowEpochUs는_현재_epoch_밀리초와_일치하는_마이크로초다() {
        long beforeMs = System.currentTimeMillis();
        long us = ApmEventSender.nowEpochUs();
        long afterMs = System.currentTimeMillis();

        assertTrue(us / 1000 >= beforeMs && us / 1000 <= afterMs, "nowEpochUs=" + us);
    }
}
