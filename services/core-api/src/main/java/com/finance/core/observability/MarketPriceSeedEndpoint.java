package com.finance.core.observability;

import com.finance.core.service.BinanceService;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import com.finance.core.web.ApiRequestException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@Endpoint(id = "marketprices")
@ConditionalOnProperty(name = "app.market.manual-seed-enabled", havingValue = "true")
public class MarketPriceSeedEndpoint {

    private final BinanceService binanceService;

    public MarketPriceSeedEndpoint(BinanceService binanceService) {
        this.binanceService = binanceService;
    }

    @ReadOperation
    public Map<String, Object> status() {
        Map<String, Object> payload = new LinkedHashMap<>();
        Map<String, Double> prices = binanceService.currentPriceSnapshot();
        Instant lastPriceUpdateAt = binanceService.lastPriceUpdateAt();
        payload.put("enabled", true);
        payload.put("count", prices.size());
        payload.put("lastPriceUpdateAt", Instant.EPOCH.equals(lastPriceUpdateAt) ? null : lastPriceUpdateAt.toString());
        payload.put("prices", prices);
        return payload;
    }

    @WriteOperation
    public Map<String, Object> seed(@Nullable String symbol, @Nullable Double price) {
        Map<String, Object> payload = status();
        if (!StringUtils.hasText(symbol)) {
            payload.put("accepted", false);
            payload.put("error", "market_symbol_required");
            return payload;
        }
        if (price == null || price <= 0) {
            payload.put("accepted", false);
            payload.put("error", "invalid_market_price");
            return payload;
        }

        try {
            String normalizedSymbol = binanceService.seedTrackedPrice(symbol.trim(), price);
            payload = status();
            payload.put("accepted", true);
            payload.put("seededSymbol", normalizedSymbol);
            payload.put("seededPrice", price);
            return payload;
        } catch (ApiRequestException ex) {
            payload.put("accepted", false);
            payload.put("error", ex.code());
            return payload;
        }
    }
}
