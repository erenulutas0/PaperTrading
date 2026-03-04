package com.finance.core.service;

import com.finance.core.domain.AppUser;
import com.finance.core.domain.Portfolio;
import com.finance.core.dto.LeaderboardEntry;
import com.finance.core.repository.PortfolioRepository;
import com.finance.core.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class LeaderboardService {

    private final PortfolioRepository portfolioRepository;
    private final UserRepository userRepository;
    private final BinanceService binanceService;
    private final CacheService cacheService;
    private final PerformanceCalculationService performanceCalculationService;

    // Period leaderboard caches keep portfolioId -> metric score
    private static final String LEADERBOARD_RETURN_CACHE_KEY_PREFIX = "leaderboard_portfolios:";
    private static final String LEADERBOARD_PROFIT_CACHE_KEY_PREFIX = "leaderboard_portfolios_profit:";
    private static final Duration CACHE_TTL = Duration.ofSeconds(60);
    private static final int LEADERBOARD_REFRESH_BATCH_SIZE = 250;
    private static final List<String> SUPPORTED_PERIODS = List.of("1D", "1W", "1M", "ALL");

    public Page<LeaderboardEntry> getLeaderboard(String period, String sortBy, String direction, Pageable pageable) {
        String safePeriod = normalizePeriod(period);
        LeaderboardSortBy safeSortBy = normalizeSortBy(sortBy);
        LeaderboardDirection safeDirection = normalizeDirection(direction);
        String cacheKey = resolveCacheKey(safePeriod, safeSortBy);

        long start = pageable.getOffset();
        long end = start + pageable.getPageSize() - 1;

        Set<ZSetOperations.TypedTuple<Object>> range = loadLeaderboardRange(
                cacheKey,
                safePeriod,
                safeDirection,
                start,
                end);
        if (range == null || range.isEmpty()) {
            return Page.empty(pageable);
        }

        List<UUID> rankedPortfolioIds = new ArrayList<>();
        for (ZSetOperations.TypedTuple<Object> tuple : range) {
            parseMemberAsUuid(tuple.getValue()).ifPresent(rankedPortfolioIds::add);
        }
        if (rankedPortfolioIds.isEmpty()) {
            return Page.empty(pageable);
        }

        List<Portfolio> allPortfolios = portfolioRepository.findByIdInAndVisibility(rankedPortfolioIds,
                Portfolio.Visibility.PUBLIC);
        if (allPortfolios.isEmpty()) {
            return Page.empty(pageable);
        }

        LocalDateTime startTime = performanceCalculationService.getStartTimeForPeriod(safePeriod);
        Map<String, Double> prices = binanceService.getPrices();
        Map<UUID, Portfolio> portfolioById = allPortfolios.stream()
                .collect(Collectors.toMap(Portfolio::getId, p -> p));

        Set<UUID> ownerUuids = allPortfolios.stream()
                .map(this::parseOwnerUuid)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<UUID, AppUser> userMap = userRepository.findAllById(ownerUuids).stream()
                .collect(Collectors.toMap(AppUser::getId, u -> u));

        List<LeaderboardEntry> entries = new ArrayList<>();
        int rank = (int) start + 1;

        for (UUID portfolioId : rankedPortfolioIds) {
            Portfolio portfolio = portfolioById.get(portfolioId);
            if (portfolio == null)
                continue;

            try {
                PerformanceCalculationService.PerformanceMetrics metrics = performanceCalculationService.calculateMetrics(
                        portfolio,
                        startTime,
                        safePeriod,
                        prices);

                entries.add(LeaderboardEntry.builder()
                        .rank(rank++)
                        .portfolioId(portfolio.getId())
                        .portfolioName(portfolio.getName())
                        .ownerId(portfolio.getOwnerId())
                        .ownerName(resolveOwnerName(portfolio, userMap))
                        .returnPercentage(metrics.getReturnPercentage())
                        .startEquity(metrics.getStartEquity())
                        .profitLoss(metrics.getProfitLoss())
                        .totalEquity(metrics.getCurrentEquity())
                        .build());
            } catch (Exception e) {
                log.error("Failed to compute leaderboard metrics for portfolio={} period={}",
                        portfolio.getId(), safePeriod, e);
            }
        }

        long total = Optional.ofNullable(cacheService.zCard(cacheKey)).orElse(0L);
        return new PageImpl<>(entries, pageable, total);
    }

    @Scheduled(fixedRate = 10000)
    @SchedulerLock(name = "LeaderboardService.refreshLeaderboardJob", lockAtMostFor = "PT1M", lockAtLeastFor = "PT2S")
    public void refreshLeaderboardJob() {
        Map<String, Double> prices = binanceService.getPrices();
        if (prices.isEmpty()) {
            log.warn("Leaderboard Job: Skipping refresh - no market prices available from Binance.");
            return;
        }

        for (String period : SUPPORTED_PERIODS) {
            try {
                int updatedEntries = refreshPeriod(period, prices);
                log.info("Leaderboard Job: Period {} updated with {} portfolios", period, updatedEntries);
            } catch (Exception e) {
                log.error("Leaderboard Job: Failed to refresh period={}", period, e);
            }
        }
    }

    public void invalidateCache() {
        cacheService.deletePattern(LEADERBOARD_RETURN_CACHE_KEY_PREFIX + "*");
        cacheService.deletePattern(LEADERBOARD_PROFIT_CACHE_KEY_PREFIX + "*");
    }

    private Set<ZSetOperations.TypedTuple<Object>> loadLeaderboardRange(
            String cacheKey,
            String period,
            LeaderboardDirection direction,
            long start,
            long end) {
        Set<ZSetOperations.TypedTuple<Object>> range = fetchRange(cacheKey, direction, start, end);
        if (range != null && !range.isEmpty()) {
            return range;
        }

        Map<String, Double> prices = binanceService.getPrices();
        if (!prices.isEmpty()) {
            refreshPeriod(period, prices);
            return fetchRange(cacheKey, direction, start, end);
        }

        return range;
    }

    private Set<ZSetOperations.TypedTuple<Object>> fetchRange(
            String cacheKey,
            LeaderboardDirection direction,
            long start,
            long end) {
        if (direction == LeaderboardDirection.ASC) {
            return cacheService.zRangeWithScores(cacheKey, start, end);
        }
        return cacheService.zRevRangeWithScores(cacheKey, start, end);
    }

    private int refreshPeriod(String period, Map<String, Double> prices) {
        String returnCacheKey = LEADERBOARD_RETURN_CACHE_KEY_PREFIX + period;
        String profitCacheKey = LEADERBOARD_PROFIT_CACHE_KEY_PREFIX + period;
        cacheService.delete(returnCacheKey);
        cacheService.delete(profitCacheKey);

        LocalDateTime startTime = performanceCalculationService.getStartTimeForPeriod(period);
        int updatedCount = 0;

        int page = 0;
        Page<Portfolio> portfolioPage;
        do {
            portfolioPage = portfolioRepository.findByVisibility(
                    Portfolio.Visibility.PUBLIC,
                    PageRequest.of(page, LEADERBOARD_REFRESH_BATCH_SIZE));

            for (Portfolio portfolio : portfolioPage.getContent()) {
                try {
                    PerformanceCalculationService.PerformanceMetrics metrics = performanceCalculationService
                            .calculateMetrics(portfolio, startTime, period, prices);
                    String member = portfolio.getId().toString();
                    cacheService.zAdd(returnCacheKey, member, metrics.getReturnPercentage().doubleValue());
                    cacheService.zAdd(profitCacheKey, member, metrics.getProfitLoss().doubleValue());
                    updatedCount++;
                } catch (Exception e) {
                    log.error("Failed to refresh leaderboard metrics for portfolio={} period={}",
                            portfolio.getId(),
                            period,
                            e);
                }
            }
            page++;
        } while (portfolioPage.hasNext());

        cacheService.expire(returnCacheKey, CACHE_TTL.multipliedBy(2));
        cacheService.expire(profitCacheKey, CACHE_TTL.multipliedBy(2));
        return updatedCount;
    }

    private String normalizePeriod(String period) {
        if (period == null) {
            return "1D";
        }

        String normalized = period.toUpperCase(Locale.ROOT);
        return SUPPORTED_PERIODS.contains(normalized) ? normalized : "1D";
    }

    private LeaderboardSortBy normalizeSortBy(String sortBy) {
        if (sortBy == null) {
            return LeaderboardSortBy.RETURN_PERCENTAGE;
        }

        String normalized = sortBy.trim().toUpperCase(Locale.ROOT);
        if ("PROFIT".equals(normalized)) {
            return LeaderboardSortBy.PROFIT_LOSS;
        }
        if ("RETURN".equals(normalized) || "ROI".equals(normalized)) {
            return LeaderboardSortBy.RETURN_PERCENTAGE;
        }

        try {
            return LeaderboardSortBy.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            return LeaderboardSortBy.RETURN_PERCENTAGE;
        }
    }

    private LeaderboardDirection normalizeDirection(String direction) {
        if (direction == null) {
            return LeaderboardDirection.DESC;
        }

        String normalized = direction.trim().toUpperCase(Locale.ROOT);
        if ("ASCENDING".equals(normalized) || "UP".equals(normalized)) {
            return LeaderboardDirection.ASC;
        }
        if ("DESCENDING".equals(normalized) || "DOWN".equals(normalized)) {
            return LeaderboardDirection.DESC;
        }

        try {
            return LeaderboardDirection.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            return LeaderboardDirection.DESC;
        }
    }

    private String resolveCacheKey(String period, LeaderboardSortBy sortBy) {
        if (sortBy == LeaderboardSortBy.PROFIT_LOSS) {
            return LEADERBOARD_PROFIT_CACHE_KEY_PREFIX + period;
        }
        return LEADERBOARD_RETURN_CACHE_KEY_PREFIX + period;
    }

    private Optional<UUID> parseMemberAsUuid(Object rawMember) {
        if (rawMember == null) {
            return Optional.empty();
        }

        String member = rawMember.toString().replace("\"", "").trim();
        try {
            return Optional.of(UUID.fromString(member));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private UUID parseOwnerUuid(Portfolio portfolio) {
        try {
            return UUID.fromString(portfolio.getOwnerId());
        } catch (Exception e) {
            return null;
        }
    }

    private String resolveOwnerName(Portfolio portfolio, Map<UUID, AppUser> userMap) {
        UUID ownerUuid = parseOwnerUuid(portfolio);
        if (ownerUuid != null) {
            AppUser user = userMap.get(ownerUuid);
            if (user != null) {
                if (user.getDisplayName() != null && !user.getDisplayName().isBlank()) {
                    return user.getDisplayName();
                }
                if (user.getUsername() != null && !user.getUsername().isBlank()) {
                    return user.getUsername();
                }
            }
        }

        String ownerId = portfolio.getOwnerId() != null ? portfolio.getOwnerId() : "unknown";
        return "User " + ownerId.substring(0, Math.min(8, ownerId.length()));
    }

    private enum LeaderboardSortBy {
        RETURN_PERCENTAGE,
        PROFIT_LOSS
    }

    private enum LeaderboardDirection {
        ASC,
        DESC
    }
}
