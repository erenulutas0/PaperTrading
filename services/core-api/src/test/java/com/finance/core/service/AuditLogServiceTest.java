package com.finance.core.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.core.domain.AuditActionType;
import com.finance.core.domain.AuditResourceType;
import com.finance.core.repository.AuditLogRepository;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuditLogServiceTest {

    @Test
    void record_whenDetailsSerializationFails_shouldThrowIllegalStateException() throws Exception {
        AuditLogRepository repository = mock(AuditLogRepository.class);
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        when(objectMapper.writeValueAsString(Map.of("key", "value")))
                .thenThrow(new JsonProcessingException("boom") { });

        AuditLogService service = new AuditLogService(repository, objectMapper);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                service.record(
                        UUID.randomUUID(),
                        AuditActionType.PORTFOLIO_CREATED,
                        AuditResourceType.PORTFOLIO,
                        UUID.randomUUID(),
                        Map.of("key", "value")));

        assertEquals("Failed to serialize audit details", exception.getMessage());
    }
}
