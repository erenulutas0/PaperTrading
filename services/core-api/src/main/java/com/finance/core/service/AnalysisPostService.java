package com.finance.core.service;

import com.finance.core.domain.ActivityEvent;
import com.finance.core.domain.AuditActionType;
import com.finance.core.domain.AuditResourceType;
import com.finance.core.domain.AnalysisPost;
import com.finance.core.domain.AppUser;
import com.finance.core.dto.AnalysisPostRequest;
import com.finance.core.dto.AnalysisPostResponse;
import com.finance.core.repository.AnalysisPostRepository;
import com.finance.core.repository.UserRepository;
import com.finance.core.web.ApiRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalysisPostService {

    @org.springframework.beans.factory.annotation.Autowired
    @org.springframework.context.annotation.Lazy
    private AnalysisPostService self;

    private final AnalysisPostRepository postRepository;
    private final UserRepository userRepository;
    private final BinanceService binanceService;
    private final ActivityFeedService activityFeedService;
    private final AuditLogService auditLogService;

    /**
     * Create an immutable analysis post.
     * - Server timestamp only (no client date accepted)
     * - Price at creation is snapshotted from live market data
     * - targetDate is computed from targetDays (prevents backdating)
     */
    // @CacheEvict to clear stats when a post is created
    @org.springframework.cache.annotation.CacheEvict(value = "authorStats", key = "#authorId.toString()")
    @Transactional
    public AnalysisPostResponse createPost(UUID authorId, AnalysisPostRequest request) {
        AppUser author = requireUser(authorId);

        AnalysisPost.Direction direction = parseDirection(request.getDirection());

        // Snapshot current market price
        String symbol = request.getInstrumentSymbol().toUpperCase();
        BigDecimal currentPrice = loadCurrentPrice(symbol);

        // Validate target price direction consistency
        if (request.getTargetPrice() != null) {
            BigDecimal target = request.getTargetPrice();
            validateTargetPrice(direction, target, currentPrice);
        }

        // Compute target date from days
        LocalDateTime targetDate = null;
        if (request.getTargetDays() != null && request.getTargetDays() > 0) {
            targetDate = LocalDateTime.now().plusDays(request.getTargetDays());
        }

        AnalysisPost post = AnalysisPost.builder()
                .authorId(authorId)
                .title(request.getTitle())
                .content(request.getContent())
                .instrumentSymbol(symbol)
                .direction(direction)
                .targetPrice(request.getTargetPrice())
                .stopPrice(request.getStopPrice())
                .timeframe(request.getTimeframe())
                .targetDate(targetDate)
                .priceAtCreation(currentPrice)
                .build();

        post = postRepository.save(post);
        log.info("Analysis post created: {} by user {} for {} ({})",
                post.getId(), authorId, symbol, direction);

        // Publish post creation event
        activityFeedService.publish(
                authorId, author.getUsername(),
                ActivityEvent.EventType.POST_CREATED,
                ActivityEvent.TargetType.POST,
                post.getId(), post.getTitle());

        LinkedHashMap<String, Object> details = new LinkedHashMap<>();
        details.put("title", post.getTitle());
        details.put("instrumentSymbol", post.getInstrumentSymbol());
        details.put("direction", post.getDirection().name());
        details.put("priceAtCreation", post.getPriceAtCreation());
        details.put("targetDate", post.getTargetDate());
        auditLogService.record(
                authorId,
                AuditActionType.ANALYSIS_POST_CREATED,
                AuditResourceType.ANALYSIS_POST,
                post.getId(),
                details);

        return toResponse(post, author);
    }

    /**
     * Soft-delete a post (tombstone pattern).
     * The post remains visible as "[Deleted]" to maintain audit trail.
     * Only the author can delete their own post.
     */
    @org.springframework.cache.annotation.CacheEvict(value = "authorStats", key = "#requesterId.toString()")
    @Transactional
    public void deletePost(UUID postId, UUID requesterId) {
        ensureUserExists(requesterId);
        AnalysisPost post = requirePost(postId);

        if (!post.getAuthorId().equals(requesterId)) {
            throw ApiRequestException.forbidden("analysis_post_delete_forbidden", "Only the author can delete their post");
        }

        if (post.isDeleted()) {
            throw ApiRequestException.conflict("analysis_post_already_deleted", "Analysis post already deleted");
        }

        post.setDeleted(true);
        post.setDeletedAt(LocalDateTime.now());
        postRepository.save(post);

        LinkedHashMap<String, Object> details = new LinkedHashMap<>();
        details.put("title", post.getTitle());
        details.put("instrumentSymbol", post.getInstrumentSymbol());
        details.put("deletedAt", post.getDeletedAt());
        auditLogService.record(
                requesterId,
                AuditActionType.ANALYSIS_POST_DELETED,
                AuditResourceType.ANALYSIS_POST,
                post.getId(),
                details);
        log.info("Post {} soft-deleted by author {}", postId, requesterId);
    }

    /** Get a single post by ID */
    public AnalysisPostResponse getPost(UUID postId) {
        AnalysisPost post = requirePost(postId);
        AppUser author = userRepository.findById(post.getAuthorId())
                .orElseThrow(() -> ApiRequestException.notFound(
                        "analysis_post_author_not_found",
                        "Analysis post author not found"));
        return toResponse(post, author);
    }

    /** Global feed: all non-deleted posts, newest first */
    public Page<AnalysisPostResponse> getFeed(Pageable pageable) {
        return postRepository.findByDeletedFalseOrderByCreatedAtDesc(pageable)
                .map(post -> {
                    AppUser author = userRepository.findById(post.getAuthorId()).orElse(null);
                    return toResponse(post, author);
                });
    }

    /** User's analysis posts */
    public Page<AnalysisPostResponse> getPostsByAuthor(UUID authorId, Pageable pageable) {
        AppUser author = requireUser(authorId);

        return postRepository.findByAuthorIdAndDeletedFalseOrderByCreatedAtDesc(authorId, pageable)
                .map(post -> toResponse(post, author));
    }

    /** Get author accuracy stats */
    @org.springframework.cache.annotation.Cacheable(value = "authorStats", key = "#authorId.toString()")
    public Map<String, Long> getAuthorStats(UUID authorId) {
        ensureUserExists(authorId);
        long total = postRepository.countByAuthorIdAndDeletedFalse(authorId);
        long hits = postRepository.countByAuthorIdAndOutcomeAndDeletedFalse(
                authorId, AnalysisPost.Outcome.HIT);
        long misses = postRepository.countByAuthorIdAndOutcomeAndDeletedFalse(
                authorId, AnalysisPost.Outcome.MISSED);
        long pending = postRepository.countByAuthorIdAndOutcomeAndDeletedFalse(
                authorId, AnalysisPost.Outcome.PENDING);
        java.util.Map<String, Long> stats = new java.util.HashMap<>();
        stats.put("total", total);
        stats.put("hits", hits);
        stats.put("misses", misses);
        stats.put("pending", pending);
        return stats;
    }

    private AppUser requireUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> ApiRequestException.notFound("user_not_found", "User not found"));
    }

    private void ensureUserExists(UUID userId) {
        if (userId == null || !userRepository.existsById(userId)) {
            throw ApiRequestException.notFound("user_not_found", "User not found");
        }
    }

    private AnalysisPost requirePost(UUID postId) {
        return postRepository.findById(postId)
                .orElseThrow(() -> ApiRequestException.notFound("analysis_post_not_found", "Analysis post not found"));
    }

    private AnalysisPost.Direction parseDirection(String rawDirection) {
        try {
            return AnalysisPost.Direction.valueOf(rawDirection.trim().toUpperCase());
        } catch (RuntimeException exception) {
            throw ApiRequestException.badRequest("invalid_analysis_direction", "Invalid analysis direction");
        }
    }

    private BigDecimal loadCurrentPrice(String symbol) {
        Map<String, Double> prices = binanceService.getPrices();
        Double currentPrice = prices.get(symbol);
        if (currentPrice == null) {
            throw ApiRequestException.conflict(
                    "analysis_market_data_unavailable",
                    "No market data available for " + symbol);
        }
        return BigDecimal.valueOf(currentPrice);
    }

    private void validateTargetPrice(AnalysisPost.Direction direction, BigDecimal target, BigDecimal currentPrice) {
        if (direction == AnalysisPost.Direction.BULLISH && target.compareTo(currentPrice) <= 0) {
            throw ApiRequestException.badRequest(
                    "invalid_analysis_target_price",
                    "BULLISH target price must be above current price (" + currentPrice.toPlainString() + ")");
        }
        if (direction == AnalysisPost.Direction.BEARISH && target.compareTo(currentPrice) >= 0) {
            throw ApiRequestException.badRequest(
                    "invalid_analysis_target_price",
                    "BEARISH target price must be below current price (" + currentPrice.toPlainString() + ")");
        }
    }

    private AnalysisPostResponse toResponse(AnalysisPost post, AppUser author) {
        Map<String, Long> stats = self.getAuthorStats(post.getAuthorId());
        int totalPosts = stats.get("total").intValue();
        int hitCount = stats.get("hits").intValue();

        String username = author != null ? author.getUsername() : "unknown";
        String displayName = author != null
                ? (author.getDisplayName() != null ? author.getDisplayName() : author.getUsername())
                : "Unknown";

        return AnalysisPostResponse.from(post, username, displayName, totalPosts, hitCount);
    }
}
