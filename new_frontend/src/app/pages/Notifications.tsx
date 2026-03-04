import { Bell, Check, Trash2, TrendingUp, TrendingDown, Users, Trophy, MessageSquare } from "lucide-react";
import { Button } from "../components/ui/button";
import { Card } from "../components/ui/card";
import { Badge } from "../components/ui/badge";
import { mockNotifications } from "../data/mockData";
import { formatRelativeTime } from "../utils/formatters";
import { Link } from "react-router";

export function Notifications() {
  const unreadCount = mockNotifications.filter(n => !n.read).length;

  const getIcon = (type: string) => {
    switch (type) {
      case 'TRADE':
        return <TrendingUp className="w-5 h-5 text-primary" />;
      case 'SOCIAL':
        return <Users className="w-5 h-5 text-secondary" />;
      case 'TOURNAMENT':
        return <Trophy className="w-5 h-5 text-warning" />;
      case 'ANALYSIS':
        return <MessageSquare className="w-5 h-5 text-primary" />;
      default:
        return <Bell className="w-5 h-5" />;
    }
  };

  return (
    <div className="min-h-screen bg-background">
      <div className="container mx-auto px-4 py-6">
        <div className="max-w-3xl mx-auto space-y-6">
          {/* Header */}
          <div className="flex items-center justify-between">
            <div>
              <h1 className="text-3xl font-bold flex items-center gap-2">
                <Bell className="w-8 h-8" />
                Notifications
              </h1>
              <p className="text-muted-foreground">
                {unreadCount > 0 ? `${unreadCount} unread notifications` : 'All caught up!'}
              </p>
            </div>
            <Button variant="outline" size="sm">
              <Check className="w-4 h-4 mr-2" />
              Mark all read
            </Button>
          </div>

          {/* Notifications List */}
          <div className="space-y-2">
            {mockNotifications.map((notification) => (
              <Card 
                key={notification.id} 
                className={`p-4 glass-panel ${
                  notification.read 
                    ? 'border-border opacity-75' 
                    : 'border-primary/30 bg-primary/5'
                } hover:border-primary/50 transition-colors`}
              >
                {notification.link ? (
                  <Link to={notification.link}>
                    <div className="flex items-start gap-4">
                      <div className={`w-10 h-10 rounded-lg flex items-center justify-center ${
                        notification.read ? 'bg-accent' : 'bg-primary/10'
                      }`}>
                        {getIcon(notification.type)}
                      </div>
                      <div className="flex-1 min-w-0">
                        <div className="flex items-start justify-between gap-2 mb-1">
                          <div className="font-medium">{notification.title}</div>
                          {!notification.read && (
                            <Badge className="bg-primary text-primary-foreground text-xs">New</Badge>
                          )}
                        </div>
                        <p className="text-sm text-muted-foreground mb-2">
                          {notification.message}
                        </p>
                        <div className="text-xs text-muted-foreground">
                          {formatRelativeTime(notification.timestamp)}
                        </div>
                      </div>
                      <Button variant="ghost" size="icon" className="shrink-0">
                        <Trash2 className="w-4 h-4" />
                      </Button>
                    </div>
                  </Link>
                ) : (
                  <div className="flex items-start gap-4">
                    <div className={`w-10 h-10 rounded-lg flex items-center justify-center ${
                      notification.read ? 'bg-accent' : 'bg-primary/10'
                    }`}>
                      {getIcon(notification.type)}
                    </div>
                    <div className="flex-1 min-w-0">
                      <div className="flex items-start justify-between gap-2 mb-1">
                        <div className="font-medium">{notification.title}</div>
                        {!notification.read && (
                          <Badge className="bg-primary text-primary-foreground text-xs">New</Badge>
                        )}
                      </div>
                      <p className="text-sm text-muted-foreground mb-2">
                        {notification.message}
                      </p>
                      <div className="text-xs text-muted-foreground">
                        {formatRelativeTime(notification.timestamp)}
                      </div>
                    </div>
                    <Button variant="ghost" size="icon" className="shrink-0">
                      <Trash2 className="w-4 h-4" />
                    </Button>
                  </div>
                )}
              </Card>
            ))}
          </div>

          {/* Empty State (if no notifications) */}
          {mockNotifications.length === 0 && (
            <Card className="p-12 glass-panel border-border text-center">
              <Bell className="w-16 h-16 mx-auto mb-4 text-muted-foreground opacity-50" />
              <h3 className="text-xl font-bold mb-2">No notifications</h3>
              <p className="text-muted-foreground">
                You're all caught up! Check back later for updates.
              </p>
            </Card>
          )}
        </div>
      </div>
    </div>
  );
}
