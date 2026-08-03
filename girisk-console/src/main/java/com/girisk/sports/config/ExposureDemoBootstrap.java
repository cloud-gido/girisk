package com.girisk.sports.config;

import com.girisk.sports.service.ExposureDemoBootstrapService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/** On exposure-demo startup, seed Redis + sports store when the high-risk board is empty. */
@Component
@ConditionalOnProperty(name = "girisk.demo.auto-seed", havingValue = "true")
public class ExposureDemoBootstrap implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(ExposureDemoBootstrap.class);

    private final ExposureDemoBootstrapService service;

    public ExposureDemoBootstrap(ExposureDemoBootstrapService service) {
        this.service = service;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (!service.isRedisAvailable()) {
            log.warn("girisk.demo.auto-seed=true but Redis unavailable; skip demo bootstrap");
            return;
        }
        try {
            Map<String, Object> result = service.ensureDemoLoaded();
            log.info("Exposure demo auto-seed: {}", result);
        } catch (Exception e) {
            log.warn("Exposure demo auto-seed failed (board may be empty): {}", e.getMessage());
        }
    }
}
