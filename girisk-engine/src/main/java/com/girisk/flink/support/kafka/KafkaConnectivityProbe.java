package com.girisk.flink.support.kafka;

import com.girisk.flink.support.util.CliParameterTool;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DescribeTopicsResult;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.common.Node;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**
 * 作业启动前探测 Kafka（与 Source Enumerator 相同的 AdminClient 配置）。失败时抛出明确异常，便于区分
 * 「网络/MSK 不可达」与「JAR/打包」问题。
 */
public final class KafkaConnectivityProbe {

    private KafkaConnectivityProbe() {}

    public static void verifyOrThrow(CliParameterTool t, String sourceTopic) throws Exception {
        long timeoutSec = t.getLong("kafka.probe.timeoutSec", 120L);
        Properties props = KafkaClientConfigs.adminClientProperties(t);
        String bootstrap = KafkaClientConfigs.resolveBootstrap(t);

        System.out.printf(
                Locale.ROOT,
                "[KafkaConnectivityProbe] bootstrap=%s topic=%s timeoutSec=%d%n",
                bootstrap,
                sourceTopic,
                timeoutSec);

        try {
            KafkaClientClassLoaders.callWithUserCodeClassLoader(
                    KafkaConnectivityProbe.class,
                    () -> runProbe(props, sourceTopic, timeoutSec),
                    timeoutSec + 30L);
        } catch (Exception e) {
            String hint =
                    "Kafka 连通性探测失败。若日志含 ScramMessages NoClassDefFoundError：删除集群"
                            + " $FLINK_HOME/lib/kafka-clients*.jar 后重启 Flink。"
                            + " 若含 listNodes Timeout：检查 JM→MSK 9096 网络/ACL。"
                            + " 平台提交请勿开启 --kafka.probe.enabled（默认 false）。";
            throw new IllegalStateException(
                    hint + " bootstrap=" + bootstrap + " topic=" + sourceTopic, e);
        }
    }

    private static Void runProbe(Properties props, String sourceTopic, long timeoutSec) throws Exception {
        try (AdminClient admin = AdminClient.create(props)) {
            Collection<Node> nodes =
                    admin.describeCluster().nodes().get(timeoutSec, TimeUnit.SECONDS);
            System.out.printf(
                    Locale.ROOT,
                    "[KafkaConnectivityProbe] listNodes OK, brokerCount=%d%n",
                    nodes.size());

            DescribeTopicsResult topicsResult = admin.describeTopics(List.of(sourceTopic));
            TopicDescription desc =
                    topicsResult
                            .allTopicNames()
                            .get(timeoutSec, TimeUnit.SECONDS)
                            .get(sourceTopic);
            if (desc == null) {
                throw new IllegalStateException("topic 不存在: " + sourceTopic);
            }
            System.out.printf(
                    Locale.ROOT,
                    "[KafkaConnectivityProbe] describeTopic OK, partitions=%d%n",
                    desc.partitions().size());
        }
        return null;
    }
}
