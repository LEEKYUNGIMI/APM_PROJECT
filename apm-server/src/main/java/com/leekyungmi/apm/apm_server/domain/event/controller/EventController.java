package com.leekyungmi.apm.apm_server.domain.event.controller;

import com.leekyungmi.apm.apm_server.domain.event.dto.EventRequest;
import com.leekyungmi.apm.apm_server.domain.event.dto.EventResponse;
import com.leekyungmi.apm.apm_server.domain.event.dto.EventSearch;
import com.leekyungmi.apm.apm_server.domain.event.dto.EventStatsResponse;
import com.leekyungmi.apm.apm_server.domain.event.service.EventService;
import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

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

    @GetMapping("/stats")
    public ResponseEntity<EventStatsResponse> stats(
            @RequestParam(required = false) String appId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to){
        return ResponseEntity.ok(eventService.getStats(appId, from, to));
    }

    @GetMapping("/apps")
    public ResponseEntity<List<String>> apps(){
        return ResponseEntity.ok(eventService.getAppIds());
    }
}
