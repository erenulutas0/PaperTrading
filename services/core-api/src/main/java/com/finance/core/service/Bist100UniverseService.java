package com.finance.core.service;

import com.finance.core.dto.MarketType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class Bist100UniverseService {

    private static final String IS_YATIRIM_BIST_PAGE = "https://www.isyatirim.com.tr/tr-tr/analiz/hisse/Sayfalar/Temel-Degerler-Ve-Oranlar.aspx#page-1";
    private static final Pattern SYMBOL_PATTERN = Pattern.compile("\\b[A-Z0-9]{4,5}\\b");
    private static final Duration UNIVERSE_CACHE_TTL = Duration.ofHours(6);

    private final RestClient restClient = RestClient.create();
    private final Set<String> seedSymbols;

    private volatile List<String> cachedUniverse;
    private volatile Instant cachedAt = Instant.EPOCH;

    public Bist100UniverseService() {
        this.seedSymbols = loadSeedSymbols();
        this.cachedUniverse = List.copyOf(this.seedSymbols);
    }

    public MarketType getMarketType() {
        return MarketType.BIST100;
    }

    public List<String> getSymbols() {
        Instant now = Instant.now();
        if (Duration.between(cachedAt, now).compareTo(UNIVERSE_CACHE_TTL) < 0 && !cachedUniverse.isEmpty()) {
            return cachedUniverse;
        }

        synchronized (this) {
            Instant refreshedNow = Instant.now();
            if (Duration.between(cachedAt, refreshedNow).compareTo(UNIVERSE_CACHE_TTL) < 0 && !cachedUniverse.isEmpty()) {
                return cachedUniverse;
            }
            List<String> refreshed = refreshFromDynamicSource();
            cachedUniverse = refreshed;
            cachedAt = refreshedNow;
            return cachedUniverse;
        }
    }

    public boolean supportsSymbol(String symbol) {
        String normalized = normalizeSymbol(symbol);
        return getSymbols().contains(normalized);
    }

    private List<String> refreshFromDynamicSource() {
        try {
            String html = restClient.get()
                    .uri(IS_YATIRIM_BIST_PAGE)
                    .retrieve()
                    .body(String.class);
            if (html == null || html.isBlank()) {
                return List.copyOf(seedSymbols);
            }

            Set<String> parsed = new LinkedHashSet<>();
            Matcher matcher = SYMBOL_PATTERN.matcher(html.toUpperCase(Locale.ROOT));
            while (matcher.find()) {
                String token = matcher.group();
                if (seedSymbols.contains(token)) {
                    parsed.add(token);
                }
            }

            if (parsed.size() < Math.min(80, seedSymbols.size())) {
                log.warn("BIST100 dynamic universe parse yielded {} symbols; falling back to seed", parsed.size());
                return List.copyOf(seedSymbols);
            }

            log.info("BIST100 dynamic universe refreshed with {} symbols", parsed.size());
            return List.copyOf(parsed);
        } catch (Exception e) {
            log.warn("BIST100 dynamic universe refresh failed: {}", e.getMessage());
            return List.copyOf(seedSymbols);
        }
    }

    private Set<String> loadSeedSymbols() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource("market/bist100-seed.txt").getInputStream(),
                StandardCharsets.UTF_8))) {
            Set<String> symbols = new LinkedHashSet<>();
            String line;
            while ((line = reader.readLine()) != null) {
                String normalized = normalizeSymbol(line);
                if (!normalized.isBlank()) {
                    symbols.add(normalized);
                }
            }
            if (symbols.isEmpty()) {
                throw new IllegalStateException("BIST100 seed file is empty");
            }
            return symbols;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load BIST100 seed symbols", e);
        }
    }

    private String normalizeSymbol(String symbol) {
        return symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT);
    }
}
