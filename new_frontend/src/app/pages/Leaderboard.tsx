import { Link } from "react-router";
import { Trophy, TrendingUp, TrendingDown, ChevronUp, ChevronDown, Minus } from "lucide-react";
import { Button } from "../components/ui/button";
import { Card } from "../components/ui/card";
import { Badge } from "../components/ui/badge";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "../components/ui/tabs";
import { mockLeaderboard } from "../data/mockData";
import { formatCurrency, formatPercent } from "../utils/formatters";

export function Leaderboard() {
  const getRankIcon = (rank: number) => {
    if (rank === 1) return "🥇";
    if (rank === 2) return "🥈";
    if (rank === 3) return "🥉";
    return rank;
  };

  const getRankChange = (entry: typeof mockLeaderboard[0]) => {
    if (!entry.previousRank) return null;
    const change = entry.previousRank - entry.rank;
    if (change > 0) {
      return (
        <div className="flex items-center text-success text-sm">
          <ChevronUp className="w-4 h-4" />
          <span>{change}</span>
        </div>
      );
    }
    if (change < 0) {
      return (
        <div className="flex items-center text-destructive text-sm">
          <ChevronDown className="w-4 h-4" />
          <span>{Math.abs(change)}</span>
        </div>
      );
    }
    return (
      <div className="flex items-center text-muted-foreground text-sm">
        <Minus className="w-4 h-4" />
      </div>
    );
  };

  return (
    <div className="min-h-screen bg-background">
      <div className="container mx-auto px-4 py-6 space-y-6">
        {/* Header */}
        <div>
          <h1 className="text-3xl font-bold flex items-center gap-2">
            <Trophy className="w-8 h-8 text-primary" />
            Leaderboard
          </h1>
          <p className="text-muted-foreground">Top performing traders ranked by verified returns</p>
        </div>

        {/* Period Tabs */}
        <Tabs defaultValue="all" className="space-y-6">
          <TabsList className="glass-panel">
            <TabsTrigger value="1d">1 Day</TabsTrigger>
            <TabsTrigger value="1w">1 Week</TabsTrigger>
            <TabsTrigger value="1m">1 Month</TabsTrigger>
            <TabsTrigger value="all">All Time</TabsTrigger>
          </TabsList>

          <TabsContent value="all" className="space-y-4">
            {/* Top 3 Highlight */}
            <div className="grid md:grid-cols-3 gap-4">
              {mockLeaderboard.slice(0, 3).map((entry) => (
                <Card 
                  key={entry.userId} 
                  className={`p-6 glass-panel ${
                    entry.rank === 1 
                      ? 'border-primary bg-gradient-to-br from-primary/5 to-transparent' 
                      : 'border-border'
                  }`}
                >
                  <Link to={`/profile/${entry.username}`}>
                    <div className="text-center space-y-3">
                      <div className="text-4xl">{getRankIcon(entry.rank)}</div>
                      <div className="w-16 h-16 mx-auto rounded-full bg-gradient-to-br from-primary to-secondary flex items-center justify-center text-2xl font-bold">
                        {entry.username.charAt(0).toUpperCase()}
                      </div>
                      <div>
                        <div className="flex items-center justify-center gap-2">
                          <div className="font-bold">@{entry.username}</div>
                          {entry.verified && (
                            <Badge className="bg-primary/10 text-primary border-primary/20 text-xs">
                              ✓
                            </Badge>
                          )}
                        </div>
                        <div className="text-sm text-muted-foreground">{entry.portfolioName}</div>
                      </div>
                      <div className={`text-2xl font-bold ${entry.returnPercent >= 0 ? 'text-success' : 'text-destructive'}`}>
                        {formatPercent(entry.returnPercent)}
                      </div>
                      <div className="grid grid-cols-2 gap-2 text-xs">
                        <div>
                          <div className="text-muted-foreground">Trades</div>
                          <div className="font-medium">{entry.trades}</div>
                        </div>
                        <div>
                          <div className="text-muted-foreground">Win Rate</div>
                          <div className="font-medium">{entry.winRate.toFixed(1)}%</div>
                        </div>
                      </div>
                    </div>
                  </Link>
                </Card>
              ))}
            </div>

            {/* Full Leaderboard Table */}
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
                      <th className="text-center p-4 text-sm font-medium text-muted-foreground">Change</th>
                    </tr>
                  </thead>
                  <tbody>
                    {mockLeaderboard.map((entry) => (
                      <tr key={entry.userId} className="border-b border-border last:border-0 hover:bg-accent/50 transition-colors">
                        <td className="p-4">
                          <div className="text-lg font-bold">{getRankIcon(entry.rank)}</div>
                        </td>
                        <td className="p-4">
                          <Link to={`/profile/${entry.username}`} className="flex items-center gap-2 hover:text-primary">
                            <div className="w-8 h-8 rounded-full bg-gradient-to-br from-primary to-secondary flex items-center justify-center text-sm font-bold">
                              {entry.username.charAt(0).toUpperCase()}
                            </div>
                            <div>
                              <div className="flex items-center gap-1">
                                <span className="font-medium">@{entry.username}</span>
                                {entry.verified && (
                                  <Badge className="bg-primary/10 text-primary border-primary/20 text-xs">
                                    ✓
                                  </Badge>
                                )}
                              </div>
                            </div>
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
                          <div className={`text-sm ${entry.returnPercent >= 0 ? 'text-success' : 'text-destructive'}`}>
                            {formatCurrency(entry.return)}
                          </div>
                        </td>
                        <td className="p-4 text-right font-medium">{entry.trades}</td>
                        <td className="p-4 text-right font-medium">{entry.winRate.toFixed(1)}%</td>
                        <td className="p-4 text-center">{getRankChange(entry)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </Card>
          </TabsContent>

          <TabsContent value="1d" className="text-center py-12 text-muted-foreground">
            Daily leaderboard data will be available here
          </TabsContent>
          <TabsContent value="1w" className="text-center py-12 text-muted-foreground">
            Weekly leaderboard data will be available here
          </TabsContent>
          <TabsContent value="1m" className="text-center py-12 text-muted-foreground">
            Monthly leaderboard data will be available here
          </TabsContent>
        </Tabs>
      </div>
    </div>
  );
}
