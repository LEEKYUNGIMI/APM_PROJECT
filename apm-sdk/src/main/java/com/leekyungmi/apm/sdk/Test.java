package com.leekyungmi.apm.sdk;

public class Test {
    public static void main(String[] args) {
        ThreadLocal<String> threadLocal = new ThreadLocal<>();
        threadLocal.set("test");
        System.out.println(threadLocal.get());

        new Thread(()->{
            System.out.println(threadLocal.get());
        }).start();
    }
}
