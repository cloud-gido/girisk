package com.girisk.flink.risk;

import com.girisk.flink.risk.excel.FootballSportsOrder;
import com.girisk.flink.risk.model.OrderPostStatus;
import com.girisk.flink.risk.model.OrderPostStatusUpdate;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfirmedOrderWindowStateTest {

    @Test
    void confirmedAddsAndRejectedRemoves() {
        List<MatchExposureKafkaProcessFunction.StoredOrder> stored = new ArrayList<>();
        FootballSportsOrder o = new FootballSportsOrder();
        o.orderId = "O1";
        o.fixtureId = "F1";
        o.stakeYuan = 100;

        ConfirmedOrderWindowState.applyPostUpdate(
                stored,
                new OrderPostStatusUpdate(
                        OrderPostStatus.CONFIRMED, "O1", "F1", 1000L, o));
        assertEquals(1, stored.size());

        ConfirmedOrderWindowState.applyPostUpdate(
                stored,
                new OrderPostStatusUpdate(
                        OrderPostStatus.REJECTED, "O1", "F1", 2000L, null));
        assertTrue(stored.isEmpty());
    }
}
