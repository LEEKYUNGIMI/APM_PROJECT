package com.leekyungmi.apm.simple.db;

/** schema.sql에서 H2 함수 SLEEP으로 등록. 느린 쿼리(DB_QUERY span 소요시간) 시연용. */
public final class H2Functions {

    private H2Functions() {
    }

    public static int sleep(int millis) throws InterruptedException {
        Thread.sleep(millis);
        return 0;
    }
}
