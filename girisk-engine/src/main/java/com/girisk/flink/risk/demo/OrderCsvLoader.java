package com.girisk.flink.risk.demo;

import com.girisk.flink.risk.excel.FootballSportsOrder;

import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Loads seq,orderId,selection,odds,stakeYuan CSV (Germany replay format). */
public final class OrderCsvLoader {

    private OrderCsvLoader() {}

    public static List<FootballSportsOrder> load(
            Path csvPath, String fixtureId, String homeTeam, String awayTeam) throws Exception {
        List<FootballSportsOrder> out = new ArrayList<>();
        try (BufferedReader br = Files.newBufferedReader(csvPath, StandardCharsets.UTF_8)) {
            String line = br.readLine(); // header
            while ((line = br.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String[] p = line.split(",", -1);
                if (p.length < 5) {
                    continue;
                }
                FootballSportsOrder o = new FootballSportsOrder();
                o.fixtureId = fixtureId;
                o.orderId = p[1].trim();
                o.orderTime = "2026-06-01T12:00:00Z";
                o.league = "国际友谊";
                o.homeTeam = homeTeam;
                o.awayTeam = awayTeam;
                o.kickoffTime = "2026-06-01T20:00:00Z";
                o.playType = "胜平负";
                o.parlayType = "单关";
                o.handicapText = "无";
                o.selection = p[2].trim();
                o.odds = Double.parseDouble(p[3].trim());
                double stake = Double.parseDouble(p[4].trim());
                o.stakeCentsExact = Math.round(stake * 100.0);
                o.stakeYuan = Math.max(1L, Math.round(stake));
                out.add(o);
            }
        }
        return out;
    }

    /** Map Chinese selection to OrderRiskCheckEvent legPick.side. */
    public static String selectionToSide(String selection) {
        if ("主胜".equals(selection)) {
            return "HOME";
        }
        if ("客胜".equals(selection)) {
            return "AWAY";
        }
        if ("平局".equals(selection)) {
            return "DRAW";
        }
        return selection;
    }
}
