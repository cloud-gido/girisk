package com.girisk.flink.risk.kafka;

import com.girisk.flink.support.kafka.KafkaClientConfigs;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

import com.girisk.flink.support.util.CliParameterTool;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * 类似 kafka-console-producer：每输入一行回车即发送，不退出；输入 {@code quit} / {@code exit} 或 Ctrl+C 结束。
 *
 * <p>IDE 直接运行 main；默认连开发 Kafka {@link com.girisk.flink.support.KafkaBootstrapDefaults#DEV}，可省略
 * {@code --bootstrap}。
 */
public final class FootballOrderKafkaInteractiveProducer {

    public static void main(String[] args) throws Exception {
        CliParameterTool t = CliParameterTool.fromMap(parseArgs(args));
        String bootstrap = KafkaClientConfigs.resolveBootstrap(t);
        String topic = t.get("topic", "football.order");
        boolean ensureTopic = t.getBoolean("ensureTopic", true);

        if (ensureTopic) {
            KafkaTopicEnsurer.ensureTopicExists(
                    t, topic, t.getInt("topicPartitions", 1), (short) t.getInt("topicReplication", 1));
        }

        Properties props = KafkaClientConfigs.producerProperties(t);
        KafkaClientConfigs.logSecuritySummary(t);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.CLIENT_ID_CONFIG, "football-order-interactive-producer");

        System.out.printf("交互写 Kafka → %s  topic=%s%n", bootstrap, topic);
        System.out.println(KafkaFootballOrderCsvParser.formatSpec());
        System.out.println("每行一笔，回车发送；输入 quit 或 exit 退出。");
        System.out.println();

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props);
                BufferedReader reader =
                        new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            while (true) {
                System.out.print("> ");
                System.out.flush();
                String line = reader.readLine();
                if (line == null) {
                    break;
                }
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                if (isQuit(trimmed)) {
                    System.out.println("退出。");
                    break;
                }
                try {
                    sendOne(producer, topic, line);
                } catch (IllegalArgumentException e) {
                    System.out.println("  格式错误: " + e.getMessage());
                } catch (Exception e) {
                    System.out.println("  发送失败: " + e.getMessage());
                    if (e.getMessage() != null && e.getMessage().contains("not present")) {
                        KafkaTopicEnsurer.printCreateTopicHint(bootstrap, topic);
                    }
                }
            }
            producer.flush();
        }
    }

    private static void sendOne(KafkaProducer<String, String> producer, String topic, String rawLine)
            throws Exception {
        String payload = FootballOrderKafkaProducerJob.normalize(rawLine);
        FootballOrderUnifiedParser.parse(payload);
        String key = payload.split(",", 2)[0].trim();
        producer.send(new ProducerRecord<>(topic, key, payload)).get();
        System.out.printf("  ✓ 已发送 [%s]%n", key);
    }

    private static boolean isQuit(String line) {
        String lower = line.toLowerCase();
        return "quit".equals(lower) || "exit".equals(lower) || "q".equals(lower);
    }

    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            if (args[i].startsWith("--")) {
                String key = args[i].substring(2);
                if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                    map.put(key, args[++i]);
                } else {
                    map.put(key, "true");
                }
            }
        }
        return map;
    }

    private FootballOrderKafkaInteractiveProducer() {}
}
