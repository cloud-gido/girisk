package com.girisk.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "girisk.kafka")
public class RiskKafkaProperties {

    private boolean enabled = true;
    private String bootstrapServers = "10.0.0.10:30992";
    private String orderTopic = "girisk.order.event";
    /** Legacy local stream decision topic (Spring mock path). */
    private String decisionTopic = "girisk.decision.event";
    /** Flink single decision egress — consumed for audit / REVIEW cases. */
    private String flinkDecisionTopic = "girisk.decision.v1";
    /** Config plane → Flink compacted topic. */
    private String configTopic = "girisk.config.v1";
    private String statusTopic = "girisk.trading.order.risk-check.post.v1";
    private String consumerGroup = "girisk-console-stream";
    private String flinkDecisionGroup = "girisk-console-flink-decision";
    private int topicPartitions = 3;
    private short topicReplicas = 1;
    /** config.v1 同步发送超时（秒） */
    private int configPublishTimeoutSeconds = 10;
    /** 应用层重试次数（含首次） */
    private int configPublishMaxAttempts = 5;
    private long configPublishBackoffMs = 400L;
    /** 启动时从 Redis 全量刷到 config.v1，修复漂移 */
    private boolean configBootstrapSyncEnabled = true;
    /** Redis/内存 outbox：配置写与入队同事务，Poller 异步投递 Kafka */
    private boolean configOutboxEnabled = true;
    /** outbox 轮询间隔（毫秒） */
    private long configOutboxPollMs = 500L;
    /** 单次 poll 最多处理条数 */
    private int configOutboxBatchSize = 32;
    /** 单条最大投递尝试（含首次 claim 时的 attempts） */
    private int configOutboxMaxAttempts = 20;
    /** 单次 poll 最多条数（小批量，降低 max.poll.interval 踢出风险） */
    private int consumerMaxPollRecords = 50;
    /** 两次 poll 最大间隔（毫秒）；处理再慢也不易被踢 */
    private int consumerMaxPollIntervalMs = 300_000;
    private int consumerSessionTimeoutMs = 45_000;
    private int consumerHeartbeatIntervalMs = 3_000;
    /** 决策入库失败重试次数（含首次），耗尽后写 DECISION_INGEST_FAIL 并跳过 */
    private int decisionIngestMaxAttempts = 3;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getBootstrapServers() { return bootstrapServers; }
    public void setBootstrapServers(String bootstrapServers) { this.bootstrapServers = bootstrapServers; }
    public String getOrderTopic() { return orderTopic; }
    public void setOrderTopic(String orderTopic) { this.orderTopic = orderTopic; }
    public String getDecisionTopic() { return decisionTopic; }
    public void setDecisionTopic(String decisionTopic) { this.decisionTopic = decisionTopic; }
    public String getFlinkDecisionTopic() { return flinkDecisionTopic; }
    public void setFlinkDecisionTopic(String flinkDecisionTopic) { this.flinkDecisionTopic = flinkDecisionTopic; }
    public String getConfigTopic() { return configTopic; }
    public void setConfigTopic(String configTopic) { this.configTopic = configTopic; }
    public String getStatusTopic() { return statusTopic; }
    public void setStatusTopic(String statusTopic) { this.statusTopic = statusTopic; }
    public String getConsumerGroup() { return consumerGroup; }
    public void setConsumerGroup(String consumerGroup) { this.consumerGroup = consumerGroup; }
    public String getFlinkDecisionGroup() { return flinkDecisionGroup; }
    public void setFlinkDecisionGroup(String flinkDecisionGroup) { this.flinkDecisionGroup = flinkDecisionGroup; }
    public int getTopicPartitions() { return topicPartitions; }
    public void setTopicPartitions(int topicPartitions) { this.topicPartitions = topicPartitions; }
    public short getTopicReplicas() { return topicReplicas; }
    public void setTopicReplicas(short topicReplicas) { this.topicReplicas = topicReplicas; }
    public int getConfigPublishTimeoutSeconds() { return configPublishTimeoutSeconds; }
    public void setConfigPublishTimeoutSeconds(int configPublishTimeoutSeconds) {
        this.configPublishTimeoutSeconds = configPublishTimeoutSeconds;
    }
    public int getConfigPublishMaxAttempts() { return configPublishMaxAttempts; }
    public void setConfigPublishMaxAttempts(int configPublishMaxAttempts) {
        this.configPublishMaxAttempts = configPublishMaxAttempts;
    }
    public long getConfigPublishBackoffMs() { return configPublishBackoffMs; }
    public void setConfigPublishBackoffMs(long configPublishBackoffMs) {
        this.configPublishBackoffMs = configPublishBackoffMs;
    }
    public boolean isConfigBootstrapSyncEnabled() { return configBootstrapSyncEnabled; }
    public void setConfigBootstrapSyncEnabled(boolean configBootstrapSyncEnabled) {
        this.configBootstrapSyncEnabled = configBootstrapSyncEnabled;
    }
    public boolean isConfigOutboxEnabled() { return configOutboxEnabled; }
    public void setConfigOutboxEnabled(boolean configOutboxEnabled) {
        this.configOutboxEnabled = configOutboxEnabled;
    }
    public long getConfigOutboxPollMs() { return configOutboxPollMs; }
    public void setConfigOutboxPollMs(long configOutboxPollMs) {
        this.configOutboxPollMs = configOutboxPollMs;
    }
    public int getConfigOutboxBatchSize() { return configOutboxBatchSize; }
    public void setConfigOutboxBatchSize(int configOutboxBatchSize) {
        this.configOutboxBatchSize = configOutboxBatchSize;
    }
    public int getConfigOutboxMaxAttempts() { return configOutboxMaxAttempts; }
    public void setConfigOutboxMaxAttempts(int configOutboxMaxAttempts) {
        this.configOutboxMaxAttempts = configOutboxMaxAttempts;
    }
    public int getConsumerMaxPollRecords() { return consumerMaxPollRecords; }
    public void setConsumerMaxPollRecords(int consumerMaxPollRecords) {
        this.consumerMaxPollRecords = consumerMaxPollRecords;
    }
    public int getConsumerMaxPollIntervalMs() { return consumerMaxPollIntervalMs; }
    public void setConsumerMaxPollIntervalMs(int consumerMaxPollIntervalMs) {
        this.consumerMaxPollIntervalMs = consumerMaxPollIntervalMs;
    }
    public int getConsumerSessionTimeoutMs() { return consumerSessionTimeoutMs; }
    public void setConsumerSessionTimeoutMs(int consumerSessionTimeoutMs) {
        this.consumerSessionTimeoutMs = consumerSessionTimeoutMs;
    }
    public int getConsumerHeartbeatIntervalMs() { return consumerHeartbeatIntervalMs; }
    public void setConsumerHeartbeatIntervalMs(int consumerHeartbeatIntervalMs) {
        this.consumerHeartbeatIntervalMs = consumerHeartbeatIntervalMs;
    }
    public int getDecisionIngestMaxAttempts() { return decisionIngestMaxAttempts; }
    public void setDecisionIngestMaxAttempts(int decisionIngestMaxAttempts) {
        this.decisionIngestMaxAttempts = decisionIngestMaxAttempts;
    }
}
