package com.leekyungmi.apm.apm_server.domain.event.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class EventTimeseriesResponse {
    private LocalDateTime bucketStart;
    private long count;
    private double avgDurationMs;
    private double p95DurationMs;
    private double p99DurationMs;
    private long errorCount;
}
