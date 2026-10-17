package com.leekyungmi.apm.apm_server.domain.event.controller;

import com.leekyungmi.apm.apm_server.domain.event.dto.EventBatchResponse;
import com.leekyungmi.apm.apm_server.domain.event.dto.EventRequest;
import com.leekyungmi.apm.apm_server.domain.event.dto.EventResponse;
import com.leekyungmi.apm.apm_server.domain.event.dto.EventSearch;
import com.leekyungmi.apm.apm_server.domain.event.dto.EventStatsResponse;
import com.leekyungmi.apm.apm_server.domain.event.dto.EventTimeseriesResponse;
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

    @PostMapping("/batch")
    public ResponseEntity<EventBatchResponse> createBatch(@RequestBody List<EventRequest> eventRequests){
        return ResponseEntity.ok(eventService.saveAll(eventRequests));
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

    @GetMapping("/timeseries")
    public ResponseEntity<List<EventTimeseriesResponse>> timeseries(
            @RequestParam(required = false) String appId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "60") int bucketSeconds){
        return ResponseEntity.ok(eventService.getTimeseries(appId, from, to, bucketSeconds));
    }

    @GetMapping("/apps")
    public ResponseEntity<List<String>> apps(){
        return ResponseEntity.ok(eventService.getAppIds());
    }
}
