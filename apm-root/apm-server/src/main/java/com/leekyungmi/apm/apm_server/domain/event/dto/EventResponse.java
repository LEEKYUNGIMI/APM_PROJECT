package com.leekyungmi.apm.apm_server.domain.event.dto;

import com.leekyungmi.apm.apm_server.domain.event.EventType;
import com.leekyungmi.apm.apm_server.domain.event.entity.Event;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class EventResponse {
    private Long id;
    private String appId;
    private EventType type;
    private String uri;
    private String method;
    private int statusCode;
    private int durationMs;
    private String message;
    private String serverIp;
    private LocalDateTime occurredAt;
    private String traceId;
    private String spanId;
    private String parentSpanId;
    private long startTimeMs;
    private Long startTimeUs;
    private Long durationUs;

    public static EventResponse from(Event event) {
        return EventResponse.builder()
                .id(event.getId())
                .appId(event.getAppId())
                .type(event.getType())
                .uri(event.getUri())
                .method(event.getMethod())
                .statusCode(event.getStatusCode())
                .durationMs(event.getDurationMs())
                .message(event.getMessage())
                .serverIp(event.getServerIp())
                .occurredAt(event.getOccurredAt())
                .traceId(event.getTraceId())
                .spanId(event.getSpanId())
                .parentSpanId(event.getParentSpanId())
                .startTimeMs(event.getStartTimeMs())
                .startTimeUs(event.getStartTimeUs())
                .durationUs(event.getDurationUs())
                .build();
    }
}
