package com.finance.core.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.finance.core.domain.AppUser;
import com.finance.core.domain.AuditActionType;
import com.finance.core.domain.AuditResourceType;
import com.finance.core.domain.Portfolio;
import com.finance.core.domain.PortfolioItem;
import com.finance.core.domain.StrategyBot;
import com.finance.core.domain.StrategyBotRun;
import com.finance.core.domain.StrategyBotRunEquityPoint;
import com.finance.core.domain.StrategyBotRunEvent;
import com.finance.core.domain.StrategyBotRunFill;
import com.finance.core.domain.TradeActivity;
import com.finance.core.dto.MarketCandleResponse;
import com.finance.core.dto.MarketType;
import com.finance.core.dto.PublicStrategyBotDetailResponse;
import com.finance.core.dto.PublicStrategyBotRunDetailResponse;
import com.finance.core.dto.StrategyBotAnalyticsResponse;
import com.finance.core.dto.StrategyBotBoardEntryResponse;
import com.finance.core.dto.StrategyBotRunReconciliationResponse;
import com.finance.core.dto.StrategyBotRunEquityPointResponse;
import com.finance.core.dto.StrategyBotRunEventResponse;
import com.finance.core.dto.StrategyBotRunFillResponse;
import com.finance.core.dto.StrategyBotRunRequest;
import com.finance.core.dto.StrategyBotRunResponse;
import com.finance.core.dto.StrategyBotRunScorecardResponse;
import com.finance.core.repository.PortfolioItemRepository;
import com.finance.core.repository.PortfolioRepository;
import com.finance.core.repository.StrategyBotRepository;
import com.finance.core.repository.StrategyBotRunEquityPointRepository;
import com.finance.core.repository.StrategyBotRunEventRepository;
import com.finance.core.repository.StrategyBotRunFillRepository;
import com.finance.core.repository.StrategyBotRunRepository;
import com.finance.core.repository.TradeActivityRepository;
import com.finance.core.repository.UserRepository;
import com.finance.core.web.ApiRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class StrategyBotRunService {
    private static final Duration BOT_ANALYTICS_CACHE_TTL = Duration.ofSeconds(30);
    private static final Duration PUBLIC_BOT_DETAIL_CACHE_TTL = Duration.ofSeconds(30);
    private static final Duration BOT_BOARD_CACHE_TTL = Duration.ofSeconds(15);
    private final StrategyBotRunRepository strategyBotRunRepository;
    private final StrategyBotRepository strategyBotRepository;
    private final StrategyBotService strategyBotService;
    private final StrategyBotRuleEngineService strategyBotRuleEngineService;
    private final PortfolioRepository portfolioRepository;
    private final PortfolioItemRepository portfolioItemRepository;
    private final StrategyBotRunFillRepository strategyBotRunFillRepository;
    private final StrategyBotRunEquityPointRepository strategyBotRunEquityPointRepository;
    private final StrategyBotRunEventRepository strategyBotRunEventRepository;
    private final TradeActivityRepository tradeActivityRepository;
    private final UserRepository userRepository;
    private final MarketDataFacadeService marketDataFacadeService;
    private final ObjectMapper objectMapper;
    private final AuditLogService auditLogService;
    private final PerformanceAnalyticsService performanceAnalyticsService;
    private final CacheService cacheService;

    @Value("${app.strategy-bots.synthetic-crypto-candles-enabled:false}")
    private boolean syntheticCryptoCandlesEnabled;

    @Value("${app.strategy-bots.synthetic-crypto-candle-count:96}")
    private int syntheticCryptoCandleCount;

    @Transactional(readOnly = true)
    public Page<StrategyBotRunResponse> getRuns(UUID botId, UUID userId, Pageable pageable) {
        StrategyBot bot = strategyBotService.getOwnedBotEntity(botId, userId);
        return strategyBotRunRepository.findByStrategyBotIdAndUserId(bot.getId(), userId, pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public StrategyBotRunResponse getRun(UUID botId, UUID runId, UUID userId) {
        strategyBotService.getOwnedBotEntity(botId, userId);
        return toResponse(strategyBotRunRepository.findByIdAndStrategyBotIdAndUserId(runId, botId, userId)
                .orElseThrow(() -> ApiRequestException.notFound("strategy_bot_run_not_found", "Strategy bot run not found")));
    }

    @Transactional(readOnly = true)
    public Page<StrategyBotRunFillResponse> getRunFills(UUID botId, UUID runId, UUID userId, Pageable pageable) {
        StrategyBotRun run = getOwnedRunEntity(botId, runId, userId);
        return strategyBotRunFillRepository.findByStrategyBotRunIdOrderBySequenceNoAsc(run.getId(), pageable)
                .map(this::toFillResponse);
    }

    @Transactional(readOnly = true)
    public Page<StrategyBotRunEventResponse> getRunEvents(UUID botId, UUID runId, UUID userId, Pageable pageable) {
        StrategyBotRun run = getOwnedRunEntity(botId, runId, userId);
        return strategyBotRunEventRepository.findByStrategyBotRunIdOrderBySequenceNoAsc(run.getId(), pageable)
                .map(this::toEventResponse);
    }

    @Transactional(readOnly = true)
    public Page<StrategyBotRunEquityPointResponse> getRunEquityCurve(UUID botId, UUID runId, UUID userId, Pageable pageable) {
        StrategyBotRun run = getOwnedRunEntity(botId, runId, userId);
        return strategyBotRunEquityPointRepository.findByStrategyBotRunIdOrderBySequenceNoAsc(run.getId(), pageable)
                .map(this::toEquityPointResponse);
    }

    @Transactional(readOnly = true)
    public StrategyBotRunReconciliationResponse getRunReconciliation(UUID botId, UUID runId, UUID userId) {
        StrategyBot bot = strategyBotService.getOwnedBotEntity(botId, userId);
        StrategyBotRun run = getOwnedRunEntity(botId, runId, userId);
        return buildRunReconciliationState(bot, run).response();
    }

    @Transactional(readOnly = true)
    public StrategyBotAnalyticsResponse getBotAnalytics(UUID botId, UUID userId, String runMode, Integer lookbackDays) {
        StrategyBot bot = strategyBotService.getOwnedBotEntity(botId, userId);
        return getCachedBotAnalytics(
                bot,
                userId,
                normalizeBoardRunMode(runMode),
                normalizeBoardLookbackDays(lookbackDays));
    }

    @Transactional(readOnly = true)
    public Page<StrategyBotBoardEntryResponse> getBotBoard(UUID userId,
                                                           Pageable pageable,
                                                           String sortBy,
                                                           String direction,
                                                           String runMode,
                                                           Integer lookbackDays) {
        StrategyBotRun.RunMode scopedRunMode = normalizeBoardRunMode(runMode);
        Integer normalizedLookbackDays = normalizeBoardLookbackDays(lookbackDays);
        String normalizedSort = normalizeBoardSort(sortBy);
        String normalizedDirection = normalizeBoardDirection(direction);
        return getCachedOwnedBotBoardPage(
                userId,
                pageable,
                normalizedSort,
                normalizedDirection,
                scopedRunMode,
                normalizedLookbackDays);
    }

    private Page<StrategyBotBoardEntryResponse> getCachedOwnedBotBoardPage(UUID userId,
                                                                           Pageable pageable,
                                                                           String normalizedSort,
                                                                           String normalizedDirection,
                                                                           StrategyBotRun.RunMode scopedRunMode,
                                                                           Integer normalizedLookbackDays) {
        String cacheKey = ownedBotBoardCacheKey(
                userId,
                pageable,
                normalizedSort,
                normalizedDirection,
                scopedRunMode,
                normalizedLookbackDays);
        CachedBoardPage cached = cacheService.get(cacheKey, String.class)
                .flatMap(this::readCachedBoardPage)
                .orElse(null);
        if (cached != null) {
            return toCachedBoardPage(cached, pageable);
        }
        Page<StrategyBotBoardEntryResponse> page = buildOwnedBotBoardPage(
                userId,
                pageable,
                normalizedSort,
                normalizedDirection,
                scopedRunMode,
                normalizedLookbackDays);
        cacheStrategyBotJson(cacheKey, CachedBoardPage.from(page), BOT_BOARD_CACHE_TTL);
        return page;
    }

    private Page<StrategyBotBoardEntryResponse> buildOwnedBotBoardPage(UUID userId,
                                                                       Pageable pageable,
                                                                       String normalizedSort,
                                                                       String normalizedDirection,
                                                                       StrategyBotRun.RunMode scopedRunMode,
                                                                       Integer normalizedLookbackDays) {
        if (supportsPagedBoardFastPath(normalizedSort, scopedRunMode, normalizedLookbackDays)) {
            return buildOwnedBotBoardPageFast(
                    userId,
                    pageable,
                    normalizedSort,
                    normalizedDirection,
                    scopedRunMode,
                    normalizedLookbackDays);
        }
        List<StrategyBotBoardEntryResponse> entries = buildBotBoardEntries(
                userId,
                normalizedSort,
                normalizedDirection,
                scopedRunMode,
                normalizedLookbackDays);

        int start = Math.toIntExact(pageable.getOffset());
        if (start >= entries.size()) {
            return new PageImpl<>(List.of(), pageable, entries.size());
        }
        int end = Math.min(start + pageable.getPageSize(), entries.size());
        return new PageImpl<>(entries.subList(start, end), pageable, entries.size());
    }

    @Transactional(readOnly = true)
    public Page<StrategyBotBoardEntryResponse> discoverPublicBotBoard(Pageable pageable,
                                                                      String sortBy,
                                                                      String direction,
                                                                      String runMode,
                                                                      Integer lookbackDays,
                                                                      String query) {
        StrategyBotRun.RunMode scopedRunMode = normalizeBoardRunMode(runMode);
        Integer normalizedLookbackDays = normalizeBoardLookbackDays(lookbackDays);
        String normalizedSort = normalizeBoardSort(sortBy);
        String normalizedDirection = normalizeBoardDirection(direction);
        String normalizedQuery = normalizeSearchQuery(query);
        return getCachedPublicBotBoardPage(
                pageable,
                normalizedSort,
                normalizedDirection,
                normalizedQuery,
                scopedRunMode,
                normalizedLookbackDays);
    }

    private Page<StrategyBotBoardEntryResponse> getCachedPublicBotBoardPage(Pageable pageable,
                                                                            String normalizedSort,
                                                                            String normalizedDirection,
                                                                            String normalizedQuery,
                                                                            StrategyBotRun.RunMode scopedRunMode,
                                                                            Integer normalizedLookbackDays) {
        String cacheKey = publicBotBoardCacheKey(
                pageable,
                normalizedSort,
                normalizedDirection,
                normalizedQuery,
                scopedRunMode,
                normalizedLookbackDays);
        CachedBoardPage cached = cacheService.get(cacheKey, String.class)
                .flatMap(this::readCachedBoardPage)
                .orElse(null);
        if (cached != null) {
            return toCachedBoardPage(cached, pageable);
        }
        Page<StrategyBotBoardEntryResponse> page = buildPublicBotBoardPage(
                pageable,
                normalizedSort,
                normalizedDirection,
                normalizedQuery,
                scopedRunMode,
                normalizedLookbackDays);
        cacheStrategyBotJson(cacheKey, CachedBoardPage.from(page), BOT_BOARD_CACHE_TTL);
        return page;
    }

    private Page<StrategyBotBoardEntryResponse> buildPublicBotBoardPage(Pageable pageable,
                                                                        String normalizedSort,
                                                                        String normalizedDirection,
                                                                        String normalizedQuery,
                                                                        StrategyBotRun.RunMode scopedRunMode,
                                                                        Integer normalizedLookbackDays) {
        if (supportsPagedBoardFastPath(normalizedSort, scopedRunMode, normalizedLookbackDays)) {
            return buildPublicBotBoardPageFast(
                    pageable,
                    normalizedSort,
                    normalizedDirection,
                    normalizedQuery,
                    scopedRunMode,
                    normalizedLookbackDays);
        }
        List<StrategyBotBoardEntryResponse> entries = buildPublicBotBoardEntries(
                normalizedSort,
                normalizedDirection,
                scopedRunMode,
                normalizedLookbackDays,
                normalizedQuery);

        int start = Math.toIntExact(pageable.getOffset());
        if (start >= entries.size()) {
            return new PageImpl<>(List.of(), pageable, entries.size());
        }
        int end = Math.min(start + pageable.getPageSize(), entries.size());
        return new PageImpl<>(entries.subList(start, end), pageable, entries.size());
    }

    @Transactional(readOnly = true)
    public PublicStrategyBotDetailResponse getPublicBotDetail(UUID botId,
                                                              String runMode,
                                                              Integer lookbackDays) {
        StrategyBot bot = strategyBotRepository.findPublicDiscoverableBotById(
                        botId,
                        Portfolio.Visibility.PUBLIC,
                        StrategyBot.Status.DRAFT)
                .orElseThrow(() -> ApiRequestException.notFound("strategy_bot_not_found", "Strategy bot not found"));
        return getCachedPublicBotDetail(
                bot,
                normalizeBoardRunMode(runMode),
                normalizeBoardLookbackDays(lookbackDays));
    }

    private PublicStrategyBotDetailResponse buildPublicBotDetail(StrategyBot bot,
                                                                 StrategyBotRun.RunMode scopedRunMode,
                                                                 Integer normalizedLookbackDays) {
        AppUser owner = userRepository.findById(bot.getUserId()).orElse(null);
        Portfolio linkedPortfolio = bot.getLinkedPortfolioId() == null
                ? null
                : portfolioRepository.findById(bot.getLinkedPortfolioId())
                        .filter(portfolio -> portfolio.getVisibility() == Portfolio.Visibility.PUBLIC)
                        .orElse(null);
        StrategyBotAnalyticsResponse analytics = buildBotAnalyticsFromSnapshots(
                bot.getId(),
                scopedRunMode,
                normalizedLookbackDays,
                () -> strategyBotRunRepository.findByStrategyBotIdOrderByRequestedAtDesc(bot.getId()));

        return PublicStrategyBotDetailResponse.builder()
                .strategyBotId(bot.getId())
                .name(bot.getName())
                .description(bot.getDescription())
                .botKind(bot.getBotKind().name())
                .status(bot.getStatus().name())
                .market(bot.getMarket())
                .symbol(bot.getSymbol())
                .timeframe(bot.getTimeframe())
                .linkedPortfolioId(bot.getLinkedPortfolioId())
                .linkedPortfolioName(linkedPortfolio == null ? null : linkedPortfolio.getName())
                .ownerId(owner == null ? bot.getUserId() : owner.getId())
                .ownerUsername(owner == null ? null : owner.getUsername())
                .ownerDisplayName(owner == null ? null : owner.getDisplayName())
                .ownerAvatarUrl(owner == null ? null : owner.getAvatarUrl())
                .ownerTrustScore(owner == null ? null : round(owner.getTrustScore()))
                .maxPositionSizePercent(bot.getMaxPositionSizePercent())
                .stopLossPercent(bot.getStopLossPercent())
                .takeProfitPercent(bot.getTakeProfitPercent())
                .cooldownMinutes(bot.getCooldownMinutes())
                .entryRules(parseJson(bot.getEntryRules()))
                .exitRules(parseJson(bot.getExitRules()))
                .analytics(analytics)
                .build();
    }

    private StrategyBotAnalyticsResponse getCachedBotAnalytics(StrategyBot bot,
                                                               UUID userId,
                                                               StrategyBotRun.RunMode scopedRunMode,
                                                               Integer lookbackDays) {
        String cacheKey = botAnalyticsCacheKey(bot.getId(), userId, scopedRunMode, lookbackDays);
        StrategyBotAnalyticsResponse cached = cacheService.get(cacheKey, String.class)
                .flatMap(this::readCachedStrategyBotAnalytics)
                .orElse(null);
        if (cached != null) {
            return cached;
        }
        StrategyBotAnalyticsResponse analytics = buildBotAnalytics(bot, userId, scopedRunMode, lookbackDays);
        cacheStrategyBotJson(cacheKey, analytics, BOT_ANALYTICS_CACHE_TTL);
        return analytics;
    }

    private PublicStrategyBotDetailResponse getCachedPublicBotDetail(StrategyBot bot,
                                                                     StrategyBotRun.RunMode scopedRunMode,
                                                                     Integer lookbackDays) {
        String cacheKey = publicBotDetailCacheKey(bot.getId(), scopedRunMode, lookbackDays);
        PublicStrategyBotDetailResponse cached = cacheService.get(cacheKey, String.class)
                .flatMap(this::readCachedPublicStrategyBotDetail)
                .orElse(null);
        if (cached != null) {
            return cached;
        }
        PublicStrategyBotDetailResponse detail = buildPublicBotDetail(bot, scopedRunMode, lookbackDays);
        cacheStrategyBotJson(cacheKey, detail, PUBLIC_BOT_DETAIL_CACHE_TTL);
        return detail;
    }

    private void invalidateBotReadCaches(UUID botId) {
        if (botId != null) {
            cacheService.deletePattern("strategy-bot:analytics:" + botId + ":*");
            cacheService.deletePattern("strategy-bot:public-detail:" + botId + ":*");
        }
        cacheService.deletePattern("strategy-bot:board:*");
        cacheService.deletePattern("strategy-bot:discover:*");
    }

    private String botAnalyticsCacheKey(UUID botId,
                                        UUID userId,
                                        StrategyBotRun.RunMode scopedRunMode,
                                        Integer lookbackDays) {
        return "strategy-bot:analytics:" + botId
                + ":user:" + userId
                + ":run-mode:" + boardRunModeScopeKey(scopedRunMode)
                + ":lookback:" + boardLookbackScopeKey(lookbackDays);
    }

    private String publicBotDetailCacheKey(UUID botId,
                                           StrategyBotRun.RunMode scopedRunMode,
                                           Integer lookbackDays) {
        return "strategy-bot:public-detail:" + botId
                + ":run-mode:" + boardRunModeScopeKey(scopedRunMode)
                + ":lookback:" + boardLookbackScopeKey(lookbackDays);
    }

    private String boardRunModeScopeKey(StrategyBotRun.RunMode scopedRunMode) {
        return scopedRunMode == null ? "ALL" : scopedRunMode.name();
    }

    private String boardLookbackScopeKey(Integer lookbackDays) {
        return lookbackDays == null ? "ALL" : String.valueOf(lookbackDays);
    }

    private Optional<StrategyBotAnalyticsResponse> readCachedStrategyBotAnalytics(String raw) {
        return readCachedStrategyBotJson(raw, StrategyBotAnalyticsResponse.class);
    }

    private Optional<PublicStrategyBotDetailResponse> readCachedPublicStrategyBotDetail(String raw) {
        return readCachedStrategyBotJson(raw, PublicStrategyBotDetailResponse.class);
    }

    private Optional<CachedBoardPage> readCachedBoardPage(String raw) {
        return readCachedStrategyBotJson(raw, CachedBoardPage.class);
    }

    private Optional<CachedBoardEntries> readCachedBoardEntries(String raw) {
        return readCachedStrategyBotJson(raw, CachedBoardEntries.class);
    }

    private <T> Optional<T> readCachedStrategyBotJson(String raw, Class<T> type) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(raw, type));
        } catch (JsonProcessingException ex) {
            return Optional.empty();
        }
    }

    private void cacheStrategyBotJson(String cacheKey, Object payload, Duration ttl) {
        try {
            cacheService.set(cacheKey, objectMapper.writeValueAsString(payload), ttl);
        } catch (JsonProcessingException ignored) {
            // Cache write failure should not fail the primary read path.
        }
    }

    private Page<StrategyBotBoardEntryResponse> toCachedBoardPage(CachedBoardPage cached, Pageable pageable) {
        List<StrategyBotBoardEntryResponse> content = cached.content() == null ? List.of() : cached.content();
        return new PageImpl<>(content, pageable, cached.totalElements());
    }

    private List<StrategyBotBoardEntryResponse> getCachedOwnedBotBoardEntries(UUID userId,
                                                                              String normalizedSort,
                                                                              String normalizedDirection,
                                                                              StrategyBotRun.RunMode scopedRunMode,
                                                                              Integer normalizedLookbackDays) {
        String cacheKey = ownedBotBoardEntriesCacheKey(
                userId,
                normalizedSort,
                normalizedDirection,
                scopedRunMode,
                normalizedLookbackDays);
        CachedBoardEntries cached = cacheService.get(cacheKey, String.class)
                .flatMap(this::readCachedBoardEntries)
                .orElse(null);
        if (cached != null && cached.entries() != null) {
            return cached.entries();
        }
        List<StrategyBotBoardEntryResponse> entries = buildBotBoardEntries(
                userId,
                normalizedSort,
                normalizedDirection,
                scopedRunMode,
                normalizedLookbackDays);
        cacheStrategyBotJson(cacheKey, CachedBoardEntries.from(entries), BOT_BOARD_CACHE_TTL);
        return entries;
    }

    private List<StrategyBotBoardEntryResponse> getCachedPublicBotBoardEntries(String normalizedSort,
                                                                               String normalizedDirection,
                                                                               StrategyBotRun.RunMode scopedRunMode,
                                                                               Integer normalizedLookbackDays,
                                                                               String normalizedQuery) {
        String cacheKey = publicBotBoardEntriesCacheKey(
                normalizedSort,
                normalizedDirection,
                scopedRunMode,
                normalizedLookbackDays,
                normalizedQuery);
        CachedBoardEntries cached = cacheService.get(cacheKey, String.class)
                .flatMap(this::readCachedBoardEntries)
                .orElse(null);
        if (cached != null && cached.entries() != null) {
            return cached.entries();
        }
        List<StrategyBotBoardEntryResponse> entries = buildPublicBotBoardEntries(
                normalizedSort,
                normalizedDirection,
                scopedRunMode,
                normalizedLookbackDays,
                normalizedQuery);
        cacheStrategyBotJson(cacheKey, CachedBoardEntries.from(entries), BOT_BOARD_CACHE_TTL);
        return entries;
    }

    private String ownedBotBoardCacheKey(UUID userId,
                                         Pageable pageable,
                                         String normalizedSort,
                                         String normalizedDirection,
                                         StrategyBotRun.RunMode scopedRunMode,
                                         Integer normalizedLookbackDays) {
        return "strategy-bot:board:owned:user:" + userId
                + ":page:" + pageable.getPageNumber()
                + ":size:" + pageable.getPageSize()
                + ":sort:" + normalizedSort
                + ":direction:" + normalizedDirection
                + ":run-mode:" + boardRunModeScopeKey(scopedRunMode)
                + ":lookback:" + boardLookbackScopeKey(normalizedLookbackDays);
    }

    private String ownedBotBoardEntriesCacheKey(UUID userId,
                                                String normalizedSort,
                                                String normalizedDirection,
                                                StrategyBotRun.RunMode scopedRunMode,
                                                Integer normalizedLookbackDays) {
        return "strategy-bot:board:owned-export:user:" + userId
                + ":sort:" + normalizedSort
                + ":direction:" + normalizedDirection
                + ":run-mode:" + boardRunModeScopeKey(scopedRunMode)
                + ":lookback:" + boardLookbackScopeKey(normalizedLookbackDays);
    }

    private String publicBotBoardCacheKey(Pageable pageable,
                                          String normalizedSort,
                                          String normalizedDirection,
                                          String normalizedQuery,
                                          StrategyBotRun.RunMode scopedRunMode,
                                          Integer normalizedLookbackDays) {
        return "strategy-bot:discover:page:" + pageable.getPageNumber()
                + ":size:" + pageable.getPageSize()
                + ":sort:" + normalizedSort
                + ":direction:" + normalizedDirection
                + ":run-mode:" + boardRunModeScopeKey(scopedRunMode)
                + ":lookback:" + boardLookbackScopeKey(normalizedLookbackDays)
                + ":q:" + cacheKeySegment(normalizedQuery);
    }

    private String publicBotBoardEntriesCacheKey(String normalizedSort,
                                                 String normalizedDirection,
                                                 StrategyBotRun.RunMode scopedRunMode,
                                                 Integer normalizedLookbackDays,
                                                 String normalizedQuery) {
        return "strategy-bot:discover:export"
                + ":sort:" + normalizedSort
                + ":direction:" + normalizedDirection
                + ":run-mode:" + boardRunModeScopeKey(scopedRunMode)
                + ":lookback:" + boardLookbackScopeKey(normalizedLookbackDays)
                + ":q:" + cacheKeySegment(normalizedQuery);
    }

    private String cacheKeySegment(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            return "ALL";
        }
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(normalized.getBytes(StandardCharsets.UTF_8));
    }

    @Transactional(readOnly = true)
    public PublicStrategyBotRunDetailResponse getPublicBotRunDetail(UUID botId, UUID runId) {
        StrategyBot bot = strategyBotRepository.findPublicDiscoverableBotById(
                        botId,
                        Portfolio.Visibility.PUBLIC,
                        StrategyBot.Status.DRAFT)
                .orElseThrow(() -> ApiRequestException.notFound("strategy_bot_not_found", "Strategy bot not found"));
        StrategyBotRun run = strategyBotRunRepository.findById(runId)
                .filter(candidate -> candidate.getStrategyBotId().equals(bot.getId()))
                .orElseThrow(() -> ApiRequestException.notFound("strategy_bot_run_not_found", "Strategy bot run not found"));
        AppUser owner = userRepository.findById(bot.getUserId()).orElse(null);
        Portfolio linkedPortfolio = bot.getLinkedPortfolioId() == null
                ? null
                : portfolioRepository.findById(bot.getLinkedPortfolioId())
                        .filter(portfolio -> portfolio.getVisibility() == Portfolio.Visibility.PUBLIC)
                        .orElse(null);

        return PublicStrategyBotRunDetailResponse.builder()
                .strategyBotId(bot.getId())
                .runId(run.getId())
                .botName(bot.getName())
                .botDescription(bot.getDescription())
                .botStatus(bot.getStatus().name())
                .market(bot.getMarket())
                .symbol(bot.getSymbol())
                .timeframe(bot.getTimeframe())
                .linkedPortfolioId(bot.getLinkedPortfolioId())
                .linkedPortfolioName(linkedPortfolio == null ? null : linkedPortfolio.getName())
                .ownerId(owner == null ? bot.getUserId() : owner.getId())
                .ownerUsername(owner == null ? null : owner.getUsername())
                .ownerDisplayName(owner == null ? null : owner.getDisplayName())
                .ownerAvatarUrl(owner == null ? null : owner.getAvatarUrl())
                .ownerTrustScore(owner == null ? null : round(owner.getTrustScore()))
                .runMode(run.getRunMode().name())
                .status(run.getStatus().name())
                .requestedInitialCapital(run.getRequestedInitialCapital())
                .effectiveInitialCapital(run.getEffectiveInitialCapital())
                .fromDate(run.getFromDate())
                .toDate(run.getToDate())
                .compiledEntryRules(parseJson(run.getCompiledEntryRules()))
                .compiledExitRules(parseJson(run.getCompiledExitRules()))
                .summary(parseJson(run.getSummary()))
                .errorMessage(run.getErrorMessage())
                .requestedAt(run.getRequestedAt())
                .startedAt(run.getStartedAt())
                .completedAt(run.getCompletedAt())
                .events(getRunEventRows(run))
                .fills(getRunFillRows(run))
                .equityCurve(getRunEquityPointRows(run))
                .build();
    }

    @Transactional(readOnly = true)
    public String buildPublicRunExportJson(UUID botId, UUID runId) {
        PublicStrategyBotRunDetailResponse detail = getPublicBotRunDetail(botId, runId);
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("strategyBotId", detail.getStrategyBotId());
        payload.put("runId", detail.getRunId());
        payload.put("botName", detail.getBotName());
        payload.put("botDescription", detail.getBotDescription());
        payload.put("botStatus", detail.getBotStatus());
        payload.put("market", detail.getMarket());
        payload.put("symbol", detail.getSymbol());
        payload.put("timeframe", detail.getTimeframe());
        payload.put("linkedPortfolioId", detail.getLinkedPortfolioId());
        payload.put("linkedPortfolioName", detail.getLinkedPortfolioName());
        payload.put("ownerId", detail.getOwnerId());
        payload.put("ownerUsername", detail.getOwnerUsername());
        payload.put("ownerDisplayName", detail.getOwnerDisplayName());
        payload.put("ownerAvatarUrl", detail.getOwnerAvatarUrl());
        payload.put("ownerTrustScore", detail.getOwnerTrustScore());
        payload.put("exportedAt", LocalDateTime.now().toString());
        payload.put("run", detail);
        return writePrettyJsonExport(payload, "Failed to serialize public strategy bot run export");
    }

    @Transactional(readOnly = true)
    public byte[] buildPublicRunExportCsv(UUID botId, UUID runId) {
        PublicStrategyBotRunDetailResponse detail = getPublicBotRunDetail(botId, runId);

        List<List<Object>> rows = new ArrayList<>();
        rows.add(List.of(
                "section",
                "key",
                "value",
                "runId",
                "runMode",
                "status",
                "requestedAt",
                "completedAt",
                "returnPercent",
                "netPnl",
                "maxDrawdownPercent",
                "winRate",
                "tradeCount",
                "profitFactor",
                "expectancyPerTrade",
                "timeInMarketPercent",
                "linkedPortfolioAligned",
                "executionEngineReady",
                "lastEvaluatedOpenTime",
                "errorMessage"));

        addMetricRow(rows, "context", "strategyBotId", detail.getStrategyBotId());
        addMetricRow(rows, "context", "runId", detail.getRunId());
        addMetricRow(rows, "context", "botName", detail.getBotName());
        addMetricRow(rows, "context", "botDescription", detail.getBotDescription());
        addMetricRow(rows, "context", "botStatus", detail.getBotStatus());
        addMetricRow(rows, "context", "market", detail.getMarket());
        addMetricRow(rows, "context", "symbol", detail.getSymbol());
        addMetricRow(rows, "context", "timeframe", detail.getTimeframe());
        addMetricRow(rows, "context", "linkedPortfolioId", detail.getLinkedPortfolioId());
        addMetricRow(rows, "context", "linkedPortfolioName", detail.getLinkedPortfolioName());
        addMetricRow(rows, "context", "ownerId", detail.getOwnerId());
        addMetricRow(rows, "context", "ownerUsername", detail.getOwnerUsername());
        addMetricRow(rows, "context", "ownerDisplayName", detail.getOwnerDisplayName());
        addMetricRow(rows, "context", "ownerTrustScore", detail.getOwnerTrustScore());
        addMetricRow(rows, "context", "requestedInitialCapital", detail.getRequestedInitialCapital());
        addMetricRow(rows, "context", "effectiveInitialCapital", detail.getEffectiveInitialCapital());
        addMetricRow(rows, "context", "fromDate", detail.getFromDate());
        addMetricRow(rows, "context", "toDate", detail.getToDate());
        addMetricRow(rows, "context", "exportedAt", LocalDateTime.now());
        addMetricRow(rows, "rules", "compiledEntryRules", stringifyJson(detail.getCompiledEntryRules()));
        addMetricRow(rows, "rules", "compiledExitRules", stringifyJson(detail.getCompiledExitRules()));

        JsonNode summary = detail.getSummary() == null ? objectMapper.nullNode() : objectMapper.valueToTree(detail.getSummary());
        addRunExportSummaryMetric(rows, summary, "executionEngineReady");
        addRunExportSummaryMetric(rows, summary, "returnPercent");
        addRunExportSummaryMetric(rows, summary, "netPnl");
        addRunExportSummaryMetric(rows, summary, "maxDrawdownPercent");
        addRunExportSummaryMetric(rows, summary, "winRate");
        addRunExportSummaryMetric(rows, summary, "tradeCount");
        addRunExportSummaryMetric(rows, summary, "eventCount");
        addRunExportSummaryMetric(rows, summary, "profitFactor");
        addRunExportSummaryMetric(rows, summary, "expectancyPerTrade");
        addRunExportSummaryMetric(rows, summary, "timeInMarketPercent");
        addRunExportSummaryMetric(rows, summary, "lastEvaluatedOpenTime");
        addReasonMetricRows(rows, "entryReason", summary.get("entryReasonCounts"));
        addReasonMetricRows(rows, "exitReason", summary.get("exitReasonCounts"));

        detail.getEvents().forEach(event -> {
            List<Object> row = new ArrayList<>();
            row.add("event");
            row.add(event.getPhase());
            row.add(event.getAction());
            row.add(detail.getRunId());
            row.add(detail.getRunMode());
            row.add(detail.getStatus());
            row.add(event.getOpenTime());
            row.add("");
            row.add("");
            row.add(event.getClosePrice());
            row.add("");
            row.add("");
            row.add(event.getPositionQuantity());
            row.add("");
            row.add("");
            row.add(event.getEquity());
            row.add("");
            row.add("");
            row.add("");
            row.add(writeCompactJson(Map.of(
                    "cashBalance", event.getCashBalance(),
                    "matchedRules", event.getMatchedRules(),
                    "details", event.getDetails())));
            rows.add(row);
        });

        detail.getFills().forEach(fill -> {
            List<Object> row = new ArrayList<>();
            row.add("fill");
            row.add(fill.getSequenceNo());
            row.add("");
            row.add(detail.getRunId());
            row.add(fill.getSide());
            row.add("");
            row.add(fill.getOpenTime());
            row.add("");
            row.add("");
            row.add(fill.getPrice());
            row.add("");
            row.add("");
            row.add(fill.getQuantity());
            row.add("");
            row.add("");
            row.add("");
            row.add("");
            row.add("");
            row.add("");
            row.add(joinJsonArray(fill.getMatchedRules()));
            rows.add(row);
        });

        detail.getEquityCurve().forEach(point -> {
            List<Object> row = new ArrayList<>();
            row.add("equity");
            row.add(point.getSequenceNo());
            row.add("");
            row.add(detail.getRunId());
            row.add("");
            row.add("");
            row.add(point.getOpenTime());
            row.add("");
            row.add("");
            row.add(point.getClosePrice());
            row.add("");
            row.add("");
            row.add("");
            row.add("");
            row.add("");
            row.add(point.getEquity());
            row.add("");
            row.add("");
            row.add("");
            row.add("");
            rows.add(row);
        });

        String content = rows.stream()
                .map(row -> row.stream().map(this::escapeCsv).reduce((left, right) -> left + "," + right).orElse(""))
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
        return content.getBytes(StandardCharsets.UTF_8);
    }

    @Transactional(readOnly = true)
    public String buildPublicBotBoardExportJson(String sortBy,
                                                String direction,
                                                String runMode,
                                                Integer lookbackDays,
                                                String query) {
        StrategyBotRun.RunMode scopedRunMode = normalizeBoardRunMode(runMode);
        Integer normalizedLookbackDays = normalizeBoardLookbackDays(lookbackDays);
        String normalizedSort = normalizeBoardSort(sortBy);
        String normalizedDirection = normalizeBoardDirection(direction);
        String normalizedQuery = normalizeSearchQuery(query);
        List<StrategyBotBoardEntryResponse> entries = getCachedPublicBotBoardEntries(
                normalizedSort,
                normalizedDirection,
                scopedRunMode,
                normalizedLookbackDays,
                normalizedQuery);

        LinkedHashMap<String, Object> payload = buildBoardExportPayload(
                entries,
                normalizedSort,
                normalizedDirection,
                scopedRunMode,
                normalizedLookbackDays,
                normalizedQuery,
                "PUBLIC");
        return writePrettyJsonExport(payload, "Failed to serialize public strategy bot board export");
    }

    @Transactional(readOnly = true)
    public byte[] buildPublicBotBoardExportCsv(String sortBy,
                                               String direction,
                                               String runMode,
                                               Integer lookbackDays,
                                               String query) {
        StrategyBotRun.RunMode scopedRunMode = normalizeBoardRunMode(runMode);
        Integer normalizedLookbackDays = normalizeBoardLookbackDays(lookbackDays);
        String normalizedSort = normalizeBoardSort(sortBy);
        String normalizedDirection = normalizeBoardDirection(direction);
        String normalizedQuery = normalizeSearchQuery(query);
        List<StrategyBotBoardEntryResponse> entries = getCachedPublicBotBoardEntries(
                normalizedSort,
                normalizedDirection,
                scopedRunMode,
                normalizedLookbackDays,
                normalizedQuery);
        return buildBoardExportCsvBytes(
                entries,
                normalizedSort,
                normalizedDirection,
                scopedRunMode,
                normalizedLookbackDays,
                normalizedQuery,
                "PUBLIC");
    }

    @Transactional(readOnly = true)
    public String buildPublicBotDetailExportJson(UUID botId, String runMode, Integer lookbackDays) {
        StrategyBotRun.RunMode scopedRunMode = normalizeBoardRunMode(runMode);
        Integer normalizedLookbackDays = normalizeBoardLookbackDays(lookbackDays);
        PublicStrategyBotDetailResponse detail = getPublicBotDetail(botId, runMode, lookbackDays);

        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("strategyBotId", detail.getStrategyBotId());
        payload.put("name", detail.getName());
        payload.put("description", detail.getDescription());
        payload.put("botKind", detail.getBotKind());
        payload.put("status", detail.getStatus());
        payload.put("market", detail.getMarket());
        payload.put("symbol", detail.getSymbol());
        payload.put("timeframe", detail.getTimeframe());
        payload.put("linkedPortfolioId", detail.getLinkedPortfolioId());
        payload.put("linkedPortfolioName", detail.getLinkedPortfolioName());
        payload.put("ownerId", detail.getOwnerId());
        payload.put("ownerUsername", detail.getOwnerUsername());
        payload.put("ownerDisplayName", detail.getOwnerDisplayName());
        payload.put("ownerAvatarUrl", detail.getOwnerAvatarUrl());
        payload.put("ownerTrustScore", detail.getOwnerTrustScore());
        payload.put("maxPositionSizePercent", detail.getMaxPositionSizePercent());
        payload.put("stopLossPercent", detail.getStopLossPercent());
        payload.put("takeProfitPercent", detail.getTakeProfitPercent());
        payload.put("cooldownMinutes", detail.getCooldownMinutes());
        payload.put("runModeScope", scopedRunMode == null ? "ALL" : scopedRunMode.name());
        payload.put("lookbackDays", normalizedLookbackDays);
        payload.put("exportedAt", LocalDateTime.now().toString());
        payload.put("entryRules", detail.getEntryRules());
        payload.put("exitRules", detail.getExitRules());
        payload.put("analytics", detail.getAnalytics());
        return writePrettyJsonExport(payload, "Failed to serialize public strategy bot detail export");
    }

    @Transactional(readOnly = true)
    public byte[] buildPublicBotDetailExportCsv(UUID botId, String runMode, Integer lookbackDays) {
        StrategyBotRun.RunMode scopedRunMode = normalizeBoardRunMode(runMode);
        Integer normalizedLookbackDays = normalizeBoardLookbackDays(lookbackDays);
        PublicStrategyBotDetailResponse detail = getPublicBotDetail(botId, runMode, lookbackDays);
        StrategyBotAnalyticsResponse analytics = detail.getAnalytics();

        List<List<Object>> rows = new ArrayList<>();
        rows.add(List.of(
                "section",
                "key",
                "value",
                "runId",
                "runMode",
                "status",
                "requestedAt",
                "completedAt",
                "returnPercent",
                "netPnl",
                "maxDrawdownPercent",
                "winRate",
                "tradeCount",
                "profitFactor",
                "expectancyPerTrade",
                "timeInMarketPercent",
                "linkedPortfolioAligned",
                "executionEngineReady",
                "lastEvaluatedOpenTime",
                "errorMessage"));

        addMetricRow(rows, "context", "strategyBotId", detail.getStrategyBotId());
        addMetricRow(rows, "context", "name", detail.getName());
        addMetricRow(rows, "context", "description", detail.getDescription());
        addMetricRow(rows, "context", "botKind", detail.getBotKind());
        addMetricRow(rows, "context", "status", detail.getStatus());
        addMetricRow(rows, "context", "market", detail.getMarket());
        addMetricRow(rows, "context", "symbol", detail.getSymbol());
        addMetricRow(rows, "context", "timeframe", detail.getTimeframe());
        addMetricRow(rows, "context", "linkedPortfolioId", detail.getLinkedPortfolioId());
        addMetricRow(rows, "context", "linkedPortfolioName", detail.getLinkedPortfolioName());
        addMetricRow(rows, "context", "ownerId", detail.getOwnerId());
        addMetricRow(rows, "context", "ownerUsername", detail.getOwnerUsername());
        addMetricRow(rows, "context", "ownerDisplayName", detail.getOwnerDisplayName());
        addMetricRow(rows, "context", "ownerTrustScore", detail.getOwnerTrustScore());
        addMetricRow(rows, "context", "maxPositionSizePercent", detail.getMaxPositionSizePercent());
        addMetricRow(rows, "context", "stopLossPercent", detail.getStopLossPercent());
        addMetricRow(rows, "context", "takeProfitPercent", detail.getTakeProfitPercent());
        addMetricRow(rows, "context", "cooldownMinutes", detail.getCooldownMinutes());
        addMetricRow(rows, "context", "runModeScope", scopedRunMode == null ? "ALL" : scopedRunMode.name());
        addMetricRow(rows, "context", "lookbackDays", normalizedLookbackDays);
        addMetricRow(rows, "context", "exportedAt", LocalDateTime.now());
        addMetricRow(rows, "rules", "entryRules", stringifyJson(detail.getEntryRules()));
        addMetricRow(rows, "rules", "exitRules", stringifyJson(detail.getExitRules()));

        addMetricRow(rows, "summary", "totalRuns", analytics.getTotalRuns());
        addMetricRow(rows, "summary", "backtestRuns", analytics.getBacktestRuns());
        addMetricRow(rows, "summary", "forwardTestRuns", analytics.getForwardTestRuns());
        addMetricRow(rows, "summary", "completedRuns", analytics.getCompletedRuns());
        addMetricRow(rows, "summary", "runningRuns", analytics.getRunningRuns());
        addMetricRow(rows, "summary", "failedRuns", analytics.getFailedRuns());
        addMetricRow(rows, "summary", "cancelledRuns", analytics.getCancelledRuns());
        addMetricRow(rows, "summary", "compilerReadyRuns", analytics.getCompilerReadyRuns());
        addMetricRow(rows, "summary", "positiveCompletedRuns", analytics.getPositiveCompletedRuns());
        addMetricRow(rows, "summary", "negativeCompletedRuns", analytics.getNegativeCompletedRuns());
        addMetricRow(rows, "summary", "totalSimulatedTrades", analytics.getTotalSimulatedTrades());
        addMetricRow(rows, "summary", "avgReturnPercent", analytics.getAvgReturnPercent());
        addMetricRow(rows, "summary", "avgNetPnl", analytics.getAvgNetPnl());
        addMetricRow(rows, "summary", "avgMaxDrawdownPercent", analytics.getAvgMaxDrawdownPercent());
        addMetricRow(rows, "summary", "avgWinRate", analytics.getAvgWinRate());
        addMetricRow(rows, "summary", "avgTradeCount", analytics.getAvgTradeCount());
        addMetricRow(rows, "summary", "avgProfitFactor", analytics.getAvgProfitFactor());
        addMetricRow(rows, "summary", "avgExpectancyPerTrade", analytics.getAvgExpectancyPerTrade());

        addScorecardRow(rows, "highlightRun", "bestRun", analytics.getBestRun());
        addScorecardRow(rows, "highlightRun", "worstRun", analytics.getWorstRun());
        addScorecardRow(rows, "highlightRun", "latestCompletedRun", analytics.getLatestCompletedRun());
        addScorecardRow(rows, "highlightRun", "activeForwardRun", analytics.getActiveForwardRun());

        addReasonRows(rows, "entryDriver", analytics.getEntryDriverTotals());
        addReasonRows(rows, "exitDriver", analytics.getExitDriverTotals());

        if (analytics.getRecentScorecards() != null) {
            for (StrategyBotRunScorecardResponse scorecard : analytics.getRecentScorecards()) {
                addScorecardRow(rows, "recentScorecard", "recent", scorecard);
            }
        }

        String content = rows.stream()
                .map(row -> row.stream().map(this::escapeCsv).reduce((left, right) -> left + "," + right).orElse(""))
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
        return content.getBytes(StandardCharsets.UTF_8);
    }

    @Transactional(readOnly = true)
    public String buildBotBoardExportJson(UUID userId,
                                          String sortBy,
                                          String direction,
                                          String runMode,
                                          Integer lookbackDays) {
        StrategyBotRun.RunMode scopedRunMode = normalizeBoardRunMode(runMode);
        Integer normalizedLookbackDays = normalizeBoardLookbackDays(lookbackDays);
        String normalizedSort = normalizeBoardSort(sortBy);
        String normalizedDirection = normalizeBoardDirection(direction);
        List<StrategyBotBoardEntryResponse> entries = getCachedOwnedBotBoardEntries(
                userId,
                normalizedSort,
                normalizedDirection,
                scopedRunMode,
                normalizedLookbackDays);
        LinkedHashMap<String, Object> payload = buildBoardExportPayload(
                entries,
                normalizedSort,
                normalizedDirection,
                scopedRunMode,
                normalizedLookbackDays,
                null,
                "PRIVATE");
        return writePrettyJsonExport(payload, "Failed to serialize strategy bot board export");
    }

    @Transactional(readOnly = true)
    public byte[] buildBotBoardExportCsv(UUID userId,
                                         String sortBy,
                                         String direction,
                                         String runMode,
                                         Integer lookbackDays) {
        StrategyBotRun.RunMode scopedRunMode = normalizeBoardRunMode(runMode);
        Integer normalizedLookbackDays = normalizeBoardLookbackDays(lookbackDays);
        String normalizedSort = normalizeBoardSort(sortBy);
        String normalizedDirection = normalizeBoardDirection(direction);
        List<StrategyBotBoardEntryResponse> entries = getCachedOwnedBotBoardEntries(
                userId,
                normalizedSort,
                normalizedDirection,
                scopedRunMode,
                normalizedLookbackDays);
        return buildBoardExportCsvBytes(
                entries,
                normalizedSort,
                normalizedDirection,
                scopedRunMode,
                normalizedLookbackDays,
                null,
                "PRIVATE");
    }

    @Transactional(readOnly = true)
    public String buildBotAnalyticsExportJson(UUID botId, UUID userId, String runMode, Integer lookbackDays) {
        StrategyBot bot = strategyBotService.getOwnedBotEntity(botId, userId);
        StrategyBotRun.RunMode scopedRunMode = normalizeBoardRunMode(runMode);
        Integer normalizedLookbackDays = normalizeBoardLookbackDays(lookbackDays);
        StrategyBotAnalyticsResponse analytics = getCachedBotAnalytics(bot, userId, scopedRunMode, normalizedLookbackDays);
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("strategyBotId", bot.getId());
        payload.put("name", bot.getName());
        payload.put("description", bot.getDescription());
        payload.put("market", bot.getMarket());
        payload.put("symbol", bot.getSymbol());
        payload.put("timeframe", bot.getTimeframe());
        payload.put("status", bot.getStatus().name());
        payload.put("linkedPortfolioId", bot.getLinkedPortfolioId());
        payload.put("runModeScope", scopedRunMode == null ? "ALL" : scopedRunMode.name());
        payload.put("lookbackDays", normalizedLookbackDays);
        payload.put("exportedAt", LocalDateTime.now().toString());
        payload.put("analytics", analytics);
        try {
            return objectMapper.copy()
                    .findAndRegisterModules()
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize strategy bot analytics export", ex);
        }
    }

    @Transactional(readOnly = true)
    public byte[] buildBotAnalyticsExportCsv(UUID botId, UUID userId, String runMode, Integer lookbackDays) {
        StrategyBot bot = strategyBotService.getOwnedBotEntity(botId, userId);
        StrategyBotRun.RunMode scopedRunMode = normalizeBoardRunMode(runMode);
        Integer normalizedLookbackDays = normalizeBoardLookbackDays(lookbackDays);
        StrategyBotAnalyticsResponse analytics = getCachedBotAnalytics(bot, userId, scopedRunMode, normalizedLookbackDays);

        List<List<Object>> rows = new ArrayList<>();
        rows.add(List.of(
                "section",
                "key",
                "value",
                "runId",
                "runMode",
                "status",
                "requestedAt",
                "completedAt",
                "returnPercent",
                "netPnl",
                "maxDrawdownPercent",
                "winRate",
                "tradeCount",
                "profitFactor",
                "expectancyPerTrade",
                "timeInMarketPercent",
                "linkedPortfolioAligned",
                "executionEngineReady",
                "lastEvaluatedOpenTime",
                "errorMessage"));

        addMetricRow(rows, "context", "strategyBotId", bot.getId());
        addMetricRow(rows, "context", "name", bot.getName());
        addMetricRow(rows, "context", "description", bot.getDescription());
        addMetricRow(rows, "context", "market", bot.getMarket());
        addMetricRow(rows, "context", "symbol", bot.getSymbol());
        addMetricRow(rows, "context", "timeframe", bot.getTimeframe());
        addMetricRow(rows, "context", "status", bot.getStatus().name());
        addMetricRow(rows, "context", "linkedPortfolioId", bot.getLinkedPortfolioId());
        addMetricRow(rows, "context", "runModeScope", scopedRunMode == null ? "ALL" : scopedRunMode.name());
        addMetricRow(rows, "context", "lookbackDays", normalizedLookbackDays);
        addMetricRow(rows, "context", "exportedAt", LocalDateTime.now());

        addMetricRow(rows, "summary", "totalRuns", analytics.getTotalRuns());
        addMetricRow(rows, "summary", "backtestRuns", analytics.getBacktestRuns());
        addMetricRow(rows, "summary", "forwardTestRuns", analytics.getForwardTestRuns());
        addMetricRow(rows, "summary", "completedRuns", analytics.getCompletedRuns());
        addMetricRow(rows, "summary", "runningRuns", analytics.getRunningRuns());
        addMetricRow(rows, "summary", "failedRuns", analytics.getFailedRuns());
        addMetricRow(rows, "summary", "cancelledRuns", analytics.getCancelledRuns());
        addMetricRow(rows, "summary", "compilerReadyRuns", analytics.getCompilerReadyRuns());
        addMetricRow(rows, "summary", "positiveCompletedRuns", analytics.getPositiveCompletedRuns());
        addMetricRow(rows, "summary", "negativeCompletedRuns", analytics.getNegativeCompletedRuns());
        addMetricRow(rows, "summary", "totalSimulatedTrades", analytics.getTotalSimulatedTrades());
        addMetricRow(rows, "summary", "avgReturnPercent", analytics.getAvgReturnPercent());
        addMetricRow(rows, "summary", "avgNetPnl", analytics.getAvgNetPnl());
        addMetricRow(rows, "summary", "avgMaxDrawdownPercent", analytics.getAvgMaxDrawdownPercent());
        addMetricRow(rows, "summary", "avgWinRate", analytics.getAvgWinRate());
        addMetricRow(rows, "summary", "avgTradeCount", analytics.getAvgTradeCount());
        addMetricRow(rows, "summary", "avgProfitFactor", analytics.getAvgProfitFactor());
        addMetricRow(rows, "summary", "avgExpectancyPerTrade", analytics.getAvgExpectancyPerTrade());

        addScorecardRow(rows, "highlightRun", "bestRun", analytics.getBestRun());
        addScorecardRow(rows, "highlightRun", "worstRun", analytics.getWorstRun());
        addScorecardRow(rows, "highlightRun", "latestCompletedRun", analytics.getLatestCompletedRun());
        addScorecardRow(rows, "highlightRun", "activeForwardRun", analytics.getActiveForwardRun());

        addReasonRows(rows, "entryDriver", analytics.getEntryDriverTotals());
        addReasonRows(rows, "exitDriver", analytics.getExitDriverTotals());

        if (analytics.getRecentScorecards() != null) {
            for (StrategyBotRunScorecardResponse scorecard : analytics.getRecentScorecards()) {
                addScorecardRow(rows, "recentScorecard", "recent", scorecard);
            }
        }

        String content = rows.stream()
                .map(row -> row.stream().map(this::escapeCsv).reduce((left, right) -> left + "," + right).orElse(""))
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
        return content.getBytes(StandardCharsets.UTF_8);
    }

    @Transactional(readOnly = true)
    public String buildRunExportJson(UUID botId, UUID runId, UUID userId) {
        StrategyBot bot = strategyBotService.getOwnedBotEntity(botId, userId);
        StrategyBotRun run = getOwnedRunEntity(botId, runId, userId);

        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("strategyBotId", bot.getId());
        payload.put("name", bot.getName());
        payload.put("description", bot.getDescription());
        payload.put("market", bot.getMarket());
        payload.put("symbol", bot.getSymbol());
        payload.put("timeframe", bot.getTimeframe());
        payload.put("status", bot.getStatus().name());
        payload.put("linkedPortfolioId", bot.getLinkedPortfolioId());
        payload.put("exportedAt", LocalDateTime.now().toString());
        payload.put("run", toResponse(run));
        payload.put("events", getRunEventRows(run));
        payload.put("fills", getRunFillRows(run));
        payload.put("equityCurve", getRunEquityPointRows(run));
        payload.put("reconciliationPlan", safeBuildRunReconciliation(bot, run));
        try {
            return objectMapper.copy()
                    .findAndRegisterModules()
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize strategy bot run export", ex);
        }
    }

    @Transactional(readOnly = true)
    public byte[] buildRunExportCsv(UUID botId, UUID runId, UUID userId) {
        StrategyBot bot = strategyBotService.getOwnedBotEntity(botId, userId);
        StrategyBotRun run = getOwnedRunEntity(botId, runId, userId);
        StrategyBotRunResponse runResponse = toResponse(run);
        List<StrategyBotRunEventResponse> events = getRunEventRows(run);
        List<StrategyBotRunFillResponse> fills = getRunFillRows(run);
        List<StrategyBotRunEquityPointResponse> equityPoints = getRunEquityPointRows(run);
        StrategyBotRunReconciliationResponse reconciliation = safeBuildRunReconciliation(bot, run);

        List<List<Object>> rows = new ArrayList<>();
        rows.add(List.of(
                "section",
                "key",
                "value",
                "runId",
                "runMode",
                "status",
                "requestedAt",
                "startedAt",
                "completedAt",
                "openTime",
                "sequenceNo",
                "side",
                "price",
                "quantity",
                "realizedPnl",
                "equity",
                "closePrice",
                "portfolioAligned",
                "warnings",
                "details"));

        addRunExportMetricRow(rows, "context", "strategyBotId", bot.getId());
        addRunExportMetricRow(rows, "context", "name", bot.getName());
        addRunExportMetricRow(rows, "context", "description", bot.getDescription());
        addRunExportMetricRow(rows, "context", "market", bot.getMarket());
        addRunExportMetricRow(rows, "context", "symbol", bot.getSymbol());
        addRunExportMetricRow(rows, "context", "timeframe", bot.getTimeframe());
        addRunExportMetricRow(rows, "context", "status", bot.getStatus().name());
        addRunExportMetricRow(rows, "context", "linkedPortfolioId", bot.getLinkedPortfolioId());
        addRunExportMetricRow(rows, "context", "exportedAt", LocalDateTime.now());

        addRunExportMetricRow(rows, "run", "runId", runResponse.getId());
        addRunExportMetricRow(rows, "run", "runMode", runResponse.getRunMode());
        addRunExportMetricRow(rows, "run", "status", runResponse.getStatus());
        addRunExportMetricRow(rows, "run", "requestedInitialCapital", runResponse.getRequestedInitialCapital());
        addRunExportMetricRow(rows, "run", "effectiveInitialCapital", runResponse.getEffectiveInitialCapital());
        addRunExportMetricRow(rows, "run", "fromDate", runResponse.getFromDate());
        addRunExportMetricRow(rows, "run", "toDate", runResponse.getToDate());
        addRunExportMetricRow(rows, "run", "requestedAt", runResponse.getRequestedAt());
        addRunExportMetricRow(rows, "run", "startedAt", runResponse.getStartedAt());
        addRunExportMetricRow(rows, "run", "completedAt", runResponse.getCompletedAt());
        addRunExportMetricRow(rows, "run", "errorMessage", runResponse.getErrorMessage());

        addRunExportSummaryMetric(rows, runResponse.getSummary(), "phase");
        addRunExportSummaryMetric(rows, runResponse.getSummary(), "executionEngineReady");
        addRunExportSummaryMetric(rows, runResponse.getSummary(), "fillCount");
        addRunExportSummaryMetric(rows, runResponse.getSummary(), "eventCount");
        addRunExportSummaryMetric(rows, runResponse.getSummary(), "endingEquity");
        addRunExportSummaryMetric(rows, runResponse.getSummary(), "netPnl");
        addRunExportSummaryMetric(rows, runResponse.getSummary(), "returnPercent");
        addRunExportSummaryMetric(rows, runResponse.getSummary(), "tradeCount");
        addRunExportSummaryMetric(rows, runResponse.getSummary(), "winCount");
        addRunExportSummaryMetric(rows, runResponse.getSummary(), "lossCount");
        addRunExportSummaryMetric(rows, runResponse.getSummary(), "maxDrawdownPercent");
        addRunExportSummaryMetric(rows, runResponse.getSummary(), "avgWinPnl");
        addRunExportSummaryMetric(rows, runResponse.getSummary(), "avgLossPnl");
        addRunExportSummaryMetric(rows, runResponse.getSummary(), "profitFactor");
        addRunExportSummaryMetric(rows, runResponse.getSummary(), "expectancyPerTrade");
        addRunExportSummaryMetric(rows, runResponse.getSummary(), "bestTradePnl");
        addRunExportSummaryMetric(rows, runResponse.getSummary(), "worstTradePnl");
        addRunExportSummaryMetric(rows, runResponse.getSummary(), "avgHoldHours");
        addRunExportSummaryMetric(rows, runResponse.getSummary(), "maxHoldHours");
        addRunExportSummaryMetric(rows, runResponse.getSummary(), "timeInMarketPercent");
        addRunExportSummaryMetric(rows, runResponse.getSummary(), "avgExposurePercent");
        addRunExportSummaryMetric(rows, runResponse.getSummary(), "linkedPortfolioName");
        addRunExportSummaryMetric(rows, runResponse.getSummary(), "linkedPortfolioBalance");
        addRunExportSummaryMetric(rows, runResponse.getSummary(), "linkedPortfolioReferenceEquity");
        addRunExportSummaryMetric(rows, runResponse.getSummary(), "linkedPortfolioDrift");
        addRunExportSummaryMetric(rows, runResponse.getSummary(), "linkedPortfolioDriftPercent");
        addRunExportSummaryMetric(rows, runResponse.getSummary(), "linkedPortfolioReconciliationBaseline");
        addRunExportSummaryMetric(rows, runResponse.getSummary(), "linkedPortfolioAligned");
        addRunExportSummaryMetric(rows, runResponse.getSummary(), "positionOpen");
        addRunExportSummaryMetric(rows, runResponse.getSummary(), "openQuantity");
        addRunExportSummaryMetric(rows, runResponse.getSummary(), "openEntryPrice");
        addRunExportSummaryMetric(rows, runResponse.getSummary(), "lastEvaluatedOpenTime");
        addRunExportMetricRow(rows, "summary", "supportedFeatures", joinJsonArray(runResponse.getSummary().path("supportedFeatures")));
        addRunExportMetricRow(rows, "summary", "unsupportedRules", joinJsonArray(runResponse.getSummary().path("unsupportedRules")));
        addRunExportMetricRow(rows, "summary", "warnings", joinJsonArray(runResponse.getSummary().path("warnings")));
        addReasonMetricRows(rows, "entryDriver", runResponse.getSummary().path("entryReasonCounts"));
        addReasonMetricRows(rows, "exitDriver", runResponse.getSummary().path("exitReasonCounts"));

        for (StrategyBotRunEventResponse event : events) {
            List<Object> row = new ArrayList<>();
            row.add("event");
            row.add(event.getPhase());
            row.add(event.getAction());
            row.add(runResponse.getId());
            row.add(runResponse.getRunMode());
            row.add(runResponse.getStatus());
            row.add(runResponse.getRequestedAt());
            row.add(runResponse.getStartedAt());
            row.add(runResponse.getCompletedAt());
            row.add(event.getOpenTime());
            row.add(event.getSequenceNo());
            row.add("");
            row.add(event.getClosePrice());
            row.add(event.getPositionQuantity());
            row.add("");
            row.add(event.getEquity());
            row.add("");
            row.add("");
            row.add("");
            row.add(writeCompactJson(Map.of(
                    "cashBalance", event.getCashBalance(),
                    "matchedRules", event.getMatchedRules(),
                    "details", event.getDetails())));
            rows.add(row);
        }

        for (StrategyBotRunFillResponse fill : fills) {
            List<Object> row = new ArrayList<>();
            row.add("fill");
            row.add("");
            row.add("");
            row.add(runResponse.getId());
            row.add(runResponse.getRunMode());
            row.add(runResponse.getStatus());
            row.add(runResponse.getRequestedAt());
            row.add(runResponse.getStartedAt());
            row.add(runResponse.getCompletedAt());
            row.add(fill.getOpenTime());
            row.add(fill.getSequenceNo());
            row.add(fill.getSide());
            row.add(fill.getPrice());
            row.add(fill.getQuantity());
            row.add(fill.getRealizedPnl());
            row.add("");
            row.add("");
            row.add("");
            row.add("");
            row.add(joinJsonArray(fill.getMatchedRules()));
            rows.add(row);
        }

        for (StrategyBotRunEquityPointResponse point : equityPoints) {
            List<Object> row = new ArrayList<>();
            row.add("equityPoint");
            row.add("");
            row.add("");
            row.add(runResponse.getId());
            row.add(runResponse.getRunMode());
            row.add(runResponse.getStatus());
            row.add(runResponse.getRequestedAt());
            row.add(runResponse.getStartedAt());
            row.add(runResponse.getCompletedAt());
            row.add(point.getOpenTime());
            row.add(point.getSequenceNo());
            row.add("");
            row.add("");
            row.add("");
            row.add("");
            row.add(point.getEquity());
            row.add(point.getClosePrice());
            row.add("");
            row.add("");
            row.add("");
            rows.add(row);
        }

        if (reconciliation != null) {
            addRunExportMetricRow(rows, "reconciliation", "linkedPortfolioId", reconciliation.getLinkedPortfolioId());
            addRunExportMetricRow(rows, "reconciliation", "linkedPortfolioName", reconciliation.getLinkedPortfolioName());
            addRunExportMetricRow(rows, "reconciliation", "symbol", reconciliation.getSymbol());
            addRunExportMetricRow(rows, "reconciliation", "runStatus", reconciliation.getRunStatus());
            addRunExportMetricRow(rows, "reconciliation", "targetPositionOpen", reconciliation.isTargetPositionOpen());
            addRunExportMetricRow(rows, "reconciliation", "targetQuantity", reconciliation.getTargetQuantity());
            addRunExportMetricRow(rows, "reconciliation", "targetAveragePrice", reconciliation.getTargetAveragePrice());
            addRunExportMetricRow(rows, "reconciliation", "targetLastPrice", reconciliation.getTargetLastPrice());
            addRunExportMetricRow(rows, "reconciliation", "targetCashBalance", reconciliation.getTargetCashBalance());
            addRunExportMetricRow(rows, "reconciliation", "targetEquity", reconciliation.getTargetEquity());
            addRunExportMetricRow(rows, "reconciliation", "currentCashBalance", reconciliation.getCurrentCashBalance());
            addRunExportMetricRow(rows, "reconciliation", "currentQuantity", reconciliation.getCurrentQuantity());
            addRunExportMetricRow(rows, "reconciliation", "currentAveragePrice", reconciliation.getCurrentAveragePrice());
            addRunExportMetricRow(rows, "reconciliation", "cashDelta", reconciliation.getCashDelta());
            addRunExportMetricRow(rows, "reconciliation", "quantityDelta", reconciliation.getQuantityDelta());
            addRunExportMetricRow(rows, "reconciliation", "cashAligned", reconciliation.isCashAligned());
            addRunExportMetricRow(rows, "reconciliation", "quantityAligned", reconciliation.isQuantityAligned());
            addRunExportMetricRow(rows, "reconciliation", "portfolioAligned", reconciliation.isPortfolioAligned());
            addRunExportMetricRow(rows, "reconciliation", "extraSymbolCount", reconciliation.getExtraSymbolCount());
            addRunExportMetricRow(rows, "reconciliation", "warnings", String.join(" | ", reconciliation.getWarnings()));
        }

        String content = rows.stream()
                .map(row -> row.stream().map(this::escapeCsv).reduce((left, right) -> left + "," + right).orElse(""))
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
        return content.getBytes(StandardCharsets.UTF_8);
    }

    private StrategyBotAnalyticsResponse buildBotAnalytics(StrategyBot bot, UUID userId) {
        return buildBotAnalytics(bot, userId, null, null);
    }

    private StrategyBotAnalyticsResponse buildBotAnalytics(StrategyBot bot,
                                                           UUID userId,
                                                           StrategyBotRun.RunMode scopedRunMode,
                                                           Integer lookbackDays) {
        return buildBotAnalyticsFromSnapshots(
                bot.getId(),
                scopedRunMode,
                lookbackDays,
                () -> strategyBotRunRepository.findByStrategyBotIdAndUserIdOrderByRequestedAtDesc(bot.getId(), userId));
    }

    private StrategyBotAnalyticsResponse buildBotAnalytics(StrategyBot bot,
                                                           List<StrategyBotRun> rawRuns,
                                                           StrategyBotRun.RunMode scopedRunMode,
                                                           Integer lookbackDays) {
        return buildBotAnalytics(bot.getId(), rawRuns, scopedRunMode, lookbackDays);
    }

    private StrategyBotAnalyticsResponse buildBotAnalyticsFromSnapshots(UUID botId,
                                                                        StrategyBotRun.RunMode scopedRunMode,
                                                                        Integer lookbackDays,
                                                                        Supplier<List<StrategyBotRun>> fallbackRunLoader) {
        String runModeScope = toBoardRunModeScope(scopedRunMode);
        boolean lookbackActive = lookbackDays != null;
        LocalDateTime lookbackCutoff = resolveBoardLookbackCutoff(lookbackDays);
        List<UUID> botIds = List.of(botId);

        StrategyBotRunRepository.BoardAggregateView aggregate = defaultIfNull(
                        strategyBotRunRepository.findBoardAggregatesByStrategyBotIdIn(
                                botIds,
                                runModeScope,
                                lookbackActive,
                                lookbackCutoff))
                .stream()
                .findFirst()
                .orElse(null);
        Map<UUID, UUID> bestRunIds = indexSelectedRunIds(defaultIfNull(
                strategyBotRunRepository.findBestCompletedRunIdsByStrategyBotIdIn(
                        botIds,
                        runModeScope,
                        lookbackActive,
                        lookbackCutoff)));
        Map<UUID, UUID> worstRunIds = indexSelectedRunIds(defaultIfNull(
                strategyBotRunRepository.findWorstCompletedRunIdsByStrategyBotIdIn(
                        botIds,
                        runModeScope,
                        lookbackActive,
                        lookbackCutoff)));
        Map<UUID, UUID> latestCompletedRunIds = indexSelectedRunIds(defaultIfNull(
                strategyBotRunRepository.findLatestCompletedRunIdsByStrategyBotIdIn(
                        botIds,
                        runModeScope,
                        lookbackActive,
                        lookbackCutoff)));
        Map<UUID, UUID> activeForwardRunIds = indexSelectedRunIds(defaultIfNull(
                strategyBotRunRepository.findActiveForwardRunIdsByStrategyBotIdIn(
                        botIds,
                        runModeScope,
                        lookbackActive,
                        lookbackCutoff)));
        List<StrategyBotRunRepository.SelectedRunView> recentRunRows = defaultIfNull(
                strategyBotRunRepository.findRecentRunIdsByStrategyBotIdIn(
                        botIds,
                        runModeScope,
                        lookbackActive,
                        lookbackCutoff,
                        12));
        Map<String, Integer> entryDriverTotals = aggregateReasonCountsByBotId(defaultIfNull(
                        strategyBotRunRepository.findEntryReasonCountsByStrategyBotIdIn(
                                botIds,
                                runModeScope,
                                lookbackActive,
                                lookbackCutoff)))
                .getOrDefault(botId, Map.of());
        Map<String, Integer> exitDriverTotals = aggregateReasonCountsByBotId(defaultIfNull(
                        strategyBotRunRepository.findExitReasonCountsByStrategyBotIdIn(
                                botIds,
                                runModeScope,
                                lookbackActive,
                                lookbackCutoff)))
                .getOrDefault(botId, Map.of());

        if (aggregate == null
                && bestRunIds.isEmpty()
                && worstRunIds.isEmpty()
                && latestCompletedRunIds.isEmpty()
                && activeForwardRunIds.isEmpty()
                && recentRunRows.isEmpty()
                && entryDriverTotals.isEmpty()
                && exitDriverTotals.isEmpty()) {
            return buildBotAnalytics(botId, fallbackRunLoader.get(), scopedRunMode, lookbackDays);
        }

        LinkedHashSet<UUID> selectedRunIds = new LinkedHashSet<>();
        selectedRunIds.addAll(bestRunIds.values());
        selectedRunIds.addAll(worstRunIds.values());
        selectedRunIds.addAll(latestCompletedRunIds.values());
        selectedRunIds.addAll(activeForwardRunIds.values());
        recentRunRows.stream()
                .map(StrategyBotRunRepository.SelectedRunView::getId)
                .filter(Objects::nonNull)
                .forEach(selectedRunIds::add);

        Map<UUID, StrategyBotRun> selectedRunsById = selectedRunIds.isEmpty()
                ? Map.of()
                : defaultIfNull(strategyBotRunRepository.findByIdIn(selectedRunIds))
                .stream()
                .collect(LinkedHashMap::new,
                        (map, run) -> map.put(run.getId(), run),
                        LinkedHashMap::putAll);

        List<StrategyBotRunScorecardResponse> recentScorecards = recentRunRows.stream()
                .map(StrategyBotRunRepository.SelectedRunView::getId)
                .map(selectedRunsById::get)
                .filter(Objects::nonNull)
                .map(this::toScorecard)
                .toList();

        return StrategyBotAnalyticsResponse.builder()
                .strategyBotId(botId)
                .totalRuns(intValue(aggregate == null ? null : aggregate.getTotalRuns()))
                .backtestRuns(intValue(aggregate == null ? null : aggregate.getBacktestRuns()))
                .forwardTestRuns(intValue(aggregate == null ? null : aggregate.getForwardTestRuns()))
                .completedRuns(intValue(aggregate == null ? null : aggregate.getCompletedRuns()))
                .runningRuns(intValue(aggregate == null ? null : aggregate.getRunningRuns()))
                .failedRuns(intValue(aggregate == null ? null : aggregate.getFailedRuns()))
                .cancelledRuns(intValue(aggregate == null ? null : aggregate.getCancelledRuns()))
                .compilerReadyRuns(intValue(aggregate == null ? null : aggregate.getCompilerReadyRuns()))
                .positiveCompletedRuns(intValue(aggregate == null ? null : aggregate.getPositiveCompletedRuns()))
                .negativeCompletedRuns(intValue(aggregate == null ? null : aggregate.getNegativeCompletedRuns()))
                .totalSimulatedTrades(intValue(aggregate == null ? null : aggregate.getTotalSimulatedTrades()))
                .avgReturnPercent(roundNullable(aggregate == null ? null : aggregate.getAvgReturnPercent()))
                .avgNetPnl(roundNullable(aggregate == null ? null : aggregate.getAvgNetPnl()))
                .avgMaxDrawdownPercent(roundNullable(aggregate == null ? null : aggregate.getAvgMaxDrawdownPercent()))
                .avgWinRate(roundNullable(aggregate == null ? null : aggregate.getAvgWinRate()))
                .avgTradeCount(roundNullable(aggregate == null ? null : aggregate.getAvgTradeCount()))
                .avgProfitFactor(roundNullable(aggregate == null ? null : aggregate.getAvgProfitFactor()))
                .avgExpectancyPerTrade(roundNullable(aggregate == null ? null : aggregate.getAvgExpectancyPerTrade()))
                .bestRun(toScorecard(selectedRunsById.get(bestRunIds.get(botId))))
                .worstRun(toScorecard(selectedRunsById.get(worstRunIds.get(botId))))
                .latestCompletedRun(toScorecard(selectedRunsById.get(latestCompletedRunIds.get(botId))))
                .activeForwardRun(toScorecard(selectedRunsById.get(activeForwardRunIds.get(botId))))
                .entryDriverTotals(entryDriverTotals)
                .exitDriverTotals(exitDriverTotals)
                .recentScorecards(recentScorecards)
                .build();
    }

    private StrategyBotAnalyticsResponse buildBotAnalytics(UUID botId,
                                                           List<StrategyBotRun> rawRuns,
                                                           StrategyBotRun.RunMode scopedRunMode,
                                                           Integer lookbackDays) {
        List<StrategyBotRun> runs = filterRuns(rawRuns, scopedRunMode, lookbackDays);
        List<StrategyBotRunScorecardResponse> scorecards = runs.stream()
                .map(this::toScorecard)
                .toList();
        List<StrategyBotRunScorecardResponse> completedScorecards = scorecards.stream()
                .filter(scorecard -> "COMPLETED".equals(scorecard.getStatus()))
                .toList();

        return StrategyBotAnalyticsResponse.builder()
                .strategyBotId(botId)
                .totalRuns(runs.size())
                .backtestRuns((int) runs.stream().filter(run -> run.getRunMode() == StrategyBotRun.RunMode.BACKTEST).count())
                .forwardTestRuns((int) runs.stream().filter(run -> run.getRunMode() == StrategyBotRun.RunMode.FORWARD_TEST).count())
                .completedRuns((int) runs.stream().filter(run -> run.getStatus() == StrategyBotRun.Status.COMPLETED).count())
                .runningRuns((int) runs.stream().filter(run -> run.getStatus() == StrategyBotRun.Status.RUNNING).count())
                .failedRuns((int) runs.stream().filter(run -> run.getStatus() == StrategyBotRun.Status.FAILED).count())
                .cancelledRuns((int) runs.stream().filter(run -> run.getStatus() == StrategyBotRun.Status.CANCELLED).count())
                .compilerReadyRuns((int) scorecards.stream().filter(scorecard -> Boolean.TRUE.equals(scorecard.getExecutionEngineReady())).count())
                .positiveCompletedRuns((int) completedScorecards.stream().filter(scorecard -> scorecard.getReturnPercent() != null && scorecard.getReturnPercent() > 0.0).count())
                .negativeCompletedRuns((int) completedScorecards.stream().filter(scorecard -> scorecard.getReturnPercent() != null && scorecard.getReturnPercent() < 0.0).count())
                .totalSimulatedTrades(completedScorecards.stream()
                        .map(StrategyBotRunScorecardResponse::getTradeCount)
                        .filter(Objects::nonNull)
                        .mapToInt(Integer::intValue)
                        .sum())
                .avgReturnPercent(averageDouble(completedScorecards, StrategyBotRunScorecardResponse::getReturnPercent))
                .avgNetPnl(averageDouble(completedScorecards, StrategyBotRunScorecardResponse::getNetPnl))
                .avgMaxDrawdownPercent(averageDouble(completedScorecards, StrategyBotRunScorecardResponse::getMaxDrawdownPercent))
                .avgWinRate(averageDouble(completedScorecards, StrategyBotRunScorecardResponse::getWinRate))
                .avgTradeCount(averageDouble(completedScorecards, scorecard -> scorecard.getTradeCount() == null ? null : scorecard.getTradeCount().doubleValue()))
                .avgProfitFactor(averageDouble(completedScorecards, StrategyBotRunScorecardResponse::getProfitFactor))
                .avgExpectancyPerTrade(averageDouble(completedScorecards, StrategyBotRunScorecardResponse::getExpectancyPerTrade))
                .bestRun(maxBy(completedScorecards, StrategyBotRunScorecardResponse::getReturnPercent))
                .worstRun(minBy(completedScorecards, StrategyBotRunScorecardResponse::getReturnPercent))
                .latestCompletedRun(completedScorecards.isEmpty() ? null : completedScorecards.get(0))
                .activeForwardRun(scorecards.stream()
                        .filter(scorecard -> "FORWARD_TEST".equals(scorecard.getRunMode()) && "RUNNING".equals(scorecard.getStatus()))
                        .findFirst()
                        .orElse(null))
                .entryDriverTotals(aggregateReasonCounts(runs, "entryReasonCounts"))
                .exitDriverTotals(aggregateReasonCounts(runs, "exitReasonCounts"))
                .recentScorecards(scorecards.stream().limit(12).toList())
                .build();
    }

    private List<StrategyBotBoardEntryResponse> buildBotBoardEntries(UUID userId,
                                                                     String sortBy,
                                                                     String direction,
                                                                     StrategyBotRun.RunMode scopedRunMode,
                                                                     Integer lookbackDays) {
        ensureUserExists(userId);
        List<StrategyBot> bots = strategyBotRepository.findByUserId(userId, Pageable.unpaged())
                .stream()
                .toList();
        if (bots.isEmpty()) {
            return List.of();
        }

        List<UUID> botIds = bots.stream().map(StrategyBot::getId).toList();
        Map<UUID, BoardAnalyticsSnapshot> snapshots = loadBoardAnalyticsSnapshots(
                botIds,
                scopedRunMode,
                lookbackDays,
                () -> loadOwnedRunsByBotId(botIds, userId));

        return bots.stream()
                .map(bot -> toBoardEntry(
                        bot,
                        snapshots.getOrDefault(bot.getId(), defaultBoardAnalyticsSnapshot())))
                .sorted(resolveBoardComparator(sortBy, direction))
                .toList();
    }

    private Page<StrategyBotBoardEntryResponse> buildOwnedBotBoardPageFast(UUID userId,
                                                                           Pageable pageable,
                                                                           String sortBy,
                                                                           String direction,
                                                                           StrategyBotRun.RunMode scopedRunMode,
                                                                           Integer lookbackDays) {
        ensureUserExists(userId);
        Page<StrategyBot> page = loadOwnedBoardBotsPageFast(
                userId,
                pageable,
                sortBy,
                direction,
                scopedRunMode,
                lookbackDays);
        if (page.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, page.getTotalElements());
        }

        List<StrategyBot> bots = page.getContent();
        Map<UUID, BoardAnalyticsSnapshot> snapshots = loadBoardAnalyticsSnapshots(
                bots.stream().map(StrategyBot::getId).toList(),
                scopedRunMode,
                lookbackDays,
                () -> loadOwnedRunsByBotId(bots.stream().map(StrategyBot::getId).toList(), userId));
        List<StrategyBotBoardEntryResponse> entries = bots.stream()
                .map(bot -> toBoardEntry(
                        bot,
                        snapshots.getOrDefault(bot.getId(), defaultBoardAnalyticsSnapshot())))
                .toList();
        return new PageImpl<>(entries, pageable, page.getTotalElements());
    }

    private List<StrategyBotBoardEntryResponse> buildPublicBotBoardEntries(String sortBy,
                                                                           String direction,
                                                                           StrategyBotRun.RunMode scopedRunMode,
                                                                           Integer lookbackDays,
                                                                           String query) {
        List<StrategyBot> bots = strategyBotRepository.findPublicDiscoverableBots(
                Portfolio.Visibility.PUBLIC,
                StrategyBot.Status.DRAFT,
                normalizeSearchQuery(query));
        if (bots.isEmpty()) {
            return List.of();
        }

        Map<UUID, AppUser> ownersById = userRepository.findByIdIn(bots.stream()
                        .map(StrategyBot::getUserId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList())
                .stream()
                .collect(LinkedHashMap::new,
                        (map, user) -> map.put(user.getId(), user),
                        LinkedHashMap::putAll);

        Map<UUID, Portfolio> publicPortfoliosById = portfolioRepository.findByIdInAndVisibility(
                        bots.stream()
                                .map(StrategyBot::getLinkedPortfolioId)
                                .filter(Objects::nonNull)
                                .distinct()
                                .toList(),
                        Portfolio.Visibility.PUBLIC)
                .stream()
                .collect(LinkedHashMap::new,
                        (map, portfolio) -> map.put(portfolio.getId(), portfolio),
                        LinkedHashMap::putAll);

        List<UUID> botIds = bots.stream().map(StrategyBot::getId).toList();
        Map<UUID, BoardAnalyticsSnapshot> snapshots = loadBoardAnalyticsSnapshots(
                botIds,
                scopedRunMode,
                lookbackDays,
                () -> loadPublicRunsByBotId(botIds));

        return bots.stream()
                .map(bot -> toBoardEntry(
                        bot,
                        snapshots.getOrDefault(bot.getId(), defaultBoardAnalyticsSnapshot()),
                        ownersById.get(bot.getUserId()),
                        publicPortfoliosById.get(bot.getLinkedPortfolioId())))
                .sorted(resolveBoardComparator(sortBy, direction))
                .toList();
    }

    private Page<StrategyBotBoardEntryResponse> buildPublicBotBoardPageFast(Pageable pageable,
                                                                            String sortBy,
                                                                            String direction,
                                                                            String query,
                                                                            StrategyBotRun.RunMode scopedRunMode,
                                                                            Integer lookbackDays) {
        Page<StrategyBot> page = loadPublicBoardBotsPageFast(
                pageable,
                sortBy,
                direction,
                query,
                scopedRunMode,
                lookbackDays);
        if (page.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, page.getTotalElements());
        }

        List<StrategyBot> bots = page.getContent();
        Map<UUID, BoardAnalyticsSnapshot> snapshots = loadBoardAnalyticsSnapshots(
                bots.stream().map(StrategyBot::getId).toList(),
                scopedRunMode,
                lookbackDays,
                () -> loadPublicRunsByBotId(bots.stream().map(StrategyBot::getId).toList()));
        Map<UUID, AppUser> ownersById = userRepository.findByIdIn(bots.stream()
                        .map(StrategyBot::getUserId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList())
                .stream()
                .collect(LinkedHashMap::new,
                        (map, user) -> map.put(user.getId(), user),
                        LinkedHashMap::putAll);

        Map<UUID, Portfolio> publicPortfoliosById = portfolioRepository.findByIdInAndVisibility(
                        bots.stream()
                                .map(StrategyBot::getLinkedPortfolioId)
                                .filter(Objects::nonNull)
                                .distinct()
                                .toList(),
                        Portfolio.Visibility.PUBLIC)
                .stream()
                .collect(LinkedHashMap::new,
                        (map, portfolio) -> map.put(portfolio.getId(), portfolio),
                        LinkedHashMap::putAll);

        List<StrategyBotBoardEntryResponse> entries = bots.stream()
                .map(bot -> toBoardEntry(
                        bot,
                        snapshots.getOrDefault(bot.getId(), defaultBoardAnalyticsSnapshot()),
                        ownersById.get(bot.getUserId()),
                        publicPortfoliosById.get(bot.getLinkedPortfolioId())))
                .toList();
        return new PageImpl<>(entries, pageable, page.getTotalElements());
    }

    private List<StrategyBotRun> filterRuns(List<StrategyBotRun> runs,
                                            StrategyBotRun.RunMode scopedRunMode,
                                            Integer lookbackDays) {
        LocalDateTime cutoff = lookbackDays == null ? null : LocalDateTime.now().minusDays(lookbackDays.longValue());
        return runs.stream()
                .filter(run -> scopedRunMode == null || run.getRunMode() == scopedRunMode)
                .filter(run -> {
                    if (cutoff == null) {
                        return true;
                    }
                    LocalDateTime anchor = run.getCompletedAt() != null ? run.getCompletedAt() : run.getRequestedAt();
                    return anchor != null && !anchor.isBefore(cutoff);
                })
                .toList();
    }

    private Map<UUID, List<StrategyBotRun>> loadOwnedRunsByBotId(List<UUID> botIds, UUID userId) {
        if (botIds == null || botIds.isEmpty()) {
            return Map.of();
        }

        Map<UUID, List<StrategyBotRun>> runsByBotId = new HashMap<>();
        strategyBotRunRepository.findByStrategyBotIdInAndUserIdOrderByRequestedAtDesc(botIds, userId)
                .forEach(run -> runsByBotId
                        .computeIfAbsent(run.getStrategyBotId(), ignored -> new ArrayList<>())
                        .add(run));
        return runsByBotId;
    }

    private Map<UUID, List<StrategyBotRun>> loadPublicRunsByBotId(List<UUID> botIds) {
        if (botIds == null || botIds.isEmpty()) {
            return Map.of();
        }

        Map<UUID, List<StrategyBotRun>> runsByBotId = new HashMap<>();
        strategyBotRunRepository.findByStrategyBotIdInOrderByRequestedAtDesc(botIds)
                .forEach(run -> runsByBotId
                        .computeIfAbsent(run.getStrategyBotId(), ignored -> new ArrayList<>())
                        .add(run));
        return runsByBotId;
    }

    private Page<StrategyBot> loadOwnedBoardBotsPageFast(UUID userId,
                                                         Pageable pageable,
                                                         String sortBy,
                                                         String direction,
                                                         StrategyBotRun.RunMode scopedRunMode,
                                                         Integer lookbackDays) {
        String runModeScope = toBoardRunModeScope(scopedRunMode);
        boolean lookbackActive = lookbackDays != null;
        LocalDateTime lookbackCutoff = resolveBoardLookbackCutoff(lookbackDays);
        return switch (sortBy) {
            case "AVG_RETURN" -> loadBotsByIdPage(
                    hasScopedBoardFilters(scopedRunMode, lookbackDays)
                            ? ("ASC".equals(direction)
                            ? strategyBotRepository.findOwnedBotIdsOrderByScopedAvgReturnAsc(
                            userId,
                            runModeScope,
                            lookbackActive,
                            lookbackCutoff,
                            pageable)
                            : strategyBotRepository.findOwnedBotIdsOrderByScopedAvgReturnDesc(
                            userId,
                            runModeScope,
                            lookbackActive,
                            lookbackCutoff,
                            pageable))
                            : ("ASC".equals(direction)
                            ? strategyBotRepository.findOwnedBotIdsOrderByAvgReturnAsc(userId, pageable)
                            : strategyBotRepository.findOwnedBotIdsOrderByAvgReturnDesc(userId, pageable)),
                    pageable);
            case "AVG_NET_PNL" -> loadBotsByIdPage(
                    hasScopedBoardFilters(scopedRunMode, lookbackDays)
                            ? ("ASC".equals(direction)
                            ? strategyBotRepository.findOwnedBotIdsOrderByScopedAvgNetPnlAsc(
                            userId,
                            runModeScope,
                            lookbackActive,
                            lookbackCutoff,
                            pageable)
                            : strategyBotRepository.findOwnedBotIdsOrderByScopedAvgNetPnlDesc(
                            userId,
                            runModeScope,
                            lookbackActive,
                            lookbackCutoff,
                            pageable))
                            : ("ASC".equals(direction)
                            ? strategyBotRepository.findOwnedBotIdsOrderByAvgNetPnlAsc(userId, pageable)
                            : strategyBotRepository.findOwnedBotIdsOrderByAvgNetPnlDesc(userId, pageable)),
                    pageable);
            case "AVG_WIN_RATE" -> loadBotsByIdPage(
                    hasScopedBoardFilters(scopedRunMode, lookbackDays)
                            ? ("ASC".equals(direction)
                            ? strategyBotRepository.findOwnedBotIdsOrderByScopedAvgWinRateAsc(
                            userId,
                            runModeScope,
                            lookbackActive,
                            lookbackCutoff,
                            pageable)
                            : strategyBotRepository.findOwnedBotIdsOrderByScopedAvgWinRateDesc(
                            userId,
                            runModeScope,
                            lookbackActive,
                            lookbackCutoff,
                            pageable))
                            : ("ASC".equals(direction)
                            ? strategyBotRepository.findOwnedBotIdsOrderByAvgWinRateAsc(userId, pageable)
                            : strategyBotRepository.findOwnedBotIdsOrderByAvgWinRateDesc(userId, pageable)),
                    pageable);
            case "AVG_PROFIT_FACTOR" -> loadBotsByIdPage(
                    hasScopedBoardFilters(scopedRunMode, lookbackDays)
                            ? ("ASC".equals(direction)
                            ? strategyBotRepository.findOwnedBotIdsOrderByScopedAvgProfitFactorAsc(
                            userId,
                            runModeScope,
                            lookbackActive,
                            lookbackCutoff,
                            pageable)
                            : strategyBotRepository.findOwnedBotIdsOrderByScopedAvgProfitFactorDesc(
                            userId,
                            runModeScope,
                            lookbackActive,
                            lookbackCutoff,
                            pageable))
                            : ("ASC".equals(direction)
                            ? strategyBotRepository.findOwnedBotIdsOrderByAvgProfitFactorAsc(userId, pageable)
                            : strategyBotRepository.findOwnedBotIdsOrderByAvgProfitFactorDesc(userId, pageable)),
                    pageable);
            case "TOTAL_SIMULATED_TRADES" -> loadBotsByIdPage(
                    hasScopedBoardFilters(scopedRunMode, lookbackDays)
                            ? ("ASC".equals(direction)
                            ? strategyBotRepository.findOwnedBotIdsOrderByScopedTotalSimulatedTradesAsc(
                            userId,
                            runModeScope,
                            lookbackActive,
                            lookbackCutoff,
                            pageable)
                            : strategyBotRepository.findOwnedBotIdsOrderByScopedTotalSimulatedTradesDesc(
                            userId,
                            runModeScope,
                            lookbackActive,
                            lookbackCutoff,
                            pageable))
                            : ("ASC".equals(direction)
                            ? strategyBotRepository.findOwnedBotIdsOrderByTotalSimulatedTradesAsc(userId, pageable)
                            : strategyBotRepository.findOwnedBotIdsOrderByTotalSimulatedTradesDesc(userId, pageable)),
                    pageable);
            case "TOTAL_RUNS" -> hasScopedBoardFilters(scopedRunMode, lookbackDays)
                    ? loadBotsByIdPage(
                    "ASC".equals(direction)
                            ? strategyBotRepository.findOwnedBotIdsOrderByScopedRunCountAsc(
                            userId,
                            runModeScope,
                            lookbackActive,
                            lookbackCutoff,
                            pageable)
                            : strategyBotRepository.findOwnedBotIdsOrderByScopedRunCountDesc(
                            userId,
                            runModeScope,
                            lookbackActive,
                            lookbackCutoff,
                            pageable),
                    pageable)
                    : ("ASC".equals(direction)
                    ? strategyBotRepository.findByUserIdOrderByRunCountAsc(userId, pageable)
                    : strategyBotRepository.findByUserIdOrderByRunCountDesc(userId, pageable));
            case "LATEST_REQUESTED_AT" -> hasScopedBoardFilters(scopedRunMode, lookbackDays)
                    ? loadBotsByIdPage(
                    "ASC".equals(direction)
                            ? strategyBotRepository.findOwnedBotIdsOrderByScopedLatestRequestedAtAsc(
                            userId,
                            runModeScope,
                            lookbackActive,
                            lookbackCutoff,
                            pageable)
                            : strategyBotRepository.findOwnedBotIdsOrderByScopedLatestRequestedAtDesc(
                            userId,
                            runModeScope,
                            lookbackActive,
                            lookbackCutoff,
                            pageable),
                    pageable)
                    : ("ASC".equals(direction)
                    ? strategyBotRepository.findByUserIdOrderByLatestRequestedAtAsc(userId, pageable)
                    : strategyBotRepository.findByUserIdOrderByLatestRequestedAtDesc(userId, pageable));
            default -> throw ApiRequestException.badRequest("invalid_strategy_bot_board_sort", "Invalid strategy bot board sort");
        };
    }

    private Page<StrategyBot> loadPublicBoardBotsPageFast(Pageable pageable,
                                                          String sortBy,
                                                          String direction,
                                                          String query,
                                                          StrategyBotRun.RunMode scopedRunMode,
                                                          Integer lookbackDays) {
        String normalizedQuery = normalizeSearchQuery(query);
        String runModeScope = toBoardRunModeScope(scopedRunMode);
        boolean lookbackActive = lookbackDays != null;
        LocalDateTime lookbackCutoff = resolveBoardLookbackCutoff(lookbackDays);
        return switch (sortBy) {
            case "AVG_RETURN" -> loadBotsByIdPage(
                    hasScopedBoardFilters(scopedRunMode, lookbackDays)
                            ? ("ASC".equals(direction)
                            ? strategyBotRepository.findPublicDiscoverableBotIdsOrderByScopedAvgReturnAsc(
                                    Portfolio.Visibility.PUBLIC.name(),
                                    StrategyBot.Status.DRAFT.name(),
                                    normalizedQuery,
                                    runModeScope,
                                    lookbackActive,
                                    lookbackCutoff,
                                    pageable)
                            : strategyBotRepository.findPublicDiscoverableBotIdsOrderByScopedAvgReturnDesc(
                                    Portfolio.Visibility.PUBLIC.name(),
                                    StrategyBot.Status.DRAFT.name(),
                                    normalizedQuery,
                                    runModeScope,
                                    lookbackActive,
                                    lookbackCutoff,
                                    pageable))
                            : ("ASC".equals(direction)
                            ? strategyBotRepository.findPublicDiscoverableBotIdsOrderByAvgReturnAsc(
                                    Portfolio.Visibility.PUBLIC.name(),
                                    StrategyBot.Status.DRAFT.name(),
                                    normalizedQuery,
                                    pageable)
                            : strategyBotRepository.findPublicDiscoverableBotIdsOrderByAvgReturnDesc(
                                    Portfolio.Visibility.PUBLIC.name(),
                                    StrategyBot.Status.DRAFT.name(),
                                    normalizedQuery,
                                    pageable)),
                    pageable);
            case "AVG_NET_PNL" -> loadBotsByIdPage(
                    hasScopedBoardFilters(scopedRunMode, lookbackDays)
                            ? ("ASC".equals(direction)
                            ? strategyBotRepository.findPublicDiscoverableBotIdsOrderByScopedAvgNetPnlAsc(
                                    Portfolio.Visibility.PUBLIC.name(),
                                    StrategyBot.Status.DRAFT.name(),
                                    normalizedQuery,
                                    runModeScope,
                                    lookbackActive,
                                    lookbackCutoff,
                                    pageable)
                            : strategyBotRepository.findPublicDiscoverableBotIdsOrderByScopedAvgNetPnlDesc(
                                    Portfolio.Visibility.PUBLIC.name(),
                                    StrategyBot.Status.DRAFT.name(),
                                    normalizedQuery,
                                    runModeScope,
                                    lookbackActive,
                                    lookbackCutoff,
                                    pageable))
                            : ("ASC".equals(direction)
                            ? strategyBotRepository.findPublicDiscoverableBotIdsOrderByAvgNetPnlAsc(
                                    Portfolio.Visibility.PUBLIC.name(),
                                    StrategyBot.Status.DRAFT.name(),
                                    normalizedQuery,
                                    pageable)
                            : strategyBotRepository.findPublicDiscoverableBotIdsOrderByAvgNetPnlDesc(
                                    Portfolio.Visibility.PUBLIC.name(),
                                    StrategyBot.Status.DRAFT.name(),
                                    normalizedQuery,
                                    pageable)),
                    pageable);
            case "AVG_WIN_RATE" -> loadBotsByIdPage(
                    hasScopedBoardFilters(scopedRunMode, lookbackDays)
                            ? ("ASC".equals(direction)
                            ? strategyBotRepository.findPublicDiscoverableBotIdsOrderByScopedAvgWinRateAsc(
                                    Portfolio.Visibility.PUBLIC.name(),
                                    StrategyBot.Status.DRAFT.name(),
                                    normalizedQuery,
                                    runModeScope,
                                    lookbackActive,
                                    lookbackCutoff,
                                    pageable)
                            : strategyBotRepository.findPublicDiscoverableBotIdsOrderByScopedAvgWinRateDesc(
                                    Portfolio.Visibility.PUBLIC.name(),
                                    StrategyBot.Status.DRAFT.name(),
                                    normalizedQuery,
                                    runModeScope,
                                    lookbackActive,
                                    lookbackCutoff,
                                    pageable))
                            : ("ASC".equals(direction)
                            ? strategyBotRepository.findPublicDiscoverableBotIdsOrderByAvgWinRateAsc(
                                    Portfolio.Visibility.PUBLIC.name(),
                                    StrategyBot.Status.DRAFT.name(),
                                    normalizedQuery,
                                    pageable)
                            : strategyBotRepository.findPublicDiscoverableBotIdsOrderByAvgWinRateDesc(
                                    Portfolio.Visibility.PUBLIC.name(),
                                    StrategyBot.Status.DRAFT.name(),
                                    normalizedQuery,
                                    pageable)),
                    pageable);
            case "AVG_PROFIT_FACTOR" -> loadBotsByIdPage(
                    hasScopedBoardFilters(scopedRunMode, lookbackDays)
                            ? ("ASC".equals(direction)
                            ? strategyBotRepository.findPublicDiscoverableBotIdsOrderByScopedAvgProfitFactorAsc(
                                    Portfolio.Visibility.PUBLIC.name(),
                                    StrategyBot.Status.DRAFT.name(),
                                    normalizedQuery,
                                    runModeScope,
                                    lookbackActive,
                                    lookbackCutoff,
                                    pageable)
                            : strategyBotRepository.findPublicDiscoverableBotIdsOrderByScopedAvgProfitFactorDesc(
                                    Portfolio.Visibility.PUBLIC.name(),
                                    StrategyBot.Status.DRAFT.name(),
                                    normalizedQuery,
                                    runModeScope,
                                    lookbackActive,
                                    lookbackCutoff,
                                    pageable))
                            : ("ASC".equals(direction)
                            ? strategyBotRepository.findPublicDiscoverableBotIdsOrderByAvgProfitFactorAsc(
                                    Portfolio.Visibility.PUBLIC.name(),
                                    StrategyBot.Status.DRAFT.name(),
                                    normalizedQuery,
                                    pageable)
                            : strategyBotRepository.findPublicDiscoverableBotIdsOrderByAvgProfitFactorDesc(
                                    Portfolio.Visibility.PUBLIC.name(),
                                    StrategyBot.Status.DRAFT.name(),
                                    normalizedQuery,
                                    pageable)),
                    pageable);
            case "TOTAL_SIMULATED_TRADES" -> loadBotsByIdPage(
                    hasScopedBoardFilters(scopedRunMode, lookbackDays)
                            ? ("ASC".equals(direction)
                            ? strategyBotRepository.findPublicDiscoverableBotIdsOrderByScopedTotalSimulatedTradesAsc(
                                    Portfolio.Visibility.PUBLIC.name(),
                                    StrategyBot.Status.DRAFT.name(),
                                    normalizedQuery,
                                    runModeScope,
                                    lookbackActive,
                                    lookbackCutoff,
                                    pageable)
                            : strategyBotRepository.findPublicDiscoverableBotIdsOrderByScopedTotalSimulatedTradesDesc(
                                    Portfolio.Visibility.PUBLIC.name(),
                                    StrategyBot.Status.DRAFT.name(),
                                    normalizedQuery,
                                    runModeScope,
                                    lookbackActive,
                                    lookbackCutoff,
                                    pageable))
                            : ("ASC".equals(direction)
                            ? strategyBotRepository.findPublicDiscoverableBotIdsOrderByTotalSimulatedTradesAsc(
                                    Portfolio.Visibility.PUBLIC.name(),
                                    StrategyBot.Status.DRAFT.name(),
                                    normalizedQuery,
                                    pageable)
                            : strategyBotRepository.findPublicDiscoverableBotIdsOrderByTotalSimulatedTradesDesc(
                                    Portfolio.Visibility.PUBLIC.name(),
                                    StrategyBot.Status.DRAFT.name(),
                                    normalizedQuery,
                                    pageable)),
                    pageable);
            case "TOTAL_RUNS" -> hasScopedBoardFilters(scopedRunMode, lookbackDays)
                    ? loadBotsByIdPage(
                    "ASC".equals(direction)
                            ? strategyBotRepository.findPublicDiscoverableBotIdsOrderByScopedRunCountAsc(
                            Portfolio.Visibility.PUBLIC.name(),
                            StrategyBot.Status.DRAFT.name(),
                            normalizedQuery,
                            runModeScope,
                            lookbackActive,
                            lookbackCutoff,
                            pageable)
                            : strategyBotRepository.findPublicDiscoverableBotIdsOrderByScopedRunCountDesc(
                            Portfolio.Visibility.PUBLIC.name(),
                            StrategyBot.Status.DRAFT.name(),
                            normalizedQuery,
                            runModeScope,
                            lookbackActive,
                            lookbackCutoff,
                            pageable),
                    pageable)
                    : ("ASC".equals(direction)
                    ? strategyBotRepository.findPublicDiscoverableBotsOrderByRunCountAsc(
                            Portfolio.Visibility.PUBLIC,
                            StrategyBot.Status.DRAFT,
                            normalizedQuery,
                            pageable)
                    : strategyBotRepository.findPublicDiscoverableBotsOrderByRunCountDesc(
                            Portfolio.Visibility.PUBLIC,
                            StrategyBot.Status.DRAFT,
                            normalizedQuery,
                            pageable));
            case "LATEST_REQUESTED_AT" -> hasScopedBoardFilters(scopedRunMode, lookbackDays)
                    ? loadBotsByIdPage(
                    "ASC".equals(direction)
                            ? strategyBotRepository.findPublicDiscoverableBotIdsOrderByScopedLatestRequestedAtAsc(
                            Portfolio.Visibility.PUBLIC.name(),
                            StrategyBot.Status.DRAFT.name(),
                            normalizedQuery,
                            runModeScope,
                            lookbackActive,
                            lookbackCutoff,
                            pageable)
                            : strategyBotRepository.findPublicDiscoverableBotIdsOrderByScopedLatestRequestedAtDesc(
                            Portfolio.Visibility.PUBLIC.name(),
                            StrategyBot.Status.DRAFT.name(),
                            normalizedQuery,
                            runModeScope,
                            lookbackActive,
                            lookbackCutoff,
                            pageable),
                    pageable)
                    : ("ASC".equals(direction)
                    ? strategyBotRepository.findPublicDiscoverableBotsOrderByLatestRequestedAtAsc(
                            Portfolio.Visibility.PUBLIC,
                            StrategyBot.Status.DRAFT,
                            normalizedQuery,
                            pageable)
                    : strategyBotRepository.findPublicDiscoverableBotsOrderByLatestRequestedAtDesc(
                            Portfolio.Visibility.PUBLIC,
                            StrategyBot.Status.DRAFT,
                            normalizedQuery,
                            pageable));
            default -> throw ApiRequestException.badRequest("invalid_strategy_bot_board_sort", "Invalid strategy bot board sort");
        };
    }

    private boolean supportsPagedBoardFastPath(String sortBy,
                                               StrategyBotRun.RunMode scopedRunMode,
                                               Integer lookbackDays) {
        if ("AVG_RETURN".equals(sortBy)
                || "AVG_NET_PNL".equals(sortBy)
                || "AVG_WIN_RATE".equals(sortBy)
                || "AVG_PROFIT_FACTOR".equals(sortBy)
                || "TOTAL_RUNS".equals(sortBy)
                || "LATEST_REQUESTED_AT".equals(sortBy)
                || "TOTAL_SIMULATED_TRADES".equals(sortBy)) {
            return true;
        }
        return false;
    }

    private boolean hasScopedBoardFilters(StrategyBotRun.RunMode scopedRunMode, Integer lookbackDays) {
        return scopedRunMode != null || lookbackDays != null;
    }

    private String toBoardRunModeScope(StrategyBotRun.RunMode scopedRunMode) {
        return scopedRunMode == null ? "ALL" : scopedRunMode.name();
    }

    private LocalDateTime resolveBoardLookbackCutoff(Integer lookbackDays) {
        return lookbackDays == null
                ? LocalDateTime.of(1970, 1, 1, 0, 0)
                : LocalDateTime.now().minusDays(lookbackDays.longValue());
    }

    private Page<StrategyBot> loadBotsByIdPage(Page<UUID> idPage, Pageable pageable) {
        if (idPage.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, idPage.getTotalElements());
        }

        List<UUID> ids = idPage.getContent();
        Map<UUID, StrategyBot> botsById = strategyBotRepository.findAllById(ids)
                .stream()
                .collect(LinkedHashMap::new,
                        (map, bot) -> map.put(bot.getId(), bot),
                        LinkedHashMap::putAll);

        List<StrategyBot> orderedBots = ids.stream()
                .map(botsById::get)
                .filter(Objects::nonNull)
                .toList();
        return new PageImpl<>(orderedBots, pageable, idPage.getTotalElements());
    }

    private StrategyBotRun.RunMode normalizeBoardRunMode(String runMode) {
        if (runMode == null || runMode.isBlank() || "ALL".equalsIgnoreCase(runMode)) {
            return null;
        }
        try {
            return StrategyBotRun.RunMode.valueOf(runMode.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw ApiRequestException.badRequest("invalid_strategy_bot_board_run_mode", "Invalid strategy bot board run mode");
        }
    }

    private Integer normalizeBoardLookbackDays(Integer lookbackDays) {
        if (lookbackDays == null) {
            return null;
        }
        if (lookbackDays <= 0) {
            throw ApiRequestException.badRequest("invalid_strategy_bot_board_lookback", "Strategy bot board lookback days must be positive");
        }
        return lookbackDays;
    }

    private String normalizeBoardSort(String sortBy) {
        String normalizedSort = sortBy == null ? "AVG_RETURN" : sortBy.trim().toUpperCase(Locale.ROOT);
        return switch (normalizedSort) {
            case "AVG_RETURN",
                 "AVG_NET_PNL",
                 "AVG_WIN_RATE",
                 "AVG_PROFIT_FACTOR",
                 "TOTAL_RUNS",
                 "TOTAL_SIMULATED_TRADES",
                 "LATEST_REQUESTED_AT" -> normalizedSort;
            default -> throw ApiRequestException.badRequest("invalid_strategy_bot_board_sort", "Invalid strategy bot board sort");
        };
    }

    private String normalizeBoardDirection(String direction) {
        if (direction == null || direction.isBlank()) {
            return "DESC";
        }
        if ("ASC".equalsIgnoreCase(direction)) {
            return "ASC";
        }
        if ("DESC".equalsIgnoreCase(direction)) {
            return "DESC";
        }
        throw ApiRequestException.badRequest("invalid_strategy_bot_board_direction", "Invalid strategy bot board direction");
    }

    private String normalizeSearchQuery(String query) {
        if (query == null) {
            return "";
        }
        return query.trim();
    }

    private LinkedHashMap<String, Object> buildBoardExportPayload(List<StrategyBotBoardEntryResponse> entries,
                                                                  String normalizedSort,
                                                                  String normalizedDirection,
                                                                  StrategyBotRun.RunMode scopedRunMode,
                                                                  Integer normalizedLookbackDays,
                                                                  String normalizedQuery,
                                                                  String visibilityScope) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("visibilityScope", visibilityScope);
        payload.put("sortBy", normalizedSort);
        payload.put("direction", normalizedDirection);
        payload.put("runModeScope", scopedRunMode == null ? "ALL" : scopedRunMode.name());
        payload.put("lookbackDays", normalizedLookbackDays);
        payload.put("q", normalizedQuery == null || normalizedQuery.isBlank() ? null : normalizedQuery);
        payload.put("entryCount", entries.size());
        payload.put("exportedAt", LocalDateTime.now().toString());
        payload.put("entries", entries);
        return payload;
    }

    private byte[] buildBoardExportCsvBytes(List<StrategyBotBoardEntryResponse> entries,
                                            String normalizedSort,
                                            String normalizedDirection,
                                            StrategyBotRun.RunMode scopedRunMode,
                                            Integer normalizedLookbackDays,
                                            String normalizedQuery,
                                            String visibilityScope) {
        List<List<Object>> rows = new ArrayList<>();
        rows.add(List.of(
                "section",
                "key",
                "value",
                "strategyBotId",
                "name",
                "status",
                "market",
                "symbol",
                "timeframe",
                "totalRuns",
                "completedRuns",
                "runningRuns",
                "failedRuns",
                "cancelledRuns",
                "totalSimulatedTrades",
                "avgReturnPercent",
                "avgNetPnl",
                "avgMaxDrawdownPercent",
                "avgWinRate",
                "avgProfitFactor",
                "avgExpectancyPerTrade",
                "latestRequestedAt",
                "bestRunId",
                "latestCompletedRunId",
                "activeForwardRunId"));

        addBoardMetricRow(rows, "context", "visibilityScope", visibilityScope);
        addBoardMetricRow(rows, "context", "sortBy", normalizedSort);
        addBoardMetricRow(rows, "context", "direction", normalizedDirection);
        addBoardMetricRow(rows, "context", "runModeScope", scopedRunMode == null ? "ALL" : scopedRunMode.name());
        addBoardMetricRow(rows, "context", "lookbackDays", normalizedLookbackDays);
        addBoardMetricRow(rows, "context", "q", normalizedQuery == null || normalizedQuery.isBlank() ? "" : normalizedQuery);
        addBoardMetricRow(rows, "context", "entryCount", entries.size());
        addBoardMetricRow(rows, "context", "exportedAt", LocalDateTime.now());

        entries.forEach(entry -> addBoardEntryRow(rows, entry));

        String content = rows.stream()
                .map(row -> row.stream().map(this::escapeCsv).reduce((left, right) -> left + "," + right).orElse(""))
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
        return content.getBytes(StandardCharsets.UTF_8);
    }

    private String writePrettyJsonExport(Object payload, String errorMessage) {
        try {
            return objectMapper.copy()
                    .findAndRegisterModules()
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(errorMessage, ex);
        }
    }

    private String stringifyJson(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof JsonNode jsonNode) {
            return jsonNode.toString();
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return String.valueOf(value);
        }
    }

    private record CachedBoardPage(List<StrategyBotBoardEntryResponse> content, long totalElements) {
        private static CachedBoardPage from(Page<StrategyBotBoardEntryResponse> page) {
            return new CachedBoardPage(page.getContent(), page.getTotalElements());
        }
    }

    private record CachedBoardEntries(List<StrategyBotBoardEntryResponse> entries) {
        private static CachedBoardEntries from(List<StrategyBotBoardEntryResponse> entries) {
            return new CachedBoardEntries(entries == null ? List.of() : entries);
        }
    }

    private StrategyBotBoardEntryResponse toBoardEntry(StrategyBot bot, StrategyBotAnalyticsResponse analytics) {
        return toBoardEntry(bot, analytics, null, null);
    }

    private StrategyBotBoardEntryResponse toBoardEntry(StrategyBot bot, BoardAnalyticsSnapshot snapshot) {
        return toBoardEntry(bot, snapshot, null, null);
    }

    private StrategyBotBoardEntryResponse toBoardEntry(StrategyBot bot,
                                                       BoardAnalyticsSnapshot snapshot,
                                                       AppUser owner,
                                                       Portfolio linkedPortfolio) {
        return StrategyBotBoardEntryResponse.builder()
                .strategyBotId(bot.getId())
                .description(bot.getDescription())
                .name(bot.getName())
                .status(bot.getStatus().name())
                .market(bot.getMarket())
                .symbol(bot.getSymbol())
                .timeframe(bot.getTimeframe())
                .linkedPortfolioId(bot.getLinkedPortfolioId())
                .linkedPortfolioName(linkedPortfolio == null ? null : linkedPortfolio.getName())
                .ownerId(owner == null ? bot.getUserId() : owner.getId())
                .ownerUsername(owner == null ? null : owner.getUsername())
                .ownerDisplayName(owner == null ? null : owner.getDisplayName())
                .ownerAvatarUrl(owner == null ? null : owner.getAvatarUrl())
                .ownerTrustScore(owner == null ? null : round(owner.getTrustScore()))
                .totalRuns(snapshot.totalRuns())
                .completedRuns(snapshot.completedRuns())
                .runningRuns(snapshot.runningRuns())
                .failedRuns(snapshot.failedRuns())
                .cancelledRuns(snapshot.cancelledRuns())
                .totalSimulatedTrades(snapshot.totalSimulatedTrades())
                .positiveCompletedRuns(snapshot.positiveCompletedRuns())
                .negativeCompletedRuns(snapshot.negativeCompletedRuns())
                .avgReturnPercent(snapshot.avgReturnPercent())
                .avgNetPnl(snapshot.avgNetPnl())
                .avgMaxDrawdownPercent(snapshot.avgMaxDrawdownPercent())
                .avgWinRate(snapshot.avgWinRate())
                .avgProfitFactor(snapshot.avgProfitFactor())
                .avgExpectancyPerTrade(snapshot.avgExpectancyPerTrade())
                .latestRequestedAt(snapshot.latestRequestedAt())
                .bestRun(snapshot.bestRun())
                .latestCompletedRun(snapshot.latestCompletedRun())
                .activeForwardRun(snapshot.activeForwardRun())
                .build();
    }

    private StrategyBotBoardEntryResponse toBoardEntry(StrategyBot bot,
                                                       StrategyBotAnalyticsResponse analytics,
                                                       AppUser owner,
                                                       Portfolio linkedPortfolio) {
        LocalDateTime latestRequestedAt = analytics.getRecentScorecards() == null || analytics.getRecentScorecards().isEmpty()
                ? null
                : analytics.getRecentScorecards().get(0).getRequestedAt();
        return StrategyBotBoardEntryResponse.builder()
                .strategyBotId(bot.getId())
                .description(bot.getDescription())
                .name(bot.getName())
                .status(bot.getStatus().name())
                .market(bot.getMarket())
                .symbol(bot.getSymbol())
                .timeframe(bot.getTimeframe())
                .linkedPortfolioId(bot.getLinkedPortfolioId())
                .linkedPortfolioName(linkedPortfolio == null ? null : linkedPortfolio.getName())
                .ownerId(owner == null ? bot.getUserId() : owner.getId())
                .ownerUsername(owner == null ? null : owner.getUsername())
                .ownerDisplayName(owner == null ? null : owner.getDisplayName())
                .ownerAvatarUrl(owner == null ? null : owner.getAvatarUrl())
                .ownerTrustScore(owner == null ? null : round(owner.getTrustScore()))
                .totalRuns(analytics.getTotalRuns())
                .completedRuns(analytics.getCompletedRuns())
                .runningRuns(analytics.getRunningRuns())
                .failedRuns(analytics.getFailedRuns())
                .cancelledRuns(analytics.getCancelledRuns())
                .totalSimulatedTrades(analytics.getTotalSimulatedTrades())
                .positiveCompletedRuns(analytics.getPositiveCompletedRuns())
                .negativeCompletedRuns(analytics.getNegativeCompletedRuns())
                .avgReturnPercent(analytics.getAvgReturnPercent())
                .avgNetPnl(analytics.getAvgNetPnl())
                .avgMaxDrawdownPercent(analytics.getAvgMaxDrawdownPercent())
                .avgWinRate(analytics.getAvgWinRate())
                .avgProfitFactor(analytics.getAvgProfitFactor())
                .avgExpectancyPerTrade(analytics.getAvgExpectancyPerTrade())
                .latestRequestedAt(latestRequestedAt)
                .bestRun(analytics.getBestRun())
                .latestCompletedRun(analytics.getLatestCompletedRun())
                .activeForwardRun(analytics.getActiveForwardRun())
                .build();
    }

    private Map<UUID, BoardAnalyticsSnapshot> loadBoardAnalyticsSnapshots(List<UUID> botIds,
                                                                          StrategyBotRun.RunMode scopedRunMode,
                                                                          Integer lookbackDays,
                                                                          Supplier<Map<UUID, List<StrategyBotRun>>> fallbackRunLoader) {
        if (botIds == null || botIds.isEmpty()) {
            return Map.of();
        }

        String runModeScope = toBoardRunModeScope(scopedRunMode);
        boolean lookbackActive = lookbackDays != null;
        LocalDateTime lookbackCutoff = resolveBoardLookbackCutoff(lookbackDays);

        List<StrategyBotRunRepository.BoardAggregateView> aggregateRows = defaultIfNull(
                strategyBotRunRepository.findBoardAggregatesByStrategyBotIdIn(botIds, runModeScope, lookbackActive, lookbackCutoff));
        Map<UUID, StrategyBotRunRepository.BoardAggregateView> aggregates = aggregateRows
                .stream()
                .collect(LinkedHashMap::new,
                        (map, row) -> map.put(row.getStrategyBotId(), row),
                        LinkedHashMap::putAll);

        Map<UUID, UUID> bestRunIds = indexSelectedRunIds(defaultIfNull(strategyBotRunRepository.findBestCompletedRunIdsByStrategyBotIdIn(
                botIds,
                runModeScope,
                lookbackActive,
                lookbackCutoff)));
        Map<UUID, UUID> latestCompletedRunIds = indexSelectedRunIds(defaultIfNull(strategyBotRunRepository.findLatestCompletedRunIdsByStrategyBotIdIn(
                botIds,
                runModeScope,
                lookbackActive,
                lookbackCutoff)));
        Map<UUID, UUID> activeForwardRunIds = indexSelectedRunIds(defaultIfNull(strategyBotRunRepository.findActiveForwardRunIdsByStrategyBotIdIn(
                botIds,
                runModeScope,
                lookbackActive,
                lookbackCutoff)));

        if (aggregates.isEmpty()
                && bestRunIds.isEmpty()
                && latestCompletedRunIds.isEmpty()
                && activeForwardRunIds.isEmpty()
                && fallbackRunLoader != null) {
            return loadBoardAnalyticsSnapshotsFromRuns(botIds, scopedRunMode, lookbackDays, fallbackRunLoader.get());
        }

        Set<UUID> selectedRunIds = new LinkedHashSet<>();
        selectedRunIds.addAll(bestRunIds.values());
        selectedRunIds.addAll(latestCompletedRunIds.values());
        selectedRunIds.addAll(activeForwardRunIds.values());

        Map<UUID, StrategyBotRun> selectedRunsById = selectedRunIds.isEmpty()
                ? Map.of()
                : defaultIfNull(strategyBotRunRepository.findByIdIn(selectedRunIds)).stream()
                .collect(LinkedHashMap::new,
                        (map, run) -> map.put(run.getId(), run),
                        LinkedHashMap::putAll);

        Map<UUID, BoardAnalyticsSnapshot> snapshots = new LinkedHashMap<>();
        for (UUID botId : botIds) {
            StrategyBotRunRepository.BoardAggregateView aggregate = aggregates.get(botId);
            snapshots.put(botId, new BoardAnalyticsSnapshot(
                    intValue(aggregate == null ? null : aggregate.getTotalRuns()),
                    intValue(aggregate == null ? null : aggregate.getCompletedRuns()),
                    intValue(aggregate == null ? null : aggregate.getRunningRuns()),
                    intValue(aggregate == null ? null : aggregate.getFailedRuns()),
                    intValue(aggregate == null ? null : aggregate.getCancelledRuns()),
                    intValue(aggregate == null ? null : aggregate.getTotalSimulatedTrades()),
                    intValue(aggregate == null ? null : aggregate.getPositiveCompletedRuns()),
                    intValue(aggregate == null ? null : aggregate.getNegativeCompletedRuns()),
                    roundNullable(aggregate == null ? null : aggregate.getAvgReturnPercent()),
                    roundNullable(aggregate == null ? null : aggregate.getAvgNetPnl()),
                    roundNullable(aggregate == null ? null : aggregate.getAvgMaxDrawdownPercent()),
                    roundNullable(aggregate == null ? null : aggregate.getAvgWinRate()),
                    roundNullable(aggregate == null ? null : aggregate.getAvgProfitFactor()),
                    roundNullable(aggregate == null ? null : aggregate.getAvgExpectancyPerTrade()),
                    aggregate == null ? null : aggregate.getLatestRequestedAt(),
                    toScorecard(selectedRunsById.get(bestRunIds.get(botId))),
                    toScorecard(selectedRunsById.get(latestCompletedRunIds.get(botId))),
                    toScorecard(selectedRunsById.get(activeForwardRunIds.get(botId)))));
        }
        return snapshots;
    }

    private Map<UUID, BoardAnalyticsSnapshot> loadBoardAnalyticsSnapshotsFromRuns(List<UUID> botIds,
                                                                                  StrategyBotRun.RunMode scopedRunMode,
                                                                                  Integer lookbackDays,
                                                                                  Map<UUID, List<StrategyBotRun>> runsByBotId) {
        Map<UUID, BoardAnalyticsSnapshot> snapshots = new LinkedHashMap<>();
        Map<UUID, List<StrategyBotRun>> indexedRuns = runsByBotId == null ? Map.of() : runsByBotId;
        for (UUID botId : botIds) {
            snapshots.put(botId, snapshotFromAnalytics(
                    buildBotAnalytics(botId, indexedRuns.getOrDefault(botId, List.of()), scopedRunMode, lookbackDays)));
        }
        return snapshots;
    }

    private BoardAnalyticsSnapshot snapshotFromAnalytics(StrategyBotAnalyticsResponse analytics) {
        if (analytics == null) {
            return defaultBoardAnalyticsSnapshot();
        }
        LocalDateTime latestRequestedAt = analytics.getRecentScorecards() == null || analytics.getRecentScorecards().isEmpty()
                ? null
                : analytics.getRecentScorecards().get(0).getRequestedAt();
        return new BoardAnalyticsSnapshot(
                analytics.getTotalRuns(),
                analytics.getCompletedRuns(),
                analytics.getRunningRuns(),
                analytics.getFailedRuns(),
                analytics.getCancelledRuns(),
                analytics.getTotalSimulatedTrades(),
                analytics.getPositiveCompletedRuns(),
                analytics.getNegativeCompletedRuns(),
                analytics.getAvgReturnPercent(),
                analytics.getAvgNetPnl(),
                analytics.getAvgMaxDrawdownPercent(),
                analytics.getAvgWinRate(),
                analytics.getAvgProfitFactor(),
                analytics.getAvgExpectancyPerTrade(),
                latestRequestedAt,
                analytics.getBestRun(),
                analytics.getLatestCompletedRun(),
                analytics.getActiveForwardRun());
    }

    private Map<UUID, UUID> indexSelectedRunIds(List<StrategyBotRunRepository.SelectedRunView> rows) {
        Map<UUID, UUID> indexed = new LinkedHashMap<>();
        for (StrategyBotRunRepository.SelectedRunView row : rows) {
            if (row.getStrategyBotId() != null && row.getId() != null) {
                indexed.put(row.getStrategyBotId(), row.getId());
            }
        }
        return indexed;
    }

    private Map<UUID, Map<String, Integer>> aggregateReasonCountsByBotId(List<StrategyBotRunRepository.ReasonCountView> rows) {
        Map<UUID, Map<String, Integer>> totals = new LinkedHashMap<>();
        for (StrategyBotRunRepository.ReasonCountView row : rows) {
            if (row.getStrategyBotId() == null || row.getReason() == null || row.getReason().isBlank()) {
                continue;
            }
            totals.computeIfAbsent(row.getStrategyBotId(), ignored -> new LinkedHashMap<>())
                    .merge(row.getReason(), intValue(row.getCount()), Integer::sum);
        }
        return totals;
    }

    private BoardAnalyticsSnapshot defaultBoardAnalyticsSnapshot() {
        return new BoardAnalyticsSnapshot(0, 0, 0, 0, 0, 0, 0, 0, null, null, null, null, null, null, null, null, null, null);
    }

    private <T> List<T> defaultIfNull(List<T> rows) {
        return rows == null ? List.of() : rows;
    }

    private Comparator<StrategyBotBoardEntryResponse> resolveBoardComparator(String sortBy, String direction) {
        String normalizedSort = normalizeBoardSort(sortBy);
        boolean ascending = "ASC".equals(normalizeBoardDirection(direction));
        Comparator<StrategyBotBoardEntryResponse> comparator = switch (normalizedSort) {
            case "AVG_RETURN" -> Comparator.comparing(StrategyBotBoardEntryResponse::getAvgReturnPercent, nullableDoubleComparator(ascending));
            case "AVG_NET_PNL" -> Comparator.comparing(StrategyBotBoardEntryResponse::getAvgNetPnl, nullableDoubleComparator(ascending));
            case "AVG_WIN_RATE" -> Comparator.comparing(StrategyBotBoardEntryResponse::getAvgWinRate, nullableDoubleComparator(ascending));
            case "AVG_PROFIT_FACTOR" -> Comparator.comparing(StrategyBotBoardEntryResponse::getAvgProfitFactor, nullableDoubleComparator(ascending));
            case "TOTAL_RUNS" -> Comparator.comparing(StrategyBotBoardEntryResponse::getTotalRuns, ascending ? Comparator.naturalOrder() : Comparator.reverseOrder());
            case "TOTAL_SIMULATED_TRADES" -> Comparator.comparing(StrategyBotBoardEntryResponse::getTotalSimulatedTrades, ascending ? Comparator.naturalOrder() : Comparator.reverseOrder());
            case "LATEST_REQUESTED_AT" -> Comparator.comparing(StrategyBotBoardEntryResponse::getLatestRequestedAt, nullableDateTimeComparator(ascending));
            default -> throw ApiRequestException.badRequest("invalid_strategy_bot_board_sort", "Invalid strategy bot board sort");
        };

        return comparator
                .thenComparing(StrategyBotBoardEntryResponse::getName, Comparator.nullsLast(String::compareToIgnoreCase))
                .thenComparing(StrategyBotBoardEntryResponse::getStrategyBotId);
    }

    private Comparator<Double> nullableDoubleComparator(boolean ascending) {
        return Comparator.nullsLast(ascending ? Double::compareTo : Comparator.reverseOrder());
    }

    private Comparator<LocalDateTime> nullableDateTimeComparator(boolean ascending) {
        return Comparator.nullsLast(ascending ? LocalDateTime::compareTo : Comparator.reverseOrder());
    }

    private void addMetricRow(List<List<Object>> rows, String section, String key, Object value) {
        rows.add(List.of(section, key, value == null ? "" : value, "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", ""));
    }

    private void addBoardMetricRow(List<List<Object>> rows, String section, String key, Object value) {
        List<Object> row = new ArrayList<>();
        row.add(section);
        row.add(key);
        row.add(value == null ? "" : value);
        while (row.size() < 25) {
            row.add("");
        }
        rows.add(row);
    }

    private void addBoardEntryRow(List<List<Object>> rows, StrategyBotBoardEntryResponse entry) {
        List<Object> row = new ArrayList<>();
        row.add("boardEntry");
        row.add(entry.getName());
        row.add("");
        row.add(entry.getStrategyBotId());
        row.add(entry.getName());
        row.add(entry.getStatus());
        row.add(entry.getMarket());
        row.add(entry.getSymbol());
        row.add(entry.getTimeframe());
        row.add(entry.getTotalRuns());
        row.add(entry.getCompletedRuns());
        row.add(entry.getRunningRuns());
        row.add(entry.getFailedRuns());
        row.add(entry.getCancelledRuns());
        row.add(entry.getTotalSimulatedTrades());
        row.add(entry.getAvgReturnPercent());
        row.add(entry.getAvgNetPnl());
        row.add(entry.getAvgMaxDrawdownPercent());
        row.add(entry.getAvgWinRate());
        row.add(entry.getAvgProfitFactor());
        row.add(entry.getAvgExpectancyPerTrade());
        row.add(entry.getLatestRequestedAt());
        row.add(entry.getBestRun() == null ? "" : entry.getBestRun().getId());
        row.add(entry.getLatestCompletedRun() == null ? "" : entry.getLatestCompletedRun().getId());
        row.add(entry.getActiveForwardRun() == null ? "" : entry.getActiveForwardRun().getId());
        rows.add(row);
    }

    private void addRunExportMetricRow(List<List<Object>> rows, String section, String key, Object value) {
        List<Object> row = new ArrayList<>();
        row.add(section);
        row.add(key);
        row.add(value == null ? "" : value);
        while (row.size() < 20) {
            row.add("");
        }
        rows.add(row);
    }

    private void addRunExportSummaryMetric(List<List<Object>> rows, JsonNode summary, String fieldName) {
        JsonNode node = summary.get(fieldName);
        if (node == null || node.isNull()) {
            return;
        }
        addRunExportMetricRow(rows, "summary", fieldName, node.isContainerNode() ? node.toString() : node.asText());
    }

    private void addReasonRows(List<List<Object>> rows, String section, Map<String, Integer> counts) {
        if (counts == null || counts.isEmpty()) {
            return;
        }
        counts.forEach((key, value) -> addMetricRow(rows, section, key, value));
    }

    private void addReasonMetricRows(List<List<Object>> rows, String section, JsonNode counts) {
        if (counts == null || !counts.isObject()) {
            return;
        }
        counts.fields().forEachRemaining(entry -> addRunExportMetricRow(rows, section, entry.getKey(), entry.getValue().asInt(0)));
    }

    private void addScorecardRow(List<List<Object>> rows, String section, String key, StrategyBotRunScorecardResponse scorecard) {
        if (scorecard == null) {
            return;
        }
        List<Object> row = new ArrayList<>();
        row.add(section);
        row.add(key);
        row.add("");
        row.add(scorecard.getId());
        row.add(scorecard.getRunMode());
        row.add(scorecard.getStatus());
        row.add(scorecard.getRequestedAt());
        row.add(scorecard.getCompletedAt());
        row.add(scorecard.getReturnPercent());
        row.add(scorecard.getNetPnl());
        row.add(scorecard.getMaxDrawdownPercent());
        row.add(scorecard.getWinRate());
        row.add(scorecard.getTradeCount());
        row.add(scorecard.getProfitFactor());
        row.add(scorecard.getExpectancyPerTrade());
        row.add(scorecard.getTimeInMarketPercent());
        row.add(scorecard.getLinkedPortfolioAligned());
        row.add(scorecard.getExecutionEngineReady());
        row.add(scorecard.getLastEvaluatedOpenTime());
        row.add(scorecard.getErrorMessage());
        rows.add(row);
    }

    @Transactional
    public StrategyBotRunReconciliationResponse applyRunReconciliation(UUID botId, UUID runId, UUID userId) {
        StrategyBot bot = strategyBotService.getOwnedBotEntity(botId, userId);
        StrategyBotRun run = getOwnedRunEntity(botId, runId, userId);
        if (run.getStatus() != StrategyBotRun.Status.RUNNING && run.getStatus() != StrategyBotRun.Status.COMPLETED) {
            throw ApiRequestException.conflict(
                    "strategy_bot_run_reconciliation_not_ready",
                    "Strategy bot run must be RUNNING or COMPLETED before reconciliation");
        }

        RunReconciliationState state = buildRunReconciliationState(bot, run);
        StrategyBotRunReconciliationResponse plan = state.response();
        if (!plan.getWarnings().isEmpty()) {
            throw ApiRequestException.conflict(
                    "strategy_bot_reconciliation_manual_cleanup_required",
                    "Strategy bot reconciliation requires manual cleanup");
        }
        if (plan.isTargetPositionOpen() && plan.getTargetQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw ApiRequestException.conflict(
                    "strategy_bot_reconciliation_target_invalid",
                    "Strategy bot reconciliation target quantity is invalid");
        }
        if (plan.isTargetPositionOpen() && plan.getTargetLastPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw ApiRequestException.conflict(
                    "strategy_bot_reconciliation_target_price_unavailable",
                    "Strategy bot reconciliation target price is unavailable");
        }
        if (plan.isPortfolioAligned()) {
            return plan;
        }

        Portfolio linkedPortfolio = state.linkedPortfolio();
        BigDecimal previousCashBalance = linkedPortfolio.getBalance().setScale(2, RoundingMode.HALF_UP);
        linkedPortfolio.setBalance(plan.getTargetCashBalance());
        portfolioRepository.save(linkedPortfolio);

        if (plan.isTargetPositionOpen()) {
            PortfolioItem targetItem = state.matchingItems().isEmpty()
                    ? PortfolioItem.builder()
                    .portfolio(linkedPortfolio)
                    .symbol(bot.getSymbol().toUpperCase(Locale.ROOT))
                    .side("LONG")
                    .leverage(1)
                    .build()
                    : state.matchingItems().get(0);
            targetItem.setPortfolio(linkedPortfolio);
            targetItem.setSymbol(bot.getSymbol().toUpperCase(Locale.ROOT));
            targetItem.setSide("LONG");
            targetItem.setLeverage(1);
            targetItem.setQuantity(plan.getTargetQuantity());
            targetItem.setAveragePrice(plan.getTargetAveragePrice());
            portfolioItemRepository.save(targetItem);
        } else {
            state.matchingItems().forEach(portfolioItemRepository::delete);
        }

        recordSyntheticReconciliationTrades(linkedPortfolio.getId(), plan);
        performanceAnalyticsService.invalidatePortfolioAnalytics(linkedPortfolio.getId());

        LinkedHashMap<String, Object> details = new LinkedHashMap<>();
        details.put("strategyBotId", bot.getId());
        details.put("runId", run.getId());
        details.put("linkedPortfolioId", linkedPortfolio.getId());
        details.put("previousCashBalance", previousCashBalance);
        details.put("targetCashBalance", plan.getTargetCashBalance());
        details.put("previousQuantity", plan.getCurrentQuantity());
        details.put("targetQuantity", plan.getTargetQuantity());
        details.put("targetAveragePrice", plan.getTargetAveragePrice());
        details.put("targetPositionOpen", plan.isTargetPositionOpen());
        details.put("cashDelta", plan.getCashDelta());
        details.put("quantityDelta", plan.getQuantityDelta());
        details.put("journalParityEnabled", true);
        auditLogService.record(
                userId,
                AuditActionType.STRATEGY_BOT_RUN_RECONCILED,
                AuditResourceType.STRATEGY_BOT_RUN,
                run.getId(),
                details);

        invalidateBotReadCaches(bot.getId());
        return buildRunReconciliationState(bot, run).response();
    }

    private void recordSyntheticReconciliationTrades(UUID portfolioId, StrategyBotRunReconciliationResponse plan) {
        BigDecimal quantityTolerance = new BigDecimal("0.00000001");
        BigDecimal priceTolerance = new BigDecimal("0.01");
        BigDecimal currentQuantity = plan.getCurrentQuantity().setScale(8, RoundingMode.HALF_UP);
        BigDecimal targetQuantity = plan.getTargetQuantity().setScale(8, RoundingMode.HALF_UP);
        BigDecimal quantityDelta = targetQuantity.subtract(currentQuantity).setScale(8, RoundingMode.HALF_UP);

        if (quantityDelta.compareTo(quantityTolerance) > 0) {
            tradeActivityRepository.save(TradeActivity.builder()
                    .portfolioId(portfolioId)
                    .symbol(plan.getSymbol().toUpperCase(Locale.ROOT))
                    .type("BUY (BOT SYNC)")
                    .side("LONG")
                    .quantity(quantityDelta)
                    .price(plan.getTargetAveragePrice())
                    .realizedPnl(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                    .timestamp(LocalDateTime.now())
                    .build());
            return;
        }

        if (quantityDelta.compareTo(quantityTolerance.negate()) < 0) {
            BigDecimal sellQuantity = quantityDelta.abs().setScale(8, RoundingMode.HALF_UP);
            BigDecimal exitPrice = plan.getTargetLastPrice().compareTo(BigDecimal.ZERO) > 0
                    ? plan.getTargetLastPrice()
                    : plan.getTargetAveragePrice();
            tradeActivityRepository.save(TradeActivity.builder()
                    .portfolioId(portfolioId)
                    .symbol(plan.getSymbol().toUpperCase(Locale.ROOT))
                    .type("SELL (BOT SYNC)")
                    .side("LONG")
                    .quantity(sellQuantity)
                    .price(exitPrice)
                    .realizedPnl(null)
                    .timestamp(LocalDateTime.now())
                    .build());
            return;
        }

        if (targetQuantity.compareTo(BigDecimal.ZERO) > 0
                && plan.getTargetAveragePrice().subtract(plan.getCurrentAveragePrice()).abs().compareTo(priceTolerance) >= 0) {
            tradeActivityRepository.save(TradeActivity.builder()
                    .portfolioId(portfolioId)
                    .symbol(plan.getSymbol().toUpperCase(Locale.ROOT))
                    .type("REPRICE (BOT SYNC)")
                    .side("LONG")
                    .quantity(targetQuantity)
                    .price(plan.getTargetAveragePrice())
                    .realizedPnl(null)
                    .timestamp(LocalDateTime.now())
                    .build());
            return;
        }

        if (plan.getCashDelta().abs().compareTo(priceTolerance) >= 0) {
            tradeActivityRepository.save(TradeActivity.builder()
                    .portfolioId(portfolioId)
                    .symbol(plan.getSymbol().toUpperCase(Locale.ROOT))
                    .type("CASH SYNC (BOT)")
                    .side("LONG")
                    .quantity(BigDecimal.ZERO.setScale(8, RoundingMode.HALF_UP))
                    .price(plan.getTargetCashBalance())
                    .realizedPnl(null)
                    .timestamp(LocalDateTime.now())
                    .build());
        }
    }

    private RunReconciliationState buildRunReconciliationState(StrategyBot bot, StrategyBotRun run) {
        if (bot.getLinkedPortfolioId() == null) {
            throw ApiRequestException.conflict("strategy_bot_linked_portfolio_required", "Strategy bot has no linked portfolio");
        }

        Portfolio linkedPortfolio = portfolioRepository.findById(bot.getLinkedPortfolioId())
                .orElseThrow(() -> ApiRequestException.notFound("linked_portfolio_not_found", "Linked portfolio not found"));
        JsonNode summary = parseJson(run.getSummary());

        boolean targetPositionOpen = summary.path("positionOpen").asBoolean(false);
        BigDecimal targetQuantity = decimalOrZero(summary.get("openQuantity"), 8);
        BigDecimal targetAveragePrice = decimalOrZero(summary.get("openEntryPrice"), 2);
        BigDecimal targetEquity = decimalOrZero(summary.get("endingEquity"), 2);
        BigDecimal targetLastPrice = strategyBotRunEquityPointRepository
                .findFirstByStrategyBotRunIdOrderBySequenceNoDesc(run.getId())
                .map(StrategyBotRunEquityPoint::getClosePrice)
                .orElse(BigDecimal.ZERO);
        BigDecimal targetMarketValue = targetPositionOpen
                ? targetQuantity.multiply(targetLastPrice).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal targetCashBalance = targetEquity.subtract(targetMarketValue).setScale(2, RoundingMode.HALF_UP);

        var items = portfolioItemRepository.findByPortfolioId(linkedPortfolio.getId());
        var matchingItems = items.stream()
                .filter(item -> item.getSymbol().equalsIgnoreCase(bot.getSymbol()))
                .toList();
        BigDecimal currentQuantity = matchingItems.stream()
                .map(item -> item.getQuantity() == null ? BigDecimal.ZERO : item.getQuantity())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal currentAveragePrice = matchingItems.isEmpty()
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : matchingItems.get(0).getAveragePrice().setScale(2, RoundingMode.HALF_UP);
        BigDecimal currentCashBalance = linkedPortfolio.getBalance().setScale(2, RoundingMode.HALF_UP);
        BigDecimal quantityDelta = targetQuantity.subtract(currentQuantity).setScale(8, RoundingMode.HALF_UP);
        BigDecimal cashDelta = targetCashBalance.subtract(currentCashBalance).setScale(2, RoundingMode.HALF_UP);
        int extraSymbolCount = (int) items.stream()
                .filter(item -> !item.getSymbol().equalsIgnoreCase(bot.getSymbol()))
                .count();

        List<String> warnings = new ArrayList<>();
        if (extraSymbolCount > 0) {
            warnings.add("Linked portfolio contains symbols outside the bot symbol");
        }
        if (matchingItems.size() > 1) {
            warnings.add("Linked portfolio contains multiple rows for the bot symbol");
        }
        boolean hasNonLongRows = matchingItems.stream()
                .anyMatch(item -> item.getSide() != null && !"LONG".equalsIgnoreCase(item.getSide()));
        if (hasNonLongRows) {
            warnings.add("Linked portfolio contains non-LONG rows for the bot symbol");
        }
        boolean hasLeveragedRows = matchingItems.stream()
                .anyMatch(item -> item.getLeverage() != null && item.getLeverage() != 1);
        if (hasLeveragedRows) {
            warnings.add("Linked portfolio contains leveraged rows for the bot symbol");
        }
        if (!targetPositionOpen && currentQuantity.compareTo(BigDecimal.ZERO) > 0) {
            warnings.add("Linked portfolio is still holding the bot symbol while run target is flat");
        }

        boolean cashAligned = cashDelta.abs().compareTo(new BigDecimal("0.01")) < 0;
        boolean quantityAligned = quantityDelta.abs().compareTo(new BigDecimal("0.00000001")) < 0;
        boolean portfolioAligned = cashAligned && quantityAligned && warnings.isEmpty();

        StrategyBotRunReconciliationResponse response = StrategyBotRunReconciliationResponse.builder()
                .runId(run.getId())
                .strategyBotId(bot.getId())
                .linkedPortfolioId(linkedPortfolio.getId())
                .linkedPortfolioName(linkedPortfolio.getName())
                .symbol(bot.getSymbol())
                .runStatus(run.getStatus().name())
                .targetPositionOpen(targetPositionOpen)
                .targetQuantity(targetQuantity.setScale(8, RoundingMode.HALF_UP))
                .targetAveragePrice(targetAveragePrice.setScale(2, RoundingMode.HALF_UP))
                .targetLastPrice(targetLastPrice.setScale(2, RoundingMode.HALF_UP))
                .targetCashBalance(targetCashBalance)
                .targetEquity(targetEquity.setScale(2, RoundingMode.HALF_UP))
                .currentCashBalance(currentCashBalance)
                .currentQuantity(currentQuantity.setScale(8, RoundingMode.HALF_UP))
                .currentAveragePrice(currentAveragePrice)
                .quantityDelta(quantityDelta)
                .cashDelta(cashDelta)
                .cashAligned(cashAligned)
                .quantityAligned(quantityAligned)
                .portfolioAligned(portfolioAligned)
                .extraSymbolCount(extraSymbolCount)
                .warnings(warnings)
                .build();
        return new RunReconciliationState(linkedPortfolio, matchingItems, response);
    }

    @Transactional
    public StrategyBotRunResponse requestRun(UUID botId, UUID userId, StrategyBotRunRequest request) {
        StrategyBot bot = strategyBotService.getOwnedBotEntity(botId, userId);
        if (bot.getStatus() != StrategyBot.Status.READY) {
            throw ApiRequestException.conflict("strategy_bot_not_ready", "Strategy bot must be READY before requesting a run");
        }

        StrategyBotRun.RunMode runMode = resolveRunMode(request != null ? request.getRunMode() : null);
        LocalDate fromDate = request != null ? request.getFromDate() : null;
        LocalDate toDate = request != null ? request.getToDate() : null;
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw ApiRequestException.badRequest(
                    "strategy_bot_run_date_range_invalid",
                    "Run start date must be on or before end date");
        }

        BigDecimal requestedInitialCapital = request != null ? request.getInitialCapital() : null;
        BigDecimal effectiveInitialCapital = resolveInitialCapital(bot, requestedInitialCapital);

        StrategyBotRun run = StrategyBotRun.builder()
                .strategyBotId(bot.getId())
                .userId(userId)
                .linkedPortfolioId(bot.getLinkedPortfolioId())
                .runMode(runMode)
                .status(StrategyBotRun.Status.QUEUED)
                .requestedInitialCapital(requestedInitialCapital)
                .effectiveInitialCapital(effectiveInitialCapital)
                .fromDate(fromDate)
                .toDate(toDate)
                .compiledEntryRules(bot.getEntryRules())
                .compiledExitRules(bot.getExitRules())
                .summary(buildQueuedSummary(runMode, bot, effectiveInitialCapital))
                .build();

        StrategyBotRun saved = strategyBotRunRepository.save(run);
        invalidateBotReadCaches(saved.getStrategyBotId());
        auditLogService.record(
                userId,
                AuditActionType.STRATEGY_BOT_RUN_REQUESTED,
                AuditResourceType.STRATEGY_BOT_RUN,
                saved.getId(),
                buildAuditDetails(saved, bot));
        return toResponse(saved);
    }

    @Transactional
    public StrategyBotRunResponse executeRun(UUID botId, UUID runId, UUID userId) {
        StrategyBot bot = strategyBotService.getOwnedBotEntity(botId, userId);
        StrategyBotRun run = strategyBotRunRepository.findByIdAndStrategyBotIdAndUserId(runId, botId, userId)
                .orElseThrow(() -> ApiRequestException.notFound("strategy_bot_run_not_found", "Strategy bot run not found"));

        if (run.getStatus() != StrategyBotRun.Status.QUEUED) {
            throw ApiRequestException.conflict("strategy_bot_run_not_queued", "Strategy bot run must be QUEUED before execution");
        }

        StrategyBotRuleEngineService.RuleCompilation compilation = strategyBotRuleEngineService.compile(
                parseJson(run.getCompiledEntryRules()),
                parseJson(run.getCompiledExitRules()),
                bot.getStopLossPercent(),
                bot.getTakeProfitPercent());
        if (!compilation.executionEngineReady()) {
            throw ApiRequestException.conflict(
                    "strategy_bot_run_not_executable",
                    "Strategy bot run is not executable by current engine");
        }

        if (run.getRunMode() == StrategyBotRun.RunMode.FORWARD_TEST) {
            return startForwardTestRun(bot, run, userId);
        }
        if (run.getRunMode() != StrategyBotRun.RunMode.BACKTEST) {
            throw ApiRequestException.conflict(
                    "strategy_bot_run_mode_not_supported",
                    "Only backtest execution is currently supported");
        }

        return executeBacktestRun(bot, run, userId);
    }

    @Transactional
    public StrategyBotRunResponse refreshForwardTestRun(UUID botId, UUID runId, UUID userId) {
        StrategyBot bot = strategyBotService.getOwnedBotEntity(botId, userId);
        StrategyBotRun run = strategyBotRunRepository.findByIdAndStrategyBotIdAndUserId(runId, botId, userId)
                .orElseThrow(() -> ApiRequestException.notFound("strategy_bot_run_not_found", "Strategy bot run not found"));
        StrategyBotRun.Status previousStatus = run.getStatus();
        RunSimulationSummary summary = refreshForwardTestRunInternal(bot, run, false);
        StrategyBotRun saved = strategyBotRunRepository.save(run);
        invalidateBotReadCaches(saved.getStrategyBotId());
        if (previousStatus == StrategyBotRun.Status.RUNNING && saved.getStatus() == StrategyBotRun.Status.COMPLETED) {
            auditLogService.record(
                    userId,
                    AuditActionType.STRATEGY_BOT_RUN_EXECUTED,
                    AuditResourceType.STRATEGY_BOT_RUN,
                    saved.getId(),
                    buildExecutionAuditDetails(saved, bot, summary));
        }
        return toResponse(saved);
    }

    @Transactional
    public StrategyBotRunResponse cancelRun(UUID botId, UUID runId, UUID userId) {
        StrategyBot bot = strategyBotService.getOwnedBotEntity(botId, userId);
        StrategyBotRun run = strategyBotRunRepository.findByIdAndStrategyBotIdAndUserId(runId, botId, userId)
                .orElseThrow(() -> ApiRequestException.notFound("strategy_bot_run_not_found", "Strategy bot run not found"));
        StrategyBotRun.Status previousStatus = run.getStatus();
        if (previousStatus != StrategyBotRun.Status.QUEUED && previousStatus != StrategyBotRun.Status.RUNNING) {
            throw ApiRequestException.conflict(
                    "strategy_bot_run_not_cancellable",
                    "Strategy bot run must be QUEUED or RUNNING before cancellation");
        }

        LocalDateTime cancelledAt = LocalDateTime.now();
        run.setStatus(StrategyBotRun.Status.CANCELLED);
        run.setCompletedAt(cancelledAt);
        run.setErrorMessage(null);
        run.setSummary(buildCancelledSummary(run, previousStatus, cancelledAt));

        StrategyBotRun saved = strategyBotRunRepository.save(run);
        invalidateBotReadCaches(saved.getStrategyBotId());
        auditLogService.record(
                userId,
                AuditActionType.STRATEGY_BOT_RUN_CANCELLED,
                AuditResourceType.STRATEGY_BOT_RUN,
                saved.getId(),
                buildCancelledAuditDetails(saved, bot, previousStatus, cancelledAt));
        return toResponse(saved);
    }

    @Transactional
    public StrategyBotRun refreshForwardTestRunSystem(UUID runId) {
        StrategyBotRun run = strategyBotRunRepository.findById(runId).orElse(null);
        if (run == null || run.getRunMode() != StrategyBotRun.RunMode.FORWARD_TEST || run.getStatus() != StrategyBotRun.Status.RUNNING) {
            return null;
        }
        try {
            StrategyBot bot = strategyBotService.getOwnedBotEntity(run.getStrategyBotId(), run.getUserId());
            RunSimulationSummary summary = refreshForwardTestRunInternal(bot, run, false);
            StrategyBotRun saved = strategyBotRunRepository.save(run);
            invalidateBotReadCaches(saved.getStrategyBotId());
            if (saved.getStatus() == StrategyBotRun.Status.COMPLETED) {
                auditLogService.record(
                        saved.getUserId(),
                        AuditActionType.STRATEGY_BOT_RUN_EXECUTED,
                        AuditResourceType.STRATEGY_BOT_RUN,
                        saved.getId(),
                        buildExecutionAuditDetails(saved, bot, summary));
            }
            return saved;
        } catch (Exception ex) {
            run.setStatus(StrategyBotRun.Status.FAILED);
            run.setCompletedAt(LocalDateTime.now());
            run.setErrorMessage(ex.getMessage());
            StrategyBotRun saved = strategyBotRunRepository.save(run);
            invalidateBotReadCaches(saved.getStrategyBotId());
            return saved;
        }
    }

    private StrategyBotRunResponse executeBacktestRun(StrategyBot bot, StrategyBotRun run, UUID userId) {
        run.setStatus(StrategyBotRun.Status.RUNNING);
        run.setStartedAt(LocalDateTime.now());

        List<MarketCandleResponse> candles = loadBacktestCandles(bot, run);
        RunSimulationSummary summary = simulateRun(bot, run, candles, true, "completed");

        persistRunOutputs(run, summary);

        run.setStatus(StrategyBotRun.Status.COMPLETED);
        run.setCompletedAt(LocalDateTime.now());
        run.setErrorMessage(null);
        run.setSummary(writeSummary(summary.payload()));

        StrategyBotRun saved = strategyBotRunRepository.save(run);
        invalidateBotReadCaches(saved.getStrategyBotId());
        auditLogService.record(
                userId,
                AuditActionType.STRATEGY_BOT_RUN_EXECUTED,
                AuditResourceType.STRATEGY_BOT_RUN,
                saved.getId(),
                buildExecutionAuditDetails(saved, bot, summary));
        return toResponse(saved);
    }

    private StrategyBotRunResponse startForwardTestRun(StrategyBot bot, StrategyBotRun run, UUID userId) {
        RunSimulationSummary summary = refreshForwardTestRunInternal(bot, run, true);
        StrategyBotRun saved = strategyBotRunRepository.save(run);
        invalidateBotReadCaches(saved.getStrategyBotId());
        auditLogService.record(
                userId,
                saved.getStatus() == StrategyBotRun.Status.COMPLETED
                        ? AuditActionType.STRATEGY_BOT_RUN_EXECUTED
                        : AuditActionType.STRATEGY_BOT_RUN_STARTED,
                AuditResourceType.STRATEGY_BOT_RUN,
                saved.getId(),
                saved.getStatus() == StrategyBotRun.Status.COMPLETED
                        ? buildExecutionAuditDetails(saved, bot, summary)
                        : buildForwardTestAuditDetails(saved, bot, summary));
        return toResponse(saved);
    }

    private RunSimulationSummary refreshForwardTestRunInternal(StrategyBot bot,
                                                               StrategyBotRun run,
                                                               boolean allowQueuedStart) {
        if (run.getRunMode() != StrategyBotRun.RunMode.FORWARD_TEST) {
            throw ApiRequestException.conflict(
                    "strategy_bot_run_refresh_mode_not_supported",
                    "Only forward-test refresh is currently supported");
        }
        if (run.getStatus() == StrategyBotRun.Status.QUEUED) {
            if (!allowQueuedStart) {
                throw ApiRequestException.conflict(
                        "strategy_bot_forward_test_not_running",
                        "Strategy bot forward-test run must be RUNNING before refresh");
            }
            run.setStatus(StrategyBotRun.Status.RUNNING);
            run.setStartedAt(LocalDateTime.now());
        } else if (run.getStatus() != StrategyBotRun.Status.RUNNING) {
            throw ApiRequestException.conflict(
                    "strategy_bot_forward_test_not_running",
                    "Strategy bot forward-test run must be RUNNING before refresh");
        }

        List<MarketCandleResponse> candles = loadBacktestCandles(bot, run);
        boolean completeNow = forwardTestWindowReachedEnd(candles, run.getToDate());
        RunSimulationSummary summary = simulateRun(bot, run, candles, completeNow, completeNow ? "completed" : "running");

        persistRunOutputs(run, summary);
        run.setSummary(writeSummary(summary.payload()));
        run.setErrorMessage(null);
        if (completeNow) {
            run.setStatus(StrategyBotRun.Status.COMPLETED);
            run.setCompletedAt(LocalDateTime.now());
        } else {
            run.setStatus(StrategyBotRun.Status.RUNNING);
            run.setCompletedAt(null);
        }
        return summary;
    }

    private BigDecimal resolveInitialCapital(StrategyBot bot, BigDecimal requestedInitialCapital) {
        if (requestedInitialCapital != null) {
            if (requestedInitialCapital.compareTo(BigDecimal.ZERO) <= 0) {
                throw ApiRequestException.badRequest("strategy_bot_initial_capital_invalid", "Initial capital must be positive");
            }
            return requestedInitialCapital;
        }
        if (bot.getLinkedPortfolioId() != null) {
            Portfolio portfolio = portfolioRepository.findById(bot.getLinkedPortfolioId())
                    .orElseThrow(() -> ApiRequestException.notFound("linked_portfolio_not_found", "Linked portfolio not found"));
            return portfolio.getBalance();
        }
        return new BigDecimal("100000");
    }

    private StrategyBotRun.RunMode resolveRunMode(String raw) {
        if (raw == null || raw.isBlank()) {
            return StrategyBotRun.RunMode.BACKTEST;
        }
        try {
            return StrategyBotRun.RunMode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw ApiRequestException.badRequest("invalid_strategy_bot_run_mode", "Invalid strategy bot run mode");
        }
    }

    private String buildQueuedSummary(StrategyBotRun.RunMode runMode, StrategyBot bot, BigDecimal effectiveInitialCapital) {
        StrategyBotRuleEngineService.RuleCompilation compilation = strategyBotRuleEngineService.compile(
                parseJson(bot.getEntryRules()),
                parseJson(bot.getExitRules()),
                bot.getStopLossPercent(),
                bot.getTakeProfitPercent());
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("phase", "queued");
        summary.put("runMode", runMode.name());
        summary.put("botStatus", bot.getStatus().name());
        summary.put("market", bot.getMarket());
        summary.put("symbol", bot.getSymbol());
        summary.put("timeframe", bot.getTimeframe());
        summary.put("executionEngineReady", compilation.executionEngineReady());
        summary.put("entryRuleCount", compilation.entryRuleCount());
        summary.put("exitRuleCount", compilation.exitRuleCount());
        summary.put("supportedEntryRuleCount", compilation.supportedEntryRuleCount());
        summary.put("supportedExitRuleCount", compilation.supportedExitRuleCount());
        summary.put("unsupportedRules", compilation.unsupportedRules());
        summary.put("warnings", compilation.warnings());
        summary.put("supportedFeatures", compilation.supportedFeatures());
        appendLinkedPortfolioReconciliation(summary, bot.getLinkedPortfolioId(), effectiveInitialCapital.doubleValue(), "initial_capital");
        return writeSummary(summary);
    }

    private Map<String, Object> buildAuditDetails(StrategyBotRun run, StrategyBot bot) {
        LinkedHashMap<String, Object> details = new LinkedHashMap<>();
        details.put("strategyBotId", bot.getId());
        details.put("runMode", run.getRunMode().name());
        details.put("status", run.getStatus().name());
        details.put("market", bot.getMarket());
        details.put("symbol", bot.getSymbol());
        details.put("timeframe", bot.getTimeframe());
        details.put("linkedPortfolioId", run.getLinkedPortfolioId());
        details.put("requestedInitialCapital", run.getRequestedInitialCapital());
        details.put("effectiveInitialCapital", run.getEffectiveInitialCapital());
        details.put("fromDate", run.getFromDate());
        details.put("toDate", run.getToDate());
        return details;
    }

    private Map<String, Object> buildExecutionAuditDetails(StrategyBotRun run, StrategyBot bot, RunSimulationSummary summary) {
        LinkedHashMap<String, Object> details = new LinkedHashMap<>(buildAuditDetails(run, bot));
        details.put("phase", "completed");
        details.put("tradeCount", summary.tradeCount());
        details.put("winCount", summary.winCount());
        details.put("lossCount", summary.lossCount());
        details.put("endingEquity", summary.endingEquity());
        details.put("netPnl", summary.netPnl());
        details.put("returnPercent", summary.returnPercent());
        details.put("maxDrawdownPercent", summary.maxDrawdownPercent());
        return details;
    }

    private Map<String, Object> buildForwardTestAuditDetails(StrategyBotRun run, StrategyBot bot, RunSimulationSummary summary) {
        LinkedHashMap<String, Object> details = new LinkedHashMap<>(buildAuditDetails(run, bot));
        details.put("phase", "running");
        details.put("tradeCount", summary.tradeCount());
        details.put("winCount", summary.winCount());
        details.put("lossCount", summary.lossCount());
        details.put("endingEquity", summary.endingEquity());
        details.put("netPnl", summary.netPnl());
        details.put("returnPercent", summary.returnPercent());
        details.put("maxDrawdownPercent", summary.maxDrawdownPercent());
        details.put("lastEvaluatedOpenTime", summary.lastEvaluatedOpenTime());
        return details;
    }

    private Map<String, Object> buildCancelledAuditDetails(StrategyBotRun run,
                                                           StrategyBot bot,
                                                           StrategyBotRun.Status previousStatus,
                                                           LocalDateTime cancelledAt) {
        LinkedHashMap<String, Object> details = new LinkedHashMap<>(buildAuditDetails(run, bot));
        details.put("phase", "cancelled");
        details.put("previousStatus", previousStatus.name());
        details.put("cancelledAt", cancelledAt);
        return details;
    }

    private void persistRunOutputs(StrategyBotRun run, RunSimulationSummary summary) {
        strategyBotRunFillRepository.deleteByStrategyBotRunId(run.getId());
        strategyBotRunEquityPointRepository.deleteByStrategyBotRunId(run.getId());
        strategyBotRunEventRepository.deleteByStrategyBotRunId(run.getId());
        strategyBotRunFillRepository.flush();
        strategyBotRunEquityPointRepository.flush();
        strategyBotRunEventRepository.flush();
        strategyBotRunFillRepository.saveAll(summary.fillRows());
        strategyBotRunEquityPointRepository.saveAll(summary.equityPoints());
        strategyBotRunEventRepository.saveAll(summary.eventRows());
    }

    private List<MarketCandleResponse> loadBacktestCandles(StrategyBot bot, StrategyBotRun run) {
        MarketType marketType = resolveMarketType(bot.getMarket());
        String normalizedTimeframe = bot.getTimeframe().toLowerCase(Locale.ROOT);
        List<MarketCandleResponse> rawCandles;
        try {
            rawCandles = marketDataFacadeService.getCandles(
                    marketType,
                    bot.getSymbol(),
                    "ALL",
                    normalizedTimeframe,
                    null,
                    500);
        } catch (Exception ex) {
            rawCandles = buildSyntheticCryptoCandlesIfEnabled(bot, run, marketType, normalizedTimeframe, ex);
            if (rawCandles.isEmpty()) {
                throw ApiRequestException.conflict(
                        "strategy_bot_run_market_data_unavailable",
                        "Strategy bot market data unavailable");
            }
        }

        if (rawCandles == null) {
            rawCandles = List.of();
        }

        if (rawCandles.isEmpty() && syntheticCryptoCandlesEnabled && marketType == MarketType.CRYPTO) {
            rawCandles = buildSyntheticCryptoCandles(bot, run, normalizedTimeframe);
        }

        List<MarketCandleResponse> filtered = rawCandles.stream()
                .sorted(Comparator.comparingLong(MarketCandleResponse::getOpenTime))
                .filter(candle -> matchesDateWindow(candle, run.getFromDate(), run.getToDate()))
                .toList();

        if (filtered.size() < 20 && syntheticCryptoCandlesEnabled && marketType == MarketType.CRYPTO) {
            filtered = buildSyntheticCryptoCandles(bot, run, normalizedTimeframe).stream()
                    .sorted(Comparator.comparingLong(MarketCandleResponse::getOpenTime))
                    .filter(candle -> matchesDateWindow(candle, run.getFromDate(), run.getToDate()))
                    .toList();
        }

        if (filtered.size() < 20) {
            throw ApiRequestException.conflict(
                    "strategy_bot_run_market_data_unavailable",
                    "Not enough candles to execute strategy bot run");
        }
        return filtered;
    }

    private List<MarketCandleResponse> buildSyntheticCryptoCandlesIfEnabled(StrategyBot bot,
                                                                             StrategyBotRun run,
                                                                             MarketType marketType,
                                                                             String normalizedTimeframe,
                                                                             Exception ex) {
        if (!syntheticCryptoCandlesEnabled || marketType != MarketType.CRYPTO) {
            return List.of();
        }
        return buildSyntheticCryptoCandles(bot, run, normalizedTimeframe);
    }

    private List<MarketCandleResponse> buildSyntheticCryptoCandles(StrategyBot bot,
                                                                   StrategyBotRun run,
                                                                   String normalizedTimeframe) {
        int candleCount = Math.max(24, syntheticCryptoCandleCount);
        long intervalMillis = resolveTimeframeMillis(normalizedTimeframe);
        long anchorTime = resolveSyntheticAnchorTime(run, intervalMillis);
        long startOpenTime = anchorTime - ((long) candleCount - 1L) * intervalMillis;
        List<MarketCandleResponse> candles = new ArrayList<>(candleCount);
        double previousClose = baseSyntheticPrice(bot.getSymbol());

        for (int i = 0; i < candleCount; i++) {
            long openTime = startOpenTime + ((long) i * intervalMillis);
            double drift = 0.6 + ((i % 7) * 0.15);
            double open = previousClose;
            double close = roundSynthetic(open + drift);
            double high = roundSynthetic(close + 0.35 + ((i % 3) * 0.08));
            double low = roundSynthetic(Math.max(1.0, open - 0.22 - ((i % 2) * 0.05)));
            double volume = 1000.0 + (i * 35.0);
            candles.add(MarketCandleResponse.builder()
                    .openTime(openTime)
                    .open(open)
                    .high(high)
                    .low(low)
                    .close(close)
                    .volume(roundSynthetic(volume))
                    .build());
            previousClose = close;
        }

        return candles;
    }

    private long resolveSyntheticAnchorTime(StrategyBotRun run, long intervalMillis) {
        LocalDate anchorDate = run.getToDate() != null
                ? run.getToDate()
                : (run.getFromDate() != null ? run.getFromDate().plusDays(4) : LocalDate.now(ZoneOffset.UTC));
        long rawAnchor = anchorDate.atTime(LocalTime.NOON)
                .toInstant(ZoneOffset.UTC)
                .toEpochMilli();
        return rawAnchor - Math.floorMod(rawAnchor, intervalMillis);
    }

    private long resolveTimeframeMillis(String timeframe) {
        return switch (timeframe == null ? "1h" : timeframe.trim().toLowerCase(Locale.ROOT)) {
            case "1m" -> 60_000L;
            case "5m" -> 300_000L;
            case "15m" -> 900_000L;
            case "30m" -> 1_800_000L;
            case "4h" -> 14_400_000L;
            case "1d" -> 86_400_000L;
            case "1h" -> 3_600_000L;
            default -> 3_600_000L;
        };
    }

    private double baseSyntheticPrice(String symbol) {
        if (symbol == null) {
            return 100.0;
        }
        String normalized = symbol.trim().toUpperCase(Locale.ROOT);
        if (normalized.startsWith("BTC")) {
            return 100_000.0;
        }
        if (normalized.startsWith("ETH")) {
            return 5_000.0;
        }
        if (normalized.startsWith("SOL")) {
            return 250.0;
        }
        return 100.0;
    }

    private double roundSynthetic(double value) {
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP).doubleValue();
    }

    private boolean matchesDateWindow(MarketCandleResponse candle, LocalDate fromDate, LocalDate toDate) {
        LocalDate candleDate = Instant.ofEpochMilli(candle.getOpenTime())
                .atZone(ZoneOffset.UTC)
                .toLocalDate();
        if (fromDate != null && candleDate.isBefore(fromDate)) {
            return false;
        }
        return toDate == null || !candleDate.isAfter(toDate);
    }

    private MarketType resolveMarketType(String market) {
        try {
            return MarketType.valueOf(market == null ? "CRYPTO" : market.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw ApiRequestException.badRequest("invalid_strategy_bot_market", "Invalid strategy bot market");
        }
    }

    private boolean forwardTestWindowReachedEnd(List<MarketCandleResponse> candles, LocalDate toDate) {
        if (toDate == null || candles.isEmpty()) {
            return false;
        }
        LocalDate latestCandleDate = Instant.ofEpochMilli(candles.get(candles.size() - 1).getOpenTime())
                .atZone(ZoneOffset.UTC)
                .toLocalDate();
        return !latestCandleDate.isBefore(toDate);
    }

    private RunSimulationSummary simulateRun(StrategyBot bot,
                                             StrategyBotRun run,
                                             List<MarketCandleResponse> candles,
                                             boolean closePositionAtEnd,
                                             String phase) {
        double cash = run.getEffectiveInitialCapital().doubleValue();
        double quantity = 0.0;
        double entryPrice = 0.0;
        boolean positionOpen = false;
        int winCount = 0;
        int lossCount = 0;
        double grossProfit = 0.0;
        double grossLoss = 0.0;
        Double bestTradePnl = null;
        Double worstTradePnl = null;
        long currentEntryOpenTime = Long.MIN_VALUE;
        long totalHoldMillis = 0L;
        long maxHoldMillis = 0L;
        int inMarketSamples = 0;
        double exposurePercentSum = 0.0;
        Map<String, Integer> entryReasonCounts = new HashMap<>();
        Map<String, Integer> exitReasonCounts = new HashMap<>();
        List<Map<String, Object>> fills = new ArrayList<>();
        List<Map<String, Object>> equityCurve = new ArrayList<>();
        List<StrategyBotRunFill> fillRows = new ArrayList<>();
        List<StrategyBotRunEquityPoint> equityPointRows = new ArrayList<>();
        List<StrategyBotRunEvent> eventRows = new ArrayList<>();
        double peakEquity = cash;
        double maxDrawdownPercent = 0.0;
        long lastEntryOpenTime = Long.MIN_VALUE;
        long cooldownMillis = Math.max(bot.getCooldownMinutes(), 0L) * 60_000L;
        int fillSequence = 0;
        int equitySequence = 0;
        int eventSequence = 0;

        for (int i = 0; i < candles.size(); i++) {
            List<MarketCandleResponse> window = candles.subList(0, i + 1);
            MarketCandleResponse candle = candles.get(i);
            String eventPhase = positionOpen ? "EXIT" : "ENTRY";
            String eventAction = "WAITING_FOR_ENTRY";
            List<String> eventMatchedRules = List.of();
            LinkedHashMap<String, Object> eventDetails = new LinkedHashMap<>();
            eventDetails.put("candleIndex", i + 1);
            eventDetails.put("windowSize", window.size());

            if (positionOpen) {
                StrategyBotRuleEngineService.SignalEvaluation exit = evaluateRulesSafely(
                        parseJson(run.getCompiledExitRules()),
                        window,
                        new StrategyBotRuleEngineService.PositionContext(
                                entryPrice,
                                false,
                                bot.getStopLossPercent(),
                                bot.getTakeProfitPercent()));
                eventMatchedRules = exit.matchedRules();
                if (!exit.warnings().isEmpty()) {
                    eventDetails.put("warnings", exit.warnings());
                }
                if (!exit.unsupportedRules().isEmpty()) {
                    eventDetails.put("unsupportedRules", exit.unsupportedRules());
                }
                boolean forcedExit = closePositionAtEnd && i == candles.size() - 1 && !exit.matched();
                boolean shouldExit = exit.matched() || (closePositionAtEnd && i == candles.size() - 1);
                if (shouldExit) {
                    double exitPrice = candle.getClose();
                    double proceeds = quantity * exitPrice;
                    double pnl = proceeds - (quantity * entryPrice);
                    long holdMillis = currentEntryOpenTime == Long.MIN_VALUE ? 0L : Math.max(0L, candle.getOpenTime() - currentEntryOpenTime);
                    double exitedQuantity = quantity;
                    cash += proceeds;
                    fills.add(fill("EXIT", candle, exitPrice, quantity, pnl, exit.matchedRules()));
                    fillRows.add(fillRow(run.getId(), ++fillSequence, "EXIT", candle, exitPrice, quantity, pnl, exit.matchedRules()));
                    if (pnl >= 0) {
                        winCount++;
                        grossProfit += pnl;
                    } else {
                        lossCount++;
                        grossLoss += Math.abs(pnl);
                    }
                    bestTradePnl = bestTradePnl == null ? pnl : Math.max(bestTradePnl, pnl);
                    worstTradePnl = worstTradePnl == null ? pnl : Math.min(worstTradePnl, pnl);
                    totalHoldMillis += holdMillis;
                    maxHoldMillis = Math.max(maxHoldMillis, holdMillis);
                    incrementReasonCounts(exitReasonCounts, exit.matchedRules(), closePositionAtEnd && i == candles.size() - 1 ? "end_of_window" : "manual_exit");
                    eventAction = forcedExit ? "FORCED_EXIT_END_OF_WINDOW" : "EXITED";
                    eventDetails.put("entryPrice", round(entryPrice));
                    eventDetails.put("exitPrice", round(exitPrice));
                    eventDetails.put("positionQuantityBeforeExit", round(exitedQuantity));
                    eventDetails.put("realizedPnl", round(pnl));
                    eventDetails.put("holdHours", round(holdMillis / 3_600_000.0));
                    if (forcedExit) {
                        eventDetails.put("fallbackReason", "end_of_window");
                    }
                    positionOpen = false;
                    quantity = 0.0;
                    entryPrice = 0.0;
                    currentEntryOpenTime = Long.MIN_VALUE;
                } else {
                    eventAction = "HOLDING_POSITION";
                    eventDetails.put("entryPrice", round(entryPrice));
                    eventDetails.put("positionQuantity", round(quantity));
                }
            } else {
                long millisSinceLastEntry = lastEntryOpenTime == Long.MIN_VALUE
                        ? Long.MAX_VALUE
                        : Math.max(0L, candle.getOpenTime() - lastEntryOpenTime);
                boolean cooldownActive = i > 0
                        && lastEntryOpenTime != Long.MIN_VALUE
                        && millisSinceLastEntry < cooldownMillis;
                if (i == 0) {
                    eventAction = "WAITING_FOR_ENTRY";
                    eventDetails.put("reason", "warmup");
                } else if (cooldownActive) {
                    eventPhase = "COOLDOWN";
                    eventAction = "COOLDOWN_BLOCKED";
                    eventDetails.put("cooldownMinutes", Math.max(bot.getCooldownMinutes(), 0L));
                    eventDetails.put("remainingCooldownMinutes", round((cooldownMillis - millisSinceLastEntry) / 60_000.0));
                } else {
                    StrategyBotRuleEngineService.SignalEvaluation entry = evaluateRulesSafely(
                            parseJson(run.getCompiledEntryRules()),
                            window,
                            null);
                    eventMatchedRules = entry.matchedRules();
                    if (!entry.warnings().isEmpty()) {
                        eventDetails.put("warnings", entry.warnings());
                    }
                    if (!entry.unsupportedRules().isEmpty()) {
                        eventDetails.put("unsupportedRules", entry.unsupportedRules());
                    }
                    if (entry.matched()) {
                        double entryCash = cash * bot.getMaxPositionSizePercent().doubleValue() / 100.0;
                        eventDetails.put("allocatedCapital", round(entryCash));
                        if (entryCash > 0.0) {
                            entryPrice = candle.getClose();
                            quantity = entryCash / entryPrice;
                            cash -= entryCash;
                            positionOpen = true;
                            lastEntryOpenTime = candle.getOpenTime();
                            currentEntryOpenTime = candle.getOpenTime();
                            incrementReasonCounts(entryReasonCounts, entry.matchedRules(), "entry_signal");
                            fills.add(fill("ENTRY", candle, entryPrice, quantity, 0.0, entry.matchedRules()));
                            fillRows.add(fillRow(run.getId(), ++fillSequence, "ENTRY", candle, entryPrice, quantity, 0.0, entry.matchedRules()));
                            eventAction = "ENTERED";
                            eventDetails.put("entryPrice", round(entryPrice));
                            eventDetails.put("positionQuantity", round(quantity));
                        } else {
                            eventAction = "ENTRY_SKIPPED_ZERO_CAPITAL";
                        }
                    }
                }
            }

            double equity = cash + (positionOpen ? quantity * candle.getClose() : 0.0);
            if (positionOpen) {
                inMarketSamples++;
                double marketValue = quantity * candle.getClose();
                if (equity > 0.0) {
                    exposurePercentSum += (marketValue / equity) * 100.0;
                }
            }
            peakEquity = Math.max(peakEquity, equity);
            if (peakEquity > 0.0) {
                maxDrawdownPercent = Math.max(maxDrawdownPercent, ((peakEquity - equity) / peakEquity) * 100.0);
            }
            equityCurve.add(equityPoint(candle, equity));
            equityPointRows.add(equityPointRow(run.getId(), ++equitySequence, candle, equity));
            eventDetails.put("positionOpen", positionOpen);
            if (positionOpen) {
                eventDetails.put("openEntryPrice", round(entryPrice));
            }
            eventRows.add(eventRow(
                    run.getId(),
                    ++eventSequence,
                    candle,
                    eventPhase,
                    eventAction,
                    cash,
                    quantity,
                    equity,
                    eventMatchedRules,
                    eventDetails));
        }

        double startingCapital = run.getEffectiveInitialCapital().doubleValue();
        double endingEquity = equityCurve.isEmpty() ? startingCapital : ((Number) equityCurve.get(equityCurve.size() - 1).get("equity")).doubleValue();
        double netPnl = endingEquity - startingCapital;
        int tradeCount = winCount + lossCount;
        double returnPercent = startingCapital == 0.0 ? 0.0 : (netPnl / startingCapital) * 100.0;
        double winRate = tradeCount == 0 ? 0.0 : (winCount * 100.0) / tradeCount;
        Double avgWinPnl = winCount == 0 ? null : round(grossProfit / winCount);
        Double avgLossPnl = lossCount == 0 ? null : round((-grossLoss) / lossCount);
        Double profitFactor = grossLoss == 0.0 ? null : round(grossProfit / grossLoss);
        Double expectancyPerTrade = tradeCount == 0 ? null : round(netPnl / tradeCount);
        Double avgHoldHours = tradeCount == 0 ? null : round(totalHoldMillis / 3_600_000.0 / tradeCount);
        Double maxHoldHours = tradeCount == 0 ? null : round(maxHoldMillis / 3_600_000.0);
        Double timeInMarketPercent = candles.isEmpty() ? null : round((inMarketSamples * 100.0) / candles.size());
        Double avgExposurePercent = inMarketSamples == 0 ? null : round(exposurePercentSum / inMarketSamples);
        Long lastEvaluatedOpenTime = candles.isEmpty() ? null : candles.get(candles.size() - 1).getOpenTime();

        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("phase", phase);
        payload.put("runMode", run.getRunMode().name());
        payload.put("executionEngineReady", true);
        payload.put("market", bot.getMarket());
        payload.put("symbol", bot.getSymbol());
        payload.put("timeframe", bot.getTimeframe());
        payload.put("startingCapital", round(startingCapital));
        payload.put("endingEquity", round(endingEquity));
        payload.put("netPnl", round(netPnl));
        payload.put("returnPercent", round(returnPercent));
        payload.put("tradeCount", tradeCount);
        payload.put("winCount", winCount);
        payload.put("lossCount", lossCount);
        payload.put("winRate", round(winRate));
        payload.put("grossProfit", round(grossProfit));
        payload.put("grossLoss", round(grossLoss));
        payload.put("avgWinPnl", avgWinPnl);
        payload.put("avgLossPnl", avgLossPnl);
        payload.put("profitFactor", profitFactor);
        payload.put("expectancyPerTrade", expectancyPerTrade);
        payload.put("bestTradePnl", bestTradePnl == null ? null : round(bestTradePnl));
        payload.put("worstTradePnl", worstTradePnl == null ? null : round(worstTradePnl));
        payload.put("avgHoldHours", avgHoldHours);
        payload.put("maxHoldHours", maxHoldHours);
        payload.put("timeInMarketPercent", timeInMarketPercent);
        payload.put("avgExposurePercent", avgExposurePercent);
        payload.put("entryReasonCounts", entryReasonCounts);
        payload.put("exitReasonCounts", exitReasonCounts);
        payload.put("positionOpen", positionOpen);
        payload.put("openQuantity", positionOpen ? round(quantity) : null);
        payload.put("openEntryPrice", positionOpen ? round(entryPrice) : null);
        payload.put("lastEvaluatedOpenTime", lastEvaluatedOpenTime);
        payload.put("fillCount", fills.size());
        payload.put("eventCount", eventRows.size());
        payload.put("maxDrawdownPercent", round(maxDrawdownPercent));
        payload.put("candleCount", candles.size());
        payload.put("fills", fills);
        payload.put("equityCurve", equityCurve);
        appendLinkedPortfolioReconciliation(payload, bot.getLinkedPortfolioId(), endingEquity, "ending_equity");

        return new RunSimulationSummary(
                payload,
                fillRows,
                equityPointRows,
                eventRows,
                tradeCount,
                winCount,
                lossCount,
                round(endingEquity),
                round(netPnl),
                round(returnPercent),
                round(maxDrawdownPercent),
                lastEvaluatedOpenTime);
    }

    private StrategyBotRuleEngineService.SignalEvaluation evaluateRulesSafely(
            JsonNode rules,
            List<MarketCandleResponse> candles,
            StrategyBotRuleEngineService.PositionContext positionContext) {
        try {
            return strategyBotRuleEngineService.evaluate(rules, candles, positionContext);
        } catch (StrategyBotRuleEngineService.InsufficientCandlesException ex) {
            return new StrategyBotRuleEngineService.SignalEvaluation(false, List.of(), List.of(), List.of());
        }
    }

    private void incrementReasonCounts(Map<String, Integer> bucket, List<String> matchedRules, String fallback) {
        if (matchedRules == null || matchedRules.isEmpty()) {
            bucket.merge(fallback, 1, Integer::sum);
            return;
        }
        matchedRules.forEach(rule -> bucket.merge(rule, 1, Integer::sum));
    }

    private void appendLinkedPortfolioReconciliation(Map<String, Object> payload,
                                                     UUID linkedPortfolioId,
                                                     double referenceEquity,
                                                     String baseline) {
        if (linkedPortfolioId == null) {
            return;
        }
        Portfolio linkedPortfolio = portfolioRepository.findById(linkedPortfolioId).orElse(null);
        if (linkedPortfolio == null) {
            payload.put("linkedPortfolioMissing", true);
            return;
        }
        double linkedPortfolioBalance = linkedPortfolio.getBalance().doubleValue();
        double drift = linkedPortfolioBalance - referenceEquity;
        Double driftPercent = referenceEquity == 0.0 ? null : round((drift / referenceEquity) * 100.0);
        payload.put("linkedPortfolioId", linkedPortfolioId);
        payload.put("linkedPortfolioName", linkedPortfolio.getName());
        payload.put("linkedPortfolioBalance", round(linkedPortfolioBalance));
        payload.put("linkedPortfolioReferenceEquity", round(referenceEquity));
        payload.put("linkedPortfolioDrift", round(drift));
        payload.put("linkedPortfolioDriftPercent", driftPercent);
        payload.put("linkedPortfolioReconciliationBaseline", baseline);
        payload.put("linkedPortfolioAligned", Math.abs(drift) < 0.01d);
    }

    private StrategyBotRunScorecardResponse toScorecard(StrategyBotRun run) {
        if (run == null) {
            return null;
        }
        JsonNode summary = parseJson(run.getSummary());
        return StrategyBotRunScorecardResponse.builder()
                .id(run.getId())
                .runMode(run.getRunMode().name())
                .status(run.getStatus().name())
                .requestedAt(run.getRequestedAt())
                .completedAt(run.getCompletedAt())
                .returnPercent(doubleValue(summary, "returnPercent"))
                .netPnl(doubleValue(summary, "netPnl"))
                .maxDrawdownPercent(doubleValue(summary, "maxDrawdownPercent"))
                .winRate(doubleValue(summary, "winRate"))
                .tradeCount(intValue(summary, "tradeCount"))
                .profitFactor(doubleValue(summary, "profitFactor"))
                .expectancyPerTrade(doubleValue(summary, "expectancyPerTrade"))
                .timeInMarketPercent(doubleValue(summary, "timeInMarketPercent"))
                .linkedPortfolioAligned(booleanValue(summary, "linkedPortfolioAligned"))
                .executionEngineReady(booleanValue(summary, "executionEngineReady"))
                .lastEvaluatedOpenTime(longValue(summary, "lastEvaluatedOpenTime"))
                .errorMessage(run.getErrorMessage())
                .build();
    }

    private Map<String, Integer> aggregateReasonCounts(List<StrategyBotRun> runs, String fieldName) {
        Map<String, Integer> counts = new HashMap<>();
        for (StrategyBotRun run : runs) {
            JsonNode node = parseJson(run.getSummary()).path(fieldName);
            if (!node.isObject()) {
                continue;
            }
            node.fields().forEachRemaining(entry -> counts.merge(entry.getKey(), entry.getValue().asInt(0), Integer::sum));
        }
        return counts.entrySet().stream()
                .sorted((left, right) -> {
                    int countCompare = Integer.compare(right.getValue(), left.getValue());
                    if (countCompare != 0) {
                        return countCompare;
                    }
                    return left.getKey().compareTo(right.getKey());
                })
                .collect(LinkedHashMap::new,
                        (map, entry) -> map.put(entry.getKey(), entry.getValue()),
                        LinkedHashMap::putAll);
    }

    private Double averageDouble(List<StrategyBotRunScorecardResponse> scorecards,
                                 Function<StrategyBotRunScorecardResponse, Double> extractor) {
        double sum = 0.0;
        int count = 0;
        for (StrategyBotRunScorecardResponse scorecard : scorecards) {
            Double value = extractor.apply(scorecard);
            if (value == null) {
                continue;
            }
            sum += value;
            count++;
        }
        if (count == 0) {
            return null;
        }
        return round(sum / count);
    }

    private StrategyBotRunScorecardResponse maxBy(List<StrategyBotRunScorecardResponse> scorecards,
                                                  Function<StrategyBotRunScorecardResponse, Double> extractor) {
        return scorecards.stream()
                .filter(scorecard -> extractor.apply(scorecard) != null)
                .max(Comparator.comparing(extractor::apply))
                .orElse(null);
    }

    private StrategyBotRunScorecardResponse minBy(List<StrategyBotRunScorecardResponse> scorecards,
                                                  Function<StrategyBotRunScorecardResponse, Double> extractor) {
        return scorecards.stream()
                .filter(scorecard -> extractor.apply(scorecard) != null)
                .min(Comparator.comparing(extractor::apply))
                .orElse(null);
    }

    private Double doubleValue(JsonNode summary, String fieldName) {
        JsonNode node = summary.get(fieldName);
        if (node == null || node.isNull()) {
            return null;
        }
        return round(node.asDouble());
    }

    private Integer intValue(JsonNode summary, String fieldName) {
        JsonNode node = summary.get(fieldName);
        if (node == null || node.isNull()) {
            return null;
        }
        return node.asInt();
    }

    private Long longValue(JsonNode summary, String fieldName) {
        JsonNode node = summary.get(fieldName);
        if (node == null || node.isNull()) {
            return null;
        }
        return node.asLong();
    }

    private Boolean booleanValue(JsonNode summary, String fieldName) {
        JsonNode node = summary.get(fieldName);
        if (node == null || node.isNull()) {
            return null;
        }
        return node.asBoolean();
    }

    private BigDecimal decimalOrZero(JsonNode value, int scale) {
        if (value == null || value.isNull() || value.asText().isBlank()) {
            return BigDecimal.ZERO.setScale(scale, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(value.asDouble()).setScale(scale, RoundingMode.HALF_UP);
    }

    private StrategyBotRun getOwnedRunEntity(UUID botId, UUID runId, UUID userId) {
        strategyBotService.getOwnedBotEntity(botId, userId);
        return strategyBotRunRepository.findByIdAndStrategyBotIdAndUserId(runId, botId, userId)
                .orElseThrow(() -> ApiRequestException.notFound("strategy_bot_run_not_found", "Strategy bot run not found"));
    }

    private void ensureUserExists(UUID userId) {
        if (userId == null || !userRepository.existsById(userId)) {
            throw ApiRequestException.notFound("user_not_found", "User not found");
        }
    }

    private List<StrategyBotRunFillResponse> getRunFillRows(StrategyBotRun run) {
        return strategyBotRunFillRepository.findByStrategyBotRunIdOrderBySequenceNoAsc(run.getId(), Pageable.unpaged())
                .stream()
                .map(this::toFillResponse)
                .toList();
    }

    private List<StrategyBotRunEventResponse> getRunEventRows(StrategyBotRun run) {
        return strategyBotRunEventRepository.findByStrategyBotRunIdOrderBySequenceNoAsc(run.getId(), Pageable.unpaged())
                .stream()
                .map(this::toEventResponse)
                .toList();
    }

    private List<StrategyBotRunEquityPointResponse> getRunEquityPointRows(StrategyBotRun run) {
        return strategyBotRunEquityPointRepository.findByStrategyBotRunIdOrderBySequenceNoAsc(run.getId(), Pageable.unpaged())
                .stream()
                .map(this::toEquityPointResponse)
                .toList();
    }

    private StrategyBotRunReconciliationResponse safeBuildRunReconciliation(StrategyBot bot, StrategyBotRun run) {
        try {
            return buildRunReconciliationState(bot, run).response();
        } catch (ApiRequestException | IllegalArgumentException | IllegalStateException ex) {
            return null;
        }
    }

    private Map<String, Object> fill(String side,
                                     MarketCandleResponse candle,
                                     double price,
                                     double quantity,
                                     double realizedPnl,
                                     List<String> matchedRules) {
        LinkedHashMap<String, Object> row = new LinkedHashMap<>();
        row.put("side", side);
        row.put("openTime", candle.getOpenTime());
        row.put("price", round(price));
        row.put("quantity", round(quantity));
        row.put("realizedPnl", round(realizedPnl));
        row.put("matchedRules", matchedRules);
        return row;
    }

    private StrategyBotRunFill fillRow(UUID runId,
                                       int sequenceNo,
                                       String side,
                                       MarketCandleResponse candle,
                                       double price,
                                       double quantity,
                                       double realizedPnl,
                                       List<String> matchedRules) {
        return StrategyBotRunFill.builder()
                .strategyBotRunId(runId)
                .sequenceNo(sequenceNo)
                .side(side)
                .openTime(candle.getOpenTime())
                .price(BigDecimal.valueOf(price).setScale(8, RoundingMode.HALF_UP))
                .quantity(BigDecimal.valueOf(quantity).setScale(8, RoundingMode.HALF_UP))
                .realizedPnl(BigDecimal.valueOf(round(realizedPnl)).setScale(2, RoundingMode.HALF_UP))
                .matchedRules(writeJsonArray(matchedRules))
                .build();
    }

    private StrategyBotRunEvent eventRow(UUID runId,
                                         int sequenceNo,
                                         MarketCandleResponse candle,
                                         String phase,
                                         String action,
                                         double cashBalance,
                                         double positionQuantity,
                                         double equity,
                                         List<String> matchedRules,
                                         Map<String, Object> details) {
        return StrategyBotRunEvent.builder()
                .strategyBotRunId(runId)
                .sequenceNo(sequenceNo)
                .openTime(candle.getOpenTime())
                .phase(phase)
                .action(action)
                .closePrice(BigDecimal.valueOf(candle.getClose()).setScale(8, RoundingMode.HALF_UP))
                .cashBalance(BigDecimal.valueOf(round(cashBalance)).setScale(2, RoundingMode.HALF_UP))
                .positionQuantity(BigDecimal.valueOf(positionQuantity).setScale(8, RoundingMode.HALF_UP))
                .equity(BigDecimal.valueOf(round(equity)).setScale(2, RoundingMode.HALF_UP))
                .matchedRules(writeJsonArray(matchedRules))
                .details(writeCompactJson(details))
                .build();
    }

    private Map<String, Object> equityPoint(MarketCandleResponse candle, double equity) {
        LinkedHashMap<String, Object> row = new LinkedHashMap<>();
        row.put("openTime", candle.getOpenTime());
        row.put("close", round(candle.getClose()));
        row.put("equity", round(equity));
        return row;
    }

    private StrategyBotRunEquityPoint equityPointRow(UUID runId,
                                                     int sequenceNo,
                                                     MarketCandleResponse candle,
                                                     double equity) {
        return StrategyBotRunEquityPoint.builder()
                .strategyBotRunId(runId)
                .sequenceNo(sequenceNo)
                .openTime(candle.getOpenTime())
                .closePrice(BigDecimal.valueOf(candle.getClose()).setScale(8, RoundingMode.HALF_UP))
                .equity(BigDecimal.valueOf(round(equity)).setScale(2, RoundingMode.HALF_UP))
                .build();
    }

    private double round(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private Integer intValue(Long value) {
        return value == null ? 0 : Math.toIntExact(value);
    }

    private Double roundNullable(Double value) {
        return value == null ? null : round(value);
    }

    private String escapeCsv(Object value) {
        String raw = value == null ? "" : String.valueOf(value);
        if (raw.contains(",") || raw.contains("\"") || raw.contains("\n")) {
            return "\"" + raw.replace("\"", "\"\"") + "\"";
        }
        return raw;
    }

    private String joinJsonArray(JsonNode node) {
        if (node == null || node.isNull()) {
            return "";
        }
        if (!node.isArray()) {
            return node.isValueNode() ? node.asText() : node.toString();
        }
        List<String> values = new ArrayList<>();
        node.forEach(entry -> values.add(entry.isValueNode() ? entry.asText() : entry.toString()));
        return String.join(" | ", values);
    }

    private String writeJsonArray(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize strategy bot run matched rules", ex);
        }
    }

    private String writeCompactJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize strategy bot run event payload", ex);
        }
    }

    private String writeSummary(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize strategy bot run summary", ex);
        }
    }

    private JsonNode parseJson(String raw) {
        try {
            return objectMapper.readTree(raw == null || raw.isBlank() ? "{}" : raw);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to parse strategy bot run payload", ex);
        }
    }

    private String buildCancelledSummary(StrategyBotRun run,
                                         StrategyBotRun.Status previousStatus,
                                         LocalDateTime cancelledAt) {
        JsonNode existing = parseJson(run.getSummary());
        ObjectNode summary = existing.isObject()
                ? ((ObjectNode) existing).deepCopy()
                : objectMapper.createObjectNode();
        summary.put("phase", "cancelled");
        summary.put("status", StrategyBotRun.Status.CANCELLED.name());
        summary.put("previousStatus", previousStatus.name());
        summary.put("cancelledAt", cancelledAt.toString());
        Map<String, Object> payload = objectMapper.convertValue(summary, new TypeReference<>() {});
        return writeSummary(payload);
    }

    private StrategyBotRunResponse toResponse(StrategyBotRun run) {
        return StrategyBotRunResponse.builder()
                .id(run.getId())
                .strategyBotId(run.getStrategyBotId())
                .userId(run.getUserId())
                .linkedPortfolioId(run.getLinkedPortfolioId())
                .runMode(run.getRunMode().name())
                .status(run.getStatus().name())
                .requestedInitialCapital(run.getRequestedInitialCapital())
                .effectiveInitialCapital(run.getEffectiveInitialCapital())
                .fromDate(run.getFromDate())
                .toDate(run.getToDate())
                .compiledEntryRules(parseJson(run.getCompiledEntryRules()))
                .compiledExitRules(parseJson(run.getCompiledExitRules()))
                .summary(parseJson(run.getSummary()))
                .errorMessage(run.getErrorMessage())
                .requestedAt(run.getRequestedAt())
                .startedAt(run.getStartedAt())
                .completedAt(run.getCompletedAt())
                .build();
    }

    private StrategyBotRunFillResponse toFillResponse(StrategyBotRunFill row) {
        return StrategyBotRunFillResponse.builder()
                .id(row.getId())
                .strategyBotRunId(row.getStrategyBotRunId())
                .sequenceNo(row.getSequenceNo())
                .side(row.getSide())
                .openTime(row.getOpenTime())
                .price(row.getPrice())
                .quantity(row.getQuantity())
                .realizedPnl(row.getRealizedPnl())
                .matchedRules(parseJson(row.getMatchedRules()))
                .build();
    }

    private StrategyBotRunEventResponse toEventResponse(StrategyBotRunEvent row) {
        return StrategyBotRunEventResponse.builder()
                .id(row.getId())
                .strategyBotRunId(row.getStrategyBotRunId())
                .sequenceNo(row.getSequenceNo())
                .openTime(row.getOpenTime())
                .phase(row.getPhase())
                .action(row.getAction())
                .closePrice(row.getClosePrice())
                .cashBalance(row.getCashBalance())
                .positionQuantity(row.getPositionQuantity())
                .equity(row.getEquity())
                .matchedRules(parseJson(row.getMatchedRules()))
                .details(parseJson(row.getDetails()))
                .build();
    }

    private StrategyBotRunEquityPointResponse toEquityPointResponse(StrategyBotRunEquityPoint row) {
        return StrategyBotRunEquityPointResponse.builder()
                .id(row.getId())
                .strategyBotRunId(row.getStrategyBotRunId())
                .sequenceNo(row.getSequenceNo())
                .openTime(row.getOpenTime())
                .closePrice(row.getClosePrice())
                .equity(row.getEquity())
                .build();
    }

    private record RunSimulationSummary(
            Map<String, Object> payload,
            List<StrategyBotRunFill> fillRows,
            List<StrategyBotRunEquityPoint> equityPoints,
            List<StrategyBotRunEvent> eventRows,
            int tradeCount,
            int winCount,
            int lossCount,
            double endingEquity,
            double netPnl,
            double returnPercent,
            double maxDrawdownPercent,
            Long lastEvaluatedOpenTime) {
    }

    private record RunReconciliationState(
            Portfolio linkedPortfolio,
            List<PortfolioItem> matchingItems,
            StrategyBotRunReconciliationResponse response) {
    }

    private record BoardAnalyticsSnapshot(
            int totalRuns,
            int completedRuns,
            int runningRuns,
            int failedRuns,
            int cancelledRuns,
            int totalSimulatedTrades,
            int positiveCompletedRuns,
            int negativeCompletedRuns,
            Double avgReturnPercent,
            Double avgNetPnl,
            Double avgMaxDrawdownPercent,
            Double avgWinRate,
            Double avgProfitFactor,
            Double avgExpectancyPerTrade,
            LocalDateTime latestRequestedAt,
            StrategyBotRunScorecardResponse bestRun,
            StrategyBotRunScorecardResponse latestCompletedRun,
            StrategyBotRunScorecardResponse activeForwardRun) {
    }
}
