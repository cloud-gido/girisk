package com.girisk.flink.risk.grid;

import com.girisk.flink.risk.model.LiveMatchScore;

/** 滚球当前比分 + 网格边长 → 动态 {@link ScoreGridParams}。 */
public final class LiveScoreGrid {

    private LiveScoreGrid() {}

    /**
     * 无滚球状态、未开赛或无比分时基准为 <b>0:0</b>；已开赛且有效比分时用实时赛果。
     *
     * <p>{@code gridSize} 始终取自 {@code template}（通常来自 {@code --grid}）。
     */
    public static ScoreGridParams resolve(ScoreGridParams template, LiveMatchScore live) {
        int gridSize = template.grid.homeSpan();
        if (live == null) {
            return fromLive(0, 0, gridSize);
        }
        return fromLive(live.effectiveHomeGoals(), live.effectiveAwayGoals(), gridSize);
    }

    public static ScoreGridParams fromLive(int homeGoals, int awayGoals, int gridSize) {
        return ScoreGridParams.fromLiveBase(homeGoals, awayGoals, gridSize);
    }
}
