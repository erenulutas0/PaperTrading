package com.finance.core.service;

import com.finance.core.domain.MarketChartNote;
import com.finance.core.dto.MarketChartNoteRequest;
import com.finance.core.dto.MarketChartNoteResponse;
import com.finance.core.dto.MarketType;
import com.finance.core.repository.MarketChartNoteRepository;
import com.finance.core.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketChartNoteServiceTest {

    @Mock
    private MarketChartNoteRepository marketChartNoteRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private MarketChartNoteService marketChartNoteService;

    @Test
    void createNote_requiresExistingUser() {
        UUID userId = UUID.fromString("20000000-0000-0000-0000-000000000001");
        when(userRepository.existsById(userId)).thenReturn(false);

        RuntimeException exception = assertThrows(RuntimeException.class, () -> marketChartNoteService.createNote(userId, new MarketChartNoteRequest()));

        assertEquals("User not found", exception.getMessage());
        verify(userRepository).existsById(userId);
        verify(marketChartNoteRepository, never()).save(any(MarketChartNote.class));
    }

    @Test
    void createNote_normalizesAndPersistsOwnedNote() {
        UUID userId = UUID.fromString("20000000-0000-0000-0000-000000000002");
        when(userRepository.existsById(userId)).thenReturn(true);
        when(marketChartNoteRepository.save(any(MarketChartNote.class))).thenAnswer(invocation -> {
            MarketChartNote note = invocation.getArgument(0);
            note.setId(UUID.fromString("20000000-0000-0000-0000-000000000003"));
            return note;
        });

        MarketChartNoteRequest request = new MarketChartNoteRequest();
        request.setMarket(MarketType.BIST100);
        request.setSymbol(" thyao ");
        request.setBody("  delayed breakout note  ");
        request.setPinned(Boolean.TRUE);

        MarketChartNoteResponse response = marketChartNoteService.createNote(userId, request);

        ArgumentCaptor<MarketChartNote> captor = ArgumentCaptor.forClass(MarketChartNote.class);
        verify(marketChartNoteRepository).save(captor.capture());
        MarketChartNote saved = captor.getValue();
        assertEquals(userId, saved.getUserId());
        assertEquals(MarketType.BIST100, saved.getMarket());
        assertEquals("THYAO", saved.getSymbol());
        assertEquals("delayed breakout note", saved.getBody());
        assertEquals(true, saved.isPinned());
        assertEquals("THYAO", response.getSymbol());
        assertEquals("delayed breakout note", response.getBody());
    }

    @Test
    void updateNote_nonOwnedNoteThrowsStableNotFound() {
        UUID userId = UUID.fromString("20000000-0000-0000-0000-000000000004");
        UUID noteId = UUID.fromString("20000000-0000-0000-0000-000000000005");
        when(userRepository.existsById(userId)).thenReturn(true);
        when(marketChartNoteRepository.findByIdAndUserId(noteId, userId)).thenReturn(Optional.empty());

        MarketChartNoteRequest request = new MarketChartNoteRequest();
        request.setBody("updated");

        RuntimeException exception = assertThrows(RuntimeException.class, () -> marketChartNoteService.updateNote(userId, noteId, request));

        assertEquals("Chart note not found", exception.getMessage());
        verify(userRepository).existsById(userId);
    }
}
