package com.leekyungmi.apm.sdk.sender;

import com.leekyungmi.apm.sdk.buffer.RingBuffer;
import com.leekyungmi.apm.sdk.trace.SpanContext;
import org.springframework.web.client.RestTemplate;

import java.net.InetAddress;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ApmEventSender {
    private static final int BUFFER_CAPACITY = 4096;
    private static final int BATCH_SIZE = 100;
    private static final long IDLE_SLEEP_MS = 100;

    // SDK 내부 전송용. 일부러 RestTemplateBuilder를 쓰지 않는다 → HTTP_CLIENT 계측 인터셉터가 붙지 않음(무한 루프 방지)
    private final RestTemplate restTemplate;
    private final String apmServerUrl;
    private final String appId;
    private final RingBuffer<Map<String, Object>> buffer = new RingBuffer<>(BUFFER_CAPACITY);
    private final Thread worker;
    private volatile boolean running = true;
    private String serverIp; // 전송 스레드에서 1회 조회해 캐싱 (요청 스레드에서 InetAddress 조회하지 않음)


    public ApmEventSender(String apmServerUrl, String appId) {
        this.restTemplate = new RestTemplate();
        this.apmServerUrl = apmServerUrl;
        this.appId = appId;
        this.worker = new Thread(this::runLoop, "apm-event-sender");
        this.worker.setDaemon(true);
        this.worker.start();
    }

    /** span 시작 시각용 epoch 마이크로초. 소요 시간은 이 값의 차이가 아니라 System.nanoTime() 차이로 잰다. */
    public static long nowEpochUs() {
        Instant now = Instant.now();
        return now.getEpochSecond() * 1_000_000 + now.getNano() / 1_000;
    }

    // 요청 스레드에서 호출된다. HTTP 전송은 하지 않고 버퍼에 넣기만 한다.
    public void send(SpanContext span, String type, String uri, String method, int statusCode,
                     long startTimeUs, long durationUs, String message) {
        buffer.offer(toEvent(span, type, uri, method, statusCode, startTimeUs, durationUs, message)); // 가득 차면 버려진다 (droppedCount에 집계)
    }

    Map<String, Object> toEvent(SpanContext span, String type, String uri, String method, int statusCode,
                                long startTimeUs, long durationUs, String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("appId", appId);
        body.put("type", type);
        body.put("uri", uri);
        body.put("method", method);
        body.put("statusCode", statusCode);
        body.put("durationMs", (int) (durationUs / 1000)); // 하위호환: μs에서 파생(내림)
        body.put("message", message);
        body.put("occurredAt", LocalDateTime.now().toString()); // 전송 시점이 아닌 발생 시점 기록. ISO-8601 문자열 (그냥 넣으면 Jackson이 배열로 직렬화)
        body.put("traceId", span.traceId());
        body.put("spanId", span.spanId());
        body.put("parentSpanId", span.parentSpanId());
        body.put("startTimeMs", startTimeUs / 1000); // 하위호환: μs에서 파생
        body.put("startTimeUs", startTimeUs);
        body.put("durationUs", durationUs);
        return body;
    }

    private void runLoop() {
        serverIp = resolveServerIp();
        List<Map<String, Object>> batch = new ArrayList<>(BATCH_SIZE);
        while (running) {
            if (buffer.drainTo(batch, BATCH_SIZE) == 0) {
                try {
                    Thread.sleep(IDLE_SLEEP_MS);
                } catch (InterruptedException e) {
                    break;
                }
                continue;
            }
            sendBatch(batch);
        }
        // 종료 시 남은 이벤트 전송
        while (buffer.drainTo(batch, BATCH_SIZE) > 0) {
            sendBatch(batch);
        }
    }

    // 최대 100건을 POST /api/events/batch 한 번으로 보낸다. 실패하면 로그 1줄 남기고 버린다(재시도 없음).
    private void sendBatch(List<Map<String, Object>> batch) {
        for (Map<String, Object> body : batch) {
            body.put("serverIp", serverIp);
        }
        try {
            restTemplate.postForObject(apmServerUrl + "/api/events/batch", batch, Void.class);
        } catch (Exception e) {
            System.err.println("Failed to send " + batch.size() + " events to APM server: " + e.getMessage());
        }
        batch.clear();
    }

    private static String resolveServerIp() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "unknown";
        }
    }

    public long droppedCount() {
        return buffer.droppedCount();
    }

    public void shutdown() {
        running = false;
        worker.interrupt();
        try {
            worker.join(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
