package com.leekyungmi.apm.apm_server.domain.event.service;

import com.leekyungmi.apm.apm_server.domain.event.EventType;
import com.leekyungmi.apm.apm_server.domain.event.repository.EventRepository;
import com.leekyungmi.apm.apm_server.domain.event.dto.EventRequest;
import com.leekyungmi.apm.apm_server.domain.event.dto.EventResponse;
import com.leekyungmi.apm.apm_server.domain.event.dto.EventSearch;
import com.leekyungmi.apm.apm_server.domain.event.dto.EventStatsResponse;
import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventService {

    private final EventRepository eventRepository;

    @Transactional
    public Long save(EventRequest eventRequest){
        return eventRepository.save(eventRequest.toEntity()).getId();
    }

    public Page<EventResponse> search(EventSearch search, Pageable pageable){
        return eventRepository.search(search, pageable)
                .map(EventResponse::from);
    }

    public EventStatsResponse getStats(String appId, LocalDateTime from, LocalDateTime to){
        Map<EventType, Long> countByType = eventRepository.countByType(appId, from, to);
        long totalCount = countByType.values().stream().mapToLong(Long::longValue).sum();

        return EventStatsResponse.builder()
                .totalCount(totalCount)
                .avgDurationMs(eventRepository.findAvgDurationsMs(appId, from, to))
                .errorRatePercent(eventRepository.findErrorRate(appId, from, to))
                .countByType(countByType)
                .build();
    }

    public List<String> getAppIds(){
        return eventRepository.findDistinctAppIds();
    }

}
