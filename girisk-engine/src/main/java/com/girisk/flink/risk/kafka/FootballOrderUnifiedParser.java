package com.girisk.flink.risk.kafka;

import com.girisk.flink.risk.excel.FootballSportsOrder;
import com.girisk.flink.risk.model.OrderPostStatusUpdate;

/** pre / post topic 解析入口。 */
public final class FootballOrderUnifiedParser {

    private FootballOrderUnifiedParser() {}

    public static FootballSportsOrder parse(String rawLine) {
        if (BetConfirmedEventJsonParser.looksLikeJson(rawLine)) {
            return BetConfirmedEventJsonParser.parse(rawLine);
        }
        return KafkaFootballOrderCsvParser.parse(
                FootballOrderKafkaProducerJob.normalize(rawLine));
    }

    /**
     * 解析 pre topic PENDING；非 {@code OrderRiskCheckEvent + PENDING} 或解析失败时写 WARN 并返回 empty。
     *
     * @param acceptCsv {@code true} 时额外接受 14 列 CSV（联调用，生产建议 {@code false}）
     */
    public static ParseOutcome tryParseForRiskPipeline(String rawLine, boolean acceptCsv) {
        TradingEnvelopePeek peek = TradingEnvelopePeek.fromRaw(rawLine);
        if (peek.json) {
            if (!peek.isExpectedRiskCheckPending()) {
                OrderParseLog.warnUnexpectedEnvelope(peek, rawLine);
                return ParseOutcome.skipped(peek.describeMismatch());
            }
            return parseJsonOrder(rawLine);
        }
        if (acceptCsv) {
            return parseCsvOrder(rawLine);
        }
        OrderParseLog.warnSkip("非 JSON envelope（期望 OrderRiskCheckEvent + status=PENDING）", rawLine);
        return ParseOutcome.skipped("非 JSON envelope");
    }

    public static ParseOutcome tryParseForRiskPipeline(String rawLine) {
        return tryParseForRiskPipeline(rawLine, false);
    }

    /** 解析 post topic：CONFIRMED / REJECTED / CASHED_OUT。 */
    public static PostParseOutcome tryParsePostStatus(String rawLine) {
        TradingEnvelopePeek peek = TradingEnvelopePeek.fromRaw(rawLine);
        if (!peek.json) {
            OrderParseLog.warnSkip("post 非 JSON envelope", rawLine);
            return PostParseOutcome.skipped("非 JSON envelope");
        }
        if (!peek.isExpectedRiskCheckPost()) {
            OrderParseLog.warnSkip(
                    "post 非预期 status（期望 CONFIRMED/REJECTED/CASHED_OUT）: "
                            + peek.describeMismatch(),
                    rawLine);
            return PostParseOutcome.skipped(peek.describeMismatch());
        }
        try {
            OrderPostStatusUpdate update = OrderRiskPostJsonParser.parse(rawLine);
            return PostParseOutcome.ok(update);
        } catch (IllegalArgumentException e) {
            OrderParseLog.warnSkip(e.getMessage(), rawLine);
            return PostParseOutcome.skipped(e.getMessage());
        }
    }

    private static ParseOutcome parseJsonOrder(String rawLine) {
        try {
            FootballSportsOrder order = BetConfirmedEventJsonParser.parse(rawLine);
            if (order.fixtureId == null || order.fixtureId.isBlank()) {
                OrderParseLog.warnSkip("缺少 fixtureId", rawLine);
                return ParseOutcome.skipped("缺少 fixtureId");
            }
            return ParseOutcome.ok(order);
        } catch (IllegalArgumentException e) {
            OrderParseLog.warnSkip(e.getMessage(), rawLine);
            return ParseOutcome.skipped(e.getMessage());
        }
    }

    private static ParseOutcome parseCsvOrder(String rawLine) {
        try {
            FootballSportsOrder order =
                    KafkaFootballOrderCsvParser.parse(
                            FootballOrderKafkaProducerJob.normalize(rawLine));
            if (order.fixtureId == null || order.fixtureId.isBlank()) {
                OrderParseLog.warnSkip("缺少 fixtureId", rawLine);
                return ParseOutcome.skipped("缺少 fixtureId");
            }
            return ParseOutcome.ok(order);
        } catch (IllegalArgumentException e) {
            OrderParseLog.warnSkip(e.getMessage(), rawLine);
            return ParseOutcome.skipped(e.getMessage());
        }
    }

    public static final class ParseOutcome {
        public final FootballSportsOrder order;
        public final String skipReason;

        private ParseOutcome(FootballSportsOrder order, String skipReason) {
            this.order = order;
            this.skipReason = skipReason;
        }

        public static ParseOutcome ok(FootballSportsOrder order) {
            return new ParseOutcome(order, null);
        }

        public static ParseOutcome skipped(String reason) {
            return new ParseOutcome(null, reason);
        }

        public boolean isOk() {
            return order != null;
        }
    }

    public static final class PostParseOutcome {
        public final OrderPostStatusUpdate update;
        public final String skipReason;

        private PostParseOutcome(OrderPostStatusUpdate update, String skipReason) {
            this.update = update;
            this.skipReason = skipReason;
        }

        public static PostParseOutcome ok(OrderPostStatusUpdate update) {
            return new PostParseOutcome(update, null);
        }

        public static PostParseOutcome skipped(String reason) {
            return new PostParseOutcome(null, reason);
        }

        public boolean isOk() {
            return update != null;
        }
    }
}
