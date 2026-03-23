package com.finance.core.service;

import com.finance.core.config.WebSocketRuntimeProperties;
import com.finance.core.domain.Notification;
import com.finance.core.domain.event.NotificationEvent;
import com.finance.core.repository.NotificationRepository;
import com.finance.core.repository.UserRepository;
import com.finance.core.web.ApiRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private SimpMessagingTemplate messagingTemplate;
    @Mock
    private WebSocketRuntimeProperties webSocketRuntimeProperties;

    @InjectMocks
    private NotificationService notificationService;

    private UUID userId;
    private UUID actorId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        actorId = UUID.randomUUID();
    }

    @Test
    void handleNotificationEvent_WithValidData_ShouldSaveNotification() {
        // Arrange
        when(webSocketRuntimeProperties.isLegacyUserTopicBroadcastEnabled()).thenReturn(false);

        NotificationEvent event = NotificationEvent.builder()
                .receiverId(userId)
                .actorId(actorId)
                .actorUsername("testuser")
                .type(Notification.NotificationType.FOLLOW)
                .build();

        when(notificationRepository.save(any(Notification.class))).thenAnswer(i -> i.getArguments()[0]);

        // Act
        notificationService.handleNotificationEvent(event);

        // Assert
        verify(notificationRepository, times(1)).save(any(Notification.class));
        verify(messagingTemplate, times(1))
                .convertAndSendToUser(eq(userId.toString()), eq("/queue/notifications"), any());
        verify(messagingTemplate, never())
                .convertAndSend(eq("/topic/notifications/" + userId), any(Object.class));
    }

    @Test
    void handleNotificationEvent_WhenLegacyBroadcastEnabled_ShouldPublishLegacyTopic() {
        when(webSocketRuntimeProperties.isLegacyUserTopicBroadcastEnabled()).thenReturn(true);
        NotificationEvent event = NotificationEvent.builder()
                .receiverId(userId)
                .actorId(actorId)
                .actorUsername("testuser")
                .type(Notification.NotificationType.FOLLOW)
                .build();
        when(notificationRepository.save(any(Notification.class))).thenAnswer(i -> i.getArguments()[0]);

        notificationService.handleNotificationEvent(event);

        verify(messagingTemplate, times(1))
                .convertAndSend(eq("/topic/notifications/" + userId), any(Object.class));
    }

    @Test
    void handleNotificationEvent_WhenSelfAction_ShouldNotNotify() {
        // Arrange
        NotificationEvent event = NotificationEvent.builder()
                .receiverId(userId)
                .actorId(userId) // Self action
                .build();

        // Act
        notificationService.handleNotificationEvent(event);

        // Assert
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void createEmitter_ShouldReturnSseEmitter() {
        // Act
        when(userRepository.existsById(userId)).thenReturn(true);
        SseEmitter emitter = notificationService.createEmitter(userId);

        // Assert
        assertNotNull(emitter);
        assertEquals(Long.MAX_VALUE, emitter.getTimeout());
    }

    @Test
    void createEmitter_ShouldReplacePreviousEmitterForSameUser() {
        when(userRepository.existsById(userId)).thenReturn(true);
        SseEmitter first = notificationService.createEmitter(userId);
        SseEmitter second = notificationService.createEmitter(userId);

        assertNotNull(first);
        assertNotNull(second);
        assertNotSame(first, second);
    }

    @Test
    void getUnreadCount_ShouldReturnCount() {
        // Arrange
        when(userRepository.existsById(userId)).thenReturn(true);
        when(notificationRepository.countByUserIdAndReadFalse(userId)).thenReturn(5L);

        // Act
        long count = notificationService.getUnreadCount(userId);

        // Assert
        assertEquals(5L, count);
        verify(notificationRepository, times(1)).countByUserIdAndReadFalse(userId);
    }

    @Test
    void getUserNotifications_ShouldReturnPage() {
        // Arrange
        when(userRepository.existsById(userId)).thenReturn(true);
        PageRequest pageRequest = PageRequest.of(0, 10);
        Page<Notification> mockPage = new PageImpl<>(List.of(new Notification()));
        when(notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageRequest))
                .thenReturn(mockPage);

        // Act
        Page<Notification> result = notificationService.getUserNotifications(userId, pageRequest);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getSize());
    }

    @Test
    void markAsRead_WhenNotificationBelongsToUser_ShouldReturnTrue() {
        when(userRepository.existsById(userId)).thenReturn(true);
        Notification notification = Notification.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .read(false)
                .type(Notification.NotificationType.FOLLOW)
                .build();

        when(notificationRepository.findByIdAndUserId(notification.getId(), userId))
                .thenReturn(java.util.Optional.of(notification));

        boolean marked = notificationService.markAsRead(userId, notification.getId());

        assertTrue(marked);
        assertTrue(notification.isRead());
    }

    @Test
    void markAsRead_WhenNotificationDoesNotBelongToUser_ShouldReturnFalse() {
        when(userRepository.existsById(userId)).thenReturn(true);
        UUID notificationId = UUID.randomUUID();
        when(notificationRepository.findByIdAndUserId(notificationId, userId))
                .thenReturn(java.util.Optional.empty());

        boolean marked = notificationService.markAsRead(userId, notificationId);

        assertFalse(marked);
    }

    @Test
    void getUnreadCount_requiresExistingUser() {
        UUID missingUserId = UUID.randomUUID();
        when(userRepository.existsById(missingUserId)).thenReturn(false);

        ApiRequestException exception = assertThrows(ApiRequestException.class, () -> notificationService.getUnreadCount(missingUserId));

        assertEquals("user_not_found", exception.code());
        assertEquals("User not found", exception.getMessage());
        verify(notificationRepository, never()).countByUserIdAndReadFalse(missingUserId);
    }
}
