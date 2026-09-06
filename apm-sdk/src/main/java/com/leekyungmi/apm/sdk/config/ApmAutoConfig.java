package com.leekyungmi.apm.sdk.config;

import com.leekyungmi.apm.sdk.interceptor.ApmInterceptor;
import com.leekyungmi.apm.sdk.sender.ApmEventSender;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class ApmAutoConfig implements WebMvcConfigurer {

    @Value("${apm.server.url}")
    private String apmServerUrl;

    @Value("${apm.app.id}")
    private String appId;

    private final ApmEventSender apmEventSender;

    public ApmAutoConfig(@Value("${apm.server.url}") String apmServerUrl) {
        this.apmEventSender = new ApmEventSender(apmServerUrl);
    }

//    @Bean(destroyMethod = "shutdown")
//    public ApmEventSender apmEventSender(@Value("${apm.server.url}") String apmServerUrl) {
//        return apmEventSender;
//    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new ApmInterceptor(apmEventSender, appId))
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/apm/**");
    }
}
