package com.girisk.audit;

/** PUT / POST test：用户填 host / port / database / username / password / 表名。 */
public class DorisAuditConfigRequest {

    private Boolean enabled;
    private String host;
    private Integer port;
    private String database;
    private String username;
    /** 空字符串 = 无密码；null = 不改（仅 PUT 合并时）；前端简化场景会直接传 ""。 */
    private String password;
    /** 决策审计表，默认 risk_decision_log。 */
    private String decisionTable;
    /** 配置审计表，默认 risk_config_log。 */
    private String configTable;
    /** 兼容旧客户端：若传了 jdbcUrl 则解析为 host/port/db。 */
    private String jdbcUrl;

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public String getDatabase() {
        return database;
    }

    public void setDatabase(String database) {
        this.database = database;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getDecisionTable() {
        return decisionTable;
    }

    public void setDecisionTable(String decisionTable) {
        this.decisionTable = decisionTable;
    }

    public String getConfigTable() {
        return configTable;
    }

    public void setConfigTable(String configTable) {
        this.configTable = configTable;
    }

    public String getJdbcUrl() {
        return jdbcUrl;
    }

    public void setJdbcUrl(String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
    }

    public DorisAuditRuntimeSettings toSettings(DorisAuditRuntimeSettings current) {
        DorisAuditRuntimeSettings next = current.copy();
        if (enabled != null) {
            next.setEnabled(enabled);
        }
        if (jdbcUrl != null && !jdbcUrl.isBlank()) {
            next.setJdbcUrl(jdbcUrl);
        }
        if (host != null) {
            next.setHost(host);
        }
        if (port != null) {
            next.setPort(port);
        }
        if (database != null) {
            next.setDatabase(database);
        }
        if (username != null) {
            next.setUsername(username);
        }
        // 简化：显式传 password（含 ""）则覆盖；不传则保留
        if (password != null) {
            next.setPassword(password);
        }
        if (decisionTable != null) {
            next.setDecisionTable(decisionTable);
        }
        if (configTable != null) {
            next.setConfigTable(configTable);
        }
        return next;
    }
}
