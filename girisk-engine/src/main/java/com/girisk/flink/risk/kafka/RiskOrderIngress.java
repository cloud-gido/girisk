package com.girisk.flink.risk.kafka;

import com.girisk.flink.risk.OrderPostFixtureEnrichFunction;
import com.girisk.flink.risk.fixture.FixtureMetadataLookup;
import com.girisk.flink.risk.model.EnrichedFootballOrder;
import com.girisk.flink.risk.model.OrderPostStatusUpdate;
import com.girisk.flink.risk.model.RiskOrderStreamEvent;
import com.girisk.flink.support.util.CliParameterTool;
import org.apache.flink.api.common.eventtime.SerializableTimestampAssigner;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

import java.time.Duration;
import java.util.Properties;

/** pre / post Kafka 源解析与合并为 {@link RiskOrderStreamEvent}。 */
public final class RiskOrderIngress {

    private RiskOrderIngress() {}

    public static Ingress build(
            StreamExecutionEnvironment env,
            CliParameterTool t,
            String bootstrap,
            Properties kafkaProps,
            String groupId,
            FixtureMetadataLookup fixtureLookup,
            long outOfOrderSec) {
        boolean postFeedback = t.getBoolean("source.post.enabled", true);
        String postTopic = t.get("source.topic.post", FootballOrderKafkaTopics.RISK_CHECK_POST).trim();
        boolean usePostFeedback = postFeedback && !postTopic.isEmpty();

        String preTopic =
                t.get("source.topic.pre", t.get("source.topic", FootballOrderKafkaTopics.RISK_CHECK_PRE))
                        .trim();
        String offsetDefault = t.get("offset", "latest");
        String offsetPre = t.get("offset.pre", offsetDefault);
        boolean acceptCsv = t.getBoolean("source.accept.csv", false);

        SingleOutputStreamOperator<EnrichedFootballOrder> preOrders =
                env.fromSource(
                                kafkaSource(
                                        bootstrap,
                                        kafkaProps,
                                        preTopic,
                                        groupId,
                                        offsetPre),
                                WatermarkStrategy.noWatermarks(),
                                "kafka-risk-check-pre")
                        .uid("kafka-risk-check-pre-source")
                        .name("kafka-risk-check-pre-source")
                        .process(
                                new KafkaFootballOrderEnrichedParseFunction(fixtureLookup, acceptCsv))
                        .uid("parse-enrich-pre-order")
                        .name("parse-enrich-pre-order");

        WatermarkStrategy<RiskOrderStreamEvent> riskWatermarks =
                WatermarkStrategy.<RiskOrderStreamEvent>forBoundedOutOfOrderness(
                                Duration.ofSeconds(outOfOrderSec))
                        .withTimestampAssigner(
                                (SerializableTimestampAssigner<RiskOrderStreamEvent>)
                                        (element, recordTimestamp) -> element.eventTimeMs());

        SingleOutputStreamOperator<RiskOrderStreamEvent> riskEvents;
        if (usePostFeedback) {
            String offsetPost = t.get("offset.post", "earliest");
            String postGroupId = t.get("group.id.post", groupId + "-post");
            DataStream<OrderPostStatusUpdate> postUpdates =
                    env.fromSource(
                                    kafkaSource(
                                            bootstrap,
                                            kafkaProps,
                                            postTopic,
                                            postGroupId,
                                            offsetPost),
                                    WatermarkStrategy.noWatermarks(),
                                    "kafka-risk-check-post")
                            .uid("kafka-risk-check-post-source")
                            .name("kafka-risk-check-post-source")
                            .process(new KafkaFootballOrderPostParseFunction())
                            .name("parse-post-order");

            riskEvents =
                    preOrders
                            .keyBy(o -> normalizeOrderId(o.order.orderId))
                            .connect(postUpdates.keyBy(u -> normalizeOrderId(u.orderId)))
                            .process(new OrderPostFixtureEnrichFunction())
                            .uid("enrich-post-fixture")
                            .name("enrich-post-fixture")
                            .assignTimestampsAndWatermarks(riskWatermarks);
        } else {
            riskEvents =
                    preOrders
                            .map(RiskOrderStreamEvent::prePending)
                            .name("wrap-pre-pending")
                            .assignTimestampsAndWatermarks(riskWatermarks);
        }

        preOrders =
                preOrders.assignTimestampsAndWatermarks(
                        WatermarkStrategy.<EnrichedFootballOrder>forBoundedOutOfOrderness(
                                        Duration.ofSeconds(outOfOrderSec))
                                .withTimestampAssigner(
                                        (SerializableTimestampAssigner<EnrichedFootballOrder>)
                                                (element, recordTimestamp) -> element.orderTimeMs));

        return new Ingress(preOrders, riskEvents, usePostFeedback, preTopic, postTopic, offsetPre);
    }

    private static KafkaSource<String> kafkaSource(
            String bootstrap,
            Properties kafkaProps,
            String topic,
            String groupId,
            String offset) {
        return KafkaSource.<String>builder()
                .setBootstrapServers(bootstrap)
                .setProperties(kafkaProps)
                .setTopics(topic)
                .setGroupId(groupId)
                .setStartingOffsets(
                        "earliest".equalsIgnoreCase(offset)
                                ? OffsetsInitializer.earliest()
                                : OffsetsInitializer.latest())
                .setValueOnlyDeserializer(new SimpleStringSchema())
                .build();
    }

    private static String normalizeOrderId(String orderId) {
        return orderId == null ? "" : orderId.trim();
    }

    public static final class Ingress {
        public final SingleOutputStreamOperator<EnrichedFootballOrder> preOrders;
        public final SingleOutputStreamOperator<RiskOrderStreamEvent> riskEvents;
        public final boolean postFeedbackEnabled;
        public final String preTopic;
        public final String postTopic;
        public final String offsetPre;

        public Ingress(
                SingleOutputStreamOperator<EnrichedFootballOrder> preOrders,
                SingleOutputStreamOperator<RiskOrderStreamEvent> riskEvents,
                boolean postFeedbackEnabled,
                String preTopic,
                String postTopic,
                String offsetPre) {
            this.preOrders = preOrders;
            this.riskEvents = riskEvents;
            this.postFeedbackEnabled = postFeedbackEnabled;
            this.preTopic = preTopic;
            this.postTopic = postTopic;
            this.offsetPre = offsetPre;
        }
    }
}
