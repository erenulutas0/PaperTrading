package com.finance.core.controller;

import com.finance.core.dto.MarketType;
import com.finance.core.service.MarketDataFacadeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/market")
@RequiredArgsConstructor
public class MarketController {

    private final MarketDataFacadeService marketDataFacadeService;

    @GetMapping("/prices")
    public Map<String, Double> getPrices(@RequestParam(required = false) String market) {
        return marketDataFacadeService.getPrices(MarketType.fromNullable(market));
    }

    @GetMapping("/instruments")
    public ResponseEntity<List<?>> getSupportedInstruments(@RequestParam(required = false) String market) {
        return ResponseEntity.ok(marketDataFacadeService.getSupportedInstruments(MarketType.fromNullable(market)));
    }

    @GetMapping("/candles")
    public ResponseEntity<List<?>> getCandles(
            @RequestParam String symbol,
            @RequestParam(required = false) String market,
            @RequestParam(defaultValue = "1D") String range,
            @RequestParam(defaultValue = "1h") String interval,
            @RequestParam(required = false) Long beforeOpenTime,
            @RequestParam(required = false) Integer limit) {
        return ResponseEntity.ok(marketDataFacadeService.getCandles(
                MarketType.fromNullable(market),
                symbol,
                range,
                interval,
                beforeOpenTime,
                limit));
    }
}
