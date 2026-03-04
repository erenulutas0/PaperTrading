package com.finance.core.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CurrentUserIdArgumentResolverTest {

    private final CurrentUserIdArgumentResolver resolver = new CurrentUserIdArgumentResolver();

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void resolveArgument_withHeader_shouldReturnUuid() throws Exception {
        UUID userId = UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", userId.toString());

        Object resolved = resolver.resolveArgument(
                parameter("requiredUuid"),
                null,
                new ServletWebRequest(request),
                null);

        assertEquals(userId, resolved);
    }

    @Test
    void resolveArgument_withAnonymousPrincipalAndHeader_shouldFallbackToHeader() throws Exception {
        UUID userId = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("anonymousUser", null));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", userId.toString());

        Object resolved = resolver.resolveArgument(
                parameter("requiredUuid"),
                null,
                new ServletWebRequest(request),
                null);

        assertEquals(userId, resolved);
    }

    @Test
    void resolveArgument_optionalMissing_shouldReturnNull() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();

        Object resolved = resolver.resolveArgument(
                parameter("optionalUuid"),
                null,
                new ServletWebRequest(request),
                null);

        assertNull(resolved);
    }

    @Test
    void resolveArgument_requiredMissing_shouldThrowUnauthorized() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertThrows(ResponseStatusException.class, () -> resolver.resolveArgument(
                parameter("requiredUuid"),
                null,
                new ServletWebRequest(request),
                null));
    }

    private MethodParameter parameter(String methodName) throws Exception {
        Method method = ResolverTargets.class.getDeclaredMethod(methodName, UUID.class);
        return new MethodParameter(method, 0);
    }

    @SuppressWarnings("unused")
    private static class ResolverTargets {

        void requiredUuid(@CurrentUserId UUID userId) {
        }

        void optionalUuid(@CurrentUserId(required = false) UUID userId) {
        }
    }
}
