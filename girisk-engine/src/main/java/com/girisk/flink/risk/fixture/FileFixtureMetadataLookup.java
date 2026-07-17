package com.girisk.flink.risk.fixture;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 从 JSON Lines 维表文件加载 fixture 元数据。
 *
 * <p>每行一个对象：{@code {"fixtureId":"999886001","league":"...","homeTeam":"...","awayTeam":"...","kickoffTime":"..."}}
 */
public final class FileFixtureMetadataLookup implements FixtureMetadataLookup {
    private static final long serialVersionUID = 1L;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private volatile Map<String, FixtureMetadata> byId = Map.of();
    private final Path file;

    public FileFixtureMetadataLookup(Path file) {
        this.file = file;
        reload();
    }

    public void reload() {
        if (file == null || !Files.isRegularFile(file)) {
            byId = Map.of();
            return;
        }
        try {
            byId = loadFile(file);
            System.err.printf(
                    Locale.ROOT, "[fixture-dim] 已加载 %d 条 ← %s%n", byId.size(), file.toAbsolutePath());
        } catch (IOException e) {
            System.err.printf(
                    Locale.ROOT, "[fixture-dim] 加载失败 %s: %s%n", file.toAbsolutePath(), e.getMessage());
            byId = Map.of();
        }
    }

    @Override
    public void refresh() {
        reload();
    }

    @Override
    public Optional<FixtureMetadata> find(String fixtureId) {
        if (fixtureId == null || fixtureId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(byId.get(fixtureId.trim()));
    }

    static Map<String, FixtureMetadata> loadFile(Path path) throws IOException {
        Map<String, FixtureMetadata> map = new HashMap<>();
        for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            JsonNode n = MAPPER.readTree(trimmed);
            FixtureMetadata m = new FixtureMetadata();
            m.fixtureId = text(n, "fixtureId");
            m.league = text(n, "league");
            m.homeTeam = text(n, "homeTeam");
            m.awayTeam = text(n, "awayTeam");
            m.kickoffTime = text(n, "kickoffTime");
            if (m.fixtureId.isEmpty()) {
                continue;
            }
            map.put(m.fixtureId, m);
        }
        return map;
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || v.isNull() ? "" : v.asText("").trim();
    }
}
