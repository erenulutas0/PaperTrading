package com.finance.core.dto;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.UUID;

@Value
@Builder
public class CommentResponse {
    UUID id;
    UUID actorId;
    String actorUsername;
    String actorDisplayName;
    String actorAvatarUrl;
    String content;
    LocalDateTime createdAt;
    long likeCount;
    boolean hasLiked;
    long replyCount;
}
