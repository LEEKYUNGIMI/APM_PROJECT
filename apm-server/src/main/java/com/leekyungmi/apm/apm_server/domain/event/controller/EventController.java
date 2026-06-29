package com.leekyungmi.apm.apm_server.domain.event.controller;

import com.leekyungmi.apm.apm_server.domain.event.dto.EventRequest;
import com.leekyungmi.apm.apm_server.domain.event.dto.EventResponse;
import com.leekyungmi.apm.apm_server.domain.event.dto.EventSearch;
import com.leekyungmi.apm.apm_server.domain.event.service.EventService;
import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/events")
public class EventController {

    private final EventService eventService;

    @PostMapping
    public ResponseEntity<Long> create(@RequestBody EventRequest eventRequest){
        return ResponseEntity.ok(eventService.save(eventRequest));
    }

    @GetMapping
    public ResponseEntity<Page<EventResponse>> search(EventSearch search, Pageable pageable){
        return ResponseEntity.ok(eventService.search(search, pageable));
    }
}
