package com.girisk.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "girisk.audit.doris")
public class DorisAuditProperties {

    /** When true, replay prefers Doris over MySQL. */
    private boolean enabled = false;
    private String jdbcUrl = "jdbc:mysql://localhost:9030/girisk?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    private String username = "root";
    private String password = "";
    private String driverClassName = "com.mysql.cj.jdbc.Driver";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getJdbcUrl() {
        return jdbcUrl;
    }

    public void setJdbcUrl(String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
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

    public String getDriverClassName() {
        return driverClassName;
    }

    public void setDriverClassName(String driverClassName) {
        this.driverClassName = driverClassName;
    }
}
