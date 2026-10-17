package com.leekyungmi.apm.apm_server.domain.event.service;

import com.leekyungmi.apm.apm_server.domain.event.EventType;
import com.leekyungmi.apm.apm_server.domain.event.dto.EventDurationStats;
import com.leekyungmi.apm.apm_server.domain.event.dto.EventRequest;
import com.leekyungmi.apm.apm_server.domain.event.dto.EventStatsResponse;
import com.leekyungmi.apm.apm_server.domain.event.repository.EventJdbcRepository;
import com.leekyungmi.apm.apm_server.domain.event.repository.EventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock EventRepository eventRepository;
    @Mock EventJdbcRepository eventJdbcRepository;
    @InjectMocks EventService eventService;

    @Test
    void saveAll_rejectsOverMaxBatchSize() {
        List<EventRequest> requests = Collections.nCopies(EventService.MAX_BATCH_SIZE + 1, new EventRequest());

        assertThatThrownBy(() -> eventService.saveAll(requests))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");
        verify(eventJdbcRepository, never()).batchInsert(any());
    }

    @Test
    void saveAll_acceptsMaxBatchSize() {
        List<EventRequest> requests = Collections.nCopies(EventService.MAX_BATCH_SIZE, new EventRequest());

        assertThat(eventService.saveAll(requests).getReceived()).isEqualTo(1000);
        verify(eventJdbcRepository).batchInsert(requests);
    }

    @Test
    void saveAll_emptySkipsInsert() {
        assertThat(eventService.saveAll(List.of()).getReceived()).isZero();
        verify(eventJdbcRepository, never()).batchInsert(any());
    }

    @Test
    void getStats_totalAndErrorRateUseRequestTypesOnly() {
        Map<EventType, Long> counts = new EnumMap<>(EventType.class);
        counts.put(EventType.HTTP_REQUEST, 6L);
        counts.put(EventType.SLOW_REQUEST, 2L);
        counts.put(EventType.ERROR, 2L);
        counts.put(EventType.DB_QUERY, 50L);
        counts.put(EventType.HTTP_CLIENT, 5L);
        when(eventRepository.countByType(null, null, null)).thenReturn(counts);
        when(eventRepository.findRequestDurationStats(null, null, null))
                .thenReturn(new EventDurationStats(120.0, 900.0, 1900.0));

        EventStatsResponse stats = eventService.getStats(null, null, null);

        assertThat(stats.getTotalCount()).isEqualTo(10);
        assertThat(stats.getErrorRatePercent()).isEqualTo(20.0);
        assertThat(stats.getP95DurationMs()).isEqualTo(900.0);
        assertThat(stats.getP99DurationMs()).isEqualTo(1900.0);
        assertThat(stats.getCountByType()).containsEntry(EventType.DB_QUERY, 50L);
    }

    @Test
    void getStats_noDataGivesZeroErrorRate() {
        Map<EventType, Long> counts = new EnumMap<>(EventType.class);
        for (EventType type : EventType.values()) counts.put(type, 0L);
        when(eventRepository.countByType(null, null, null)).thenReturn(counts);
        when(eventRepository.findRequestDurationStats(null, null, null))
                .thenReturn(new EventDurationStats(0.0, 0.0, 0.0));

        assertThat(eventService.getStats(null, null, null).getErrorRatePercent()).isZero();
    }

    @Test
    void getTimeseries_rejectsNonPositiveBucket() {
        assertThatThrownBy(() -> eventService.getTimeseries(null, null, null, 0))
                .isInstanceOf(ResponseStatusException.class);
    }
}
