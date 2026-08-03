package com.girisk.audit;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Doris 审计库运行时配置（用户侧：host / port / database / username / password / 表名）。
 * 内部拼成 MySQL 协议 JDBC：{@code jdbc:mysql://host:port/db?...}。
 */
public final class DorisAuditRuntimeSettings {

    private static final Pattern JDBC_MYSQL =
            Pattern.compile("(?i)^jdbc:mysql://([^:/?]+)(?::(\\d+))?/([^?;]+)?");
    /** Doris/MySQL 表名：字母数字下划线，可选 db.table。 */
    private static final Pattern SQL_IDENT =
            Pattern.compile("^[A-Za-z_][A-Za-z0-9_]{0,63}(?:\\.[A-Za-z_][A-Za-z0-9_]{0,63})?$");

    public static final String DEFAULT_DECISION_TABLE = "risk_decision_log";
    public static final String DEFAULT_CONFIG_TABLE = "risk_config_log";

    private boolean enabled;
    private String host = "";
    private int port = 9030;
    private String database = "girisk";
    private String username = "root";
    private String password = "";
    private String driverClassName = "com.mysql.cj.jdbc.Driver";
    private String decisionTable = DEFAULT_DECISION_TABLE;
    private String configTable = DEFAULT_CONFIG_TABLE;

    public DorisAuditRuntimeSettings() {}

    public DorisAuditRuntimeSettings(
            boolean enabled,
            String host,
            int port,
            String database,
            String username,
            String password,
            String driverClassName,
            String decisionTable,
            String configTable) {
        this.enabled = enabled;
        this.host = host == null ? "" : host.trim();
        this.port = port > 0 ? port : 9030;
        this.database = database == null || database.isBlank() ? "girisk" : database.trim();
        this.username = username == null ? "" : username;
        this.password = password == null ? "" : password;
        this.driverClassName =
                driverClassName == null || driverClassName.isBlank()
                        ? "com.mysql.cj.jdbc.Driver"
                        : driverClassName;
        this.decisionTable = normalizeTable(decisionTable, DEFAULT_DECISION_TABLE);
        this.configTable = normalizeTable(configTable, DEFAULT_CONFIG_TABLE);
    }

    public DorisAuditRuntimeSettings copy() {
        return new DorisAuditRuntimeSettings(
                enabled,
                host,
                port,
                database,
                username,
                password,
                driverClassName,
                decisionTable,
                configTable);
    }

    /** 由 host/port/database 拼 JDBC URL（账密不走 URL）。 */
    public String jdbcUrl() {
        if (host == null || host.isBlank()) {
            return "";
        }
        return "jdbc:mysql://"
                + host.trim()
                + ":"
                + (port > 0 ? port : 9030)
                + "/"
                + (database == null || database.isBlank() ? "girisk" : database.trim())
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host == null ? "" : host.trim();
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port > 0 ? port : 9030;
    }

    public String getDatabase() {
        return database;
    }

    public void setDatabase(String database) {
        this.database = database == null || database.isBlank() ? "girisk" : database.trim();
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username == null ? "" : username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password == null ? "" : password;
    }

    public String getDriverClassName() {
        return driverClassName;
    }

    public void setDriverClassName(String driverClassName) {
        this.driverClassName =
                driverClassName == null || driverClassName.isBlank()
                        ? "com.mysql.cj.jdbc.Driver"
                        : driverClassName;
    }

    public String getDecisionTable() {
        return decisionTable;
    }

    public void setDecisionTable(String decisionTable) {
        this.decisionTable = normalizeTable(decisionTable, DEFAULT_DECISION_TABLE);
    }

    public String getConfigTable() {
        return configTable;
    }

    public void setConfigTable(String configTable) {
        this.configTable = normalizeTable(configTable, DEFAULT_CONFIG_TABLE);
    }

    public boolean passwordSet() {
        return password != null && !password.isBlank();
    }

    /** 序列化兼容：对外仍可带 jdbcUrl；反序列化旧 Redis 配置时解析进 host/port/db。 */
    public String getJdbcUrl() {
        return jdbcUrl();
    }

    public void setJdbcUrl(String jdbcUrl) {
        parseJdbcUrlIntoFields(jdbcUrl);
    }

    void parseJdbcUrlIntoFields(String jdbcUrl) {
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            return;
        }
        String normalized = DorisAuditDataSourceManager.normalizeJdbcUrl(jdbcUrl);
        Matcher m = JDBC_MYSQL.matcher(normalized);
        if (!m.find()) {
            return;
        }
        this.host = m.group(1) == null ? "" : m.group(1);
        if (m.group(2) != null && !m.group(2).isBlank()) {
            try {
                this.port = Integer.parseInt(m.group(2));
            } catch (NumberFormatException ignored) {
                this.port = 9030;
            }
        } else {
            this.port = 9030;
        }
        if (m.group(3) != null && !m.group(3).isBlank()) {
            this.database = m.group(3);
        }
    }

    static String normalizeTable(String name, String defaultName) {
        if (name == null || name.isBlank()) {
            return defaultName;
        }
        String trimmed = name.trim();
        if (!SQL_IDENT.matcher(trimmed).matches()) {
            throw new IllegalArgumentException(
                    "非法表名（仅允许字母数字下划线，可选 db.table）：" + trimmed);
        }
        return trimmed;
    }
}
