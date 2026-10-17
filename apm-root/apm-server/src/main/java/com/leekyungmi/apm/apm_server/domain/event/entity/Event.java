package com.leekyungmi.apm.apm_server.domain.event.entity;

import com.leekyungmi.apm.apm_server.domain.event.EventType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor
@Builder
@AllArgsConstructor
@Table(name = "events", indexes = {
        @Index(name = "idx_events_trace_id", columnList = "trace_id"),
        @Index(name = "idx_events_occurred_at", columnList = "occurred_at")
})
public class Event {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String appId;

    @Enumerated(EnumType.STRING)
    private EventType type;

    @Column(length = 1000)
    private String uri;

    private String method;

    private int statusCode;

    private int durationMs;

    @Column(length = 2000)
    private String message;

    private String serverIp;

    private LocalDateTime occurredAt;

    @Column(length = 32)
    private String traceId;

    @Column(length = 16)
    private String spanId;

    @Column(length = 16)
    private String parentSpanId;

    private long startTimeMs;

    private Long startTimeUs;

    private Long durationUs;
}
