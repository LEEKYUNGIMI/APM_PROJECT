package com.leekyungmi.apm.apm_server.domain.event.repository;

import com.leekyungmi.apm.apm_server.domain.event.EventType;
import com.leekyungmi.apm.apm_server.domain.event.dto.EventDurationStats;
import com.leekyungmi.apm.apm_server.domain.event.dto.EventRequest;
import com.leekyungmi.apm.apm_server.domain.event.dto.EventResponse;
import com.leekyungmi.apm.apm_server.domain.event.dto.EventSearch;
import com.leekyungmi.apm.apm_server.domain.event.dto.EventTimeseriesResponse;
import com.leekyungmi.apm.apm_server.domain.event.entity.Event;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 로컬 PostgreSQL(apm_db) 필요. 개발 데이터(public 스키마)를 건드리지 않도록 apm_test 스키마를 쓰고, 각 테스트는 롤백된다.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/apm_db?currentSchema=apm_test",
        "spring.jpa.properties.hibernate.default_schema=apm_test",
        "spring.jpa.properties.hibernate.hbm2ddl.create_namespaces=true",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false"
})
@Transactional
class EventRepositoryIntegrationTest {

    private static final String TRACE = "0af7651916cd43dd8448eb211c80319c";
    private static final LocalDateTime BASE = LocalDateTime.of(2026, 10, 13, 10, 0, 0);

    @Autowired EventRepository eventRepository;
    @Autowired EventJdbcRepository eventJdbcRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    private EventRequest span(EventType type, int durationMs, LocalDateTime at, String spanId, String parent, long start) {
        return EventRequest.builder()
                .appId("app-a").type(type).uri("/x").method("GET").statusCode(200)
                .durationMs(durationMs).occurredAt(at)
                .traceId(TRACE).spanId(spanId).parentSpanId(parent).startTimeMs(start)
                .build();
    }

    @Test
    void batchInsert_thenTraceOrderedByStartTime() {
        eventJdbcRepository.batchInsert(List.of(
                span(EventType.DB_QUERY, 5, BASE, "bbbbbbbbbbbbbbbb", "aaaaaaaaaaaaaaaa", 1_002L),
                span(EventType.HTTP_REQUEST, 30, BASE, "aaaaaaaaaaaaaaaa", null, 1_000L),
                span(EventType.HTTP_CLIENT, 10, BASE, "cccccccccccccccc", "aaaaaaaaaaaaaaaa", 1_010L)));

        List<Event> spans = eventRepository.findTraceSpans(TRACE);

        assertThat(spans).extracting(Event::getSpanId)
                .containsExactly("aaaaaaaaaaaaaaaa", "bbbbbbbbbbbbbbbb", "cccccccccccccccc");
        assertThat(spans.get(0).getParentSpanId()).isNull();
        assertThat(eventRepository.findTraceSpans("ffffffffffffffffffffffffffffffff")).isEmpty();
    }

    @Test
    void batchInsert_storesMicros_andTraceOrdersByMicrosWithMsFallbackThenId() {
        EventRequest legacy = span(EventType.HTTP_REQUEST, 1, BASE, "aaaaaaaaaaaaaaaa", null, 1_000L);
        EventRequest tie = EventRequest.builder()
                .appId("app-a").type(EventType.DB_QUERY).occurredAt(BASE).traceId(TRACE)
                .spanId("bbbbbbbbbbbbbbbb").parentSpanId("aaaaaaaaaaaaaaaa")
                .startTimeMs(1_000L).startTimeUs(1_000_000L).durationUs(0L).build();
        EventRequest later = EventRequest.builder()
                .appId("app-a").type(EventType.DB_QUERY).occurredAt(BASE).traceId(TRACE)
                .spanId("cccccccccccccccc").parentSpanId("aaaaaaaaaaaaaaaa")
                .startTimeMs(1_000L).startTimeUs(1_000_340L).durationMs(0).durationUs(340L).build();
        EventRequest earlier = EventRequest.builder()
                .appId("app-a").type(EventType.HTTP_CLIENT).occurredAt(BASE).traceId(TRACE)
                .spanId("dddddddddddddddd").parentSpanId("aaaaaaaaaaaaaaaa")
                .startTimeMs(999L).startTimeUs(999_500L).durationUs(1_200L).build();
        eventJdbcRepository.batchInsert(List.of(legacy, tie, later, earlier));

        List<Event> spans = eventRepository.findTraceSpans(TRACE);

        // earlier(999500us) < legacy(null → 1000ms*1000) = tie(1000000us, id 큰 쪽이 뒤) < later(1000340us)
        assertThat(spans).extracting(Event::getSpanId)
                .containsExactly("dddddddddddddddd", "aaaaaaaaaaaaaaaa", "bbbbbbbbbbbbbbbb", "cccccccccccccccc");
        assertThat(spans.get(1).getStartTimeUs()).isNull();
        assertThat(spans.get(1).getDurationUs()).isNull();
        EventResponse micro = EventResponse.from(spans.get(3));
        assertThat(micro.getStartTimeUs()).isEqualTo(1_000_340L);
        assertThat(micro.getDurationUs()).isEqualTo(340L);
        assertThat(micro.getDurationMs()).isZero();
    }

    @Test
    void search_withoutType_returnsRequestTypesOnly() {
        eventJdbcRepository.batchInsert(List.of(
                span(EventType.HTTP_REQUEST, 30, BASE, "aaaaaaaaaaaaaaaa", null, 1L),
                span(EventType.ERROR, 30, BASE, "dddddddddddddddd", null, 2L),
                span(EventType.DB_QUERY, 5, BASE, "bbbbbbbbbbbbbbbb", "aaaaaaaaaaaaaaaa", 3L)));

        assertThat(eventRepository.search(new EventSearch(), PageRequest.of(0, 10)).getContent())
                .extracting(Event::getType)
                .containsExactlyInAnyOrder(EventType.HTTP_REQUEST, EventType.ERROR);
        assertThat(eventRepository.search(EventSearch.builder().type(EventType.DB_QUERY).build(), PageRequest.of(0, 10))
                .getTotalElements()).isEqualTo(1);
    }

    @Test
    void durationStats_percentilesOverRequestTypesOnly() {
        List<EventRequest> requests = new ArrayList<>();
        for (int i = 1; i <= 100; i++) {
            requests.add(span(EventType.HTTP_REQUEST, i, BASE, "aaaaaaaaaaaaaaaa", null, i));
        }
        requests.add(span(EventType.DB_QUERY, 100_000, BASE, "bbbbbbbbbbbbbbbb", "aaaaaaaaaaaaaaaa", 0L));
        eventJdbcRepository.batchInsert(requests);

        EventDurationStats stats = eventRepository.findRequestDurationStats("app-a", null, null);

        assertThat(stats.avgDurationMs()).isCloseTo(50.5, within(0.001));
        assertThat(stats.p95DurationMs()).isCloseTo(95.05, within(0.001));
        assertThat(stats.p99DurationMs()).isCloseTo(99.01, within(0.001));
        assertThat(eventRepository.findRequestDurationStats("no-such-app", null, null))
                .isEqualTo(new EventDurationStats(0.0, 0.0, 0.0));
    }

    @Test
    void timeseries_bucketsRequestTypesAscending() {
        eventJdbcRepository.batchInsert(List.of(
                span(EventType.HTTP_REQUEST, 10, BASE.plusSeconds(5), "aaaaaaaaaaaaaaaa", null, 1L),
                span(EventType.ERROR, 30, BASE.plusSeconds(50), "dddddddddddddddd", null, 2L),
                span(EventType.DB_QUERY, 999, BASE.plusSeconds(10), "bbbbbbbbbbbbbbbb", "aaaaaaaaaaaaaaaa", 3L),
                span(EventType.SLOW_REQUEST, 3000, BASE.plusSeconds(61), "eeeeeeeeeeeeeeee", null, 4L)));

        List<EventTimeseriesResponse> series = eventJdbcRepository.findTimeseries("app-a", BASE, BASE.plusMinutes(5), 60);

        assertThat(series).hasSize(2);
        assertThat(series.get(0).getBucketStart()).isEqualTo(BASE);
        assertThat(series.get(0).getCount()).isEqualTo(2);
        assertThat(series.get(0).getAvgDurationMs()).isEqualTo(20.0);
        assertThat(series.get(0).getErrorCount()).isEqualTo(1);
        assertThat(series.get(1).getBucketStart()).isEqualTo(BASE.plusMinutes(1));
        assertThat(series.get(1).getP99DurationMs()).isEqualTo(3000.0);
    }

    @Test
    void schema_hasIndexesAndWidenedColumns() {
        List<String> indexes = jdbcTemplate.queryForList(
                "SELECT indexname FROM pg_indexes WHERE schemaname = 'apm_test' AND tablename = 'events'", String.class);
        assertThat(indexes).contains("idx_events_trace_id", "idx_events_occurred_at");

        Integer uriLength = jdbcTemplate.queryForObject(
                "SELECT character_maximum_length FROM information_schema.columns "
                        + "WHERE table_schema = 'apm_test' AND table_name = 'events' AND column_name = 'uri'", Integer.class);
        assertThat(uriLength).isEqualTo(1000);
    }
}
