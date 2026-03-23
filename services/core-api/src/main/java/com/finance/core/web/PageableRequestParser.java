package com.finance.core.web;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

public final class PageableRequestParser {

    private PageableRequestParser() {
    }

    public static Pageable resolvePageable(
            Pageable pageable,
            String rawPage,
            String rawSize,
            String invalidPageCode,
            String invalidPageMessage,
            String invalidSizeCode,
            String invalidSizeMessage) {
        return resolvePageable(
                pageable,
                rawPage,
                rawSize,
                invalidPageCode,
                invalidPageMessage,
                invalidSizeCode,
                invalidSizeMessage,
                100);
    }

    public static Pageable resolvePageable(
            Pageable pageable,
            String rawPage,
            String rawSize,
            String invalidPageCode,
            String invalidPageMessage,
            String invalidSizeCode,
            String invalidSizeMessage,
            int maxSize) {
        Integer parsedPage = parsePage(rawPage, invalidPageCode, invalidPageMessage);
        Integer parsedSize = parseSize(rawSize, invalidSizeCode, invalidSizeMessage, maxSize);
        if (parsedPage == null && parsedSize == null) {
            return pageable;
        }
        int effectivePage = parsedPage != null ? parsedPage : pageable.getPageNumber();
        int effectiveSize = parsedSize != null ? parsedSize : pageable.getPageSize();
        return PageRequest.of(effectivePage, effectiveSize, pageable.getSort());
    }

    private static Integer parsePage(String rawPage, String code, String message) {
        if (rawPage == null || rawPage.isBlank()) {
            return null;
        }
        final int parsed;
        try {
            parsed = Integer.parseInt(rawPage.trim());
        } catch (NumberFormatException exception) {
            throw ApiRequestException.badRequest(code, message);
        }
        if (parsed < 0) {
            throw ApiRequestException.badRequest(code, message);
        }
        return parsed;
    }

    private static Integer parseSize(String rawSize, String code, String message, int maxSize) {
        if (rawSize == null || rawSize.isBlank()) {
            return null;
        }
        final int parsed;
        try {
            parsed = Integer.parseInt(rawSize.trim());
        } catch (NumberFormatException exception) {
            throw ApiRequestException.badRequest(code, message);
        }
        if (parsed < 1 || parsed > maxSize) {
            throw ApiRequestException.badRequest(code, message);
        }
        return parsed;
    }
}
