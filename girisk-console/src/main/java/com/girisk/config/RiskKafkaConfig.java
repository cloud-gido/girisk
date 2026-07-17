package com.girisk.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        RiskKafkaProperties.class,
        SportsRiskProperties.class,
        DemoBootstrapProperties.class
})
public class RiskKafkaConfig {
}
