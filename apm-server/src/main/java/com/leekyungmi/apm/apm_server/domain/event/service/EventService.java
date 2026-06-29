package com.leekyungmi.apm.apm_server.domain.event.service;

import com.leekyungmi.apm.apm_server.domain.event.repository.EventRepository;
import com.leekyungmi.apm.apm_server.domain.event.dto.EventRequest;
import com.leekyungmi.apm.apm_server.domain.event.dto.EventResponse;
import com.leekyungmi.apm.apm_server.domain.event.dto.EventSearch;
import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

}
