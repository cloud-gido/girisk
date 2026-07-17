package com.girisk.flink.risk.grid;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 假设比分网格：主队进球 {@code [homeMin, homeMax]}、客队 {@code [awayMin, awayMax]}（均含端点）。
 *
 * <p>默认与 xlsx 一致：0～5 → 6×6。也可由当前赛果 + 网格边长推导，见 {@link ScoreGridParams}。
 */
public final class ScoreGridSpec implements Serializable {
    private static final long serialVersionUID = 1L;

    public final int homeMin;
    public final int homeMax;
    public final int awayMin;
    public final int awayMax;

    public ScoreGridSpec(int homeMin, int homeMax, int awayMin, int awayMax) {
        if (homeMin < 0 || awayMin < 0 || homeMax < homeMin || awayMax < awayMin) {
            throw new IllegalArgumentException(
                    String.format(
                            Locale.ROOT,
                            "无效比分范围: 主[%d,%d] 客[%d,%d]",
                            homeMin,
                            homeMax,
                            awayMin,
                            awayMax));
        }
        this.homeMin = homeMin;
        this.homeMax = homeMax;
        this.awayMin = awayMin;
        this.awayMax = awayMax;
    }

    public static ScoreGridSpec xlsxDefault6x6() {
        return fromBaseAndGridSize(0, 0, 6);
    }

    /** 从当前赛果起各延伸 {@code gridSize} 档（含当前），如 base=2:1、size=6 → 主2～7、客1～6。 */
    public static ScoreGridSpec fromBaseAndGridSize(int baseHome, int baseAway, int gridSize) {
        if (gridSize < 1) {
            throw new IllegalArgumentException("gridSize must be >= 1");
        }
        int last = gridSize - 1;
        return new ScoreGridSpec(baseHome, baseHome + last, baseAway, baseAway + last);
    }

    public int homeSpan() {
        return homeMax - homeMin + 1;
    }

    public int awaySpan() {
        return awayMax - awayMin + 1;
    }

    public int scenarioCount() {
        return homeSpan() * awaySpan();
    }

    public boolean isXlsxDefault6x6() {
        return homeMin == 0 && homeMax == 5 && awayMin == 0 && awayMax == 5;
    }

    public String rangeLabel() {
        return String.format(Locale.ROOT, "%d:%d～%d:%d", homeMin, awayMin, homeMax, awayMax);
    }

    public List<ScoreScenario> scenarios() {
        List<ScoreScenario> list = new ArrayList<>(scenarioCount());
        for (int home = homeMin; home <= homeMax; home++) {
            for (int away = awayMin; away <= awayMax; away++) {
                list.add(new ScoreScenario(home, away));
            }
        }
        return list;
    }

    public static final class ScoreScenario implements Serializable {
        private static final long serialVersionUID = 1L;

        public final int homeGoals;
        public final int awayGoals;

        public ScoreScenario(int homeGoals, int awayGoals) {
            this.homeGoals = homeGoals;
            this.awayGoals = awayGoals;
        }

        public String scoreLabel() {
            return homeGoals + ":" + awayGoals;
        }
    }
}
