package com.girisk.flink.risk.limit;

/** 与 riskPlatform {@code SportsMarketType} 选项键对齐。 */
public enum LimitMarketType {
    ONE_X_TWO("胜负平", new String[] {"home", "draw", "away"}),
    OVER_UNDER("大小球", new String[] {"over", "under"}),
    HANDICAP("让球", new String[] {"home", "away"}),
    HANDICAP_THREE_WAY("让球胜平负", new String[] {"home", "draw", "away"});

    private final String label;
    private final String[] selections;

    LimitMarketType(String label, String[] selections) {
        this.label = label;
        this.selections = selections;
    }

    public String label() {
        return label;
    }

    public String[] selections() {
        return selections;
    }
}
