package com.girisk.api;

import com.girisk.common.dto.ApiResponse;
import com.girisk.common.exception.BusinessException;
import com.girisk.rule.model.RiskRule;
import com.girisk.rule.repository.RiskRuleRepository;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/rules")
public class RuleController {

    private final RiskRuleRepository repository;

    public RuleController(RiskRuleRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public ApiResponse<List<RiskRule>> list(@RequestParam(required = false) Long strategyId) {
        if (strategyId != null) {
            return ApiResponse.ok(repository.findByStrategyId(strategyId));
        }
        return ApiResponse.ok(repository.findAll());
    }

    @PostMapping
    public ApiResponse<Map<String, Long>> create(@RequestBody RiskRule rule) {
        long id = repository.insert(rule);
        return ApiResponse.ok(Map.of("id", id));
    }

    @PutMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable long id, @RequestBody RiskRule rule) {
        repository.findById(id).orElseThrow(() -> new BusinessException("规则不存在"));
        repository.update(id, rule);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable long id) {
        repository.delete(id);
        return ApiResponse.ok(null);
    }

    @PatchMapping("/{id}/toggle")
    public ApiResponse<Void> toggle(@PathVariable long id, @RequestBody Map<String, Boolean> body) {
        RiskRule existing = repository.findById(id).orElseThrow(() -> new BusinessException("规则不存在"));
        repository.update(id, new RiskRule(
                existing.id(), existing.strategyId(), existing.code(), existing.name(),
                existing.ruleType(), existing.field(), existing.operator(), existing.threshold(),
                existing.action(), existing.scoreWeight(), existing.priority(),
                body.getOrDefault("enabled", !existing.enabled()), existing.description(),
                existing.createdAt(), LocalDateTime.now()));
        return ApiResponse.ok(null);
    }
}
