package com.girisk.common.dto;

import com.girisk.common.enums.RiskDecision;
import com.girisk.common.enums.RiskLevel;

import java.math.BigDecimal;
import java.util.List;

public record ApiResponse<T>(boolean success, T data, String message) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static <T> ApiResponse<T> fail(String message) {
        return new ApiResponse<>(false, null, message);
    }
}
