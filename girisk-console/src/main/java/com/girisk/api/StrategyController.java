package com.girisk.api;

import com.girisk.common.dto.ApiResponse;
import com.girisk.common.exception.BusinessException;
import com.girisk.strategy.model.RiskStrategy;
import com.girisk.strategy.repository.RiskStrategyRepository;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/strategies")
public class StrategyController {

    private final RiskStrategyRepository repository;

    public StrategyController(RiskStrategyRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public ApiResponse<List<RiskStrategy>> list() {
        return ApiResponse.ok(repository.findAll());
    }

    @PostMapping
    public ApiResponse<Map<String, Long>> create(@RequestBody RiskStrategy strategy) {
        long id = repository.insert(strategy);
        return ApiResponse.ok(Map.of("id", id));
    }

    @PutMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable long id, @RequestBody RiskStrategy strategy) {
        repository.findById(id).orElseThrow(() -> new BusinessException("策略不存在"));
        repository.update(id, strategy);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable long id) {
        repository.delete(id);
        return ApiResponse.ok(null);
    }
}
