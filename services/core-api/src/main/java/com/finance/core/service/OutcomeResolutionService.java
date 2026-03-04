package com.finance.core.service;

import com.finance.core.domain.AnalysisPost;
import com.finance.core.repository.AnalysisPostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Automatic outcome resolution engine.
 * Runs periodically to check if analysis posts have hit their targets,
 * hit their stop-losses, or expired past their target date.
 *
 * This is a core anti-manipulation feature: outcomes are system-determined,
 * not user-reported. Users cannot claim "I was right" — the system proves it.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OutcomeResolutionService {

    private final AnalysisPostRepository postRepository;
    private final BinanceService binanceService;
    private final org.springframework.cache.CacheManager cacheManager;

    /**
     * Check and resolve outcomes every 30 seconds.
     */
    @Scheduled(fixedRate = 30000)
    @SchedulerLock(name = "OutcomeResolutionService.resolveOutcomes", lockAtMostFor = "PT2M", lockAtLeastFor = "PT5S")
    @Transactional
    public void resolveOutcomes() {
        Map<String, Double> prices = binanceService.getPrices();
        if (prices.isEmpty()) {
            return;
        }

        // 1. Check for target hits / stop hits
        for (Map.Entry<String, Double> entry : prices.entrySet()) {
            String symbol = entry.getKey();
            double currentPrice = entry.getValue();

            List<AnalysisPost> pendingPosts = postRepository
                    .findByOutcomeAndInstrumentSymbolAndDeletedFalse(AnalysisPost.Outcome.PENDING, symbol);

            for (AnalysisPost post : pendingPosts) {
                resolvePost(post, currentPrice);
            }
        }

        // 2. Check for expired posts (target date has passed)
        List<AnalysisPost> expiredCandidates = postRepository
                .findByOutcomeAndTargetDateBeforeAndDeletedFalse(
                        AnalysisPost.Outcome.PENDING, LocalDateTime.now());

        for (AnalysisPost post : expiredCandidates) {
            Double currentPrice = prices.get(post.getInstrumentSymbol());
            if (currentPrice != null) {
                post.setOutcome(AnalysisPost.Outcome.EXPIRED);
                post.setOutcomeResolvedAt(LocalDateTime.now());
                post.setPriceAtResolution(BigDecimal.valueOf(currentPrice));
                postRepository.save(post);
                evictAuthorStats(post.getAuthorId().toString());
                log.info("Post {} EXPIRED: {} target {} not reached by deadline. Price at expiry: {}",
                        post.getId(), post.getInstrumentSymbol(), post.getTargetPrice(), currentPrice);
            }
        }
    }

    /**
     * Core resolution logic for a single post.
     */
    void resolvePost(AnalysisPost post, double currentPrice) {
        BigDecimal current = BigDecimal.valueOf(currentPrice);
        LocalDateTime now = LocalDateTime.now();

        // Check target hit
        if (post.getTargetPrice() != null) {
            boolean targetHit = false;

            if (post.getDirection() == AnalysisPost.Direction.BULLISH) {
                // BULLISH: target hit when price >= target
                targetHit = current.compareTo(post.getTargetPrice()) >= 0;
            } else if (post.getDirection() == AnalysisPost.Direction.BEARISH) {
                // BEARISH: target hit when price <= target
                targetHit = current.compareTo(post.getTargetPrice()) <= 0;
            }

            if (targetHit) {
                post.setOutcome(AnalysisPost.Outcome.HIT);
                post.setOutcomeResolvedAt(now);
                post.setPriceAtResolution(current);
                postRepository.save(post);
                evictAuthorStats(post.getAuthorId().toString());
                log.info("🎯 Post {} HIT: {} {} target {} reached! Current price: {}",
                        post.getId(), post.getDirection(), post.getInstrumentSymbol(),
                        post.getTargetPrice(), current);
                return;
            }
        }

        // Check stop hit
        if (post.getStopPrice() != null) {
            boolean stopHit = false;

            if (post.getDirection() == AnalysisPost.Direction.BULLISH) {
                // BULLISH stop hit: price fell to or below stop
                stopHit = current.compareTo(post.getStopPrice()) <= 0;
            } else if (post.getDirection() == AnalysisPost.Direction.BEARISH) {
                // BEARISH stop hit: price rose to or above stop
                stopHit = current.compareTo(post.getStopPrice()) >= 0;
            }

            if (stopHit) {
                post.setOutcome(AnalysisPost.Outcome.MISSED);
                post.setOutcomeResolvedAt(now);
                post.setPriceAtResolution(current);
                postRepository.save(post);
                evictAuthorStats(post.getAuthorId().toString());
                log.info("❌ Post {} MISSED (stop hit): {} {} stop {} triggered. Current price: {}",
                        post.getId(), post.getDirection(), post.getInstrumentSymbol(),
                        post.getStopPrice(), current);
            }
        }
    }

    private void evictAuthorStats(String authorId) {
        org.springframework.cache.Cache cache = cacheManager.getCache("authorStats");
        if (cache != null) {
            cache.evictIfPresent(authorId);
        }
    }
}
