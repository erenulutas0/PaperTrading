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

                // Baseline 50.0
                double score = 50.0;

                if (winRate > 0) {
                    if (winRate >= 50) {
                        // 50 to 100 win rate gives +0 to +40 score
                        score += (winRate - 50) * 0.8;
                    } else {
                        // 0 to 49 win rate removes up to -25 score
                        score -= (50 - winRate) * 0.5;
                    }
                }

                // A tiny premium for just being verified/having followers can be added later
                // Cap at 0-100
                if (score > 100)
                    score = 100;
                if (score < 0)
                    score = 0;

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
}
