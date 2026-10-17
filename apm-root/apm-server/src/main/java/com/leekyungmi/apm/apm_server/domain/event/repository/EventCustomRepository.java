package com.leekyungmi.apm.apm_server.domain.event.repository;

import com.leekyungmi.apm.apm_server.domain.event.EventType;
import com.leekyungmi.apm.apm_server.domain.event.dto.EventDurationStats;
import com.leekyungmi.apm.apm_server.domain.event.dto.EventSearch;
import com.leekyungmi.apm.apm_server.domain.event.entity.Event;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface EventCustomRepository {
    Page<Event> search(EventSearch search, Pageable pageable);

    EventDurationStats findRequestDurationStats(String appId, LocalDateTime from, LocalDateTime to);

    Map<EventType, Long> countByType(String appId, LocalDateTime from, LocalDateTime to);

    List<String> findDistinctAppIds();

    List<Event> findTraceSpans(String traceId);
}
