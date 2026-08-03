package com.girisk.sports.model;

import java.util.Arrays;

public record MarketGroupKey(String matchCode, SportsMarketType marketType, String line) {

    public static MarketGroupKey of(String matchCode, SportsMarketType marketType, String line) {
        String normalizedLine = marketType == SportsMarketType.ONE_X_TWO ? "" : normalizeLine(line);
        return new MarketGroupKey(matchCode, marketType, normalizedLine);
    }

    public String redisKey() {
        return matchCode + ":" + marketType.name() + ":" + line;
    }

    public static String normalizeLine(String line) {
        if (line == null || line.isBlank()) return "";
        return line.trim().replace("+", "");
    }

    public void validateSelection(String selection) {
        boolean ok = Arrays.asList(marketType.selections()).contains(selection);
        if (!ok) {
            throw new IllegalArgumentException(
                    "selection must be one of " + Arrays.toString(marketType.selections()) + " for " + marketType);
        }
    }
}
