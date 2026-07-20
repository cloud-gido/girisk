package com.girisk.flink.risk.kafka;

import com.girisk.flink.support.util.CliParameterTool;

/** 本地联调：创建 FootballOrderKafkaJob 所需的 Kafka topic（默认不含旧四出口）。 */
public final class KafkaTopicBootstrapCli {

    public static void main(String[] args) throws Exception {
        CliParameterTool t = CliParameterTool.fromArgs(args);
        String bootstrap = t.get("bootstrap", "192.168.1.68:9092");
        int partitions = t.getInt("sink.topic.partitions", 1);
        short replication = (short) t.getInt("sink.topic.replication", 1);

        String preTopic =
                t.get("source.topic.pre", FootballOrderKafkaTopics.RISK_CHECK_PRE).trim();
        String postTopic = t.get("source.topic.post", FootballOrderKafkaTopics.RISK_CHECK_POST).trim();
        String decisionTopic =
                t.get("sink.topic.decision", FootballOrderKafkaTopics.DECISION).trim();
        boolean liveScoreEnabled = t.getBoolean("live.score.enabled", true);
        String liveScoreTopic = t.get("live.score.topic", FootballOrderKafkaTopics.LIVE_SCORE).trim();

        // 可选：显式传才创建旧四出口
        String detailTopic = t.get("sink.topic.detail", "").trim();
        String summaryTopic = t.get("sink.topic.summary", "").trim();
        String limitTopic = t.get("sink.topic.limit", "").trim();
        String businessTopic = t.get("sink.topic.business", "").trim();

        System.out.printf("[KafkaTopicBootstrap] bootstrap=%s%n", bootstrap);
        KafkaTopicEnsurer.ensureTopicExists(bootstrap, preTopic, partitions, replication);
        KafkaTopicEnsurer.ensureTopicExists(bootstrap, postTopic, partitions, replication);
        KafkaTopicEnsurer.ensureTopicExists(bootstrap, decisionTopic, partitions, replication);
        if (liveScoreEnabled && !liveScoreTopic.isEmpty()) {
            KafkaTopicEnsurer.ensureTopicExists(bootstrap, liveScoreTopic, partitions, replication);
        }
        if (!detailTopic.isEmpty()) {
            KafkaTopicEnsurer.ensureTopicExists(bootstrap, detailTopic, partitions, replication);
        }
        if (!summaryTopic.isEmpty()) {
            KafkaTopicEnsurer.ensureTopicExists(bootstrap, summaryTopic, partitions, replication);
        }
        if (!limitTopic.isEmpty()) {
            KafkaTopicEnsurer.ensureTopicExists(bootstrap, limitTopic, partitions, replication);
        }
        if (!businessTopic.isEmpty()) {
            KafkaTopicEnsurer.ensureTopicExists(bootstrap, businessTopic, partitions, replication);
        }
        System.out.println("[KafkaTopicBootstrap] 完成（默认仅 pre/post/decision[+liveScore]）");
    }

    private KafkaTopicBootstrapCli() {}
}
