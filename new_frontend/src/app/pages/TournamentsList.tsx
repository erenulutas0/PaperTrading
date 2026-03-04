import { Link } from "react-router";
import { Trophy, Users, Calendar, Clock } from "lucide-react";
import { Button } from "../components/ui/button";
import { Card } from "../components/ui/card";
import { Badge } from "../components/ui/badge";
import { mockTournaments } from "../data/mockData";
import { formatDate } from "../utils/formatters";

export function TournamentsList() {
  return (
    <div className="min-h-screen bg-background">
      <div className="container mx-auto px-4 py-6 space-y-6">
        {/* Header */}
        <div>
          <h1 className="text-3xl font-bold flex items-center gap-2">
            <Trophy className="w-8 h-8 text-primary" />
            Tournaments
          </h1>
          <p className="text-muted-foreground">Compete against other traders in time-limited challenges</p>
        </div>

        {/* Filters */}
        <div className="flex items-center gap-2 overflow-x-auto pb-2">
          <Button variant="outline" size="sm" className="bg-primary/10 text-primary border-primary/20">
            All
          </Button>
          <Button variant="outline" size="sm">
            Live
          </Button>
          <Button variant="outline" size="sm">
            Upcoming
          </Button>
          <Button variant="outline" size="sm">
            Ended
          </Button>
        </div>

        {/* Tournament Grid */}
        <div className="grid md:grid-cols-2 gap-6">
          {mockTournaments.map((tournament) => (
            <Card 
              key={tournament.id} 
              className={`p-6 glass-panel ${
                tournament.status === 'LIVE' 
                  ? 'border-primary bg-gradient-to-br from-primary/5 to-transparent' 
                  : 'border-border'
              } hover:border-primary/50 transition-colors`}
            >
              <div className="space-y-4">
                {/* Header */}
                <div className="flex items-start justify-between">
                  <div className="flex-1">
                    <div className="flex items-center gap-2 mb-2">
                      <h3 className="text-xl font-bold">{tournament.name}</h3>
                      <Badge 
                        className={
                          tournament.status === 'LIVE'
                            ? 'bg-success/10 text-success border-success/20 animate-pulse'
                            : tournament.status === 'UPCOMING'
                            ? 'bg-warning/10 text-warning border-warning/20'
                            : 'bg-muted text-muted-foreground'
                        }
                      >
                        {tournament.status}
                      </Badge>
                    </div>
                    <p className="text-sm text-muted-foreground">{tournament.description}</p>
                  </div>
                </div>

                {/* Stats */}
                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <div className="flex items-center gap-2 text-muted-foreground mb-1">
                      <Users className="w-4 h-4" />
                      <span className="text-xs">Participants</span>
                    </div>
                    <div className="text-xl font-bold">{tournament.participants.toLocaleString()}</div>
                  </div>
                  <div>
                    <div className="flex items-center gap-2 text-muted-foreground mb-1">
                      <Trophy className="w-4 h-4" />
                      <span className="text-xs">Prize Pool</span>
                    </div>
                    <div className="text-xl font-bold">{tournament.prizePool}</div>
                  </div>
                </div>

                {/* Dates */}
                <div className="space-y-2 p-3 rounded-lg bg-accent/50 border border-border">
                  <div className="flex items-center gap-2 text-sm">
                    <Calendar className="w-4 h-4 text-muted-foreground" />
                    <span className="text-muted-foreground">Start:</span>
                    <span className="font-medium">{formatDate(tournament.startDate)}</span>
                  </div>
                  <div className="flex items-center gap-2 text-sm">
                    <Clock className="w-4 h-4 text-muted-foreground" />
                    <span className="text-muted-foreground">End:</span>
                    <span className="font-medium">{formatDate(tournament.endDate)}</span>
                  </div>
                </div>

                {/* Rules */}
                <div className="p-3 rounded-lg bg-muted/20">
                  <div className="text-xs text-muted-foreground mb-1">Rules</div>
                  <div className="text-sm">{tournament.rules}</div>
                </div>

                {/* Actions */}
                <div className="flex gap-2">
                  {tournament.status === 'LIVE' ? (
                    <Button asChild className="flex-1 bg-gradient-to-r from-primary to-secondary">
                      <Link to={`/tournament/${tournament.id}`}>
                        View Live Hub
                      </Link>
                    </Button>
                  ) : tournament.status === 'UPCOMING' ? (
                    <Button asChild className="flex-1" variant="outline">
                      <Link to={`/tournament/${tournament.id}`}>
                        Register Now
                      </Link>
                    </Button>
                  ) : (
                    <Button asChild className="flex-1" variant="outline">
                      <Link to={`/tournament/${tournament.id}`}>
                        View Results
                      </Link>
                    </Button>
                  )}
                  <Button variant="outline">Details</Button>
                </div>
              </div>
            </Card>
          ))}
        </div>
      </div>
    </div>
  );
}
