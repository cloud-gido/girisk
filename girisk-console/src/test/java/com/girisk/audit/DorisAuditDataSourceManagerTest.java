package com.girisk.audit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DorisAuditDataSourceManagerTest {

    @Test
    void normalizeJdbcUrl_addsJdbcPrefix() {
        assertEquals(
                "jdbc:mysql://host:9030/girisk",
                DorisAuditDataSourceManager.normalizeJdbcUrl("mysql://host:9030/girisk"));
        assertEquals(
                "jdbc:mysql://host:9030/girisk",
                DorisAuditDataSourceManager.normalizeJdbcUrl("jdbc:mysql://host:9030/girisk"));
    }

    @Test
    void settings_buildAndParseJdbcUrl() {
        DorisAuditRuntimeSettings s = new DorisAuditRuntimeSettings();
        s.setHost("10.0.0.1");
        s.setPort(9030);
        s.setDatabase("girisk");
        assertTrue(s.jdbcUrl().startsWith("jdbc:mysql://10.0.0.1:9030/girisk"));

        DorisAuditRuntimeSettings parsed = new DorisAuditRuntimeSettings();
        parsed.parseJdbcUrlIntoFields(
                "jdbc:mysql://doris-fe:9030/girisk?useSSL=false&allowPublicKeyRetrieval=true");
        assertEquals("doris-fe", parsed.getHost());
        assertEquals(9030, parsed.getPort());
        assertEquals("girisk", parsed.getDatabase());
    }

    @Test
    void settings_normalizeTableNames() {
        DorisAuditRuntimeSettings s = new DorisAuditRuntimeSettings();
        s.setDecisionTable("ods_gameline_risk_decision_log");
        assertEquals("ods_gameline_risk_decision_log", s.getDecisionTable());
        s.setConfigTable("bigdata_ods.risk_config_log");
        assertEquals("bigdata_ods.risk_config_log", s.getConfigTable());
    }
}
