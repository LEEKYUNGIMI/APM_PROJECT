package com.leekyungmi.apm.sdk.sender;

import com.leekyungmi.apm.sdk.buffer.RingBuffer;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ApmEventSender {
    private static final int BUFFER_CAPACITY = 4096;
    private static final int BATCH_SIZE = 100;
    private static final long IDLE_SLEEP_MS = 100;

    private final RestTemplate restTemplate;
    private final String apmServerUrl;
    private final RingBuffer<Map<String, Object>> buffer = new RingBuffer<>(BUFFER_CAPACITY);
    private final Thread worker;
    private volatile boolean running = true;


    public ApmEventSender(String apmServerUrl) {
        this.restTemplate = new RestTemplate();
        this.apmServerUrl = apmServerUrl;
        this.worker = new Thread(this::runLoop, "apm-event-sender");
        this.worker.setDaemon(true);
        this.worker.start();
    }

    // 요청 스레드에서 호출된다. HTTP 전송은 하지 않고 버퍼에 넣기만 한다.
    public void send(String sppId, String type, String uri, String method, int statusCode, int durationMs, String message, String serverIp) {
        Map<String, Object> body = new HashMap<>();
        body.put("appId", sppId);
        body.put("type", type);
        body.put("uri", uri);
        body.put("method", method);
        body.put("statusCode", statusCode);
        body.put("durationMs", durationMs);
        body.put("message", message);
        body.put("serverIp", serverIp);
        body.put("occurredAt", LocalDateTime.now()); // 전송 시점이 아닌 발생 시점 기록

        buffer.offer(body); // 가득 차면 버려진다 (droppedCount에 집계)
    }

    private void runLoop() {
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

    private void sendBatch(List<Map<String, Object>> batch) {
        for (Map<String, Object> body : batch) {
            try{
                restTemplate.postForObject(apmServerUrl + "/api/events", body, Void.class);
            }catch (Exception e){
                System.err.println("Failed to send event to APM server: " + e.getMessage());
            }
        }
        batch.clear();
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
