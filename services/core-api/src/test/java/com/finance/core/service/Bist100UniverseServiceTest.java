package com.finance.core.service;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

class Bist100UniverseServiceTest {

    @Test
    void normalizeSymbol_shouldBeLocaleSafeUnderTurkishLocale() {
        Bist100UniverseService service = new Bist100UniverseService();

        Locale previous = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("tr-TR"));
        try {
            assertEquals("BIST100", ReflectionTestUtils.invokeMethod(service, "normalizeSymbol", "bist100"));
        } finally {
            Locale.setDefault(previous);
        }
    }
}
