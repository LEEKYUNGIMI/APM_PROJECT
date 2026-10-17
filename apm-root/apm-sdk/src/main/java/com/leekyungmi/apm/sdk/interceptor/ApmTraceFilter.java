package com.leekyungmi.apm.sdk.interceptor;

import com.leekyungmi.apm.sdk.sender.ApmEventSender;
import com.leekyungmi.apm.sdk.trace.SpanContext;
import com.leekyungmi.apm.sdk.trace.TraceContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 요청 1건 = 서버 span 1개. traceparent를 받아 trace를 잇고, 요청이 끝나면 span을 발행한다.
 * 요청 스레드에서 실행되며, 발행은 버퍼에 넣기만 한다.
 */
public class ApmTraceFilter extends OncePerRequestFilter {
    static final long SLOW_THRESHOLD_MS = 2000;

    private final ApmEventSender sender;

    public ApmTraceFilter(ApmEventSender sender) {
        this.sender = sender;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/api/apm/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        SpanContext span = TraceContext.start(request.getHeader(TraceContext.TRACEPARENT));
        long startTimeUs = ApmEventSender.nowEpochUs();
        long startNanos = System.nanoTime();
        Throwable error = null;

        try {
            filterChain.doFilter(request, response);
        } catch (ServletException | IOException | RuntimeException | Error e) {
            error = e;
            throw e;
        } finally {
            TraceContext.clear();
            try {
                long durationUs = (System.nanoTime() - startNanos) / 1000;
                // 예외가 필터 밖으로 나가는 시점엔 아직 500이 기록되지 않았을 수 있다
                int statusCode = error != null ? 500 : response.getStatus();
                String type = error != null || statusCode >= 500 ? "ERROR"
                        : durationUs / 1000 >= SLOW_THRESHOLD_MS ? "SLOW_REQUEST" : "HTTP_REQUEST";
                sender.send(span, type, request.getRequestURI(), request.getMethod(), statusCode,
                        startTimeUs, durationUs, messageOf(error));
            } catch (Exception e) {
                // 수집 실패가 호스트 앱 요청을 깨면 안 된다
            }
        }
    }

    private static String messageOf(Throwable error) {
        if (error == null) {
            return null;
        }
        // DispatcherServlet은 컨트롤러 예외를 ServletException으로 감싼다 → 원인 메시지를 쓴다
        Throwable cause = error instanceof ServletException && error.getCause() != null ? error.getCause() : error;
        return cause.getMessage();
    }
}
