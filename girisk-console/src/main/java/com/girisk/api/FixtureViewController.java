package com.girisk.api;

import com.girisk.common.dto.ApiResponse;
import com.girisk.configcenter.model.RiskFixtureView;
import com.girisk.configcenter.repository.RiskFixtureViewRepository;
import com.girisk.flink.RedisFixtureViewReader;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/fixtures")
public class FixtureViewController {

    private final RiskFixtureViewRepository repository;
    private final RedisFixtureViewReader redisViews;

    public FixtureViewController(RiskFixtureViewRepository repository, RedisFixtureViewReader redisViews) {
        this.repository = repository;
        this.redisViews = redisViews;
    }

    @GetMapping
    public ApiResponse<List<RiskFixtureView>> list() {
        List<RiskFixtureView> fromRedis = redisViews.listAll(200);
        if (!fromRedis.isEmpty()) {
            return ApiResponse.ok(fromRedis);
        }
        return ApiResponse.ok(repository.findAll());
    }

    @GetMapping("/top")
    public ApiResponse<List<RiskFixtureView>> top(@RequestParam(defaultValue = "10") int limit) {
        List<RiskFixtureView> fromRedis = redisViews.topByWorstLoss(limit);
        if (!fromRedis.isEmpty()) {
            return ApiResponse.ok(fromRedis);
        }
        return ApiResponse.ok(repository.findTopByWorstLoss(limit));
    }

    @GetMapping("/{fixtureId}")
    public ApiResponse<RiskFixtureView> one(@PathVariable String fixtureId) {
        RiskFixtureView fromRedis = redisViews.findByFixtureId(fixtureId);
        if (fromRedis != null) {
            return ApiResponse.ok(fromRedis);
        }
        return ApiResponse.fail("场次视图不存在: " + fixtureId);
    }
}
