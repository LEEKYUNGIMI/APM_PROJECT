package com.leekyungmi.apm.apm_server.domain.event.dto;

import com.leekyungmi.apm.apm_server.domain.event.EventType;
import com.leekyungmi.apm.apm_server.domain.event.entity.Event;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class EventResponse {
    private String appId;
    private EventType type;
    private String uri;
    private String method;
    private int statusCode;
    private int durationMs;
    private String message;
    private String serverIp;
    private LocalDateTime occurredAt;

    public static EventResponse from(Event event) {
        return EventResponse.builder()
                .appId(event.getAppId())
                .type(event.getType())
                .uri(event.getUri())
                .method(event.getMethod())
                .statusCode(event.getStatusCode())
                .durationMs(event.getDurationMs())
                .message(event.getMessage())
                .serverIp(event.getServerIp())
                .occurredAt(event.getOccurredAt())
                .build();
    }
}
