package com.girisk.sports.service;

import com.girisk.config.SportsRiskProperties;
import com.girisk.configcenter.model.RiskFixtureView;
import com.girisk.flink.RedisFixtureViewReader;
import com.girisk.sports.model.SportsMatch;
import com.girisk.sports.repository.SportsMatchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Flink Redis {@code girisk:view:fixture:*} → sports_match 空壳 upsert。
 * 队名/联赛可留白，运营在 Console 补全。
 */
@Service
public class SportsMatchSyncService {

    private static final Logger log = LoggerFactory.getLogger(SportsMatchSyncService.class);

    private final RedisFixtureViewReader fixtureViewReader;
    private final SportsMatchRepository matchRepository;
    private final SportsRiskProperties props;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public SportsMatchSyncService(
            RedisFixtureViewReader fixtureViewReader,
            SportsMatchRepository matchRepository,
            SportsRiskProperties props) {
        this.fixtureViewReader = fixtureViewReader;
        this.matchRepository = matchRepository;
        this.props = props;
    }

    @Scheduled(fixedDelayString = "${girisk.sports.fixture-sync-ms:30000}", initialDelay = 5_000)
    public void scheduledSync() {
        syncFromRedis();
    }

    /** @return 新插入的空壳数量 */
    public int syncFromRedis() {
        if (!fixtureViewReader.available()) {
            return 0;
        }
        if (!running.compareAndSet(false, true)) {
            return 0;
        }
        try {
            List<RiskFixtureView> views = fixtureViewReader.listAll(500);
            int inserted = 0;
            BigDecimal threshold = BigDecimal.valueOf(props.getMaxWorstLossYuan());
            BigDecimal delta = BigDecimal.valueOf(props.getDefaultDelta());
            for (RiskFixtureView v : views) {
                if (v == null || v.fixtureId() == null || v.fixtureId().isBlank()) {
                    continue;
                }
                String code = v.fixtureId().trim();
                if (matchRepository.findByCode(code).isPresent()) {
                    continue;
                }
                String home = blankTeam(v.homeTeam());
                String away = blankTeam(v.awayTeam());
                matchRepository.insert(
                        code,
                        home,
                        away,
                        "football",
                        null,
                        null,
                        threshold,
                        delta);
                inserted++;
            }
            if (inserted > 0) {
                log.info("Synced {} fixture shell(s) from Redis into sports_match", inserted);
            }
            return inserted;
        } finally {
            running.set(false);
        }
    }

    /** Redis reader 用 "-" 表示缺省；入库改为 null 以便前端留白。 */
    static String blankTeam(String t) {
        if (t == null || t.isBlank() || "-".equals(t.trim())) {
            return null;
        }
        return t.trim();
    }

    public SportsMatch requireOrSync(String matchCode) {
        return matchRepository.findByCode(matchCode).orElseGet(() -> {
            syncFromRedis();
            return matchRepository.findByCode(matchCode).orElse(null);
        });
    }
}
