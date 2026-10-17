package com.leekyungmi.apm.sdk.jdbc;

import com.leekyungmi.apm.sdk.sender.ApmEventSender;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;

import javax.sql.DataSource;

/** 컨텍스트의 DataSource 빈을 쿼리 계측 프록시로 감싼다. */
public class ApmDataSourcePostProcessor implements BeanPostProcessor {

    private final ObjectProvider<ApmEventSender> senderProvider;

    public ApmDataSourcePostProcessor(ObjectProvider<ApmEventSender> senderProvider) {
        this.senderProvider = senderProvider;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof DataSource dataSource && !JdbcProxies.isProxied(dataSource)) {
            return JdbcProxies.wrap(dataSource, senderProvider);
        }
        return bean;
    }
}
