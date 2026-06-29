package com.leekyungmi.apm.apm_server.domain.event.repository;

import com.leekyungmi.apm.apm_server.domain.event.dto.EventSearch;
import com.leekyungmi.apm.apm_server.domain.event.entity.Event;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface EventCustomRepository {
    Page<Event> search(EventSearch search, Pageable pageable);
}
