package com.girisk.stream;

import java.math.BigDecimal;

public interface VelocityCounter {
    int recordAndGetCount(String userId);
    BigDecimal recordAndGetAmountSum(String userId, BigDecimal amount);
}
