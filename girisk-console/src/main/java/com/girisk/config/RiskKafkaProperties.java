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
}
