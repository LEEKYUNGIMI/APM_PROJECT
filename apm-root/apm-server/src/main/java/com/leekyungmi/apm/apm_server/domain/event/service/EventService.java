package com.leekyungmi.apm.apm_server.domain.event.service;

import com.leekyungmi.apm.apm_server.domain.event.EventType;
import com.leekyungmi.apm.apm_server.domain.event.repository.EventJdbcRepository;
import com.leekyungmi.apm.apm_server.domain.event.repository.EventRepository;
import com.leekyungmi.apm.apm_server.domain.event.dto.EventBatchResponse;
import com.leekyungmi.apm.apm_server.domain.event.dto.EventDurationStats;
import com.leekyungmi.apm.apm_server.domain.event.dto.EventRequest;
import com.leekyungmi.apm.apm_server.domain.event.dto.EventResponse;
import com.leekyungmi.apm.apm_server.domain.event.dto.EventSearch;
import com.leekyungmi.apm.apm_server.domain.event.dto.EventStatsResponse;
import com.leekyungmi.apm.apm_server.domain.event.dto.EventTimeseriesResponse;
import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventService {

    public static final int MAX_BATCH_SIZE = 1000;

    private final EventRepository eventRepository;
    private final EventJdbcRepository eventJdbcRepository;

    @Transactional
    public Long save(EventRequest eventRequest){
        return eventRepository.save(eventRequest.toEntity()).getId();
    }

    @Transactional
    public EventBatchResponse saveAll(List<EventRequest> eventRequests){
        if (eventRequests.size() > MAX_BATCH_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "batch size " + eventRequests.size() + " exceeds max " + MAX_BATCH_SIZE);
        }
        if (!eventRequests.isEmpty()) {
            eventJdbcRepository.batchInsert(eventRequests);
        }
        return EventBatchResponse.builder().received(eventRequests.size()).build();
    }

    public Page<EventResponse> search(EventSearch search, Pageable pageable){
        return eventRepository.search(search, pageable)
                .map(EventResponse::from);
    }

    public EventStatsResponse getStats(String appId, LocalDateTime from, LocalDateTime to){
        Map<EventType, Long> countByType = eventRepository.countByType(appId, from, to);
        // total·에러율은 요청 타입만 대상, countByType은 전 타입
        long totalCount = EventType.REQUEST_TYPES.stream().mapToLong(countByType::get).sum();
        long errorCount = countByType.get(EventType.ERROR);
        EventDurationStats durationStats = eventRepository.findRequestDurationStats(appId, from, to);

        return EventStatsResponse.builder()
                .totalCount(totalCount)
                .avgDurationMs(durationStats.avgDurationMs())
                .p95DurationMs(durationStats.p95DurationMs())
                .p99DurationMs(durationStats.p99DurationMs())
                .errorRatePercent(totalCount > 0 ? errorCount * 100.0 / totalCount : 0.0)
                .countByType(countByType)
                .build();
    }

    public List<EventTimeseriesResponse> getTimeseries(String appId, LocalDateTime from, LocalDateTime to, int bucketSeconds){
        if (bucketSeconds <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "bucketSeconds must be positive");
        }
        return eventJdbcRepository.findTimeseries(appId, from, to, bucketSeconds);
    }

    public List<EventResponse> getTrace(String traceId){
        return eventRepository.findTraceSpans(traceId).stream()
                .map(EventResponse::from)
                .toList();
    }

    public List<String> getAppIds(){
        return eventRepository.findDistinctAppIds();
    }

}
