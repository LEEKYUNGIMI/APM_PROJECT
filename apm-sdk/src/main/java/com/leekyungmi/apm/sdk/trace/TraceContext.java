package com.leekyungmi.apm.sdk.trace;

import java.util.UUID;

public class TraceContext {
    static ThreadLocal<String> threadLocal = new ThreadLocal<>();

    public static void start () {
        threadLocal.set(UUID.randomUUID().toString());
    }

    public static String get() {
        return threadLocal.get();
    }

    public static void clear() {
        threadLocal.remove();
    }

}
