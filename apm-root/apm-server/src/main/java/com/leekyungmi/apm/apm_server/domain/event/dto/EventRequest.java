package com.leekyungmi.apm.apm_server.domain.event.dto;

import com.leekyungmi.apm.apm_server.domain.event.entity.Event;
import com.leekyungmi.apm.apm_server.domain.event.EventType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EventRequest {

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

    public Event toEntity(){
        return Event.builder()
                .appId(appId)
                .type(type)
                .uri(uri)
                .method(method)
                .statusCode(statusCode)
                .durationMs(durationMs)
                .message(message)
                .serverIp(serverIp)
                .occurredAt(occurredAt)
                .traceId(traceId)
                .spanId(spanId)
                .parentSpanId(parentSpanId)
                .startTimeMs(startTimeMs)
                .startTimeUs(startTimeUs)
                .durationUs(durationUs)
                .build();
    }

}
