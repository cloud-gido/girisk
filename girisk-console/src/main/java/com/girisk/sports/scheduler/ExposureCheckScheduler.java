package com.girisk.sports.scheduler;

import com.girisk.sports.service.SportsExposureService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ExposureCheckScheduler {

    private static final Logger log = LoggerFactory.getLogger(ExposureCheckScheduler.class);

    private final SportsExposureService exposureService;

    public ExposureCheckScheduler(SportsExposureService exposureService) {
        this.exposureService = exposureService;
    }

    @Scheduled(cron = "${girisk.sports.exposure-check-cron:0 */5 * * * *}")
    public void checkAllMatches() {
        log.debug("Running scheduled sports exposure check");
        try {
            exposureService.runExposureCheckForAll();
        } catch (Exception e) {
            log.warn("Scheduled exposure check skipped: {}", e.getMessage());
        }
    }
}
