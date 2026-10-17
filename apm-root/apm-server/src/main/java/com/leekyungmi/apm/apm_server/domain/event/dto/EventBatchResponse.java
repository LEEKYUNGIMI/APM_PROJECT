package com.leekyungmi.apm.apm_server.domain.event.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class EventBatchResponse {
    private int received;
}
