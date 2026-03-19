package com.finance.core.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.core.domain.IdempotencyKeyRecord;
import com.finance.core.service.IdempotencyService;
import com.finance.core.web.ApiErrorResponse;
import com.finance.core.web.RequestCorrelation;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ReadListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class IdempotencyKeyFilter extends OncePerRequestFilter {

    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    private static final String IDEMPOTENT_REPLAY_HEADER = "X-Idempotent-Replay";
    private static final List<String> WRITE_METHODS = List.of("POST", "PUT", "PATCH", "DELETE");
    private static final String LEGACY_USER_ID_HEADER = "X-User-Id";

    private final IdempotencyService idempotencyService;
    private final IdempotencyProperties idempotencyProperties;
    private final ObjectMapper objectMapper;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        if (!shouldProtect(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String idempotencyKey = request.getHeader(IDEMPOTENCY_KEY_HEADER);
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }
        if (idempotencyKey.length() > 255) {
            writeError(response, HttpServletResponse.SC_BAD_REQUEST, "invalid_idempotency_key",
                    "Idempotency-Key must be 255 characters or fewer", request);
            return;
        }

        CachedBodyHttpServletRequest wrappedRequest = new CachedBodyHttpServletRequest(request);
        String actorScope = resolveActorScope(wrappedRequest);
        String requestPath = trimRequestPath(buildRequestPath(wrappedRequest));
        String requestHash = hashRequest(wrappedRequest.getCachedBody());

        IdempotencyService.ClaimResult claim = idempotencyService.claim(
                actorScope,
                idempotencyKey.trim(),
                wrappedRequest.getMethod(),
                requestPath,
                requestHash,
                LocalDateTime.now().plus(idempotencyProperties.getTtl()));

        switch (claim.type()) {
            case REPLAY -> {
                writeReplayResponse(response, claim.record(), request);
                return;
            }
            case CONFLICT -> {
                writeError(response, HttpServletResponse.SC_CONFLICT, "idempotency_key_reused",
                        "Idempotency-Key was already used with a different request", request);
                return;
            }
            case IN_PROGRESS -> {
                writeError(response, HttpServletResponse.SC_CONFLICT, "idempotency_request_in_progress",
                        "An identical request with this Idempotency-Key is already in progress", request);
                return;
            }
            case CLAIMED -> {
                // continue
            }
        }

        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);
        try {
            filterChain.doFilter(wrappedRequest, responseWrapper);
            handleCompletion(claim.record(), responseWrapper);
        } catch (Exception ex) {
            idempotencyService.release(claim.record());
            throw ex;
        } finally {
            responseWrapper.copyBodyToResponse();
        }
    }

    private void handleCompletion(IdempotencyKeyRecord record, ContentCachingResponseWrapper response) throws IOException {
        int status = response.getStatus();
        if (status >= 200 && status < 300) {
            String body = readResponseBody(response.getContentAsByteArray());
            if (body.length() > idempotencyProperties.getMaxBodyBytes()) {
                idempotencyService.release(record);
                return;
            }
            idempotencyService.complete(record, status, response.getContentType(), body);
        } else {
            idempotencyService.release(record);
        }
    }

    private boolean shouldProtect(HttpServletRequest request) {
        if (!idempotencyProperties.isEnabled()) {
            return false;
        }
        String method = request.getMethod().toUpperCase(Locale.ROOT);
        if (!WRITE_METHODS.contains(method)) {
            return false;
        }
        String path = request.getRequestURI();
        if (!path.startsWith("/api/v1/")) {
            return false;
        }
        return !pathMatcher.match("/api/v1/auth/**", path);
    }

    private String resolveActorScope(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof String principal && !principal.isBlank()) {
            return principal;
        }
        String legacy = request.getHeader(LEGACY_USER_ID_HEADER);
        if (legacy != null && !legacy.isBlank()) {
            return legacy.trim();
        }
        return "anonymous";
    }

    private String buildRequestPath(HttpServletRequest request) {
        String query = request.getQueryString();
        if (query == null || query.isBlank()) {
            return request.getRequestURI();
        }
        return request.getRequestURI() + "?" + query;
    }

    private String trimRequestPath(String path) {
        if (path.length() <= 255) {
            return path;
        }
        return path.substring(0, 255);
    }

    private String hashRequest(byte[] body) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(body));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private void writeReplayResponse(HttpServletResponse response, IdempotencyKeyRecord record, HttpServletRequest request) throws IOException {
        response.setStatus(record.getResponseStatus() != null ? record.getResponseStatus() : HttpServletResponse.SC_OK);
        if (record.getResponseContentType() != null && !record.getResponseContentType().isBlank()) {
            response.setContentType(record.getResponseContentType());
        } else {
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        }
        response.setHeader(IDEMPOTENT_REPLAY_HEADER, "true");
        Object requestId = request.getAttribute(RequestCorrelation.REQUEST_ID_ATTRIBUTE);
        if (requestId instanceof String value && !value.isBlank()) {
            response.setHeader(RequestCorrelation.REQUEST_ID_HEADER, value);
        }
        if (record.getResponseBody() != null) {
            response.getWriter().write(record.getResponseBody());
        }
    }

    private void writeError(
            HttpServletResponse response,
            int status,
            String code,
            String message,
            HttpServletRequest request) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        Object requestId = request.getAttribute(RequestCorrelation.REQUEST_ID_ATTRIBUTE);
        if (requestId instanceof String value && !value.isBlank()) {
            response.setHeader(RequestCorrelation.REQUEST_ID_HEADER, value);
        }
        ApiErrorResponse payload = new ApiErrorResponse(
                code,
                message,
                null,
                requestId instanceof String value ? value : "");
        objectMapper.writeValue(response.getWriter(), payload);
    }

    private String readResponseBody(byte[] body) {
        if (body == null || body.length == 0) {
            return "";
        }
        return new String(body, StandardCharsets.UTF_8);
    }

    private static final class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {

        private final byte[] cachedBody;

        private CachedBodyHttpServletRequest(HttpServletRequest request) throws IOException {
            super(request);
            this.cachedBody = request.getInputStream().readAllBytes();
        }

        private byte[] getCachedBody() {
            return cachedBody;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(cachedBody);
            return new ServletInputStream() {
                @Override
                public int read() {
                    return inputStream.read();
                }

                @Override
                public boolean isFinished() {
                    return inputStream.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener readListener) {
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }
}
