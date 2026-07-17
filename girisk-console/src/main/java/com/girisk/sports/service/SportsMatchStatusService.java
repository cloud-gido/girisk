package com.girisk.sports.service;

import com.girisk.common.exception.BusinessException;
import com.girisk.sports.dto.SportsMatchView;
import com.girisk.sports.repository.SportsMatchRepository;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Set;

@Service
public class SportsMatchStatusService {

    private static final Set<String> ALLOWED = Set.of("ACTIVE", "SUSPENDED");

    private final SportsMatchRepository matchRepository;
    private final SportsExposureService exposureService;

    public SportsMatchStatusService(
            SportsMatchRepository matchRepository,
            SportsExposureService exposureService) {
        this.matchRepository = matchRepository;
        this.exposureService = exposureService;
    }

    public SportsMatchView setStatus(String matchCode, String status) {
        if (matchCode == null || matchCode.isBlank()) {
            throw new BusinessException("matchCode required");
        }
        if (matchRepository.findByCode(matchCode).isEmpty()) {
            throw new BusinessException("比赛不存在: " + matchCode);
        }
        String normalized = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED.contains(normalized)) {
            throw new BusinessException("status 仅支持 ACTIVE 或 SUSPENDED");
        }
        matchRepository.updateStatus(matchCode, normalized);
        return exposureService.getMatchView(matchCode);
    }
}
