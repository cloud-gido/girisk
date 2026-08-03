package com.girisk.audit;

import com.girisk.config.DorisAuditProperties;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Doris 审计连接池：env bootstrap + Redis 覆盖；用户配置 host/port/database/账密。
 */
@Component
public class DorisAuditDataSourceManager {

    private static final Logger log = LoggerFactory.getLogger(DorisAuditDataSourceManager.class);

    private final DorisAuditProperties bootstrap;
    private final DorisAuditSettingsStore store;

    private final AtomicReference<DorisAuditRuntimeSettings> effective =
            new AtomicReference<>(new DorisAuditRuntimeSettings());
    private final AtomicReference<HikariDataSource> poolRef = new AtomicReference<>();
    private final AtomicReference<String> lastError = new AtomicReference<>("");

    public DorisAuditDataSourceManager(DorisAuditProperties bootstrap, DorisAuditSettingsStore store) {
        this.bootstrap = bootstrap;
        this.store = store;
    }

    @PostConstruct
    public void init() {
        DorisAuditRuntimeSettings fromEnv = fromBootstrap();
        DorisAuditRuntimeSettings merged = store.load().map(overlay -> merge(fromEnv, overlay)).orElse(fromEnv);
        apply(merged, false);
    }

    @PreDestroy
    public void destroy() {
        closePool(poolRef.getAndSet(null));
    }

    public synchronized DorisAuditRuntimeSettings effectiveSettings() {
        return effective.get().copy();
    }

    public boolean available() {
        DorisAuditRuntimeSettings s = effective.get();
        HikariDataSource pool = poolRef.get();
        return s.isEnabled() && pool != null && !pool.isClosed();
    }

    public String lastError() {
        String e = lastError.get();
        return e == null ? "" : e;
    }

    public JdbcTemplate jdbcTemplateOrNull() {
        HikariDataSource pool = poolRef.get();
        if (pool == null || pool.isClosed()) {
            return null;
        }
        return new JdbcTemplate(pool);
    }

    /**
     * 保存并热加载。密码以 incoming 为准（含空串 = 无密码，适配内网无账密 Doris）。
     */
    public synchronized Map<String, Object> updateAndApply(DorisAuditRuntimeSettings incoming) {
        DorisAuditRuntimeSettings next = incoming.copy();
        if (next.getDriverClassName() == null || next.getDriverClassName().isBlank()) {
            next.setDriverClassName(
                    bootstrap.getDriverClassName() == null
                            ? "com.mysql.cj.jdbc.Driver"
                            : bootstrap.getDriverClassName());
        }
        store.save(next);
        apply(next, true);
        return statusView(true);
    }

    /** 试连不持久化。密码以 candidate 为准（空 = 无密码）。 */
    public synchronized Map<String, Object> testConnection(DorisAuditRuntimeSettings candidate) {
        DorisAuditRuntimeSettings probe = candidate.copy();
        if (probe.getHost() == null || probe.getHost().isBlank()) {
            return Map.of("ok", false, "message", "请填写主机地址");
        }
        HikariDataSource ds = null;
        try {
            ds = openPool(probe);
            try (Connection c = ds.getConnection()) {
                c.createStatement().execute("SELECT 1");
                c.createStatement()
                        .execute("SELECT 1 FROM " + probe.getDecisionTable() + " LIMIT 1");
            }
            return Map.of(
                    "ok",
                    true,
                    "message",
                    "连接成功，决策表 " + probe.getDecisionTable() + " 可读");
        } catch (Exception e) {
            return Map.of(
                    "ok",
                    false,
                    "message",
                    e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        } finally {
            closePool(ds);
        }
    }

    public Map<String, Object> statusView(boolean includeSensitiveForAdmin) {
        DorisAuditRuntimeSettings s = effective.get();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("enabled", s.isEnabled());
        m.put("host", s.getHost());
        m.put("port", s.getPort());
        m.put("database", s.getDatabase());
        m.put("username", s.getUsername());
        m.put("passwordSet", s.passwordSet());
        m.put("decisionTable", s.getDecisionTable());
        m.put("configTable", s.getConfigTable());
        if (includeSensitiveForAdmin) {
            m.put("jdbcUrl", s.jdbcUrl());
        }
        m.put("available", available());
        m.put("activeSource", available() ? "doris" : "postgres");
        m.put("lastError", lastError());
        return m;
    }

    private void apply(DorisAuditRuntimeSettings settings, boolean logChange) {
        DorisAuditRuntimeSettings next = settings.copy();
        HikariDataSource previous = poolRef.get();
        if (!next.isEnabled()) {
            poolRef.set(null);
            closePool(previous);
            effective.set(next);
            lastError.set("");
            if (logChange) {
                log.info("Doris audit datasource disabled");
            }
            return;
        }
        if (next.getHost() == null || next.getHost().isBlank()) {
            poolRef.set(null);
            closePool(previous);
            effective.set(next);
            lastError.set("已启用但未填写主机地址");
            log.warn("Doris audit enabled but host blank");
            return;
        }
        try {
            HikariDataSource created = openPool(next);
            try (Connection c = created.getConnection()) {
                c.createStatement().execute("SELECT 1");
                // 顺带校验决策表可读（表名已做标识符白名单）
                c.createStatement()
                        .execute("SELECT 1 FROM " + next.getDecisionTable() + " LIMIT 1");
            }
            poolRef.set(created);
            closePool(previous);
            effective.set(next);
            lastError.set("");
            if (logChange) {
                log.info(
                        "Doris audit datasource applied {}:{}/{}",
                        next.getHost(),
                        next.getPort(),
                        next.getDatabase());
            }
        } catch (Exception e) {
            closePool(previous);
            poolRef.set(null);
            effective.set(next);
            lastError.set(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            log.warn("Doris audit datasource apply failed: {}", lastError.get());
        }
    }

    private DorisAuditRuntimeSettings fromBootstrap() {
        DorisAuditRuntimeSettings s = new DorisAuditRuntimeSettings();
        s.setEnabled(bootstrap.isEnabled());
        s.setUsername(bootstrap.getUsername() == null ? "root" : bootstrap.getUsername());
        s.setPassword(bootstrap.getPassword() == null ? "" : bootstrap.getPassword());
        s.setDriverClassName(
                bootstrap.getDriverClassName() == null
                        ? "com.mysql.cj.jdbc.Driver"
                        : bootstrap.getDriverClassName());
        s.parseJdbcUrlIntoFields(normalizeJdbcUrl(bootstrap.getJdbcUrl()));
        return s;
    }

    private static DorisAuditRuntimeSettings merge(
            DorisAuditRuntimeSettings base, DorisAuditRuntimeSettings overlay) {
        DorisAuditRuntimeSettings out = base.copy();
        out.setEnabled(overlay.isEnabled());
        if (overlay.getHost() != null && !overlay.getHost().isBlank()) {
            out.setHost(overlay.getHost());
        }
        if (overlay.getPort() > 0) {
            out.setPort(overlay.getPort());
        }
        if (overlay.getDatabase() != null && !overlay.getDatabase().isBlank()) {
            out.setDatabase(overlay.getDatabase());
        }
        if (overlay.getUsername() != null) {
            out.setUsername(overlay.getUsername());
        }
        // Redis 覆盖层：始终采用 overlay 密码（可为空 = 无密码）
        out.setPassword(overlay.getPassword() == null ? "" : overlay.getPassword());
        if (overlay.getDriverClassName() != null && !overlay.getDriverClassName().isBlank()) {
            out.setDriverClassName(overlay.getDriverClassName());
        }
        if (overlay.getDecisionTable() != null && !overlay.getDecisionTable().isBlank()) {
            out.setDecisionTable(overlay.getDecisionTable());
        }
        if (overlay.getConfigTable() != null && !overlay.getConfigTable().isBlank()) {
            out.setConfigTable(overlay.getConfigTable());
        }
        return out;
    }

    private HikariDataSource openPool(DorisAuditRuntimeSettings s) {
        HikariDataSource ds = new HikariDataSource();
        ds.setPoolName("doris-audit");
        ds.setJdbcUrl(s.jdbcUrl());
        ds.setUsername(s.getUsername() == null ? "" : s.getUsername());
        ds.setPassword(s.getPassword() == null ? "" : s.getPassword());
        ds.setDriverClassName(
                s.getDriverClassName() == null || s.getDriverClassName().isBlank()
                        ? "com.mysql.cj.jdbc.Driver"
                        : s.getDriverClassName());
        ds.setMaximumPoolSize(4);
        ds.setMinimumIdle(0);
        ds.setConnectionTimeout(5_000);
        ds.setInitializationFailTimeout(-1);
        return ds;
    }

    private static void closePool(HikariDataSource ds) {
        if (ds != null && !ds.isClosed()) {
            try {
                ds.close();
            } catch (Exception ignored) {
                // ignore
            }
        }
    }

    static String normalizeJdbcUrl(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        String trimmed = url.trim();
        if (trimmed.startsWith("jdbc:")) {
            return trimmed;
        }
        if (trimmed.startsWith("mysql://")) {
            return "jdbc:" + trimmed;
        }
        return trimmed;
    }
}
