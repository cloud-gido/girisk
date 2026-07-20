package com.girisk.sports.dto;

/**
 * 某层门控视图：生效值 + 本层覆盖 + 来源层。
 * 继承：MATCH > LEAGUE > SPORT > OVERALL(默认) > 系统全开。
 */
public record ScopeGateParamsView(
        String scopeType,
        String scopeKey,
        boolean tradingEnabled,
        boolean limitGateEnabled,
        boolean exposureGateEnabled,
        String tradingSource,
        String limitGateSource,
        String exposureGateSource,
        boolean overrideActive,
        Boolean overrideTradingEnabled,
        Boolean overrideLimitGateEnabled,
        Boolean overrideExposureGateEnabled,
        boolean inheritedTradingEnabled,
        boolean inheritedLimitGateEnabled,
        boolean inheritedExposureGateEnabled,
        boolean canWrite,
        String updatedBy,
        String updatedAt
) {
}
