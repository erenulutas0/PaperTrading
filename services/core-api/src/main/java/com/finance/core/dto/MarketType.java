package com.finance.core.dto;

import java.util.Locale;

public enum MarketType {
    CRYPTO,
    BIST100;

    public static MarketType fromNullable(String raw) {
        if (raw == null || raw.isBlank()) {
            return CRYPTO;
        }
        return MarketType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
    }
}
