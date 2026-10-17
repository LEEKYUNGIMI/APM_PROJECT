package com.leekyungmi.apm.sdk.trace;

import java.util.HexFormat;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 요청 스레드의 "현재 서버 span"을 ThreadLocal로 들고 있는다.
 * ApmTraceFilter가 start/clear 하고, DB·HTTP 클라이언트 계측은 current()를 부모로 자식 span을 만든다.
 */
public final class TraceContext {

    public static final String TRACEPARENT = "traceparent";

    private static final ThreadLocal<SpanContext> CURRENT = new ThreadLocal<>();
    private static final HexFormat HEX = HexFormat.of(); // 소문자

    private TraceContext() {
    }

    /**
     * 서버 span을 시작한다. traceparent가 유효하면 그 trace를 잇고(부모 = 헤더의 spanId),
     * 없거나 형식 오류면 새 trace의 루트 span이 된다.
     */
    public static SpanContext start(String traceparent) {
        SpanContext remote = parseTraceparent(traceparent);
        SpanContext span = remote != null
                ? remote.newChild()
                : new SpanContext(newTraceId(), newSpanId(), null);
        CURRENT.set(span);
        return span;
    }

    public static SpanContext current() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }

    /**
     * W3C traceparent "{version}-{traceId}-{spanId}-{flags}" 파싱.
     * 반환값의 spanId는 원격(부모) span의 id, parentSpanId는 null. 형식 오류면 null.
     */
    public static SpanContext parseTraceparent(String header) {
        if (header == null) {
            return null;
        }
        String[] parts = header.trim().split("-", -1);
        if (parts.length < 4) {
            return null;
        }
        String version = parts[0];
        String traceId = parts[1];
        String spanId = parts[2];
        String flags = parts[3];
        if (!isLowerHex(version, 2) || version.equals("ff")) {
            return null;
        }
        if (version.equals("00") && parts.length != 4) {
            return null;
        }
        if (!isLowerHex(traceId, 32) || isAllZero(traceId)
                || !isLowerHex(spanId, 16) || isAllZero(spanId)
                || !isLowerHex(flags, 2)) {
            return null;
        }
        return new SpanContext(traceId, spanId, null);
    }

    public static String newTraceId() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        return HEX.toHexDigits(random.nextLong()) + HEX.toHexDigits(random.nextLong());
    }

    public static String newSpanId() {
        return HEX.toHexDigits(ThreadLocalRandom.current().nextLong());
    }

    private static boolean isLowerHex(String s, int length) {
        if (s.length() != length) {
            return false;
        }
        for (int i = 0; i < length; i++) {
            char c = s.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isAllZero(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) != '0') {
                return false;
            }
        }
        return true;
    }
}
