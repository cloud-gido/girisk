package com.girisk.audit;

import com.girisk.event.repository.RiskEventRepository;
import com.girisk.sports.service.ScopeDutyAuth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * 运营操作审计：复用 {@code risk_event}，统一事件类型前缀。
 * <ul>
 *   <li>{@code IAM_*} — 账号/角色变更</li>
 *   <li>{@code DUTY_*} — 限额/闸门/停盘</li>
 * </ul>
 */
@Service
public class OpsAuditService {

    private static final Logger log = LoggerFactory.getLogger(OpsAuditService.class);

    public static final String IAM_USER_CREATE = "IAM_USER_CREATE";
    public static final String IAM_USER_UPDATE = "IAM_USER_UPDATE";
    public static final String IAM_USER_ENABLE = "IAM_USER_ENABLE";
    public static final String IAM_PASSWORD_RESET = "IAM_PASSWORD_RESET";
    public static final String IAM_ROLE_PERMS = "IAM_ROLE_PERMS";
    public static final String DUTY_GATE_UPSERT = "DUTY_GATE_UPSERT";
    public static final String DUTY_GATE_CLEAR = "DUTY_GATE_CLEAR";
    public static final String DUTY_LIMIT_UPSERT = "DUTY_LIMIT_UPSERT";
    public static final String DUTY_LIMIT_CLEAR = "DUTY_LIMIT_CLEAR";
    public static final String DUTY_FIXTURE_LIMIT = "DUTY_FIXTURE_LIMIT";
    public static final String DUTY_MATCH_STATUS = "DUTY_MATCH_STATUS";
    public static final String AUTH_LOGOUT = "AUTH_LOGOUT";
    public static final String AUTH_LOGIN = "AUTH_LOGIN";

    private final RiskEventRepository events;
    private final ObjectProvider<ScopeDutyAuth> dutyAuth;

    public OpsAuditService(RiskEventRepository events, ObjectProvider<ScopeDutyAuth> dutyAuth) {
        this.events = events;
        this.dutyAuth = dutyAuth;
    }

    public void record(String eventType, String title, String detail) {
        record(eventType, "INFO", null, title, detail);
    }

    public void record(String eventType, String severity, String refId, String title, String detail) {
        String actor = resolveActor();
        try {
            events.insert(eventType, severity == null ? "INFO" : severity, refId, actor, title, detail);
        } catch (Exception e) {
            log.warn("ops audit insert failed type={}: {}", eventType, e.getMessage());
        }
    }

    private String resolveActor() {
        ScopeDutyAuth auth = dutyAuth.getIfAvailable();
        if (auth == null) {
            return "system";
        }
        String u = auth.currentUsername();
        return u == null || u.isBlank() || "anonymous".equals(u) ? "system" : u;
    }
}
