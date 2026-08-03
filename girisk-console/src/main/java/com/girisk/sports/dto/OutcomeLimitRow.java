package com.girisk.sports.dto;

import java.math.BigDecimal;

/** 单个盘口的限额明细，与产品文档字段对齐 */
public record OutcomeLimitRow(
        String selection,
        /** 含种子账面（限额公式 / 超额判断）；兼容旧字段名 stake */
        BigDecimal stake,
        BigDecimal targetAmount,
        BigDecimal maxAllowedAmount,
        BigDecimal acceptMax,
        /** 真实已投注（不含冷启动）；缺省时前端可用 stake - seedYuan */
        BigDecimal actualStake,
        /** 每盘口冷启动种子 */
        BigDecimal seedYuan
) {
    /** 旧调用兼容：无 actual/seed */
    public OutcomeLimitRow(
            String selection,
            BigDecimal stake,
            BigDecimal targetAmount,
            BigDecimal maxAllowedAmount,
            BigDecimal acceptMax) {
        this(selection, stake, targetAmount, maxAllowedAmount, acceptMax, null, null);
    }
}
