package com.finance.core.service;

import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
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
}
