package com.girisk.flink.risk;

import com.girisk.flink.support.kafka.KafkaClientClassLoaders;
import com.girisk.flink.support.kafka.KafkaClientConfigs;
import com.girisk.flink.support.kafka.KafkaConnectivityProbe;
import com.girisk.flink.risk.grid.ScoreGridParams;
import com.girisk.flink.risk.runtime.FlinkProductionRuntime;
import com.girisk.flink.risk.kafka.FootballOrderKafkaStringSink;
import com.girisk.flink.risk.kafka.FootballOrderKafkaTopics;
import com.girisk.flink.risk.FootballOrderDetailLiveScoreCoProcessFunction;
import com.girisk.flink.risk.kafka.FootballOrderMatrixExpandFunction;
import com.girisk.flink.risk.kafka.FootballOrderMatrixJsonLinesFunction;
import com.girisk.flink.risk.kafka.LiveScoreKafkaParseFunction;
import com.girisk.flink.risk.kafka.LiveScoreFixtureKeyFunction;
import com.girisk.flink.risk.fixture.FixtureMetadataLookups;
import com.girisk.flink.risk.kafka.KafkaTopicEnsurer;
import com.girisk.flink.risk.kafka.RiskOrderIngress;
import com.girisk.flink.risk.redis.RedisFixtureViewSink;
import com.girisk.flink.risk.model.EnrichedFootballOrder;
import com.girisk.flink.risk.model.LiveMatchScore;
import com.girisk.flink.risk.model.RiskOrderStreamEvent;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import com.girisk.flink.support.util.CliParameterTool;

import java.util.Locale;
import java.util.Properties;

/**
 * 足球订单作业：两个 Kafka topic。
 *
 * <ul>
 *   <li>{@code --sink.topic.detail}：明细，每笔订单 × 假设比分（schemaVersion=3）</li>
 *   <li>{@code --sink.topic.summary}：汇总，每场每次触发一条嵌套快照（schemaVersion=8，含 triggerOrder + assumedScores 全网格）</li>
 *   <li>{@code --sink.topic.business}：summary ∪ limit（union all，每条仅一侧有值）</li>
 * </ul>
 *
 * <p>默认 {@code --bootstrap} 为开发 Kafka {@link KafkaBootstrapDefaults#DEV}（本机无 broker）。
 */
public final class FootballOrderKafkaJob {

    public static void main(String[] args) throws Exception {
        CliParameterTool t = CliParameterTool.fromArgs(args);
        KafkaClientClassLoaders.useUserCodeClassLoader(FootballOrderKafkaJob.class);
        String bootstrap = KafkaClientConfigs.resolveBootstrap(t);
        Properties kafkaProps = KafkaClientConfigs.clientProperties(t);
        KafkaClientConfigs.logSecuritySummary(t);
        if (KafkaClientConfigs.isSecured(t)) {
            KafkaClientClassLoaders.logScramClasspathDiagnostics(FootballOrderKafkaJob.class);
        }
        if (KafkaClientConfigs.isSecured(t) && !kafkaProps.containsKey("sasl.jaas.config")) {
            System.err.println(
                    "[FootballOrderKafkaJob] SASL 已启用但未配置 JAAS：请设置 --kafka.sasl.username/password"
                            + " 或 INFRA_KAFKA_SASL_USERNAME/PASSWORD 或 --kafka.sasl.jaas.config");
        }
        String groupId = t.get("group.id", "girisk-engine");

        if (t.getBoolean("kafka.probe.enabled", false)) {
            String probeTopic =
                    t.get("source.topic.pre", FootballOrderKafkaTopics.RISK_CHECK_PRE).trim();
            KafkaConnectivityProbe.verifyOrThrow(t, probeTopic);
        } else {
            System.out.println(
                    "[FootballOrderKafkaJob] kafka.probe.enabled=false，跳过启动前 Kafka 探测（平台提交默认）");
        }

        ScoreGridParams gridParams = ScoreGridParams.from(t);
        long outOfOrderSec = t.getLong("eventTime.outOfOrderSec", 10L);

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        FlinkProductionRuntime.apply(env, t);
        if (t.has("parallelism")) {
            env.setParallelism(t.getInt("parallelism"));
        }

        RiskOrderIngress.Ingress ingress =
                RiskOrderIngress.build(
                        env,
                        t,
                        bootstrap,
                        kafkaProps,
                        groupId,
                        FixtureMetadataLookups.from(t),
                        outOfOrderSec);
        SingleOutputStreamOperator<EnrichedFootballOrder> orders = ingress.preOrders;
        SingleOutputStreamOperator<RiskOrderStreamEvent> riskEvents = ingress.riskEvents;
        boolean postFeedbackEnabled = ingress.postFeedbackEnabled;
        String preTopic = ingress.preTopic;
        String postTopic = ingress.postFeedbackEnabled ? ingress.postTopic : "";
        String offset = ingress.offsetPre;

        long cleanupHours = t.getLong("match.state.cleanupHoursAfterKickoff", 3L);
        long cleanupDelayMs = cleanupHours * 3_600_000L;
        double limitDelta = t.getDouble("limit.delta", 0.2);
        // 冷启动虚拟种子（返彩口径，产品计算器默认 2000）
        double seedPayoutYuan = t.getDouble("limit.initialSeedPayoutYuan", 2000.0);
        // Gate 2 风险敞口阈值：最差净盈亏 < -该值 时建议拒单（产品示例 1000）
        double maxWorstLossYuan = t.getDouble("exposure.maxWorstLossYuan", 1000.0);
        boolean limitEnabled = t.getBoolean("limit.enabled", true);
        boolean liveScoreEnabled = t.getBoolean("live.score.enabled", true);
        String liveScoreTopic = t.get("live.score.topic", FootballOrderKafkaTopics.LIVE_SCORE).trim();
        String liveScoreGroupId = t.get("live.score.group.id", groupId + "-live-score");
        String liveScoreOffset = t.get("live.score.offset", offset);

        String sinkBootstrap = t.get("sink.bootstrap", bootstrap);
        String expandTopic =
                t.get("sink.topic.risk", t.get("sink.topic.detail", FootballOrderKafkaTopics.DETAIL))
                        .trim();
        String summaryTopic =
                t.get(
                                "sink.topic.summary",
                                t.get("sink.topic.matrix", FootballOrderKafkaTopics.SUMMARY))
                        .trim();
        String limitTopic =
                limitEnabled
                        ? t.get("sink.topic.limit", FootballOrderKafkaTopics.LIMIT).trim()
                        : "";
        boolean businessEnabled = limitEnabled && t.getBoolean("sink.business.enabled", true);
        String businessTopic =
                businessEnabled
                        ? t.get("sink.topic.business", FootballOrderKafkaTopics.BUSINESS).trim()
                        : "";
        boolean decisionEnabled = t.getBoolean("sink.decision.enabled", true);
        String decisionTopic =
                decisionEnabled
                        ? t.get("sink.topic.decision", FootballOrderKafkaTopics.DECISION).trim()
                        : "";
        boolean redisViewEnabled = t.getBoolean("sink.redis.view.enabled", false);
        String redisHost = t.get("sink.redis.host", "127.0.0.1");
        int redisPort = t.getInt("sink.redis.port", 6379);
        String redisPassword = t.get("sink.redis.password", "");
        long pendingReserveTtlMs = t.getLong("pending.reserve.ttlMs", 30_000L);
        boolean expandPrint = t.getBoolean("sink.print.expand", false);
        boolean summaryPrint = t.getBoolean("sink.print.summary", t.getBoolean("sink.print", false));
        boolean limitPrint = t.getBoolean("sink.print.limit", false);
        boolean businessPrint = t.getBoolean("sink.print.business", false);
        boolean decisionPrint = t.getBoolean("sink.print.decision", false);

        RiskSnapshotEmitFlags snapshotEmitFlags =
                new RiskSnapshotEmitFlags(
                        !summaryTopic.isEmpty() || summaryPrint || redisViewEnabled,
                        !limitTopic.isEmpty() || limitPrint,
                        !businessTopic.isEmpty() || businessPrint,
                        !decisionTopic.isEmpty() || decisionPrint);
        boolean needSnapshotPipeline =
                snapshotEmitFlags.summary
                        || snapshotEmitFlags.limit
                        || snapshotEmitFlags.business
                        || snapshotEmitFlags.decision;

        System.out.printf(
                Locale.ROOT,
                "[FootballOrderKafkaJob] bootstrap=%s pre=%s post=%s postFeedback=%s liveScore=%s(fallback=%d:%d grid=%d) detail=%s summary=%s limit=%s business=%s seedPayoutYuan=%s worstLossYuan=%s offset.pre=%s%n",
                bootstrap,
                preTopic,
                postFeedbackEnabled ? postTopic : "disabled",
                postFeedbackEnabled,
                liveScoreEnabled ? liveScoreTopic : "disabled",
                gridParams.baseHome,
                gridParams.baseAway,
                gridParams.grid.homeSpan(),
                expandTopic,
                summaryTopic,
                limitTopic,
                businessTopic,
                seedPayoutYuan,
                maxWorstLossYuan,
                offset);
        if (t.has("fixture.dim.file")) {
            System.out.println(
                    "[FootballOrderKafkaJob] --fixture.dim.file 已配置：维表命中时补全联赛/主客/开赛，未命中则留空仍输出");
        }

        if (t.getBoolean("sink.ensureTopic", false)) {
            int sinkPartitions = t.getInt("sink.topic.partitions", 1);
            short sinkReplication = (short) t.getInt("sink.topic.replication", 1);
            KafkaTopicEnsurer.ensureTopicExistsOrWarn(t, preTopic, sinkPartitions, sinkReplication);
            if (postFeedbackEnabled && !postTopic.isEmpty()) {
                KafkaTopicEnsurer.ensureTopicExistsOrWarn(t, postTopic, sinkPartitions, sinkReplication);
            }
            if (liveScoreEnabled && !liveScoreTopic.isEmpty()) {
                KafkaTopicEnsurer.ensureTopicExistsOrWarn(
                        t, liveScoreTopic, sinkPartitions, sinkReplication);
            }
            if (!expandTopic.isEmpty()) {
                KafkaTopicEnsurer.ensureTopicExistsOrWarn(t, expandTopic, sinkPartitions, sinkReplication);
            }
            if (!summaryTopic.isEmpty() && !summaryTopic.equals(expandTopic)) {
                KafkaTopicEnsurer.ensureTopicExistsOrWarn(t, summaryTopic, sinkPartitions, sinkReplication);
            }
            if (!limitTopic.isEmpty()
                    && !limitTopic.equals(expandTopic)
                    && !limitTopic.equals(summaryTopic)) {
                KafkaTopicEnsurer.ensureTopicExistsOrWarn(t, limitTopic, sinkPartitions, sinkReplication);
            }
            if (!businessTopic.isEmpty()
                    && !businessTopic.equals(expandTopic)
                    && !businessTopic.equals(summaryTopic)
                    && !businessTopic.equals(limitTopic)) {
                KafkaTopicEnsurer.ensureTopicExistsOrWarn(t, businessTopic, sinkPartitions, sinkReplication);
            }
        }

        DataStream<LiveMatchScore> liveScores = null;
        if (liveScoreEnabled && !liveScoreTopic.isEmpty()) {
            KafkaSource<String> liveScoreSource =
                    KafkaSource.<String>builder()
                            .setBootstrapServers(bootstrap)
                            .setProperties(kafkaProps)
                            .setTopics(liveScoreTopic)
                            .setGroupId(liveScoreGroupId)
                            .setStartingOffsets(
                                    "earliest".equalsIgnoreCase(liveScoreOffset)
                                            ? OffsetsInitializer.earliest()
                                            : OffsetsInitializer.latest())
                            .setValueOnlyDeserializer(new SimpleStringSchema())
                            .build();
            liveScores =
                    env.fromSource(
                                    liveScoreSource,
                                    WatermarkStrategy.noWatermarks(),
                                    "kafka-live-score")
                            .uid("kafka-live-score-source")
                            .name("kafka-live-score-source")
                            .flatMap(new LiveScoreKafkaParseFunction())
                            .name("parse-live-score")
                            .map(new LiveScoreFixtureKeyFunction())
                            .name("live-score-fixture-key");
        }

        if (!expandTopic.isEmpty() || expandPrint) {
            DataStream<String> expanded;
            if (liveScores != null) {
                expanded =
                        orders.keyBy(
                                        o ->
                                                o.order.fixtureId == null
                                                        ? ""
                                                        : o.order.fixtureId.trim())
                                .connect(liveScores.keyBy(LiveMatchScore::getFixtureIdForKey))
                                .process(
                                        new FootballOrderDetailLiveScoreCoProcessFunction(
                                                gridParams, cleanupDelayMs))
                                .uid("order-grid-expand-live-score")
                                .name("order-grid-expand-json-live");
            } else {
                expanded =
                        orders.flatMap(new FootballOrderMatrixJsonLinesFunction(gridParams))
                                .name("order-grid-expand-json");
            }
            if (!expandTopic.isEmpty()) {
                expanded.sinkTo(FootballOrderKafkaStringSink.utf8Lines(sinkBootstrap, expandTopic, kafkaProps))
                        .name("kafka-sink-order-expand");
            }
            if (expandPrint) {
                expanded.print("OrderExpand");
            }
        }

        if (needSnapshotPipeline) {
            SingleOutputStreamOperator<String> summary;
            if (liveScores != null) {
                summary =
                        riskEvents
                                .keyBy(RiskOrderStreamEvent::fixtureIdForKey)
                                .connect(liveScores.keyBy(LiveMatchScore::getFixtureIdForKey))
                                .process(
                                        new MatchExposureLiveScoreCoProcessFunction(
                                                gridParams,
                                                cleanupDelayMs,
                                                limitDelta,
                                                seedPayoutYuan,
                                                maxWorstLossYuan,
                                                snapshotEmitFlags,
                                                postFeedbackEnabled,
                                                pendingReserveTtlMs))
                                .uid("match-exposure-summary-live")
                                .name("match-exposure-summary-json-live");
            } else {
                summary =
                        riskEvents
                                .keyBy(
                                        postFeedbackEnabled
                                                ? RiskOrderStreamEvent::fixtureIdForKey
                                                : e -> e.prePending.matchKey)
                                .process(
                                        new MatchExposureKafkaProcessFunction(
                                                gridParams,
                                                cleanupDelayMs,
                                                limitDelta,
                                                seedPayoutYuan,
                                                maxWorstLossYuan,
                                                snapshotEmitFlags,
                                                postFeedbackEnabled))
                                .uid("match-exposure-summary")
                                .name("match-exposure-summary-json");
            }
            if (!summaryTopic.isEmpty()) {
                summary.sinkTo(FootballOrderKafkaStringSink.utf8Lines(sinkBootstrap, summaryTopic, kafkaProps))
                        .name("kafka-sink-match-summary");
            }
            if (summaryPrint) {
                summary.print("MatchSummary");
            }
            if (redisViewEnabled) {
                summary.addSink(new RedisFixtureViewSink(redisHost, redisPort, redisPassword))
                        .name("redis-fixture-view-sink");
            }

            DataStream<String> limits =
                    summary.getSideOutput(MatchExposureKafkaProcessFunction.LIMIT_SNAPSHOT_TAG);
            if (!limitTopic.isEmpty()) {
                limits.sinkTo(FootballOrderKafkaStringSink.utf8Lines(sinkBootstrap, limitTopic, kafkaProps))
                        .name("kafka-sink-match-limit");
            }
            if (limitPrint) {
                limits.print("MatchLimit");
            }

            DataStream<String> business =
                    summary.getSideOutput(MatchExposureKafkaProcessFunction.BUSINESS_SNAPSHOT_TAG);
            if (!businessTopic.isEmpty()) {
                business.sinkTo(
                                FootballOrderKafkaStringSink.utf8Lines(
                                        sinkBootstrap, businessTopic, kafkaProps))
                        .name("kafka-sink-match-business");
            }
            if (businessPrint) {
                business.print("MatchBusiness");
            }

            DataStream<String> decisions =
                    summary.getSideOutput(MatchExposureKafkaProcessFunction.DECISION_TAG);
            if (!decisionTopic.isEmpty()) {
                decisions
                        .sinkTo(
                                FootballOrderKafkaStringSink.utf8Lines(
                                        sinkBootstrap, decisionTopic, kafkaProps))
                        .name("kafka-sink-risk-decision");
            }
            if (decisionPrint) {
                decisions.print("RiskDecision");
            }
        }

        if (t.getBoolean("matrix.legacyPrint", false)) {
            orders.flatMap(new FootballOrderMatrixExpandFunction(gridParams))
                    .name("expand-score-matrix-text")
                    .print("OrderMatrix");
        }

        env.execute(
                String.format(
                        "football-order-kafka-%d-%d", gridParams.baseHome, gridParams.baseAway));
    }

    private FootballOrderKafkaJob() {}
}
