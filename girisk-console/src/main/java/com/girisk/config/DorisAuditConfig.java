package com.girisk.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Registers Doris audit bootstrap properties；连接池见 {@code DorisAuditDataSourceManager}. */
@Configuration
@EnableConfigurationProperties(DorisAuditProperties.class)
public class DorisAuditConfig {
}
