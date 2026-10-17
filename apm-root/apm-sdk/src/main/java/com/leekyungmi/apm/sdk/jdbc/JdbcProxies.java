package com.leekyungmi.apm.sdk.jdbc;

import com.leekyungmi.apm.sdk.sender.ApmEventSender;
import com.leekyungmi.apm.sdk.trace.SpanContext;
import com.leekyungmi.apm.sdk.trace.TraceContext;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.util.ClassUtils;

import javax.sql.DataSource;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Set;
import java.util.function.Supplier;

/**
 * java.lang.reflect.Proxy로 DataSource → Connection → Statement를 감싸 쿼리 실행 1회 = DB_QUERY span 1개를 만든다.
 * 쿼리를 실행한 스레드(보통 요청 스레드)에서 동작하며, 현재 서버 span이 없으면(기동 시 스크립트 등) span을 만들지 않는다.
 */
final class JdbcProxies {

    static final int MAX_SQL_LENGTH = 1000;
    private static final Set<String> EXECUTE_METHODS = Set.of(
            "execute", "executeQuery", "executeUpdate", "executeLargeUpdate", "executeBatch", "executeLargeBatch");

    private JdbcProxies() {
    }

    static boolean isProxied(DataSource dataSource) {
        return Proxy.isProxyClass(dataSource.getClass())
                && Proxy.getInvocationHandler(dataSource) instanceof DataSourceHandler;
    }

    static DataSource wrap(DataSource target, ObjectProvider<ApmEventSender> senderProvider) {
        // 원본이 구현한 인터페이스(Closeable 등)를 유지해야 컨테이너 종료 시 close가 호출된다
        Class<?>[] interfaces = ClassUtils.getAllInterfacesForClass(target.getClass(), target.getClass().getClassLoader());
        return (DataSource) Proxy.newProxyInstance(target.getClass().getClassLoader(), interfaces,
                new DataSourceHandler(target, new SenderRef(senderProvider)));
    }

    /** 첫 쿼리 때 전송기를 꺼내 캐싱한다 (BeanPostProcessor 생성 시점에 전송기 빈을 당겨오지 않기 위함). */
    private static final class SenderRef implements Supplier<ApmEventSender> {
        private final ObjectProvider<ApmEventSender> provider;
        private volatile ApmEventSender sender;

        SenderRef(ObjectProvider<ApmEventSender> provider) {
            this.provider = provider;
        }

        @Override
        public ApmEventSender get() {
            ApmEventSender s = sender;
            if (s == null) {
                s = provider.getIfAvailable();
                sender = s;
            }
            return s;
        }
    }

    private abstract static class DelegatingHandler implements InvocationHandler {
        final Object target;

        DelegatingHandler(Object target) {
            this.target = target;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getName().equals("equals") && args != null && args.length == 1) {
                return proxy == args[0];
            }
            if (method.getName().equals("hashCode") && (args == null || args.length == 0)) {
                return System.identityHashCode(proxy);
            }
            return handle(method, args);
        }

        abstract Object handle(Method method, Object[] args) throws Throwable;

        Object call(Method method, Object[] args) throws Throwable {
            try {
                return method.invoke(target, args);
            } catch (InvocationTargetException e) {
                throw e.getTargetException();
            }
        }
    }

    private static final class DataSourceHandler extends DelegatingHandler {
        private final SenderRef sender;

        DataSourceHandler(Object target, SenderRef sender) {
            super(target);
            this.sender = sender;
        }

        @Override
        Object handle(Method method, Object[] args) throws Throwable {
            Object result = call(method, args);
            if (method.getName().equals("getConnection") && result instanceof Connection connection) {
                return Proxy.newProxyInstance(JdbcProxies.class.getClassLoader(), new Class<?>[]{Connection.class},
                        new ConnectionHandler(connection, sender));
            }
            return result;
        }
    }

    private static final class ConnectionHandler extends DelegatingHandler {
        private final SenderRef sender;

        ConnectionHandler(Object target, SenderRef sender) {
            super(target);
            this.sender = sender;
        }

        @Override
        Object handle(Method method, Object[] args) throws Throwable {
            Object result = call(method, args);
            String name = method.getName();
            if (result instanceof Statement statement
                    && (name.equals("createStatement") || name.equals("prepareStatement") || name.equals("prepareCall"))) {
                // prepareStatement/prepareCall은 첫 인자가 SQL, createStatement는 execute 시점에 SQL이 온다
                String sql = args != null && args.length > 0 && args[0] instanceof String s ? s : null;
                return Proxy.newProxyInstance(JdbcProxies.class.getClassLoader(), new Class<?>[]{method.getReturnType()},
                        new StatementHandler(statement, sql, sender));
            }
            return result;
        }
    }

    private static final class StatementHandler extends DelegatingHandler {
        private final String preparedSql;
        private final SenderRef sender;

        StatementHandler(Object target, String preparedSql, SenderRef sender) {
            super(target);
            this.preparedSql = preparedSql;
            this.sender = sender;
        }

        @Override
        Object handle(Method method, Object[] args) throws Throwable {
            SpanContext parent = TraceContext.current();
            if (parent == null || !EXECUTE_METHODS.contains(method.getName())) {
                return call(method, args);
            }
            String sql = args != null && args.length > 0 && args[0] instanceof String s ? s : preparedSql;
            long startTimeUs = ApmEventSender.nowEpochUs();
            long startNanos = System.nanoTime();
            Throwable error = null;
            try {
                return call(method, args);
            } catch (Throwable e) {
                error = e;
                throw e;
            } finally {
                record(parent, sql, startTimeUs, (System.nanoTime() - startNanos) / 1000, error);
            }
        }

        private void record(SpanContext parent, String sql, long startTimeUs, long durationUs, Throwable error) {
            try {
                ApmEventSender s = sender.get();
                if (s == null) {
                    return;
                }
                String uri = sql == null ? "(batch)" : sql.length() > MAX_SQL_LENGTH ? sql.substring(0, MAX_SQL_LENGTH) : sql;
                int statusCode = error instanceof SQLException ? 500 : 0;
                s.send(parent.newChild(), "DB_QUERY", uri, "SQL", statusCode,
                        startTimeUs, durationUs, error != null ? error.getMessage() : null);
            } catch (Exception e) {
                // 수집 실패가 호스트 앱 쿼리를 깨면 안 된다
            }
        }
    }
}
