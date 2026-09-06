package com.leekyungmi.apm.sdk.interceptor;

import com.leekyungmi.apm.sdk.sender.ApmEventSender;
import com.leekyungmi.apm.sdk.trace.TraceContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class ApmTraceFilter extends OncePerRequestFilter {
    private final ApmEventSender sender;

    public ApmTraceFilter(ApmEventSender sender) {
        this.sender = sender;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        TraceContext.start();
        long startTime = System.currentTimeMillis();

        try {
            filterChain.doFilter(request, response);
        } finally {
            // 시간경과계산
            long durationMs = System.currentTimeMillis() - startTime;
            String traceId = TraceContext.get();
            TraceContext.clear();
            String URI = request.getRequestURI();
            String method = request.getMethod();
            int statusCode = response.getStatus();

            // 이벤트 전송 추가
            sender.send(
                    traceId, "HTTP_REQUEST", URI, method,  statusCode, (int)durationMs, null, null
            );
        }
    }
}
