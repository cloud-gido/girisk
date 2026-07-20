package com.girisk.common.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serializable;

/**
 * Compact message on {@code girisk.config.v1}.
 *
 * <p>Kafka key = {@code scopeType:scopeKey} (e.g. {@code LEAGUE:football:EPL}, {@code OVERALL:_}).
 * Tombstone: {@code deleted=true} clears that scope override.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ScopeRiskConfigMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    public int schemaVersion = 1;
    /** {@link ScopeRiskConfigKinds#SCOPE_OVERRIDE} or {@link ScopeRiskConfigKinds#GLOBAL_RELEASE} */
    public String kind = ScopeRiskConfigKinds.SCOPE_OVERRIDE;
    public long configEpoch;
    /** OVERALL | SPORT | LEAGUE | MATCH */
    public String scopeType;
    /** {@code _} | sportCode | sport:league | matchCode */
    public String scopeKey;
    public String publishedBy;
    public String publishedAt;
    public boolean deleted;
    public Gates gates;
    public Limits limits;

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Gates implements Serializable {
        private static final long serialVersionUID = 1L;
        public Boolean tradingEnabled;
        public Boolean limitGateEnabled;
        public Boolean exposureGateEnabled;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Limits implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 等比例 δ（0～1） */
        public Double delta;
        /** 冷启动种子（返彩元） */
        public Double seedPayoutYuan;
        /** 最差亏损阈值（元） */
        public Double maxWorstLossYuan;
        /** 单注返彩上限（元）；null/0 = 不启用 */
        public Double maxBetPayoutYuan;
    }

    public static String kafkaKey(String scopeType, String scopeKey) {
        String t = scopeType == null ? "OVERALL" : scopeType.trim().toUpperCase();
        String k = scopeKey == null || scopeKey.isBlank() ? "_" : scopeKey.trim();
        return t + ":" + k;
    }
}
