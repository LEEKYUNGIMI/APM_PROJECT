package com.leekyungmi.apm.apm_server.domain.event;

import java.util.EnumSet;
import java.util.Set;

public enum EventType {
    HTTP_REQUEST,
    SLOW_REQUEST,
    ERROR,
    DB_QUERY,
    HTTP_CLIENT;

    /** 서버가 받은 요청 span 타입. 목록 기본값·통계·시계열 집계 대상. */
    public static final Set<EventType> REQUEST_TYPES = EnumSet.of(HTTP_REQUEST, SLOW_REQUEST, ERROR);
}
