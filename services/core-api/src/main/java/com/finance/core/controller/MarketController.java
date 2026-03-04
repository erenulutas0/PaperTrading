package com.finance.core.controller;

import com.finance.core.service.BinanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
