package com.girisk.api;

import com.girisk.common.dto.ApiResponse;
import com.girisk.list.model.RiskListEntry;
import com.girisk.list.repository.RiskListRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/lists")
public class ListController {

    private final RiskListRepository repository;

    public ListController(RiskListRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public ApiResponse<List<RiskListEntry>> list(@RequestParam(required = false) String listType) {
        return ApiResponse.ok(repository.findAll(listType));
    }

    @PostMapping
    public ApiResponse<Map<String, Long>> create(@RequestBody RiskListEntry entry) {
        long id = repository.insert(entry);
        return ApiResponse.ok(Map.of("id", id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable long id) {
        repository.delete(id);
        return ApiResponse.ok(null);
    }

    @PatchMapping("/{id}/toggle")
    public ApiResponse<Void> toggle(@PathVariable long id, @RequestBody Map<String, Boolean> body) {
        repository.toggle(id, body.getOrDefault("enabled", true));
        return ApiResponse.ok(null);
    }
}
