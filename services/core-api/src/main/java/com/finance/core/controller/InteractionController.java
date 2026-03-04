package com.finance.core.controller;

import com.finance.core.domain.Interaction;
import com.finance.core.dto.InteractionRequest;
import com.finance.core.service.InteractionService;
import com.finance.core.web.CurrentUserId;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/interactions")
@RequiredArgsConstructor
public class InteractionController {

    private final InteractionService interactionService;

    @PostMapping("/{targetId}/like")
    public ResponseEntity<Void> toggleLike(
            @PathVariable UUID targetId,
            @CurrentUserId UUID userId,
            @RequestBody InteractionRequest request) {
        interactionService.toggleLike(userId, targetId, request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{targetId}/comments")
    public ResponseEntity<Interaction> addComment(
            @PathVariable UUID targetId,
            @CurrentUserId UUID userId,
            @RequestBody InteractionRequest request) {
        return ResponseEntity.ok(interactionService.addComment(userId, targetId, request));
    }

    @GetMapping("/{targetId}/comments")
    public ResponseEntity<Page<Interaction>> getComments(
            @PathVariable UUID targetId,
            @RequestParam String type,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(interactionService.getComments(targetId, type, pageable));
    }

    @GetMapping("/{targetId}/likes/count")
    public ResponseEntity<Map<String, Object>> getLikeInfo(
            @PathVariable UUID targetId,
            @RequestParam String type,
            @CurrentUserId(required = false) UUID userId) {

        long count = interactionService.getLikeCount(targetId, type);
        boolean hasLiked = false;

        if (userId != null) {
            hasLiked = interactionService.hasLiked(userId, targetId, type);
        }

        return ResponseEntity.ok(Map.of("count", count, "hasLiked", hasLiked));
    }
}
