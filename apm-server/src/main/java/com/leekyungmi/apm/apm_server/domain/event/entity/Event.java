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
@Table(name = "events")
public class Event {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String appId;

    @Enumerated(EnumType.STRING)
    private EventType type;

    private String uri;

    private String method;

    private int statusCode;

    private int durationMs;

    private String message;

    private String serverIp;

    private LocalDateTime occurredAt;
}
