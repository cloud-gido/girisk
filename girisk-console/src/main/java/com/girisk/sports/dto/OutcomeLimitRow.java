package com.girisk.sports.dto;

import java.math.BigDecimal;

/** 单个盘口的限额明细，与产品文档字段对齐 */
public record OutcomeLimitRow(
        String selection,
        BigDecimal stake,
        BigDecimal targetAmount,
        BigDecimal maxAllowedAmount,
        BigDecimal acceptMax
) {}
