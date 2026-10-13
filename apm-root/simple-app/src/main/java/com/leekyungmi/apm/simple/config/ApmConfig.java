package com.leekyungmi.apm.simple.config;

import com.leekyungmi.apm.sdk.interceptor.ApmTraceFilter;
import com.leekyungmi.apm.sdk.sender.ApmEventSender;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApmConfig {

    @Bean
    public ApmEventSender apmEventSender() {
        return new ApmEventSender("http://localhost:8080");
    }

    @Bean
    public FilterRegistrationBean<ApmTraceFilter> apmFilter(ApmEventSender sender) {
        FilterRegistrationBean<ApmTraceFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new ApmTraceFilter(sender));
        registration.addUrlPatterns("/*");
        return registration;
    }
}
