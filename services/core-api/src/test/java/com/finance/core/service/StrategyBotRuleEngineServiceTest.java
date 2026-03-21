package com.finance.core.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.core.dto.MarketCandleResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StrategyBotRuleEngineServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final StrategyBotRuleEngineService service = new StrategyBotRuleEngineService();

    @Test
    void compile_shouldMarkSupportedRuleSetAsExecutionReady() throws Exception {
        var compilation = service.compile(
                objectMapper.readTree("{\"all\":[\"price_above_ma_3\",\"rsi_above_55\"]}"),
                objectMapper.readTree("{\"any\":[\"stop_loss_hit\",\"take_profit_hit\"]}"),
                new BigDecimal("3.5"),
                new BigDecimal("8"));

        assertThat(compilation.executionEngineReady()).isTrue();
        assertThat(compilation.entryRuleCount()).isEqualTo(2);
        assertThat(compilation.exitRuleCount()).isEqualTo(2);
        assertThat(compilation.supportedEntryRuleCount()).isEqualTo(2);
        assertThat(compilation.supportedExitRuleCount()).isEqualTo(2);
        assertThat(compilation.unsupportedRules()).isEmpty();
        assertThat(compilation.supportedFeatures()).contains("moving_average", "rsi", "risk_exit");
    }

    @Test
    void compile_shouldFlagUnsupportedRulesAndMissingRiskConfig() throws Exception {
        var compilation = service.compile(
                objectMapper.readTree("{\"all\":[\"macd_cross\",\"volume_above_sma_5\"]}"),
                objectMapper.readTree("{\"any\":[\"stop_loss_hit\"]}"),
                null,
                null);

        assertThat(compilation.executionEngineReady()).isFalse();
        assertThat(compilation.unsupportedRules()).contains("macd_cross", "stop_loss_hit");
        assertThat(compilation.warnings()).anyMatch(message -> message.contains("stop loss percent is missing"));
        assertThat(compilation.supportedFeatures()).contains("volume");
    }

    @Test
    void evaluate_shouldMatchAllMovingAverageAndRsiRules() throws Exception {
        var evaluation = service.evaluate(
                objectMapper.readTree("{\"all\":[\"price_above_ma_3\",\"rsi_above_55\"]}"),
                bullishCandles(),
                null);

        assertThat(evaluation.matched()).isTrue();
        assertThat(evaluation.matchedRules()).containsExactlyInAnyOrder("price_above_ma_3", "rsi_above_55");
        assertThat(evaluation.unsupportedRules()).isEmpty();
    }

    @Test
    void evaluate_shouldMatchTakeProfitForLongPosition() throws Exception {
        var evaluation = service.evaluate(
                objectMapper.readTree("{\"any\":[\"take_profit_hit\"]}"),
                List.of(
                        candle(1, 100, 102, 99, 101, 1000),
                        candle(2, 101, 109, 100, 108, 1100)),
                new StrategyBotRuleEngineService.PositionContext(
                        100.0,
                        false,
                        new BigDecimal("3"),
                        new BigDecimal("8")));

        assertThat(evaluation.matched()).isTrue();
        assertThat(evaluation.matchedRules()).containsExactly("take_profit_hit");
    }

    @Test
    void evaluate_shouldRejectUnsupportedTokens() throws Exception {
        var evaluation = service.evaluate(
                objectMapper.readTree("{\"all\":[\"macd_cross\"]}"),
                bullishCandles(),
                null);

        assertThat(evaluation.matched()).isFalse();
        assertThat(evaluation.unsupportedRules()).containsExactly("macd_cross");
    }

    private List<MarketCandleResponse> bullishCandles() {
        return List.of(
                candle(1, 100, 101, 99, 100, 1000),
                candle(2, 100, 102, 99, 101, 1050),
                candle(3, 101, 103, 100, 102, 1100),
                candle(4, 102, 104, 101, 103, 1150),
                candle(5, 103, 105, 102, 104, 1200),
                candle(6, 104, 106, 103, 105, 1250),
                candle(7, 105, 107, 104, 106, 1300),
                candle(8, 106, 108, 105, 107, 1350),
                candle(9, 107, 109, 106, 108, 1400),
                candle(10, 108, 110, 107, 109, 1450),
                candle(11, 109, 111, 108, 110, 1500),
                candle(12, 110, 112, 109, 111, 1550),
                candle(13, 111, 113, 110, 112, 1600),
                candle(14, 112, 114, 111, 113, 1650),
                candle(15, 113, 115, 112, 114, 1700),
                candle(16, 114, 116, 113, 115, 1750));
    }

    private MarketCandleResponse candle(long openTime, double open, double high, double low, double close, double volume) {
        return MarketCandleResponse.builder()
                .openTime(openTime)
                .open(open)
                .high(high)
                .low(low)
                .close(close)
                .volume(volume)
                .build();
    }
}
