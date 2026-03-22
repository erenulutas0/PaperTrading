package com.finance.core.service;

import com.finance.core.domain.AppUser;
import com.finance.core.domain.Portfolio;
import com.finance.core.dto.AccountLeaderboardEntry;
import com.finance.core.dto.LeaderboardEntry;
import com.finance.core.dto.TrustScoreBreakdownResponse;
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
    private final TrustScoreService trustScoreService;

    // Period leaderboard caches keep portfolioId -> metric score
    private static final String LEADERBOARD_RETURN_CACHE_KEY_PREFIX = "leaderboard_portfolios:";
    private static final String LEADERBOARD_PROFIT_CACHE_KEY_PREFIX = "leaderboard_portfolios_profit:";
    private static final String ACCOUNT_LEADERBOARD_RETURN_CACHE_KEY_PREFIX = "leaderboard_accounts:";
    private static final String ACCOUNT_LEADERBOARD_PROFIT_CACHE_KEY_PREFIX = "leaderboard_accounts_profit:";
    private static final Duration CACHE_TTL = Duration.ofSeconds(60);
    private static final int LEADERBOARD_REFRESH_BATCH_SIZE = 250;
    private static final List<String> SUPPORTED_PERIODS = List.of("1D", "1W", "1M", "ALL");

    public Page<LeaderboardEntry> getLeaderboard(String period, String sortBy, String direction, Pageable pageable) {
        String safePeriod = normalizePeriod(period);
        LeaderboardSortBy safeSortBy = normalizeSortBy(sortBy);
        if (!isPortfolioSortBy(safeSortBy)) {
            safeSortBy = LeaderboardSortBy.RETURN_PERCENTAGE;
        }
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
            return buildPortfolioLeaderboardFromAllPublicPortfolios(safePeriod, safeSortBy, safeDirection, pageable);
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
        if (total <= 0L && !entries.isEmpty()) {
            return buildPortfolioLeaderboardFromAllPublicPortfolios(safePeriod, safeSortBy, safeDirection, pageable);
        }
        return new PageImpl<>(entries, pageable, total);
    }

    private Page<LeaderboardEntry> buildPortfolioLeaderboardFromAllPublicPortfolios(
            String period,
            LeaderboardSortBy sortBy,
            LeaderboardDirection direction,
            Pageable pageable) {
        List<Portfolio> publicPortfolios = portfolioRepository.findByVisibility(Portfolio.Visibility.PUBLIC);
        if (publicPortfolios.isEmpty()) {
            return Page.empty(pageable);
        }

        LocalDateTime startTime = performanceCalculationService.getStartTimeForPeriod(period);
        Map<String, Double> prices = binanceService.getPrices();
        Set<UUID> ownerIds = publicPortfolios.stream()
                .map(this::parseOwnerUuid)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<UUID, AppUser> userMap = userRepository.findAllById(ownerIds).stream()
                .collect(Collectors.toMap(AppUser::getId, u -> u));

        List<LeaderboardEntry> sorted = new ArrayList<>();
        for (Portfolio portfolio : publicPortfolios) {
            try {
                PerformanceCalculationService.PerformanceMetrics metrics = performanceCalculationService.calculateMetrics(
                        portfolio,
                        startTime,
                        period,
                        prices);
                sorted.add(LeaderboardEntry.builder()
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
                log.error("Failed to compute fallback leaderboard metrics for portfolio={} period={}",
                        portfolio.getId(),
                        period,
                        e);
            }
        }

        sorted.sort(resolvePortfolioComparator(sortBy, direction));
        long total = sorted.size();
        int fromIndex = (int) Math.min(pageable.getOffset(), total);
        int toIndex = (int) Math.min(fromIndex + pageable.getPageSize(), total);

        List<LeaderboardEntry> pageContent = new ArrayList<>();
        for (int i = fromIndex; i < toIndex; i++) {
            LeaderboardEntry entry = sorted.get(i);
            pageContent.add(LeaderboardEntry.builder()
                    .rank(i + 1)
                    .portfolioId(entry.getPortfolioId())
                    .portfolioName(entry.getPortfolioName())
                    .ownerId(entry.getOwnerId())
                    .ownerName(entry.getOwnerName())
                    .returnPercentage(entry.getReturnPercentage())
                    .startEquity(entry.getStartEquity())
                    .profitLoss(entry.getProfitLoss())
                    .totalEquity(entry.getTotalEquity())
                    .build());
        }

        return new PageImpl<>(pageContent, pageable, total);
    }

    private Page<AccountLeaderboardEntry> buildAccountLeaderboardFromAllPublicPortfolios(
            String period,
            LeaderboardSortBy sortBy,
            LeaderboardDirection direction,
            Pageable pageable) {
        List<Portfolio> publicPortfolios = portfolioRepository.findByVisibility(Portfolio.Visibility.PUBLIC);
        if (publicPortfolios.isEmpty()) {
            return Page.empty(pageable);
        }

        LocalDateTime startTime = performanceCalculationService.getStartTimeForPeriod(period);
        Map<String, Double> prices = binanceService.getPrices();
        Set<UUID> ownerIds = publicPortfolios.stream()
                .map(this::parseOwnerUuid)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<UUID, AppUser> userMap = userRepository.findAllById(ownerIds).stream()
                .collect(Collectors.toMap(AppUser::getId, u -> u));
        Map<UUID, AccountAggregate> aggregateByOwner = new HashMap<>();

        for (Portfolio portfolio : publicPortfolios) {
            UUID ownerUuid = parseOwnerUuid(portfolio);
            if (ownerUuid == null) {
                continue;
            }

            try {
                PerformanceCalculationService.PerformanceMetrics metrics = performanceCalculationService.calculateMetrics(
                        portfolio,
                        startTime,
                        period,
                        prices);
                aggregateByOwner.computeIfAbsent(ownerUuid, ignored -> new AccountAggregate())
                        .accumulate(metrics);
            } catch (Exception e) {
                log.error("Failed to compute account leaderboard metrics for portfolio={} period={}",
                        portfolio.getId(),
                        period,
                        e);
            }
        }

        List<AccountLeaderboardEntry> sorted = aggregateByOwner.entrySet().stream()
                .map(entry -> {
                    UUID ownerId = entry.getKey();
                    AccountAggregate aggregate = entry.getValue();
                    AppUser user = userMap.get(ownerId);
                    TrustScoreBreakdownResponse trustBreakdown = trustScoreService.buildTrustScoreBreakdown(ownerId);
                    return AccountLeaderboardEntry.builder()
                            .ownerId(ownerId)
                            .ownerName(resolveOwnerName(user, ownerId))
                            .returnPercentage(aggregate.getReturnPercentage())
                            .totalEquity(aggregate.getTotalEquity())
                            .profitLoss(aggregate.getProfitLoss())
                            .startEquity(aggregate.getStartEquity())
                            .publicPortfolioCount(aggregate.getPublicPortfolioCount())
                            .trustScore(trustScoreService.calculateTrustScore(trustBreakdown))
                            .winRate(trustBreakdown.getBlendedWinRate())
                            .build();
                })
                .sorted(resolveAccountComparator(sortBy, direction))
                .toList();

        long total = sorted.size();
        int fromIndex = (int) Math.min(pageable.getOffset(), total);
        int toIndex = (int) Math.min(fromIndex + pageable.getPageSize(), total);
        List<AccountLeaderboardEntry> pageContent = new ArrayList<>();
        for (int i = fromIndex; i < toIndex; i++) {
            AccountLeaderboardEntry entry = sorted.get(i);
            pageContent.add(AccountLeaderboardEntry.builder()
                    .rank(i + 1)
                    .ownerId(entry.getOwnerId())
                    .ownerName(entry.getOwnerName())
                    .returnPercentage(entry.getReturnPercentage())
                    .totalEquity(entry.getTotalEquity())
                    .profitLoss(entry.getProfitLoss())
                    .startEquity(entry.getStartEquity())
                    .publicPortfolioCount(entry.getPublicPortfolioCount())
                    .trustScore(entry.getTrustScore())
                    .winRate(entry.getWinRate())
                    .build());
        }

        return new PageImpl<>(pageContent, pageable, total);
    }

    public Page<AccountLeaderboardEntry> getAccountLeaderboard(
            String period,
            String sortBy,
            String direction,
            Pageable pageable) {
        String safePeriod = normalizePeriod(period);
        LeaderboardSortBy safeSortBy = normalizeSortBy(sortBy);
        LeaderboardDirection safeDirection = normalizeDirection(direction);

        if (isAccountOnlySortBy(safeSortBy)) {
            return buildAccountLeaderboardFromAllPublicPortfolios(safePeriod, safeSortBy, safeDirection, pageable);
        }

        String cacheKey = resolveAccountCacheKey(safePeriod, safeSortBy);

        long start = pageable.getOffset();
        long end = start + pageable.getPageSize() - 1;

        Set<ZSetOperations.TypedTuple<Object>> range = loadLeaderboardRange(
                cacheKey,
                safePeriod,
                safeDirection,
                start,
                end);
        if (range == null || range.isEmpty()) {
            return buildAccountLeaderboardFromAllPublicPortfolios(safePeriod, safeSortBy, safeDirection, pageable);
        }

        List<UUID> rankedOwnerIds = new ArrayList<>();
        for (ZSetOperations.TypedTuple<Object> tuple : range) {
            parseMemberAsUuid(tuple.getValue()).ifPresent(rankedOwnerIds::add);
        }
        if (rankedOwnerIds.isEmpty()) {
            return Page.empty(pageable);
        }

        List<String> ownerIds = rankedOwnerIds.stream().map(UUID::toString).toList();
        List<Portfolio> publicPortfolios = portfolioRepository.findByOwnerIdInAndVisibility(
                ownerIds,
                Portfolio.Visibility.PUBLIC);
        if (publicPortfolios.isEmpty()) {
            return Page.empty(pageable);
        }

        LocalDateTime startTime = performanceCalculationService.getStartTimeForPeriod(safePeriod);
        Map<String, Double> prices = binanceService.getPrices();
        Map<UUID, AppUser> userMap = userRepository.findAllById(rankedOwnerIds).stream()
                .collect(Collectors.toMap(AppUser::getId, u -> u));

        Map<UUID, AccountAggregate> aggregateByOwner = new HashMap<>();
        for (Portfolio portfolio : publicPortfolios) {
            UUID ownerUuid = parseOwnerUuid(portfolio);
            if (ownerUuid == null) {
                continue;
            }
            try {
                PerformanceCalculationService.PerformanceMetrics metrics = performanceCalculationService.calculateMetrics(
                        portfolio,
                        startTime,
                        safePeriod,
                        prices);
                aggregateByOwner.computeIfAbsent(ownerUuid, ignored -> new AccountAggregate())
                        .accumulate(metrics);
            } catch (Exception e) {
                log.error("Failed to compute account leaderboard metrics for portfolio={} period={}",
                        portfolio.getId(),
                        safePeriod,
                        e);
            }
        }

        List<AccountLeaderboardEntry> entries = new ArrayList<>();
        int rank = (int) start + 1;
        for (UUID ownerId : rankedOwnerIds) {
            AccountAggregate aggregate = aggregateByOwner.get(ownerId);
            if (aggregate == null || aggregate.getPublicPortfolioCount() == 0) {
                continue;
            }

            AppUser user = userMap.get(ownerId);
            TrustScoreBreakdownResponse trustBreakdown = trustScoreService.buildTrustScoreBreakdown(ownerId);
            double trustScore = trustScoreService.calculateTrustScore(trustBreakdown);

            entries.add(AccountLeaderboardEntry.builder()
                    .rank(rank++)
                    .ownerId(ownerId)
                    .ownerName(resolveOwnerName(user, ownerId))
                    .returnPercentage(aggregate.getReturnPercentage())
                    .totalEquity(aggregate.getTotalEquity())
                    .profitLoss(aggregate.getProfitLoss())
                    .startEquity(aggregate.getStartEquity())
                    .publicPortfolioCount(aggregate.getPublicPortfolioCount())
                    .trustScore(trustScore)
                    .winRate(trustBreakdown.getBlendedWinRate())
                    .build());
        }

        long total = Optional.ofNullable(cacheService.zCard(cacheKey)).orElse(0L);
        if (total <= 0L && !entries.isEmpty()) {
            return buildAccountLeaderboardFromAllPublicPortfolios(safePeriod, safeSortBy, safeDirection, pageable);
        }
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
        cacheService.deletePattern(ACCOUNT_LEADERBOARD_RETURN_CACHE_KEY_PREFIX + "*");
        cacheService.deletePattern(ACCOUNT_LEADERBOARD_PROFIT_CACHE_KEY_PREFIX + "*");
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
        String accountReturnCacheKey = ACCOUNT_LEADERBOARD_RETURN_CACHE_KEY_PREFIX + period;
        String accountProfitCacheKey = ACCOUNT_LEADERBOARD_PROFIT_CACHE_KEY_PREFIX + period;
        cacheService.delete(returnCacheKey);
        cacheService.delete(profitCacheKey);
        cacheService.delete(accountReturnCacheKey);
        cacheService.delete(accountProfitCacheKey);

        LocalDateTime startTime = performanceCalculationService.getStartTimeForPeriod(period);
        int updatedCount = 0;
        Map<UUID, AccountAggregate> accountAggregates = new HashMap<>();

        int page = 0;
        Page<UUID> portfolioPage;
        do {
            portfolioPage = portfolioRepository.findIdsByVisibility(
                    Portfolio.Visibility.PUBLIC,
                    PageRequest.of(page, LEADERBOARD_REFRESH_BATCH_SIZE));

            for (Portfolio portfolio : loadPortfoliosWithItems(portfolioPage.getContent())) {
                try {
                    PerformanceCalculationService.PerformanceMetrics metrics = performanceCalculationService
                            .calculateMetrics(portfolio, startTime, period, prices);
                    String member = portfolio.getId().toString();
                    cacheService.zAdd(returnCacheKey, member, metrics.getReturnPercentage().doubleValue());
                    cacheService.zAdd(profitCacheKey, member, metrics.getProfitLoss().doubleValue());
                    UUID ownerUuid = parseOwnerUuid(portfolio);
                    if (ownerUuid != null) {
                        accountAggregates.computeIfAbsent(ownerUuid, ignored -> new AccountAggregate())
                                .accumulate(metrics);
                    }
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
        for (Map.Entry<UUID, AccountAggregate> entry : accountAggregates.entrySet()) {
            AccountAggregate aggregate = entry.getValue();
            if (aggregate.getPublicPortfolioCount() == 0) {
                continue;
            }
            String member = entry.getKey().toString();
            cacheService.zAdd(accountReturnCacheKey, member, aggregate.getReturnPercentage().doubleValue());
            cacheService.zAdd(accountProfitCacheKey, member, aggregate.getProfitLoss().doubleValue());
        }
        cacheService.expire(accountReturnCacheKey, CACHE_TTL.multipliedBy(2));
        cacheService.expire(accountProfitCacheKey, CACHE_TTL.multipliedBy(2));
        return updatedCount;
    }

    private List<Portfolio> loadPortfoliosWithItems(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }

        List<Portfolio> loaded = new ArrayList<>(portfolioRepository.findByIdInAndVisibility(ids, Portfolio.Visibility.PUBLIC));
        Map<UUID, Integer> order = new HashMap<>();
        for (int i = 0; i < ids.size(); i++) {
            order.put(ids.get(i), i);
        }
        loaded.sort(Comparator.comparingInt(p -> order.getOrDefault(p.getId(), Integer.MAX_VALUE)));
        return loaded;
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
        if ("WIN".equals(normalized) || "WINRATE".equals(normalized)) {
            return LeaderboardSortBy.WIN_RATE;
        }
        if ("TRUST".equals(normalized) || "TRUSTSCORE".equals(normalized)) {
            return LeaderboardSortBy.TRUST_SCORE;
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

    private String resolveAccountCacheKey(String period, LeaderboardSortBy sortBy) {
        if (sortBy == LeaderboardSortBy.PROFIT_LOSS) {
            return ACCOUNT_LEADERBOARD_PROFIT_CACHE_KEY_PREFIX + period;
        }
        return ACCOUNT_LEADERBOARD_RETURN_CACHE_KEY_PREFIX + period;
    }

    private boolean isPortfolioSortBy(LeaderboardSortBy sortBy) {
        return sortBy == LeaderboardSortBy.RETURN_PERCENTAGE || sortBy == LeaderboardSortBy.PROFIT_LOSS;
    }

    private boolean isAccountOnlySortBy(LeaderboardSortBy sortBy) {
        return sortBy == LeaderboardSortBy.WIN_RATE || sortBy == LeaderboardSortBy.TRUST_SCORE;
    }

    private Comparator<LeaderboardEntry> resolvePortfolioComparator(
            LeaderboardSortBy sortBy,
            LeaderboardDirection direction) {
        Comparator<LeaderboardEntry> comparator;
        if (sortBy == LeaderboardSortBy.PROFIT_LOSS) {
            comparator = Comparator.comparing(
                    LeaderboardEntry::getProfitLoss,
                    Comparator.nullsLast(BigDecimal::compareTo));
        } else {
            comparator = Comparator.comparing(
                    LeaderboardEntry::getReturnPercentage,
                    Comparator.nullsLast(BigDecimal::compareTo));
        }

        comparator = comparator
                .thenComparing(LeaderboardEntry::getTotalEquity, Comparator.nullsLast(BigDecimal::compareTo))
                .thenComparing(LeaderboardEntry::getPortfolioName, Comparator.nullsLast(String::compareToIgnoreCase));
        return direction == LeaderboardDirection.ASC ? comparator : comparator.reversed();
    }

    private Comparator<AccountLeaderboardEntry> resolveAccountComparator(
            LeaderboardSortBy sortBy,
            LeaderboardDirection direction) {
        Comparator<AccountLeaderboardEntry> comparator;
        if (sortBy == LeaderboardSortBy.WIN_RATE) {
            comparator = Comparator.comparing(AccountLeaderboardEntry::getWinRate, Comparator.nullsLast(Double::compareTo));
        } else if (sortBy == LeaderboardSortBy.PROFIT_LOSS) {
            comparator = Comparator.comparing(AccountLeaderboardEntry::getProfitLoss, Comparator.nullsLast(BigDecimal::compareTo));
        } else if (sortBy == LeaderboardSortBy.RETURN_PERCENTAGE) {
            comparator = Comparator.comparing(AccountLeaderboardEntry::getReturnPercentage, Comparator.nullsLast(BigDecimal::compareTo));
        } else {
            comparator = Comparator.comparing(AccountLeaderboardEntry::getTrustScore, Comparator.nullsLast(Double::compareTo));
        }
        comparator = comparator.thenComparing(AccountLeaderboardEntry::getOwnerName, Comparator.nullsLast(String::compareToIgnoreCase));
        return direction == LeaderboardDirection.ASC ? comparator : comparator.reversed();
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
        return resolveOwnerName(ownerUuid != null ? userMap.get(ownerUuid) : null, ownerUuid);
    }

    private String resolveOwnerName(AppUser user, UUID ownerUuid) {
        if (user != null) {
            if (user.getDisplayName() != null && !user.getDisplayName().isBlank()) {
                return user.getDisplayName();
            }
            if (user.getUsername() != null && !user.getUsername().isBlank()) {
                return user.getUsername();
            }
        }

        if (ownerUuid == null) {
            return "Unknown User";
        }

        String ownerId = ownerUuid.toString();
        return "User " + ownerId.substring(0, Math.min(8, ownerId.length()));
    }

    private static final class AccountAggregate {
        private BigDecimal startEquity = BigDecimal.ZERO;
        private BigDecimal totalEquity = BigDecimal.ZERO;
        private BigDecimal profitLoss = BigDecimal.ZERO;
        private int publicPortfolioCount = 0;

        void accumulate(PerformanceCalculationService.PerformanceMetrics metrics) {
            startEquity = startEquity.add(metrics.getStartEquity());
            totalEquity = totalEquity.add(metrics.getCurrentEquity());
            profitLoss = profitLoss.add(metrics.getProfitLoss());
            publicPortfolioCount++;
        }

        BigDecimal getReturnPercentage() {
            if (startEquity.signum() == 0) {
                return BigDecimal.ZERO;
            }
            return profitLoss
                    .multiply(BigDecimal.valueOf(100))
                    .divide(startEquity, 4, java.math.RoundingMode.HALF_UP);
        }

        BigDecimal getStartEquity() {
            return startEquity;
        }

        BigDecimal getTotalEquity() {
            return totalEquity;
        }

        BigDecimal getProfitLoss() {
            return profitLoss;
        }

        int getPublicPortfolioCount() {
            return publicPortfolioCount;
        }
    }

    private enum LeaderboardSortBy {
        RETURN_PERCENTAGE,
        PROFIT_LOSS,
        WIN_RATE,
        TRUST_SCORE
    }

    private enum LeaderboardDirection {
        ASC,
        DESC
    }
}
