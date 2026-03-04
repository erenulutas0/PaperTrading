package com.finance.core.web;

import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Component
public class CurrentUserIdArgumentResolver implements HandlerMethodArgumentResolver {

    private static final String LEGACY_USER_ID_HEADER = "X-User-Id";

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        if (!parameter.hasParameterAnnotation(CurrentUserId.class)) {
            return false;
        }
        Class<?> type = parameter.getParameterType();
        return UUID.class.equals(type) || String.class.equals(type);
    }

    @Override
    public Object resolveArgument(
            @NonNull MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            @NonNull NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory) {

        CurrentUserId annotation = parameter.getParameterAnnotation(CurrentUserId.class);
        boolean required = annotation == null || annotation.required();

        String rawUserId = resolveRawUserId(webRequest);
        if (rawUserId == null || rawUserId.isBlank()) {
            if (required) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing authenticated user");
            }
            return null;
        }

        UUID userId = parseUuid(rawUserId, required);
        if (userId == null) {
            return null;
        }

        if (String.class.equals(parameter.getParameterType())) {
            return userId.toString();
        }
        return userId;
    }

    private String resolveRawUserId(NativeWebRequest webRequest) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null) {
            Object principal = authentication.getPrincipal();
            if (principal instanceof String principalString && !principalString.isBlank()) {
                if (isUuid(principalString)) {
                    return principalString;
                }
            }
        }
        return webRequest.getHeader(LEGACY_USER_ID_HEADER);
    }

    private UUID parseUuid(String raw, boolean required) {
        try {
            return UUID.fromString(raw);
        } catch (Exception e) {
            if (!required) {
                return null;
            }
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid authenticated user id");
        }
    }

    private boolean isUuid(String raw) {
        try {
            UUID.fromString(raw);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
