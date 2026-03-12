package com.finance.core.controller;

import com.finance.core.service.BinanceService;
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

    private final BinanceService binanceService;

    @GetMapping("/prices")
    public Map<String, Double> getPrices() {
        return binanceService.getPrices();
    }

    @GetMapping("/instruments")
    public ResponseEntity<List<?>> getSupportedInstruments() {
        return ResponseEntity.ok(binanceService.getSupportedInstruments());
    }

    @GetMapping("/candles")
    public ResponseEntity<List<?>> getCandles(
            @RequestParam String symbol,
            @RequestParam(defaultValue = "1D") String range,
            @RequestParam(defaultValue = "1h") String interval,
            @RequestParam(required = false) Long beforeOpenTime,
            @RequestParam(required = false) Integer limit) {
        return ResponseEntity.ok(binanceService.getCandles(symbol, range, interval, beforeOpenTime, limit));
    }
}
