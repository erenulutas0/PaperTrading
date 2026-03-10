package com.finance.core.security;

import io.micrometer.core.instrument.MeterRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.core.web.ApiErrorResponse;
import com.finance.core.web.RequestCorrelation;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.Vector;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String LEGACY_USER_ID_HEADER = "X-User-Id";

    private final JwtTokenService jwtTokenService;
    private final JwtRuntimeProperties jwtRuntimeProperties;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper;

    public JwtAuthenticationFilter(
            JwtTokenService jwtTokenService,
            JwtRuntimeProperties jwtRuntimeProperties,
            @Autowired(required = false) MeterRegistry meterRegistry,
            ObjectMapper objectMapper) {
        this.jwtTokenService = jwtTokenService;
        this.jwtRuntimeProperties = jwtRuntimeProperties;
        this.meterRegistry = meterRegistry;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        String legacyUserId = request.getHeader(LEGACY_USER_ID_HEADER);
        HttpServletRequest requestToUse = request;

        if (authorization != null && !authorization.isBlank()) {
            if (!authorization.startsWith("Bearer ")) {
                recordRequest("bearer", "rejected_invalid_authorization_header");
                reject(response, "Invalid Authorization header");
                return;
            }

            String token = authorization.substring("Bearer ".length()).trim();
            JwtTokenClaims claims;
            try {
                claims = jwtTokenService.parseAndValidate(token);
            } catch (IllegalArgumentException e) {
                recordRequest("bearer", "rejected_invalid_token");
                reject(response, e.getMessage());
                return;
            }

            UUID authenticatedUserId = claims.userId();
            if (legacyUserId != null && !legacyUserId.isBlank()
                    && jwtRuntimeProperties.isEnforceHeaderTokenMatch()
                    && !legacyUserId.equals(authenticatedUserId.toString())) {
                recordRequest("bearer", "rejected_header_token_mismatch");
                reject(response, "Authorization and X-User-Id mismatch");
                return;
            }

            requestToUse = withHeader(request, LEGACY_USER_ID_HEADER, authenticatedUserId.toString());

            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    authenticatedUserId.toString(),
                    null,
                    Collections.emptyList());
            SecurityContextHolder.getContext().setAuthentication(authentication);
            recordRequest("bearer", "accepted");
        } else if (legacyUserId != null && !legacyUserId.isBlank()
                && !jwtRuntimeProperties.isAllowLegacyUserIdHeader()) {
            recordRequest("legacy-header", "rejected_disabled");
            reject(response, "X-User-Id header is disabled. Use Bearer token");
            return;
        } else if (legacyUserId != null && !legacyUserId.isBlank()) {
            recordRequest("legacy-header", "accepted");
        } else {
            recordRequest("anonymous", "accepted");
        }

        try {
            filterChain.doFilter(requestToUse, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void reject(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        String requestId = response.getHeader(RequestCorrelation.REQUEST_ID_HEADER);
        ApiErrorResponse payload = new ApiErrorResponse(
                "unauthorized",
                sanitize(message),
                null,
                requestId == null ? "" : requestId);
        objectMapper.writeValue(response.getWriter(), payload);
    }

    private String sanitize(String value) {
        if (value == null) {
            return "Unauthorized";
        }
        return value.replace("\"", "'");
    }

    private void recordRequest(String mode, String result) {
        if (meterRegistry == null) {
            return;
        }
        meterRegistry.counter(
                "app.auth.requests.total",
                "mode", mode,
                "result", result)
                .increment();
    }

    private HttpServletRequest withHeader(HttpServletRequest request, String name, String value) {
        Map<String, String> customHeaders = new LinkedHashMap<>();
        customHeaders.put(name, value);
        return new CustomHeaderRequestWrapper(request, customHeaders);
    }

    private static final class CustomHeaderRequestWrapper extends HttpServletRequestWrapper {

        private final Map<String, String> customHeaders;

        private CustomHeaderRequestWrapper(HttpServletRequest request, Map<String, String> customHeaders) {
            super(request);
            this.customHeaders = customHeaders;
        }

        @Override
        public String getHeader(String name) {
            String normalized = normalize(name);
            for (Map.Entry<String, String> entry : customHeaders.entrySet()) {
                if (normalize(entry.getKey()).equals(normalized)) {
                    return entry.getValue();
                }
            }
            return super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            String header = getHeader(name);
            if (header == null) {
                return super.getHeaders(name);
            }
            return new Vector<>(List.of(header)).elements();
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            List<String> names = new ArrayList<>();
            Enumeration<String> original = super.getHeaderNames();
            while (original.hasMoreElements()) {
                names.add(original.nextElement());
            }
            for (String customKey : customHeaders.keySet()) {
                boolean exists = names.stream().anyMatch(existing -> normalize(existing).equals(normalize(customKey)));
                if (!exists) {
                    names.add(customKey);
                }
            }
            return new Vector<>(names).elements();
        }

        private String normalize(String value) {
            return value == null ? "" : value.toLowerCase(Locale.ROOT);
        }
    }
}
