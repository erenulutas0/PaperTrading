package com.finance.core.controller;

import com.finance.core.web.ApiErrorResponse;
import com.finance.core.web.ApiRequestException;
import com.finance.core.web.RequestCorrelation;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleRuntime_keepsGenericRuntimeNotFoundTextInBadRequestBucket() {
        HttpServletRequest request = requestWithId("global-runtime-1");

        ResponseEntity<ApiErrorResponse> response = handler.handleRuntime(
                new RuntimeException("Ghost portfolio not found somewhere"),
                request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("bad_request", response.getBody().code());
        assertEquals("Ghost portfolio not found somewhere", response.getBody().message());
        assertEquals("global-runtime-1", response.getBody().requestId());
    }

    @Test
    void handleApiRequestException_preservesTypedNotFoundContract() {
        HttpServletRequest request = requestWithId("global-runtime-2");

        ResponseEntity<ApiErrorResponse> response = handler.handleApiRequestException(
                ApiRequestException.notFound("user_not_found", "User not found"),
                request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("user_not_found", response.getBody().code());
        assertEquals("User not found", response.getBody().message());
        assertEquals("global-runtime-2", response.getBody().requestId());
    }

    private HttpServletRequest requestWithId(String requestId) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute(RequestCorrelation.REQUEST_ID_ATTRIBUTE)).thenReturn(requestId);
        return request;
    }
}
