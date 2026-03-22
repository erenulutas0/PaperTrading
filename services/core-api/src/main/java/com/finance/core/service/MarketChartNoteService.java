package com.finance.core.service;

import com.finance.core.domain.MarketChartNote;
import com.finance.core.dto.MarketChartNoteRequest;
import com.finance.core.dto.MarketChartNoteResponse;
import com.finance.core.dto.MarketType;
import com.finance.core.repository.MarketChartNoteRepository;
import com.finance.core.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MarketChartNoteService {

    private final MarketChartNoteRepository marketChartNoteRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<MarketChartNoteResponse> getNotes(UUID userId, MarketType market, String symbol) {
        ensureUserExists(userId);
        return marketChartNoteRepository.findByUserIdAndMarketAndSymbolOrderByPinnedDescCreatedAtDesc(
                        userId,
                        normalizeMarket(market),
                        normalizeSymbol(symbol))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<MarketChartNoteResponse> getNotes(UUID userId, MarketType market, String symbol, Pageable pageable) {
        ensureUserExists(userId);
        return marketChartNoteRepository.findByUserIdAndMarketAndSymbolOrderByPinnedDescCreatedAtDesc(
                        userId,
                        normalizeMarket(market),
                        normalizeSymbol(symbol),
                        pageable)
                .map(this::toResponse);
    }

    @Transactional
    public MarketChartNoteResponse createNote(UUID userId, MarketChartNoteRequest request) {
        ensureUserExists(userId);
        String symbol = normalizeSymbol(request.getSymbol());
        String body = normalizeBody(request.getBody());
        MarketType market = normalizeMarket(request.getMarket());

        MarketChartNote note = marketChartNoteRepository.save(MarketChartNote.builder()
                .userId(userId)
                .market(market)
                .symbol(symbol)
                .body(body)
                .pinned(normalizePinned(request.getPinned(), false))
                .build());

        return toResponse(note);
    }

    @Transactional
    public MarketChartNoteResponse updateNote(UUID userId, UUID noteId, MarketChartNoteRequest request) {
        ensureUserExists(userId);
        MarketChartNote note = marketChartNoteRepository.findByIdAndUserId(noteId, userId)
                .orElseThrow(() -> new RuntimeException("Chart note not found"));

        note.setBody(normalizeBody(request.getBody()));
        note.setPinned(normalizePinned(request.getPinned(), note.isPinned()));
        return toResponse(marketChartNoteRepository.save(note));
    }

    @Transactional
    public void deleteNote(UUID userId, UUID noteId) {
        ensureUserExists(userId);
        MarketChartNote note = marketChartNoteRepository.findByIdAndUserId(noteId, userId)
                .orElseThrow(() -> new RuntimeException("Chart note not found"));
        marketChartNoteRepository.delete(note);
    }

    private void ensureUserExists(UUID userId) {
        if (!userRepository.existsById(userId)) {
            throw new RuntimeException("User not found");
        }
    }

    private MarketType normalizeMarket(MarketType market) {
        return market != null ? market : MarketType.CRYPTO;
    }

    private String normalizeSymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            throw new RuntimeException("Symbol is required");
        }
        return symbol.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeBody(String body) {
        if (body == null || body.isBlank()) {
            throw new RuntimeException("Note body is required");
        }
        String trimmed = body.trim();
        if (trimmed.length() > 2000) {
            throw new RuntimeException("Note body exceeds 2000 characters");
        }
        return trimmed;
    }

    private boolean normalizePinned(Boolean pinned, boolean defaultValue) {
        return pinned != null ? pinned : defaultValue;
    }

    private MarketChartNoteResponse toResponse(MarketChartNote note) {
        return MarketChartNoteResponse.builder()
                .id(note.getId())
                .market(note.getMarket())
                .symbol(note.getSymbol())
                .body(note.getBody())
                .pinned(note.isPinned())
                .createdAt(note.getCreatedAt())
                .build();
    }
}
