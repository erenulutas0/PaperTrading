package com.finance.core.dto;

import com.finance.core.domain.AnalysisPost;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisPostResponse {

    private UUID id;
    private UUID authorId;
    private String authorUsername;
    private String authorDisplayName;

    private String title;
    private String content;
    private String instrumentSymbol;
    private String direction;

    private BigDecimal targetPrice;
    private BigDecimal stopPrice;
    private String timeframe;
    private LocalDateTime targetDate;

    private BigDecimal priceAtCreation;
    private String outcome;
    private LocalDateTime outcomeResolvedAt;
    private BigDecimal priceAtResolution;

    private boolean deleted;
    private LocalDateTime createdAt;

    /** Accuracy stats for the author */
    private int authorTotalPosts;
    private int authorHitCount;

    public static AnalysisPostResponse from(AnalysisPost post, String username, String displayName,
            int totalPosts, int hitCount) {
        return AnalysisPostResponse.builder()
                .id(post.getId())
                .authorId(post.getAuthorId())
                .authorUsername(username)
                .authorDisplayName(displayName)
                .title(post.isDeleted() ? "[Deleted]" : post.getTitle())
                .content(post.isDeleted() ? "[This analysis was removed by the author]" : post.getContent())
                .instrumentSymbol(post.getInstrumentSymbol())
                .direction(post.getDirection().name())
                .targetPrice(post.getTargetPrice())
                .stopPrice(post.getStopPrice())
                .timeframe(post.getTimeframe())
                .targetDate(post.getTargetDate())
                .priceAtCreation(post.getPriceAtCreation())
                .outcome(post.getOutcome().name())
                .outcomeResolvedAt(post.getOutcomeResolvedAt())
                .priceAtResolution(post.getPriceAtResolution())
                .deleted(post.isDeleted())
                .createdAt(post.getCreatedAt())
                .authorTotalPosts(totalPosts)
                .authorHitCount(hitCount)
                .build();
    }
}
