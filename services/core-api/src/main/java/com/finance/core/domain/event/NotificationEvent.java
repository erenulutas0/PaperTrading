package com.finance.core.domain.event;

import com.finance.core.domain.Notification;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class NotificationEvent {
    private final UUID receiverId;
    private final UUID actorId;
    private final String actorUsername;
    private final Notification.NotificationType type;
    private final UUID referenceId;
    private final String referenceLabel;
}
