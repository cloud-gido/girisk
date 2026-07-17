package com.girisk.flink.risk.kafka;

import com.girisk.flink.support.util.CliParameterTool;

/** 本地联调：创建 FootballOrderKafkaJob 所需的 Kafka topic。 */
public final class KafkaTopicBootstrapCli {

    public static void main(String[] args) throws Exception {
        CliParameterTool t = CliParameterTool.fromArgs(args);
        String bootstrap = t.get("bootstrap", "192.168.1.68:9092");
        int partitions = t.getInt("sink.topic.partitions", 1);
        short replication = (short) t.getInt("sink.topic.replication", 1);

        String preTopic =
                t.get("source.topic.pre", FootballOrderKafkaTopics.RISK_CHECK_PRE).trim();
        String postTopic = t.get("source.topic.post", FootballOrderKafkaTopics.RISK_CHECK_POST).trim();
        String detailTopic =
                t.get("sink.topic.detail", FootballOrderKafkaTopics.DETAIL).trim();
        String summaryTopic =
                t.get("sink.topic.summary", FootballOrderKafkaTopics.SUMMARY).trim();
        String limitTopic = t.get("sink.topic.limit", FootballOrderKafkaTopics.LIMIT).trim();
        String businessTopic = t.get("sink.topic.business", FootballOrderKafkaTopics.BUSINESS).trim();
        boolean liveScoreEnabled = t.getBoolean("live.score.enabled", true);
        String liveScoreTopic = t.get("live.score.topic", FootballOrderKafkaTopics.LIVE_SCORE).trim();

        System.out.printf("[KafkaTopicBootstrap] bootstrap=%s%n", bootstrap);
        KafkaTopicEnsurer.ensureTopicExists(bootstrap, preTopic, partitions, replication);
        KafkaTopicEnsurer.ensureTopicExists(bootstrap, postTopic, partitions, replication);
        KafkaTopicEnsurer.ensureTopicExists(bootstrap, detailTopic, partitions, replication);
        KafkaTopicEnsurer.ensureTopicExists(bootstrap, summaryTopic, partitions, replication);
        KafkaTopicEnsurer.ensureTopicExists(bootstrap, limitTopic, partitions, replication);
        KafkaTopicEnsurer.ensureTopicExists(bootstrap, businessTopic, partitions, replication);
        if (liveScoreEnabled && !liveScoreTopic.isEmpty()) {
            KafkaTopicEnsurer.ensureTopicExists(bootstrap, liveScoreTopic, partitions, replication);
        }
        System.out.println("[KafkaTopicBootstrap] 完成");
    }

    private KafkaTopicBootstrapCli() {}
}
