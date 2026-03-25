package com.finance.core.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.finance.core.domain.StrategyBot;
import com.finance.core.dto.StrategyBotRequest;
import com.finance.core.dto.StrategyBotResponse;
import com.finance.core.repository.PortfolioRepository;
import com.finance.core.repository.StrategyBotRepository;
import com.finance.core.repository.StrategyBotRunRepository;
import com.finance.core.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StrategyBotServiceTest {

    @Mock
    private StrategyBotRepository strategyBotRepository;
    @Mock
    private StrategyBotRunRepository strategyBotRunRepository;
    @Mock
    private PortfolioRepository portfolioRepository;
    @Mock
    private UserRepository userRepository;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private CacheService cacheService;

    @InjectMocks
    private StrategyBotService strategyBotService;

    @Test
    void createBot_normalizationIsLocaleSafeUnderTurkishLocale() {
        UUID userId = UUID.randomUUID();
        when(userRepository.existsById(userId)).thenReturn(true);
        when(strategyBotRepository.save(any(StrategyBot.class))).thenAnswer(invocation -> {
            StrategyBot bot = invocation.getArgument(0);
            bot.setId(UUID.randomUUID());
            return bot;
        });

        StrategyBotRequest request = new StrategyBotRequest();
        request.setName("BIST Trend");
        request.setMarket("bist100");
        request.setSymbol("thyao");
        request.setTimeframe("1h");
        request.setStatus("ready");
        request.setMaxPositionSizePercent(new BigDecimal("25"));

        Locale previous = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("tr-TR"));
        try {
            StrategyBotResponse response = strategyBotService.createBot(userId, request);

            assertEquals("BIST100", response.getMarket());
            assertEquals("THYAO", response.getSymbol());
            assertEquals("1H", response.getTimeframe());
            assertEquals("READY", response.getStatus());
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void updateBot_normalizationIsLocaleSafeUnderTurkishLocale() {
        UUID userId = UUID.randomUUID();
        UUID botId = UUID.randomUUID();
        when(userRepository.existsById(userId)).thenReturn(true);
        when(strategyBotRepository.findByIdAndUserId(botId, userId)).thenReturn(Optional.of(
                StrategyBot.builder()
                        .id(botId)
                        .userId(userId)
                        .name("Existing Bot")
                        .market("CRYPTO")
                        .symbol("BTCUSDT")
                        .timeframe("1H")
                        .entryRules("{}")
                        .exitRules("{}")
                        .maxPositionSizePercent(new BigDecimal("25"))
                        .status(StrategyBot.Status.DRAFT)
                        .cooldownMinutes(0)
                        .build()));
        when(strategyBotRepository.save(any(StrategyBot.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StrategyBotRequest request = new StrategyBotRequest();
        request.setMarket("bist100");
        request.setSymbol("thyao");
        request.setTimeframe("4h");

        Locale previous = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("tr-TR"));
        try {
            strategyBotService.updateBot(botId, userId, request);
        } finally {
            Locale.setDefault(previous);
        }

        ArgumentCaptor<StrategyBot> botCaptor = ArgumentCaptor.forClass(StrategyBot.class);
        verify(strategyBotRepository).save(botCaptor.capture());
        assertEquals("BIST100", botCaptor.getValue().getMarket());
        assertEquals("THYAO", botCaptor.getValue().getSymbol());
        assertEquals("4H", botCaptor.getValue().getTimeframe());
    }

    @Test
    void createBot_whenRuleSerializationFails_shouldThrowIllegalStateException() throws Exception {
        UUID userId = UUID.randomUUID();
        when(userRepository.existsById(userId)).thenReturn(true);

        ObjectMapper brokenMapper = mock(ObjectMapper.class);
        doThrow(new JsonProcessingException("boom") { })
                .when(brokenMapper)
                .writeValueAsString(any());
        ReflectionTestUtils.setField(strategyBotService, "objectMapper", brokenMapper);

        StrategyBotRequest request = new StrategyBotRequest();
        request.setName("Broken Bot");
        request.setMarket("CRYPTO");
        request.setSymbol("BTCUSDT");
        request.setTimeframe("1H");
        request.setMaxPositionSizePercent(new BigDecimal("25"));

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> strategyBotService.createBot(userId, request));

        assertEquals("Failed to serialize strategy rules", exception.getMessage());
    }

    @Test
    void listOwnedBots_whenStoredRulesAreInvalid_shouldThrowIllegalStateException() throws Exception {
        UUID userId = UUID.randomUUID();
        StrategyBot bot = StrategyBot.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .name("Broken Bot")
                .market("CRYPTO")
                .symbol("BTCUSDT")
                .timeframe("1H")
                .entryRules("{broken")
                .exitRules("{}")
                .maxPositionSizePercent(new BigDecimal("25"))
                .status(StrategyBot.Status.DRAFT)
                .cooldownMinutes(0)
                .build();
        when(userRepository.existsById(userId)).thenReturn(true);
        when(strategyBotRepository.findByUserId(eq(userId), any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(java.util.List.of(bot)));

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> strategyBotService.getUserBots(userId, org.springframework.data.domain.PageRequest.of(0, 10)));

        assertEquals("Failed to parse stored strategy rules", exception.getMessage());
    }

    @Test
    void updateBot_shouldInvalidateBotReadCaches() {
        UUID userId = UUID.randomUUID();
        UUID botId = UUID.randomUUID();
        when(userRepository.existsById(userId)).thenReturn(true);
        when(strategyBotRepository.findByIdAndUserId(botId, userId)).thenReturn(Optional.of(
                StrategyBot.builder()
                        .id(botId)
                        .userId(userId)
                        .name("Existing Bot")
                        .market("CRYPTO")
                        .symbol("BTCUSDT")
                        .timeframe("1H")
                        .entryRules("{}")
                        .exitRules("{}")
                        .maxPositionSizePercent(new BigDecimal("25"))
                        .status(StrategyBot.Status.DRAFT)
                        .cooldownMinutes(0)
                        .build()));
        when(strategyBotRepository.save(any(StrategyBot.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StrategyBotRequest request = new StrategyBotRequest();
        request.setName("Updated Bot");

        strategyBotService.updateBot(botId, userId, request);

        verify(cacheService).deletePattern("strategy-bot:analytics:" + botId + ":*");
        verify(cacheService).deletePattern("strategy-bot:public-detail:" + botId + ":*");
    }
}
