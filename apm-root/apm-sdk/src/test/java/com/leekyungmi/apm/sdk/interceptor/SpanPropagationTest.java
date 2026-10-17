package com.leekyungmi.apm.sdk.interceptor;

import com.leekyungmi.apm.sdk.client.ApmClientHttpInterceptor;
import com.leekyungmi.apm.sdk.jdbc.ApmDataSourcePostProcessor;
import com.leekyungmi.apm.sdk.sender.ApmEventSender;
import com.leekyungmi.apm.sdk.trace.SpanContext;
import com.leekyungmi.apm.sdk.trace.TraceContext;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.sql.DataSource;
import java.net.URI;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** 서버 span → (DB_QUERY, HTTP_CLIENT) 부모 관계와 traceparent 전파를 검증한다. */
class SpanPropagationTest {

    private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
    private static final String REMOTE_SPAN_ID = "00f067aa0ba902b7";

    record Sent(SpanContext span, String type, String uri, String method, int statusCode, String message,
                long startTimeUs, long durationUs) {
    }

    /** 버퍼 대신 목록에 담는 전송기. 전송 스레드는 떠 있지만 보낼 게 없어 놀기만 한다. */
    static class CapturingSender extends ApmEventSender {
        final List<Sent> sent = new CopyOnWriteArrayList<>();

        CapturingSender() {
            super("http://127.0.0.1:1", "test-app");
        }

        @Override
        public void send(SpanContext span, String type, String uri, String method, int statusCode,
                         long startTimeUs, long durationUs, String message) {
            sent.add(new Sent(span, type, uri, method, statusCode, message, startTimeUs, durationUs));
        }
    }

    private final CapturingSender sender = new CapturingSender();

    @AfterEach
    void tearDown() {
        sender.shutdown();
        TraceContext.clear();
    }

    @Test
    void 요청_안의_DB와_HTTP_호출은_서버_span의_자식이다() throws Exception {
        DataSource dataSource = instrumentedDataSource(false);
        ApmClientHttpInterceptor clientInterceptor = new ApmClientHttpInterceptor(sender, "http://apm:8080");
        MockClientHttpRequest outgoing = new MockClientHttpRequest(HttpMethod.GET, URI.create("http://other/api/products"));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders");
        request.addHeader("traceparent", "00-" + TRACE_ID + "-" + REMOTE_SPAN_ID + "-01");

        new ApmTraceFilter(sender).doFilter(request, new MockHttpServletResponse(), (req, res) -> {
            try (Connection con = dataSource.getConnection();
                 PreparedStatement ps = con.prepareStatement("SELECT 1")) {
                ps.executeQuery();
                clientInterceptor.intercept(outgoing, new byte[0],
                        (r, body) -> new MockClientHttpResponse(new byte[0], HttpStatus.OK));
            } catch (SQLException e) {
                throw new ServletException(e);
            }
        });

        assertEquals(3, sender.sent.size());
        Sent db = sender.sent.get(0);
        Sent client = sender.sent.get(1);
        Sent server = sender.sent.get(2);

        assertEquals("HTTP_REQUEST", server.type());
        assertEquals(TRACE_ID, server.span().traceId());
        assertEquals(REMOTE_SPAN_ID, server.span().parentSpanId());

        assertEquals("DB_QUERY", db.type());
        assertEquals("SELECT 1", db.uri());
        assertEquals("SQL", db.method());
        assertEquals(0, db.statusCode());
        assertEquals(TRACE_ID, db.span().traceId());
        assertEquals(server.span().spanId(), db.span().parentSpanId());

        assertEquals("HTTP_CLIENT", client.type());
        assertEquals("http://other/api/products", client.uri());
        assertEquals(200, client.statusCode());
        assertEquals(server.span().spanId(), client.span().parentSpanId());
        // 나가는 요청의 traceparent = HTTP_CLIENT span → 상대 서버 span의 부모가 된다
        assertEquals(client.span().toTraceparent(), outgoing.getHeaders().getFirst("traceparent"));

        assertNull(TraceContext.current());
    }

    @Test
    void 세_span_모두_μs_시작시각과_소요를_채우고_자식은_서버_span_구간_안에_있다() throws Exception {
        DataSource dataSource = instrumentedDataSource(false);
        ApmClientHttpInterceptor clientInterceptor = new ApmClientHttpInterceptor(sender, "http://apm:8080");
        MockClientHttpRequest outgoing = new MockClientHttpRequest(HttpMethod.GET, URI.create("http://other/api/products"));
        long beforeUs = System.currentTimeMillis() * 1000;

        new ApmTraceFilter(sender).doFilter(new MockHttpServletRequest("GET", "/api/orders"), new MockHttpServletResponse(), (req, res) -> {
            try (Connection con = dataSource.getConnection();
                 PreparedStatement ps = con.prepareStatement("SELECT 1")) {
                ps.executeQuery();
                clientInterceptor.intercept(outgoing, new byte[0], (r, body) -> {
                    try {
                        Thread.sleep(5);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return new MockClientHttpResponse(new byte[0], HttpStatus.OK);
                });
            } catch (SQLException e) {
                throw new ServletException(e);
            }
        });
        long afterUs = (System.currentTimeMillis() + 1) * 1000;

        Sent db = sender.sent.get(0);
        Sent client = sender.sent.get(1);
        Sent server = sender.sent.get(2);
        for (Sent s : List.of(db, client, server)) {
            assertTrue(s.startTimeUs() >= beforeUs && s.startTimeUs() <= afterUs, s.type() + " startTimeUs=" + s.startTimeUs());
            assertTrue(s.durationUs() >= 0, s.type() + " durationUs=" + s.durationUs());
        }
        assertTrue(client.durationUs() >= 5_000, "sleep 5ms 이상: " + client.durationUs());
        assertTrue(server.durationUs() >= client.durationUs());
        assertTrue(db.startTimeUs() >= server.startTimeUs());
        assertTrue(client.startTimeUs() >= db.startTimeUs());
    }

    @Test
    void 요청_밖의_쿼리는_span을_만들지_않는다() throws Exception {
        DataSource dataSource = instrumentedDataSource(false);
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT 1")) {
            ps.executeQuery();
        }
        assertTrue(sender.sent.isEmpty());
    }

    @Test
    void 쿼리가_실패하면_500_DB_QUERY_span과_예외를_그대로_던진다() throws Exception {
        DataSource dataSource = instrumentedDataSource(true);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/products");

        new ApmTraceFilter(sender).doFilter(request, new MockHttpServletResponse(), (req, res) -> {
            try (Connection con = dataSource.getConnection();
                 PreparedStatement ps = con.prepareStatement("SELECT broken")) {
                assertThrows(SQLException.class, ps::executeQuery);
            } catch (SQLException e) {
                throw new ServletException(e);
            }
        });

        Sent db = sender.sent.get(0);
        assertEquals("DB_QUERY", db.type());
        assertEquals(500, db.statusCode());
        assertEquals("boom", db.message());
        assertNull(sender.sent.get(1).span().parentSpanId()); // traceparent 없음 → 루트
    }

    @Test
    void 예외가_나면_ERROR_500으로_분류하고_원인_메시지를_쓴다() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/products/error");

        assertThrows(ServletException.class, () -> new ApmTraceFilter(sender).doFilter(request, new MockHttpServletResponse(),
                (req, res) -> {
                    throw new ServletException("Request processing failed", new RuntimeException("의도적 에러"));
                }));

        Sent server = sender.sent.get(0);
        assertEquals("ERROR", server.type());
        assertEquals(500, server.statusCode());
        assertEquals("의도적 에러", server.message());
    }

    @Test
    void apm_경로는_span을_만들지_않는다() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/apm/health");
        new ApmTraceFilter(sender).doFilter(request, new MockHttpServletResponse(), (req, res) -> {
        });
        assertTrue(sender.sent.isEmpty());
    }

    private DataSource instrumentedDataSource(boolean failQuery) throws SQLException {
        PreparedStatement ps = mock(PreparedStatement.class);
        if (failQuery) {
            when(ps.executeQuery()).thenThrow(new SQLException("boom"));
        }
        Connection con = mock(Connection.class);
        when(con.prepareStatement(anyString())).thenReturn(ps);
        DataSource raw = mock(DataSource.class);
        when(raw.getConnection()).thenReturn(con);

        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerSingleton("apmEventSender", sender);
        return (DataSource) new ApmDataSourcePostProcessor(beanFactory.getBeanProvider(ApmEventSender.class))
                .postProcessAfterInitialization(raw, "dataSource");
    }
}
