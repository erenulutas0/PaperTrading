package com.finance.core.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.finance.core.dto.MarketCandleResponse;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class StrategyBotRuleEngineService {

    public static final String INSUFFICIENT_CANDLES_CODE = "strategy_bot_rule_engine_insufficient_candles";

    private static final Pattern PRICE_ABOVE_MA = Pattern.compile("^price_above_ma_(\\d+)$");
    private static final Pattern PRICE_BELOW_MA = Pattern.compile("^price_below_ma_(\\d+)$");
    private static final Pattern RSI_ABOVE = Pattern.compile("^rsi_above_([0-9]+(?:\\.[0-9]+)?)$");
    private static final Pattern RSI_BELOW = Pattern.compile("^rsi_below_([0-9]+(?:\\.[0-9]+)?)$");
    private static final Pattern BREAKOUT_HIGH = Pattern.compile("^breakout_high_(\\d+)$");
    private static final Pattern BREAKDOWN_LOW = Pattern.compile("^breakdown_low_(\\d+)$");
    private static final Pattern VOLUME_ABOVE_SMA = Pattern.compile("^volume_above_sma_(\\d+)$");
    private static final int RSI_PERIOD = 14;

    public RuleCompilation compile(JsonNode entryRules,
                                   JsonNode exitRules,
                                   BigDecimal stopLossPercent,
                                   BigDecimal takeProfitPercent) {
        CompiledSide entry = compileSide(entryRules, stopLossPercent, takeProfitPercent);
        CompiledSide exit = compileSide(exitRules, stopLossPercent, takeProfitPercent);

        LinkedHashSet<String> unsupportedRules = new LinkedHashSet<>();
        unsupportedRules.addAll(entry.unsupportedRules());
        unsupportedRules.addAll(exit.unsupportedRules());

        LinkedHashSet<String> warnings = new LinkedHashSet<>();
        warnings.addAll(entry.warnings());
        warnings.addAll(exit.warnings());

        LinkedHashSet<String> supportedFeatures = new LinkedHashSet<>();
        supportedFeatures.addAll(entry.supportedFeatures());
        supportedFeatures.addAll(exit.supportedFeatures());

        return new RuleCompilation(
                unsupportedRules.isEmpty(),
                entry.ruleCount(),
                exit.ruleCount(),
                entry.supportedRuleCount(),
                exit.supportedRuleCount(),
                List.copyOf(unsupportedRules),
                List.copyOf(warnings),
                List.copyOf(supportedFeatures));
    }

    public SignalEvaluation evaluate(JsonNode rules,
                                     List<MarketCandleResponse> candles,
                                     PositionContext positionContext) {
        CompiledSide compiled = compileSide(rules,
                positionContext != null ? positionContext.stopLossPercent() : null,
                positionContext != null ? positionContext.takeProfitPercent() : null);

        if (!compiled.unsupportedRules().isEmpty()) {
            return new SignalEvaluation(false, List.of(), compiled.unsupportedRules(), compiled.warnings());
        }

        if (compiled.tokens().isEmpty()) {
            return new SignalEvaluation(true, List.of(), List.of(), compiled.warnings());
        }

        List<String> matched = new ArrayList<>();
        for (String token : compiled.tokens()) {
            if (evaluateToken(token, candles, positionContext)) {
                matched.add(token);
            }
        }

        boolean matchedAll = "all".equals(compiled.operator())
                ? matched.size() == compiled.tokens().size()
                : !matched.isEmpty();

        return new SignalEvaluation(
                matchedAll,
                List.copyOf(matched),
                List.of(),
                compiled.warnings());
    }

    private CompiledSide compileSide(JsonNode rules,
                                     BigDecimal stopLossPercent,
                                     BigDecimal takeProfitPercent) {
        String operator = "all";
        JsonNode container = rules;
        LinkedHashSet<String> warnings = new LinkedHashSet<>();

        if (container == null || container.isNull() || container.isMissingNode()) {
            return new CompiledSide(operator, List.of(), 0, 0, List.of(), List.of(), List.of());
        }

        if (container.isObject()) {
            boolean hasAll = container.has("all");
            boolean hasAny = container.has("any");
            if (hasAll && hasAny) {
                warnings.add("Rule set declares both 'all' and 'any'; preferring 'all'");
            }
            if (hasAll || hasAny) {
                operator = hasAll ? "all" : "any";
                container = container.get(operator);
            } else {
                warnings.add("Rule set uses object form without 'all'/'any'; treating it as empty");
                return new CompiledSide(operator, List.of(), 0, 0, List.of(), List.copyOf(warnings), List.of());
            }
        }

        if (!container.isArray()) {
            warnings.add("Rule set is not an array; treating it as empty");
            return new CompiledSide(operator, List.of(), 0, 0, List.of(), List.copyOf(warnings), List.of());
        }

        List<String> tokens = new ArrayList<>();
        LinkedHashSet<String> unsupportedRules = new LinkedHashSet<>();
        LinkedHashSet<String> supportedFeatures = new LinkedHashSet<>();

        for (JsonNode item : container) {
            if (!item.isTextual()) {
                warnings.add("Non-textual rule token ignored");
                continue;
            }
            String token = item.asText().trim().toLowerCase(Locale.ROOT);
            if (token.isEmpty()) {
                continue;
            }
            tokens.add(token);
            if (isSupportedToken(token, stopLossPercent, takeProfitPercent, warnings, supportedFeatures)) {
                continue;
            }
            unsupportedRules.add(token);
        }

        int supportedRuleCount = tokens.size() - unsupportedRules.size();
        return new CompiledSide(
                operator,
                List.copyOf(tokens),
                tokens.size(),
                supportedRuleCount,
                List.copyOf(unsupportedRules),
                List.copyOf(warnings),
                List.copyOf(supportedFeatures));
    }

    private boolean isSupportedToken(String token,
                                     BigDecimal stopLossPercent,
                                     BigDecimal takeProfitPercent,
                                     Set<String> warnings,
                                     Set<String> supportedFeatures) {
        if (PRICE_ABOVE_MA.matcher(token).matches()) {
            supportedFeatures.add("moving_average");
            return true;
        }
        if (PRICE_BELOW_MA.matcher(token).matches()) {
            supportedFeatures.add("moving_average");
            return true;
        }
        if (RSI_ABOVE.matcher(token).matches() || RSI_BELOW.matcher(token).matches()) {
            supportedFeatures.add("rsi");
            return true;
        }
        if (BREAKOUT_HIGH.matcher(token).matches() || BREAKDOWN_LOW.matcher(token).matches()) {
            supportedFeatures.add("breakout");
            return true;
        }
        if (VOLUME_ABOVE_SMA.matcher(token).matches()) {
            supportedFeatures.add("volume");
            return true;
        }
        if ("stop_loss_hit".equals(token)) {
            if (stopLossPercent == null || stopLossPercent.compareTo(BigDecimal.ZERO) <= 0) {
                warnings.add("stop_loss_hit is configured but stop loss percent is missing");
                return false;
            }
            supportedFeatures.add("risk_exit");
            return true;
        }
        if ("take_profit_hit".equals(token)) {
            if (takeProfitPercent == null || takeProfitPercent.compareTo(BigDecimal.ZERO) <= 0) {
                warnings.add("take_profit_hit is configured but take profit percent is missing");
                return false;
            }
            supportedFeatures.add("risk_exit");
            return true;
        }
        return false;
    }

    private boolean evaluateToken(String token,
                                  List<MarketCandleResponse> candles,
                                  PositionContext positionContext) {
        Matcher matcher = PRICE_ABOVE_MA.matcher(token);
        if (matcher.matches()) {
            int window = Integer.parseInt(matcher.group(1));
            return latestClose(candles) > simpleAverageClose(candles, window);
        }

        matcher = PRICE_BELOW_MA.matcher(token);
        if (matcher.matches()) {
            int window = Integer.parseInt(matcher.group(1));
            return latestClose(candles) < simpleAverageClose(candles, window);
        }

        matcher = RSI_ABOVE.matcher(token);
        if (matcher.matches()) {
            double threshold = Double.parseDouble(matcher.group(1));
            return calculateRsi(candles, RSI_PERIOD) > threshold;
        }

        matcher = RSI_BELOW.matcher(token);
        if (matcher.matches()) {
            double threshold = Double.parseDouble(matcher.group(1));
            return calculateRsi(candles, RSI_PERIOD) < threshold;
        }

        matcher = BREAKOUT_HIGH.matcher(token);
        if (matcher.matches()) {
            int window = Integer.parseInt(matcher.group(1));
            return latestClose(candles) > highestPreviousHigh(candles, window);
        }

        matcher = BREAKDOWN_LOW.matcher(token);
        if (matcher.matches()) {
            int window = Integer.parseInt(matcher.group(1));
            return latestClose(candles) < lowestPreviousLow(candles, window);
        }

        matcher = VOLUME_ABOVE_SMA.matcher(token);
        if (matcher.matches()) {
            int window = Integer.parseInt(matcher.group(1));
            return latestVolume(candles) > simpleAverageVolume(candles, window);
        }

        if ("stop_loss_hit".equals(token)) {
            return evaluateStopLoss(candles, positionContext);
        }
        if ("take_profit_hit".equals(token)) {
            return evaluateTakeProfit(candles, positionContext);
        }
        return false;
    }

    private boolean evaluateStopLoss(List<MarketCandleResponse> candles, PositionContext positionContext) {
        if (positionContext == null || positionContext.entryPrice() <= 0 || positionContext.stopLossPercent() == null) {
            return false;
        }
        MarketCandleResponse latest = latestCandle(candles);
        double threshold = positionContext.isShort()
                ? positionContext.entryPrice() * (1.0 + positionContext.stopLossPercent().doubleValue() / 100.0)
                : positionContext.entryPrice() * (1.0 - positionContext.stopLossPercent().doubleValue() / 100.0);
        return positionContext.isShort() ? latest.getHigh() >= threshold : latest.getLow() <= threshold;
    }

    private boolean evaluateTakeProfit(List<MarketCandleResponse> candles, PositionContext positionContext) {
        if (positionContext == null || positionContext.entryPrice() <= 0 || positionContext.takeProfitPercent() == null) {
            return false;
        }
        MarketCandleResponse latest = latestCandle(candles);
        double threshold = positionContext.isShort()
                ? positionContext.entryPrice() * (1.0 - positionContext.takeProfitPercent().doubleValue() / 100.0)
                : positionContext.entryPrice() * (1.0 + positionContext.takeProfitPercent().doubleValue() / 100.0);
        return positionContext.isShort() ? latest.getLow() <= threshold : latest.getHigh() >= threshold;
    }

    private double simpleAverageClose(List<MarketCandleResponse> candles, int window) {
        ensureMinimumCandles(candles, window);
        int fromIndex = candles.size() - window;
        double sum = 0.0;
        for (int i = fromIndex; i < candles.size(); i++) {
            sum += candles.get(i).getClose();
        }
        return sum / window;
    }

    private double simpleAverageVolume(List<MarketCandleResponse> candles, int window) {
        ensureMinimumCandles(candles, window);
        int fromIndex = candles.size() - window;
        double sum = 0.0;
        for (int i = fromIndex; i < candles.size(); i++) {
            sum += candles.get(i).getVolume();
        }
        return sum / window;
    }

    private double highestPreviousHigh(List<MarketCandleResponse> candles, int window) {
        ensureMinimumCandles(candles, window + 1);
        int toIndex = candles.size() - 1;
        int fromIndex = toIndex - window;
        double highest = Double.NEGATIVE_INFINITY;
        for (int i = fromIndex; i < toIndex; i++) {
            highest = Math.max(highest, candles.get(i).getHigh());
        }
        return highest;
    }

    private double lowestPreviousLow(List<MarketCandleResponse> candles, int window) {
        ensureMinimumCandles(candles, window + 1);
        int toIndex = candles.size() - 1;
        int fromIndex = toIndex - window;
        double lowest = Double.POSITIVE_INFINITY;
        for (int i = fromIndex; i < toIndex; i++) {
            lowest = Math.min(lowest, candles.get(i).getLow());
        }
        return lowest;
    }

    private double calculateRsi(List<MarketCandleResponse> candles, int period) {
        ensureMinimumCandles(candles, period + 1);
        int fromIndex = candles.size() - (period + 1);
        double gain = 0.0;
        double loss = 0.0;
        for (int i = fromIndex + 1; i < candles.size(); i++) {
            double change = candles.get(i).getClose() - candles.get(i - 1).getClose();
            if (change >= 0) {
                gain += change;
            } else {
                loss += Math.abs(change);
            }
        }
        if (loss == 0.0) {
            return 100.0;
        }
        double relativeStrength = (gain / period) / (loss / period);
        return 100.0 - (100.0 / (1.0 + relativeStrength));
    }

    private MarketCandleResponse latestCandle(List<MarketCandleResponse> candles) {
        ensureMinimumCandles(candles, 1);
        return candles.get(candles.size() - 1);
    }

    private double latestClose(List<MarketCandleResponse> candles) {
        return latestCandle(candles).getClose();
    }

    private double latestVolume(List<MarketCandleResponse> candles) {
        return latestCandle(candles).getVolume();
    }

    private void ensureMinimumCandles(List<MarketCandleResponse> candles, int expected) {
        if (candles == null || candles.size() < expected) {
            throw new IllegalArgumentException(INSUFFICIENT_CANDLES_CODE);
        }
    }

    private record CompiledSide(
            String operator,
            List<String> tokens,
            int ruleCount,
            int supportedRuleCount,
            List<String> unsupportedRules,
            List<String> warnings,
            List<String> supportedFeatures) {
    }

    public record RuleCompilation(
            boolean executionEngineReady,
            int entryRuleCount,
            int exitRuleCount,
            int supportedEntryRuleCount,
            int supportedExitRuleCount,
            List<String> unsupportedRules,
            List<String> warnings,
            List<String> supportedFeatures) {
    }

    public record SignalEvaluation(
            boolean matched,
            List<String> matchedRules,
            List<String> unsupportedRules,
            List<String> warnings) {
    }

    public record PositionContext(
            double entryPrice,
            boolean isShort,
            BigDecimal stopLossPercent,
            BigDecimal takeProfitPercent) {
    }
}
