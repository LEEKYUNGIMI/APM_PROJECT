package com.leekyungmi.apm.apm_server.domain.event.dto;

import com.leekyungmi.apm.apm_server.domain.event.EventType;
import lombok.Builder;
import lombok.Getter;

import java.util.Map;

@Getter
@Builder
public class EventStatsResponse {
    private long totalCount;
    private double avgDurationMs;
    private double p95DurationMs;
    private double p99DurationMs;
    private double errorRatePercent;
    private Map<EventType, Long> countByType;
}
