package com.leekyungmi.apm.sdk.client;

import com.leekyungmi.apm.sdk.sender.ApmEventSender;
import com.leekyungmi.apm.sdk.trace.SpanContext;
import com.leekyungmi.apm.sdk.trace.TraceContext;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

/**
 * RestClient·RestTemplate 나가는 호출 1회 = HTTP_CLIENT span 1개 + traceparent 주입.
 * 호출한 스레드(보통 요청 스레드)에서 실행된다. 현재 서버 span이 없으면(요청 밖) 아무것도 하지 않는다.
 */
public class ApmClientHttpInterceptor implements ClientHttpRequestInterceptor {

    private final ApmEventSender sender;
    private final String apmServerUrl;

    public ApmClientHttpInterceptor(ApmEventSender sender, String apmServerUrl) {
        this.sender = sender;
        this.apmServerUrl = apmServerUrl;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        SpanContext parent = TraceContext.current();
        String url = request.getURI().toString();
        if (parent == null || url.startsWith(apmServerUrl)) {
            return execution.execute(request, body);
        }

        SpanContext span = parent.newChild();
        request.getHeaders().set(TraceContext.TRACEPARENT, span.toTraceparent());
        long startTimeUs = ApmEventSender.nowEpochUs();
        long startNanos = System.nanoTime();
        int statusCode = 0; // 연결 실패 시 0
        String message = null;
        try {
            ClientHttpResponse response = execution.execute(request, body);
            statusCode = response.getStatusCode().value();
            return response;
        } catch (IOException | RuntimeException e) {
            message = e.getMessage();
            throw e;
        } finally {
            try {
                sender.send(span, "HTTP_CLIENT", url, request.getMethod().name(), statusCode,
                        startTimeUs, (System.nanoTime() - startNanos) / 1000, message);
            } catch (Exception e) {
                // 수집 실패가 호스트 앱 호출을 깨면 안 된다
            }
        }
    }
}
