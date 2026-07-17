package com.girisk.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "girisk.sports")
public class SportsRiskProperties {

    /** When false, HTTP decide skips local sports Gate1/Gate2 (Flink is authority). */
    private boolean onlineDecideEnabled = false;

    private String exposureCheckCron = "0 */5 * * * *";
    private double defaultDelta = 0.2;
    private double seedPayoutYuan = 2000;
    private double maxWorstLossYuan = 1000;
    /** 单注最大返彩（元）；≤0 表示不启用全局单注上限。 */
    private double maxBetPayoutYuan = 0;
    private int reserveTtlSeconds = 30;
    private boolean limitDecisionEnabled = true;

    public boolean isOnlineDecideEnabled() { return onlineDecideEnabled; }
    public void setOnlineDecideEnabled(boolean onlineDecideEnabled) { this.onlineDecideEnabled = onlineDecideEnabled; }
    public String getExposureCheckCron() { return exposureCheckCron; }
    public void setExposureCheckCron(String exposureCheckCron) { this.exposureCheckCron = exposureCheckCron; }
    public double getDefaultDelta() { return defaultDelta; }
    public void setDefaultDelta(double defaultDelta) { this.defaultDelta = defaultDelta; }
    public double getSeedPayoutYuan() { return seedPayoutYuan; }
    public void setSeedPayoutYuan(double seedPayoutYuan) { this.seedPayoutYuan = seedPayoutYuan; }
    public double getMaxWorstLossYuan() { return maxWorstLossYuan; }
    public void setMaxWorstLossYuan(double maxWorstLossYuan) { this.maxWorstLossYuan = maxWorstLossYuan; }
    public double getMaxBetPayoutYuan() { return maxBetPayoutYuan; }
    public void setMaxBetPayoutYuan(double maxBetPayoutYuan) { this.maxBetPayoutYuan = maxBetPayoutYuan; }
    public int getReserveTtlSeconds() { return reserveTtlSeconds; }
    public void setReserveTtlSeconds(int reserveTtlSeconds) { this.reserveTtlSeconds = reserveTtlSeconds; }
    public boolean isLimitDecisionEnabled() { return limitDecisionEnabled; }
    public void setLimitDecisionEnabled(boolean limitDecisionEnabled) { this.limitDecisionEnabled = limitDecisionEnabled; }
}
