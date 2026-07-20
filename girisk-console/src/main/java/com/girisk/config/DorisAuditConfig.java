package com.girisk.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Registers Doris audit properties only — no DataSource/JdbcTemplate beans. */
@Configuration
@EnableConfigurationProperties(DorisAuditProperties.class)
public class DorisAuditConfig {
}
