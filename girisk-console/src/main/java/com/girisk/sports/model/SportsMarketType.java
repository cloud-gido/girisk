package com.girisk.sports.model;

public enum SportsMarketType {
    ONE_X_TWO("胜负平", new String[]{"home", "draw", "away"}),
    OVER_UNDER("大小球", new String[]{"over", "under"}),
    HANDICAP("让球", new String[]{"home", "away"});

    private final String label;
    private final String[] selections;

    SportsMarketType(String label, String[] selections) {
        this.label = label;
        this.selections = selections;
    }

    public String label() { return label; }
    public String[] selections() { return selections; }
    public int outcomeCount() { return selections.length; }

    public static SportsMarketType from(String value) {
        if (value == null) throw new IllegalArgumentException("marketType required");
        return switch (value.toUpperCase().replace('-', '_')) {
            case "1X2", "ONE_X_TWO" -> ONE_X_TWO;
            case "OU", "OVER_UNDER" -> OVER_UNDER;
            case "HC", "HANDICAP" -> HANDICAP;
            default -> throw new IllegalArgumentException("Unknown marketType: " + value);
        };
    }
}
