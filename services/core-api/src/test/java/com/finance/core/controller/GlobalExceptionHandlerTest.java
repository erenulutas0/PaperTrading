package com.finance.core.controller;

import com.finance.core.web.ApiErrorResponse;
import com.finance.core.web.ApiRequestException;
import com.finance.core.web.RequestCorrelation;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleRuntime_keepsGenericRuntimeNotFoundTextInBadRequestBucketWithoutLeakingMessage() {
        HttpServletRequest request = requestWithId("global-runtime-1");

        ResponseEntity<ApiErrorResponse> response = handler.handleRuntime(
                new RuntimeException("Ghost portfolio not found somewhere"),
                request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("bad_request", response.getBody().code());
        assertEquals("Unexpected error", response.getBody().message());
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

    @Test
    void handleMethodArgumentTypeMismatch_returnsCorrelatedInvalidRequestParameterContract() {
        HttpServletRequest request = requestWithId("global-runtime-3");

        ResponseEntity<ApiErrorResponse> response = handler.handleMethodArgumentTypeMismatch(
                new MethodArgumentTypeMismatchException("abc", Integer.class, "page", null, new NumberFormatException()),
                request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("invalid_request_parameter", response.getBody().code());
        assertEquals("Invalid request parameter", response.getBody().message());
        assertEquals("global-runtime-3", response.getBody().requestId());
        assertInstanceOf(java.util.Map.class, response.getBody().details());
        java.util.Map<?, ?> details = (java.util.Map<?, ?>) response.getBody().details();
        assertEquals("page", details.get("parameter"));
        assertEquals("abc", details.get("value"));
        assertEquals("Integer", details.get("expectedType"));
    }

    @Test
    void handleMissingServletRequestParameter_returnsCorrelatedMissingParameterContract() {
        HttpServletRequest request = requestWithId("global-runtime-4");

        ResponseEntity<ApiErrorResponse> response = handler.handleMissingServletRequestParameter(
                new MissingServletRequestParameterException("symbol", "String"),
                request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("missing_request_parameter", response.getBody().code());
        assertEquals("Missing required request parameter", response.getBody().message());
        assertEquals("global-runtime-4", response.getBody().requestId());
        assertInstanceOf(java.util.Map.class, response.getBody().details());
        java.util.Map<?, ?> details = (java.util.Map<?, ?>) response.getBody().details();
        assertEquals("symbol", details.get("parameter"));
        assertEquals("String", details.get("expectedType"));
    }

    @Test
    void handleHttpMessageNotReadable_returnsCorrelatedInvalidPayloadContract() {
        HttpServletRequest request = requestWithId("global-runtime-5");

        ResponseEntity<ApiErrorResponse> response = handler.handleHttpMessageNotReadable(
                new HttpMessageNotReadableException("bad json"),
                request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("invalid_request_payload", response.getBody().code());
        assertEquals("Invalid request payload", response.getBody().message());
        assertEquals("global-runtime-5", response.getBody().requestId());
    }

    @Test
    void handleResponseStatusException_usesLocaleRootForDerivedStatusCodes() {
        HttpServletRequest request = requestWithId("global-runtime-6");

        Locale previous = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("tr-TR"));
        try {
            ResponseEntity<ApiErrorResponse> response = handler.handleResponseStatusException(
                    new ResponseStatusException(HttpStatus.IM_USED),
                    request);

            assertEquals(HttpStatus.IM_USED, response.getStatusCode());
            assertEquals("im_used", response.getBody().code());
            assertEquals("IM Used", response.getBody().message());
            assertEquals("global-runtime-6", response.getBody().requestId());
        } finally {
            Locale.setDefault(previous);
        }
    }

    private HttpServletRequest requestWithId(String requestId) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute(RequestCorrelation.REQUEST_ID_ATTRIBUTE)).thenReturn(requestId);
        return request;
    }
}
