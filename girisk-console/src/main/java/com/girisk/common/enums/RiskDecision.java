package com.girisk.common.enums;

public enum RiskDecision {
    PASS, REJECT, REVIEW, LIMIT, CHALLENGE;

    /** 取更严格的决策：REJECT > REVIEW > LIMIT > CHALLENGE > PASS */
    public static RiskDecision stricter(RiskDecision a, RiskDecision b) {
        return rank(a) <= rank(b) ? a : b;
    }

    private static int rank(RiskDecision d) {
        return switch (d) {
            case REJECT -> 0;
            case REVIEW -> 1;
            case LIMIT -> 2;
            case CHALLENGE -> 3;
            case PASS -> 4;
        };
    }
}
