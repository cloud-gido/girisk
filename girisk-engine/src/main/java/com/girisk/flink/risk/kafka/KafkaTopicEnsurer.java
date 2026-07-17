package com.girisk.flink.risk.kafka;

import com.girisk.flink.support.kafka.KafkaClientClassLoaders;
import com.girisk.flink.support.kafka.KafkaClientConfigs;
import com.girisk.flink.support.util.CliParameterTool;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.common.errors.TopicExistsException;

import java.util.Collections;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutionException;

/** 本地联调：若 topic 不存在则尝试创建（需 Kafka 允许自动建 topic 或具备 ACL）。 */
public final class KafkaTopicEnsurer {

    private KafkaTopicEnsurer() {}

    public static void ensureTopicExists(CliParameterTool t, String topic, int partitions, short replication)
            throws Exception {
        Properties props = KafkaClientConfigs.adminClientProperties(t);
        KafkaClientClassLoaders.callWithUserCodeClassLoader(
                KafkaTopicEnsurer.class,
                () -> {
                    ensureTopicExists(props, topic, partitions, replication);
                    return null;
                },
                90L);
    }

    public static void ensureTopicExists(
            String bootstrap, String topic, int partitions, short replication) throws Exception {
        Properties props = new Properties();
        props.put("bootstrap.servers", bootstrap);
        props.put("request.timeout.ms", "15000");
        ensureTopicExists(props, topic, partitions, replication);
    }

    private static void ensureTopicExists(
            Properties adminProps, String topic, int partitions, short replication) throws Exception {
        try (AdminClient admin = AdminClient.create(adminProps)) {
            Set<String> names = admin.listTopics().names().get();
            if (names.contains(topic)) {
                System.out.printf("[Kafka] topic 已存在: %s%n", topic);
                return;
            }
            try {
                admin.createTopics(
                                Collections.singleton(
                                        new org.apache.kafka.clients.admin.NewTopic(
                                                topic, partitions, replication)))
                        .all()
                        .get();
                System.out.printf(
                        "[Kafka] 已创建 topic: %s (partitions=%d, replication=%d)%n",
                        topic,
                        partitions,
                        replication);
            } catch (ExecutionException e) {
                if (e.getCause() instanceof TopicExistsException) {
                    System.out.printf("[Kafka] topic 已存在: %s%n", topic);
                    return;
                }
                throw e;
            }
        }
    }

    public static void ensureTopicExistsOrWarn(
            CliParameterTool t, String topic, int partitions, short replication) {
        try {
            ensureTopicExists(t, topic, partitions, replication);
        } catch (NoClassDefFoundError | ClassNotFoundException e) {
            System.err.printf(
                    "[Kafka] 未找到 kafka-clients（AdminClient），跳过自动建 topic: %s%n",
                    topic);
            System.err.printf(
                    "  请重新打包 shade JAR（含 kafka-clients），或事先创建 topic，或加 --sink.ensureTopic false%n");
            printCreateTopicHint(KafkaClientConfigs.resolveBootstrap(t), topic);
        } catch (Exception e) {
            System.err.printf(
                    "[Kafka] 自动创建 topic 失败 bootstrap=%s topic=%s : %s%n",
                    KafkaClientConfigs.resolveBootstrap(t),
                    topic,
                    e.getMessage());
            printCreateTopicHint(KafkaClientConfigs.resolveBootstrap(t), topic);
        }
    }

    public static void ensureTopicExistsOrWarn(
            String bootstrap, String topic, int partitions, short replication) {
        try {
            ensureTopicExists(bootstrap, topic, partitions, replication);
        } catch (NoClassDefFoundError | ClassNotFoundException e) {
            System.err.printf(
                    "[Kafka] 未找到 kafka-clients（AdminClient），跳过自动建 topic: %s%n",
                    topic);
            printCreateTopicHint(bootstrap, topic);
        } catch (Exception e) {
            System.err.printf(
                    "[Kafka] 自动创建 topic 失败 bootstrap=%s topic=%s : %s%n",
                    bootstrap,
                    topic,
                    e.getMessage());
            printCreateTopicHint(bootstrap, topic);
        }
    }

    public static void printCreateTopicHint(String bootstrap, String topic) {
        System.out.printf(
                "%n若报错 Topic not present，请先在 Kafka 创建 topic，例如：%n"
                        + "  kafka-topics --create --bootstrap-server %s --topic %s --partitions 1 --replication-factor 1%n"
                        + "或加参数 --ensureTopic true（默认）由程序尝试创建。%n%n",
                bootstrap,
                topic);
    }
}
