package com.girisk.config;

import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 运行时兜底：即便 EnvironmentPostProcessor 未生效，
 * 也把 {@code postgresql://} 纠正为 Hikari 可接受的 {@code jdbc:postgresql://}。
 */
@Configuration
@ConditionalOnClass(HikariDataSource.class)
public class HikariJdbcUrlNormalizeConfig {

    private static final Logger log = LoggerFactory.getLogger(HikariJdbcUrlNormalizeConfig.class);

    @Bean
    public static BeanPostProcessor giriskNormalizeHikariJdbcUrl() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
                if (bean instanceof HikariDataSource ds) {
                    String url = ds.getJdbcUrl();
                    if (url != null && !url.isBlank()) {
                        String normalized = DatasourceUrlEnvironmentPostProcessor.normalizeJdbcUrl(url);
                        if (!normalized.equals(url)) {
                            log.warn(
                                    "Normalized datasource JDBC URL (added jdbc: prefix). Was: {}…",
                                    url.length() > 48 ? url.substring(0, 48) : url);
                            ds.setJdbcUrl(normalized);
                        }
                    }
                }
                return bean;
            }
        };
    }
}
