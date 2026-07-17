package com.girisk.flink.risk.kafka;

import org.apache.flink.connector.base.DeliveryGuarantee;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import com.girisk.flink.support.kafka.KafkaClientConfigs;
import com.girisk.flink.support.util.CliParameterTool;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * 批量写 Kafka（Flink 一次性提交）：适合 {@code --file} / {@code --line} / {@code --example}。
 *
 * <p><b>逐行回车即发、不退出</b>请用 {@link FootballOrderKafkaInteractiveProducer}（kafka-console-producer 体验）。
 *
 * <pre>
 * # 交互逐条发送（推荐本地联调）
 * com.girisk.flink.risk.kafka.FootballOrderKafkaInteractiveProducer
 *
 * # 批量（文件/单行）
 * flink run -c ...FootballOrderKafkaProducerJob --file orders.txt
 * </pre>
 */
public final class FootballOrderKafkaProducerJob {

    public static void main(String[] args) throws Exception {
        CliParameterTool t = CliParameterTool.fromArgs(args);
        String bootstrap = KafkaClientConfigs.resolveBootstrap(t);
        Properties kafkaProps = KafkaClientConfigs.clientProperties(t);
        KafkaClientConfigs.logSecuritySummary(t);
        String topic = t.get("topic", "football.order");

        List<String> rawLines = loadLines(t);
        if (rawLines.isEmpty()) {
            printUsage();
            return;
        }

        boolean ensureTopic = !"false".equalsIgnoreCase(t.get("ensureTopic", "true"));
        int partitions = t.getInt("topicPartitions", 1);
        short replication = (short) t.getInt("topicReplication", 1);
        if (ensureTopic) {
            try {
                KafkaTopicEnsurer.ensureTopicExists(t, topic, partitions, replication);
            } catch (Exception e) {
                System.err.printf("[Kafka] 自动创建 topic 失败: %s%n", e.getMessage());
                KafkaTopicEnsurer.printCreateTopicHint(bootstrap, topic);
                throw e;
            }
        } else {
            KafkaTopicEnsurer.printCreateTopicHint(bootstrap, topic);
        }

        List<KafkaOrderMessage> messages = new ArrayList<>();
        for (String raw : rawLines) {
            String payload = FootballOrderKafkaProducerJob.normalize(raw);
            FootballOrderUnifiedParser.parse(payload);
            String orderId = payload.split(",", 2)[0].trim();
            messages.add(new KafkaOrderMessage(orderId, payload));
            System.out.printf("待发送: [%s] %s%n", orderId, truncate(payload, 100));
        }

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);

        DataStream<KafkaOrderMessage> stream = env.fromCollection(messages).name("manual-orders");

        KafkaSink<KafkaOrderMessage> sink =
                KafkaSink.<KafkaOrderMessage>builder()
                        .setBootstrapServers(bootstrap)
                        .setKafkaProducerConfig(kafkaProps)
                        .setRecordSerializer(
                                KafkaRecordSerializationSchema.<KafkaOrderMessage>builder()
                                        .setTopic(topic)
                                        .setKeySerializationSchema(
                                                new KafkaOrderMessageSerializationSchemas.KeySchema())
                                        .setValueSerializationSchema(
                                                new KafkaOrderMessageSerializationSchemas.ValueSchema())
                                        .build())
                        .setDeliveryGuarantee(DeliveryGuarantee.AT_LEAST_ONCE)
                        .build();

        stream.sinkTo(sink).name("kafka-order-sink");
        System.out.printf("开始写入 Kafka %s → topic [%s]，共 %d 条%n", bootstrap, topic, messages.size());
        try {
            env.execute("football-order-kafka-producer");
            System.out.printf("写入完成，共 %d 条 → topic %s%n", messages.size(), topic);
        } catch (Exception e) {
            if (rootMessage(e).contains("not present in metadata")) {
                KafkaTopicEnsurer.printCreateTopicHint(bootstrap, topic);
            }
            throw e;
        }
    }

    static String normalize(String line) {
        return OrderCsvLineNormalizer.normalizeLine(line);
    }

    private static List<String> loadLines(CliParameterTool t) throws IOException {
        if (t.has("example")) {
            return List.of(exampleLine());
        }
        if (t.has("line")) {
            return List.of(t.get("line"));
        }
        if (t.has("file")) {
            List<String> lines = new ArrayList<>();
            for (String line : Files.readAllLines(Path.of(t.get("file")), StandardCharsets.UTF_8)) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("#")) {
                    lines.add(line);
                }
            }
            return lines;
        }
        System.out.println("未指定 --example / --line / --file。");
        System.out.println("逐行回车即发请运行: com.girisk.flink.risk.kafka.FootballOrderKafkaInteractiveProducer");
        return List.of();
    }

    private static String exampleLine() {
        return "13883500,FB202605180001,2026-05-18 10:12,U1001,模拟英超,北岸FC,南城竞技,2026-05-20 20:00,胜平负,单关,无,主胜,1.86,¥100.00";
    }

    private static void printUsage() {
        System.out.println("未录入订单。参数: --bootstrap --topic [--example | --line | --file | 交互默认]");
        System.out.println(KafkaFootballOrderCsvParser.formatSpec());
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    private static String rootMessage(Throwable t) {
        Throwable c = t;
        while (c.getCause() != null) {
            c = c.getCause();
        }
        return c.getMessage() == null ? "" : c.getMessage();
    }

    private FootballOrderKafkaProducerJob() {}
}
