package com.finance.core.service;

import com.finance.core.domain.AppUser;
import com.finance.core.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class TrustScoreService {

    private static final int TRUST_SCORE_BATCH_SIZE = 250;
    private static final double BASELINE_SCORE = 50.0;
    private static final double PRIOR_WIN_RATE = 0.50;
    private static final double PRIOR_WEIGHT = 8.0;
    private static final double ACCURACY_MULTIPLIER = 120.0;
    private static final double EXPERIENCE_BONUS_PER_RESOLVED_POST = 0.75;
    private static final double MAX_EXPERIENCE_BONUS = 15.0;

    private final UserRepository userRepository;
    private final PerformanceAnalyticsService analyticsService;

    /**
     * Compute trust scores for all accounts hourly.
     * Rewards consistency over luck.
     */
    @Scheduled(fixedDelay = 3600000)
    @SchedulerLock(name = "TrustScoreService.computeTrustScores", lockAtMostFor = "PT2H", lockAtLeastFor = "PT5M")
    @Transactional
    public void computeTrustScores() {
        log.info("Computing trust scores for all accounts...");
        long processed = 0;
        int page = 0;
        Page<AppUser> userPage;

        do {
            userPage = userRepository.findAll(PageRequest.of(page, TRUST_SCORE_BATCH_SIZE));
            List<AppUser> batch = new ArrayList<>(userPage.getNumberOfElements());

            for (AppUser user : userPage.getContent()) {
                double winRate = analyticsService.calculateWinRate(user.getId());
                long resolvedPredictions = analyticsService.countResolvedPredictions(user.getId());
                double score = calculateTrustScore(winRate, resolvedPredictions);

                user.setTrustScore(score);
                batch.add(user);
            }

            if (!batch.isEmpty()) {
                userRepository.saveAll(batch);
            }
            processed += userPage.getNumberOfElements();
            page++;
        } while (userPage.hasNext());

        log.info("Trust scores computed for {} users.", processed);
    }

    double calculateTrustScore(double winRate, long resolvedPredictions) {
        double normalizedWinRate = Math.max(0.0, Math.min(100.0, winRate)) / 100.0;
        double posteriorWinRate = ((normalizedWinRate * resolvedPredictions) + (PRIOR_WIN_RATE * PRIOR_WEIGHT))
                / (resolvedPredictions + PRIOR_WEIGHT);
        double accuracyComponent = (posteriorWinRate - PRIOR_WIN_RATE) * ACCURACY_MULTIPLIER;
        double experienceBonus = Math.min(MAX_EXPERIENCE_BONUS, resolvedPredictions * EXPERIENCE_BONUS_PER_RESOLVED_POST);
        double score = BASELINE_SCORE + accuracyComponent + experienceBonus;

        if (score > 100.0) {
            return 100.0;
        }
        if (score < 0.0) {
            return 0.0;
        }
        return Math.round(score * 100.0) / 100.0;
    }
}
