package com.leekyungmi.apm.sdk.config;

import com.leekyungmi.apm.sdk.interceptor.ApmInterceptor;
import com.leekyungmi.apm.sdk.sender.ApmEventSender;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class ApmAutoConfig implements WebMvcConfigurer {

    @Value("${apm.server.url}")
    private String apmServerUrl;

    @Value("${apm.app.id}")
    private String appId;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        ApmEventSender sender = new ApmEventSender(apmServerUrl);

        registry.addInterceptor(new ApmInterceptor(sender, appId))
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/apm/**");
    }
}
