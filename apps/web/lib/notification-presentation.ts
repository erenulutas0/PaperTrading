export interface NotificationPresentationInput {
    type: string;
    actorUsername?: string | null;
    referenceLabel?: string | null;
    referenceId?: string | null;
}

function referenceLabel(input: NotificationPresentationInput): string {
    return input.referenceLabel?.trim() || 'this item';
}

export function getNotificationMessage(input: NotificationPresentationInput): string {
    const label = referenceLabel(input);

    switch (input.type) {
        case 'FOLLOW':
            return 'started following you';
        case 'PORTFOLIO_LIKE':
            return `liked your portfolio "${label}"`;
        case 'POST_LIKE':
            return `liked your analysis "${label}"`;
        case 'PORTFOLIO_COMMENT':
            return `commented on your portfolio "${label}"`;
        case 'POST_COMMENT':
            return `commented on your analysis "${label}"`;
        case 'PORTFOLIO_COMMENT_LIKE':
            return `liked your portfolio comment on "${label}"`;
        case 'POST_COMMENT_LIKE':
            return `liked your analysis comment on "${label}"`;
        case 'PORTFOLIO_COMMENT_REPLY':
            return `replied to your portfolio comment on "${label}"`;
        case 'POST_COMMENT_REPLY':
            return `replied to your analysis comment on "${label}"`;
        case 'PORTFOLIO_JOINED':
            return `joined your portfolio "${label}"`;
        case 'PRICE_ALERT':
            return label;
        default:
            return 'sent a notification';
    }
}

export function getNotificationIcon(type: string): string {
    switch (type) {
        case 'FOLLOW':
            return '👤';
        case 'PORTFOLIO_LIKE':
            return '❤️';
        case 'POST_LIKE':
            return '💙';
        case 'PORTFOLIO_COMMENT':
        case 'POST_COMMENT':
            return '💬';
        case 'PORTFOLIO_COMMENT_LIKE':
        case 'POST_COMMENT_LIKE':
            return '💟';
        case 'PORTFOLIO_COMMENT_REPLY':
        case 'POST_COMMENT_REPLY':
            return '↩️';
        case 'PORTFOLIO_JOINED':
            return '🤝';
        case 'PRICE_ALERT':
            return '🔔';
        default:
            return '📢';
    }
}

export function getNotificationLink(input: NotificationPresentationInput): string {
    if (input.type === 'FOLLOW' && input.referenceId) {
        return `/profile/${input.referenceId}`;
    }
    if (input.type === 'PRICE_ALERT') {
        return '/watchlist';
    }
    if (input.type.startsWith('PORTFOLIO')) {
        return `/dashboard/portfolio/${input.referenceId}`;
    }
    if (input.type.startsWith('POST')) {
        return `/dashboard/analysis/${input.referenceId}`;
    }
    return '/dashboard';
}
