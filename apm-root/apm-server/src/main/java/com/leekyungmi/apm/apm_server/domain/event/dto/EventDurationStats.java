package com.leekyungmi.apm.apm_server.domain.event.dto;

/** 요청 타입 span의 응답시간 집계(평균·p95·p99). 데이터 없으면 모두 0. */
public record EventDurationStats(double avgDurationMs, double p95DurationMs, double p99DurationMs) {
}
