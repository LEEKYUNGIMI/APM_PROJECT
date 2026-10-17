package com.leekyungmi.apm.apm_server.domain.event.controller;

import com.leekyungmi.apm.apm_server.domain.event.dto.EventResponse;
import com.leekyungmi.apm.apm_server.domain.event.service.EventService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** trace = 같은 traceId를 가진 event(span) 묶음. 별도 엔티티가 없어 event 도메인에 둔다. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/traces")
public class TraceController {

    private final EventService eventService;

    @GetMapping("/{traceId}")
    public ResponseEntity<List<EventResponse>> trace(@PathVariable String traceId){
        return ResponseEntity.ok(eventService.getTrace(traceId));
    }
}
