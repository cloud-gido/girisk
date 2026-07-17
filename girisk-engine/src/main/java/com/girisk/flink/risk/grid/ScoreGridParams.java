package com.girisk.flink.risk.grid;

import com.girisk.flink.support.util.CliParameterTool;

import java.io.Serializable;
import java.util.Map;

/**
 * 从命令行解析比分网格参数（Flink / 本地 CLI 共用）。
 *
 * <ul>
 *   <li><b>方式一（xlsx 默认）</b>：不传 {@code score}，{@code --grid 6} → 主/客均为 0～5；</li>
 *   <li><b>方式二（当前赛果 + 网格）</b>：{@code --score 2:1 --grid 6} → 主 2～7、客 1～6；</li>
 *   <li><b>方式三（显式区间）</b>：{@code --homeMin 0 --homeMax 5 --awayMin 0 --awayMax 5}。</li>
 * </ul>
 */
public final class ScoreGridParams implements Serializable {
    private static final long serialVersionUID = 1L;

    public final int baseHome;
    public final int baseAway;
    public final ScoreGridSpec grid;

    public static ScoreGridParams fromLiveBase(int baseHome, int baseAway, int gridSize) {
        return new ScoreGridParams(
                baseHome, baseAway, ScoreGridSpec.fromBaseAndGridSize(baseHome, baseAway, gridSize));
    }

    private ScoreGridParams(int baseHome, int baseAway, ScoreGridSpec grid) {
        this.baseHome = baseHome;
        this.baseAway = baseAway;
        this.grid = grid;
    }

    public static ScoreGridParams fromArgs(String[] args) {
        return fromMap(parseArgMap(args));
    }

    public static ScoreGridParams from(CliParameterTool tool) {
        return fromMap(tool.toMap());
    }

    public static ScoreGridParams fromMap(Map<String, String> m) {
        return parse(m);
    }

    private static ScoreGridParams parse(Map<String, String> m) {
        if (hasRangeKeys(m)) {
            int hMin = Integer.parseInt(require(m, "homeMin"));
            int hMax = Integer.parseInt(require(m, "homeMax"));
            int aMin = Integer.parseInt(require(m, "awayMin"));
            int aMax = Integer.parseInt(require(m, "awayMax"));
            return new ScoreGridParams(hMin, aMin, new ScoreGridSpec(hMin, hMax, aMin, aMax));
        }

        int gridSize = Integer.parseInt(m.getOrDefault("grid", "6"));
        int[] score = parseScore(m.getOrDefault("score", "0:0"));
        int home = Integer.parseInt(m.getOrDefault("home", String.valueOf(score[0])));
        int away = Integer.parseInt(m.getOrDefault("away", String.valueOf(score[1])));
        ScoreGridSpec spec = ScoreGridSpec.fromBaseAndGridSize(home, away, gridSize);
        return new ScoreGridParams(home, away, spec);
    }

    private static boolean hasRangeKeys(Map<String, String> m) {
        return m.containsKey("homeMin")
                || m.containsKey("homeMax")
                || m.containsKey("awayMin")
                || m.containsKey("awayMax");
    }

    private static String require(Map<String, String> m, String key) {
        String v = m.get(key);
        if (v == null) {
            throw new IllegalArgumentException("显式区间模式需同时提供 homeMin/homeMax/awayMin/awayMax，缺少: " + key);
        }
        return v;
    }

    public static int[] parseScore(String score) {
        String[] p = score.split(":");
        if (p.length != 2) {
            throw new IllegalArgumentException("score 格式应为 H:A，如 2:1");
        }
        return new int[] {Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim())};
    }

    public static Map<String, String> parseArgMap(String[] args) {
        java.util.HashMap<String, String> m = new java.util.HashMap<>();
        for (int i = 0; i < args.length; i++) {
            if (args[i].startsWith("--")) {
                String key = args[i].substring(2);
                if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                    m.put(key, args[++i]);
                } else {
                    m.put(key, "true");
                }
            }
        }
        return m;
    }
}
