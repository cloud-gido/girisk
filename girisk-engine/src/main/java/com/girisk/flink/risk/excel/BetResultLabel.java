package com.girisk.flink.risk.excel;

/** 与 Excel「比分订单矩阵」中单元格文案一致。 */
public enum BetResultLabel {
    WIN("赢"),
    LOSE("输"),
    PUSH("走水"),
    WIN_HALF("赢半"),
    LOSE_HALF("输半");

    public final String display;

    BetResultLabel(String display) {
        this.display = display;
    }
}
