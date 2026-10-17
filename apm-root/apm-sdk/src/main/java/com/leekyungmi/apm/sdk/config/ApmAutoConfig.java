package com.leekyungmi.apm.sdk.config;

import com.leekyungmi.apm.sdk.client.ApmClientHttpInterceptor;
import com.leekyungmi.apm.sdk.interceptor.ApmTraceFilter;
import com.leekyungmi.apm.sdk.jdbc.ApmDataSourcePostProcessor;
import com.leekyungmi.apm.sdk.sender.ApmEventSender;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.boot.web.client.RestTemplateCustomizer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
public class ApmAutoConfig {

    @Bean(destroyMethod = "shutdown")
    public ApmEventSender apmEventSender(@Value("${apm.server.url}") String apmServerUrl,
                                         @Value("${apm.app.id}") String appId) {
        return new ApmEventSender(apmServerUrl, appId);
    }

    // 요청 1건 = 서버 span 1개. 기존 인터셉터와 같은 범위(/api/**, /api/apm/** 제외는 필터 내부에서)
    @Bean
    public FilterRegistrationBean<ApmTraceFilter> apmTraceFilter(ApmEventSender apmEventSender) {
        FilterRegistrationBean<ApmTraceFilter> registration = new FilterRegistrationBean<>(new ApmTraceFilter(apmEventSender));
        registration.addUrlPatterns("/api/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        return registration;
    }

    // RestClient.Builder / RestTemplateBuilder로 만든 클라이언트에만 적용된다. SDK 내부 전송용 RestTemplate은 new로 만들어 제외됨
    @Bean
    public RestClientCustomizer apmRestClientCustomizer(ApmEventSender apmEventSender,
                                                        @Value("${apm.server.url}") String apmServerUrl) {
        ApmClientHttpInterceptor interceptor = new ApmClientHttpInterceptor(apmEventSender, apmServerUrl);
        return builder -> builder.requestInterceptor(interceptor);
    }

    @Bean
    public RestTemplateCustomizer apmRestTemplateCustomizer(ApmEventSender apmEventSender,
                                                            @Value("${apm.server.url}") String apmServerUrl) {
        ApmClientHttpInterceptor interceptor = new ApmClientHttpInterceptor(apmEventSender, apmServerUrl);
        return restTemplate -> restTemplate.getInterceptors().add(interceptor);
    }

    // BeanPostProcessor는 static으로 등록해야 설정 클래스를 조기 생성하지 않는다
    @Bean
    public static ApmDataSourcePostProcessor apmDataSourcePostProcessor(ObjectProvider<ApmEventSender> apmEventSender) {
        return new ApmDataSourcePostProcessor(apmEventSender);
    }
}
