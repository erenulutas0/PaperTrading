import { useParams, Link } from "react-router";
import { ArrowLeft, Trophy, Users, Clock, TrendingUp } from "lucide-react";
import { Button } from "../components/ui/button";
import { Card } from "../components/ui/card";
import { Badge } from "../components/ui/badge";
import { Progress } from "../components/ui/progress";
import { mockTournaments, mockLeaderboard } from "../data/mockData";
import { formatCurrency, formatPercent } from "../utils/formatters";

export function TournamentLiveHub() {
  const { id } = useParams();
  const tournament = mockTournaments.find(t => t.id === id) || mockTournaments[0];
  const topParticipants = mockLeaderboard.slice(0, 10);

  // Calculate progress (mock)
  const startDate = new Date(tournament.startDate);
  const endDate = new Date(tournament.endDate);
  const now = new Date();
  const totalDuration = endDate.getTime() - startDate.getTime();
  const elapsed = now.getTime() - startDate.getTime();
  const progress = Math.min(100, Math.max(0, (elapsed / totalDuration) * 100));

  return (
    <div className="min-h-screen bg-background">
      <div className="container mx-auto px-4 py-6 space-y-6">
        {/* Back Button */}
        <Button variant="ghost" size="sm" asChild>
          <Link to="/tournaments">
            <ArrowLeft className="w-4 h-4 mr-2" />
            Back to Tournaments
          </Link>
        </Button>

        {/* Tournament Header */}
        <Card className="p-6 glass-panel border-primary bg-gradient-to-br from-primary/5 to-transparent">
          <div className="space-y-4">
            <div className="flex items-start justify-between">
              <div className="flex-1">
                <div className="flex items-center gap-2 mb-2">
                  <Trophy className="w-6 h-6 text-primary" />
                  <h1 className="text-3xl font-bold">{tournament.name}</h1>
                  <Badge className="bg-success/10 text-success border-success/20 animate-pulse">
                    LIVE
                  </Badge>
                </div>
                <p className="text-muted-foreground">{tournament.description}</p>
              </div>
            </div>

            {/* Stats Grid */}
            <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
              <div>
                <div className="text-sm text-muted-foreground mb-1">Participants</div>
                <div className="text-2xl font-bold">{tournament.participants.toLocaleString()}</div>
              </div>
              <div>
                <div className="text-sm text-muted-foreground mb-1">Prize Pool</div>
                <div className="text-2xl font-bold">{tournament.prizePool}</div>
              </div>
              <div>
                <div className="text-sm text-muted-foreground mb-1">Days Left</div>
                <div className="text-2xl font-bold">
                  {Math.ceil((endDate.getTime() - now.getTime()) / (1000 * 60 * 60 * 24))}
                </div>
              </div>
              <div>
                <div className="text-sm text-muted-foreground mb-1">Your Rank</div>
                <div className="text-2xl font-bold text-primary">4th</div>
              </div>
            </div>

            {/* Progress Bar */}
            <div className="space-y-2">
              <div className="flex items-center justify-between text-sm">
                <span className="text-muted-foreground">Tournament Progress</span>
                <span className="font-medium">{progress.toFixed(0)}%</span>
              </div>
              <Progress value={progress} className="h-2" />
            </div>
          </div>
        </Card>

        {/* Live Leaderboard */}
        <div className="space-y-4">
          <div className="flex items-center justify-between">
            <h2 className="text-2xl font-bold">Live Leaderboard</h2>
            <Button variant="outline" size="sm">
              <Clock className="w-4 h-4 mr-2" />
              Auto-refresh
            </Button>
          </div>

          <Card className="glass-panel border-border overflow-hidden">
            <div className="overflow-x-auto">
              <table className="w-full">
                <thead>
                  <tr className="border-b border-border">
                    <th className="text-left p-4 text-sm font-medium text-muted-foreground">Rank</th>
                    <th className="text-left p-4 text-sm font-medium text-muted-foreground">Trader</th>
                    <th className="text-left p-4 text-sm font-medium text-muted-foreground">Portfolio</th>
                    <th className="text-right p-4 text-sm font-medium text-muted-foreground">Return</th>
                    <th className="text-right p-4 text-sm font-medium text-muted-foreground">Trades</th>
                    <th className="text-right p-4 text-sm font-medium text-muted-foreground">Win Rate</th>
                    <th className="text-center p-4 text-sm font-medium text-muted-foreground">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {topParticipants.map((entry, index) => (
                    <tr 
                      key={entry.userId} 
                      className={`border-b border-border last:border-0 hover:bg-accent/50 transition-colors ${
                        entry.rank === 4 ? 'bg-primary/5' : ''
                      }`}
                    >
                      <td className="p-4">
                        <div className="text-lg font-bold">
                          {entry.rank <= 3 ? (
                            <span>{entry.rank === 1 ? '🥇' : entry.rank === 2 ? '🥈' : '🥉'}</span>
                          ) : (
                            entry.rank
                          )}
                        </div>
                      </td>
                      <td className="p-4">
                        <Link to={`/profile/${entry.username}`} className="flex items-center gap-2 hover:text-primary">
                          <div className="w-8 h-8 rounded-full bg-gradient-to-br from-primary to-secondary flex items-center justify-center text-sm font-bold">
                            {entry.username.charAt(0).toUpperCase()}
                          </div>
                          <span className="font-medium">@{entry.username}</span>
                        </Link>
                      </td>
                      <td className="p-4">
                        <Link to={`/portfolio/${entry.portfolioId}`} className="text-muted-foreground hover:text-primary">
                          {entry.portfolioName}
                        </Link>
                      </td>
                      <td className="p-4 text-right">
                        <div className={`font-bold ${entry.returnPercent >= 0 ? 'text-success' : 'text-destructive'}`}>
                          {formatPercent(entry.returnPercent)}
                        </div>
                      </td>
                      <td className="p-4 text-right font-medium">{entry.trades}</td>
                      <td className="p-4 text-right font-medium">{entry.winRate.toFixed(1)}%</td>
                      <td className="p-4 text-center">
                        <Button size="sm" variant="ghost" asChild>
                          <Link to={`/portfolio/${entry.portfolioId}`}>View</Link>
                        </Button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </Card>
        </div>

        {/* Your Performance */}
        <Card className="p-6 glass-panel border-border">
          <h2 className="text-xl font-bold mb-4">Your Tournament Performance</h2>
          <div className="grid md:grid-cols-3 gap-4">
            <div className="p-4 rounded-lg bg-accent/50">
              <div className="text-sm text-muted-foreground mb-1">Current Return</div>
              <div className="text-2xl font-bold text-success">+25.84%</div>
              <div className="text-sm text-success">+$25,840.50</div>
            </div>
            <div className="p-4 rounded-lg bg-accent/50">
              <div className="text-sm text-muted-foreground mb-1">Total Trades</div>
              <div className="text-2xl font-bold">45</div>
              <div className="text-sm text-muted-foreground">62.2% win rate</div>
            </div>
            <div className="p-4 rounded-lg bg-accent/50">
              <div className="text-sm text-muted-foreground mb-1">Rank</div>
              <div className="text-2xl font-bold text-primary">4th</div>
              <div className="text-sm text-muted-foreground flex items-center gap-1">
                <TrendingUp className="w-3 h-3" />
                <span>Up 2 places</span>
              </div>
            </div>
          </div>
        </Card>
      </div>
    </div>
  );
}
