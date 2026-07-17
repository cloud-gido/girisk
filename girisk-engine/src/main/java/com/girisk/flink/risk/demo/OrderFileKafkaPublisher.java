package com.girisk.flink.risk.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.girisk.common.RiskTopics;
import com.girisk.flink.risk.excel.FootballSportsOrder;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * CSV orders → {@code OrderRiskCheckEvent} PENDING → Kafka pre topic (for real Flink later).
 *
 * <pre>
 * java -cp girisk-engine/target/girisk-engine-1.0.0.jar \
 *   com.girisk.flink.risk.demo.OrderFileKafkaPublisher \
 *   --file …/germany-vs-paraguay-orders.csv --dry-run
 * </pre>
 */
public final class OrderFileKafkaPublisher {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static void main(String[] args) throws Exception {
        Map<String, String> opts = parseArgs(args);
        Path file = Path.of(opts.getOrDefault("file", ""));
        if (opts.getOrDefault("file", "").isBlank() || !file.toFile().isFile()) {
            System.err.println(
                    "用法: --file <orders.csv> [--bootstrap localhost:9092] [--topic "
                            + RiskTopics.RISK_CHECK_PRE
                            + "]");
            System.err.println(
                    "      [--fixture-id germany-paraguay] [--rate 0] [--dry-run] [--limit 0]");
            System.exit(2);
        }

        String bootstrap = opts.getOrDefault("bootstrap", "localhost:9092");
        String topic = opts.getOrDefault("topic", RiskTopics.RISK_CHECK_PRE);
        String fixtureId = opts.getOrDefault("fixture-id", "germany-paraguay");
        String home = opts.getOrDefault("home", "Germany");
        String away = opts.getOrDefault("away", "Paraguay");
        boolean dryRun = opts.containsKey("dry-run") || "true".equalsIgnoreCase(opts.get("dry-run"));
        long rateMs = Long.parseLong(opts.getOrDefault("rate", "0"));
        int limit = Integer.parseInt(opts.getOrDefault("limit", "0"));

        List<FootballSportsOrder> orders = OrderCsvLoader.load(file, fixtureId, home, away);
        if (limit > 0 && limit < orders.size()) {
            orders = orders.subList(0, limit);
        }

        KafkaProducer<String, String> producer = null;
        if (!dryRun) {
            Properties props = new Properties();
            props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
            props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
            props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
            props.put(ProducerConfig.ACKS_CONFIG, "all");
            producer = new KafkaProducer<>(props);
        }

        int sent = 0;
        try {
            for (FootballSportsOrder o : orders) {
                String json = toRiskCheckJson(o, fixtureId);
                if (dryRun) {
                    if (sent < 3) {
                        System.out.println(json);
                    }
                } else {
                    producer.send(new ProducerRecord<>(topic, o.orderId, json)).get();
                }
                sent++;
                if (rateMs > 0) {
                    Thread.sleep(rateMs);
                }
            }
        } finally {
            if (producer != null) {
                producer.flush();
                producer.close();
            }
        }

        System.out.printf(
                "done: %d messages → %s (dryRun=%s bootstrap=%s)%n",
                sent, topic, dryRun, bootstrap);
    }

    static String toRiskCheckJson(FootballSportsOrder o, String fixtureId) throws Exception {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("eventType", "OrderRiskCheckEvent");
        root.put("aggregateId", o.orderId);
        root.put("eventId", "evt-" + o.orderId);
        root.put("operatorId", o.operatorId > 0 ? o.operatorId : 1);

        ObjectNode payload = root.putObject("payload");
        payload.put("status", "PENDING");
        payload.put("phase", "PRE_CONFIRM");
        payload.put("orderId", o.orderId);
        payload.put("stake", o.stakeCents() / 100.0);
        payload.put("betTime", o.orderTime == null || o.orderTime.isBlank() ? Instant.now().toString() : o.orderTime);
        payload.put("betType", "SINGLE");
        payload.put("playerId", o.userId == null || o.userId.isBlank() ? "demo-player" : o.userId);

        ArrayNode legs = payload.putArray("legs");
        ObjectNode leg = legs.addObject();
        leg.put("fixtureId", fixtureId);
        leg.put("price", o.odds);
        ObjectNode pick = leg.putObject("legPick");
        pick.put("type", "1X2");
        pick.put("side", OrderCsvLoader.selectionToSide(o.selection));
        return MAPPER.writeValueAsString(root);
    }

    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if ("--dry-run".equals(a)) {
                m.put("dry-run", "true");
                continue;
            }
            if (a.startsWith("--") && i + 1 < args.length && !args[i + 1].startsWith("--")) {
                m.put(a.substring(2), args[++i]);
            } else if (a.startsWith("--") && a.contains("=")) {
                int eq = a.indexOf('=');
                m.put(a.substring(2, eq), a.substring(eq + 1));
            }
        }
        return m;
    }
}
