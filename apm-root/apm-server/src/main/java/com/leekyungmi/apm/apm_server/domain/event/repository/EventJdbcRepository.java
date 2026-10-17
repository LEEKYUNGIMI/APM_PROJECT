package com.leekyungmi.apm.apm_server.domain.event.repository;

import com.leekyungmi.apm.apm_server.domain.event.EventType;
import com.leekyungmi.apm.apm_server.domain.event.dto.EventRequest;
import com.leekyungmi.apm.apm_server.domain.event.dto.EventTimeseriesResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * JPA로 표현하기 어려운 작업 전용: 배치 INSERT, 시간 버킷 + percentile_cont 시계열 집계(네이티브 SQL).
 */
@Repository
@RequiredArgsConstructor
public class EventJdbcRepository {

    private static final String INSERT_SQL = """
            INSERT INTO events (app_id, type, uri, method, status_code, duration_ms, message, server_ip,
                                occurred_at, trace_id, span_id, parent_span_id, start_time_ms,
                                start_time_us, duration_us)
            VALUES (:appId, :type, :uri, :method, :statusCode, :durationMs, :message, :serverIp,
                    :occurredAt, :traceId, :spanId, :parentSpanId, :startTimeMs, :startTimeUs, :durationUs)
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public void batchInsert(List<EventRequest> requests) {
        SqlParameterSource[] params = requests.stream()
                .map(r -> new MapSqlParameterSource()
                        .addValue("appId", r.getAppId())
                        .addValue("type", r.getType() != null ? r.getType().name() : null)
                        .addValue("uri", r.getUri())
                        .addValue("method", r.getMethod())
                        .addValue("statusCode", r.getStatusCode())
                        .addValue("durationMs", r.getDurationMs())
                        .addValue("message", r.getMessage())
                        .addValue("serverIp", r.getServerIp())
                        .addValue("occurredAt", r.getOccurredAt())
                        .addValue("traceId", r.getTraceId())
                        .addValue("spanId", r.getSpanId())
                        .addValue("parentSpanId", r.getParentSpanId())
                        .addValue("startTimeMs", r.getStartTimeMs())
                        .addValue("startTimeUs", r.getStartTimeUs())
                        .addValue("durationUs", r.getDurationUs()))
                .toArray(SqlParameterSource[]::new);
        jdbcTemplate.batchUpdate(INSERT_SQL, params);
    }

    public List<EventTimeseriesResponse> findTimeseries(String appId, LocalDateTime from, LocalDateTime to, int bucketSeconds) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("bucketSeconds", bucketSeconds)
                .addValue("types", EventType.REQUEST_TYPES.stream().map(Enum::name).toList())
                .addValue("errorType", EventType.ERROR.name());

        StringBuilder where = new StringBuilder("WHERE type IN (:types)");
        if (StringUtils.hasText(appId)) {
            where.append(" AND app_id = :appId");
            params.addValue("appId", appId);
        }
        if (from != null) {
            where.append(" AND occurred_at >= :from");
            params.addValue("from", from);
        }
        if (to != null) {
            where.append(" AND occurred_at <= :to");
            params.addValue("to", to);
        }

        String sql = """
                SELECT TIMESTAMP '1970-01-01 00:00:00'
                           + CAST(FLOOR(EXTRACT(EPOCH FROM occurred_at) / :bucketSeconds) * :bucketSeconds AS DOUBLE PRECISION)
                             * INTERVAL '1 second' AS bucket_start,
                       COUNT(*) AS cnt,
                       COALESCE(AVG(duration_ms), 0) AS avg_ms,
                       COALESCE(PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY duration_ms), 0) AS p95_ms,
                       COALESCE(PERCENTILE_CONT(0.99) WITHIN GROUP (ORDER BY duration_ms), 0) AS p99_ms,
                       COUNT(*) FILTER (WHERE type = :errorType) AS error_count
                FROM events
                """ + where + """

                GROUP BY 1
                ORDER BY 1
                """;

        return jdbcTemplate.query(sql, params, (rs, rowNum) -> EventTimeseriesResponse.builder()
                .bucketStart(rs.getObject("bucket_start", LocalDateTime.class))
                .count(rs.getLong("cnt"))
                .avgDurationMs(rs.getDouble("avg_ms"))
                .p95DurationMs(rs.getDouble("p95_ms"))
                .p99DurationMs(rs.getDouble("p99_ms"))
                .errorCount(rs.getLong("error_count"))
                .build());
    }
}
