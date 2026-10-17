package com.leekyungmi.apm.sdk.trace;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TraceContextTest {

    private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
    private static final String SPAN_ID = "00f067aa0ba902b7";
    private static final String HEADER = "00-" + TRACE_ID + "-" + SPAN_ID + "-01";

    @AfterEach
    void tearDown() {
        TraceContext.clear();
    }

    @Test
    void 유효한_traceparent를_파싱한다() {
        SpanContext remote = TraceContext.parseTraceparent(HEADER);
        assertNotNull(remote);
        assertEquals(TRACE_ID, remote.traceId());
        assertEquals(SPAN_ID, remote.spanId());
    }

    @Test
    void 형식이_틀린_traceparent는_null() {
        assertNull(TraceContext.parseTraceparent(null));
        assertNull(TraceContext.parseTraceparent(""));
        assertNull(TraceContext.parseTraceparent("garbage"));
        assertNull(TraceContext.parseTraceparent("00-" + TRACE_ID.toUpperCase() + "-" + SPAN_ID + "-01")); // 대문자
        assertNull(TraceContext.parseTraceparent("00-" + TRACE_ID.substring(1) + "-" + SPAN_ID + "-01"));  // 길이
        assertNull(TraceContext.parseTraceparent("00-" + "0".repeat(32) + "-" + SPAN_ID + "-01"));       // 전부 0
        assertNull(TraceContext.parseTraceparent("00-" + TRACE_ID + "-" + "0".repeat(16) + "-01"));
        assertNull(TraceContext.parseTraceparent("ff-" + TRACE_ID + "-" + SPAN_ID + "-01"));             // 금지 버전
        assertNull(TraceContext.parseTraceparent(HEADER + "-extra"));                                     // 00 버전은 4필드
    }

    @Test
    void 헤더가_있으면_trace를_잇고_부모는_헤더의_spanId() {
        SpanContext span = TraceContext.start(HEADER);
        assertEquals(TRACE_ID, span.traceId());
        assertEquals(SPAN_ID, span.parentSpanId());
        assertNotEquals(SPAN_ID, span.spanId());
        assertTrue(span.spanId().matches("[0-9a-f]{16}"));
        assertSame(span, TraceContext.current());
    }

    @Test
    void 헤더가_없거나_틀리면_새_루트_span() {
        SpanContext span = TraceContext.start("not-a-traceparent");
        assertTrue(span.traceId().matches("[0-9a-f]{32}"));
        assertTrue(span.spanId().matches("[0-9a-f]{16}"));
        assertNull(span.parentSpanId());
    }

    @Test
    void 자식_span은_같은_trace이고_부모는_현재_span() {
        SpanContext parent = TraceContext.start(null);
        SpanContext child = parent.newChild();
        assertEquals(parent.traceId(), child.traceId());
        assertEquals(parent.spanId(), child.parentSpanId());
        assertEquals("00-" + child.traceId() + "-" + child.spanId() + "-01", child.toTraceparent());
        assertNotNull(TraceContext.parseTraceparent(child.toTraceparent()));
    }

    @Test
    void clear하면_현재_span이_사라진다() {
        TraceContext.start(null);
        TraceContext.clear();
        assertNull(TraceContext.current());
    }
}
