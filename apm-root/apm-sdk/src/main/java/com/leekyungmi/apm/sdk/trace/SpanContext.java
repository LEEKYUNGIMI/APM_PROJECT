package com.leekyungmi.apm.sdk.trace;

/**
 * span 하나의 식별자 묶음. traceId 32자·spanId 16자 소문자 hex (W3C trace-context).
 * parentSpanId가 null이면 루트 span.
 */
public record SpanContext(String traceId, String spanId, String parentSpanId) {

    /** 이 span의 자식 span (같은 trace, 부모 = 이 span). */
    public SpanContext newChild() {
        return new SpanContext(traceId, TraceContext.newSpanId(), spanId);
    }

    /** 이 span을 부모로 하라고 상대에게 보낼 traceparent 헤더 값. */
    public String toTraceparent() {
        return "00-" + traceId + "-" + spanId + "-01";
    }
}
