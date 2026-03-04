import { Link } from "react-router";
import { TrendingUp, Search, Users } from "lucide-react";
import { Button } from "../components/ui/button";
import { Card } from "../components/ui/card";
import { Badge } from "../components/ui/badge";
import { Input } from "../components/ui/input";
import { mockPortfolios, mockLeaderboard } from "../data/mockData";
import { formatCurrency, formatPercent } from "../utils/formatters";

export function DiscoverPortfolios() {
  return (
    <div className="min-h-screen bg-background">
      <div className="container mx-auto px-4 py-6 space-y-6">
        {/* Header */}
        <div>
          <h1 className="text-3xl font-bold">Discover Portfolios</h1>
          <p className="text-muted-foreground">Explore and follow top-performing public portfolios</p>
        </div>

        {/* Search */}
        <div className="relative max-w-md">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
          <Input placeholder="Search traders..." className="pl-10" />
        </div>

        {/* Filters */}
        <div className="flex items-center gap-2 overflow-x-auto pb-2">
          <Button variant="outline" size="sm" className="bg-primary/10 text-primary border-primary/20">
            All Time
          </Button>
          <Button variant="outline" size="sm">
            1 Month
          </Button>
          <Button variant="outline" size="sm">
            1 Week
          </Button>
          <Button variant="outline" size="sm">
            Today
          </Button>
        </div>

        {/* Portfolio Grid */}
        <div className="grid md:grid-cols-2 lg:grid-cols-3 gap-6">
          {mockLeaderboard.slice(0, 6).map((entry) => (
            <Card key={entry.portfolioId} className="p-6 glass-panel border-border hover:border-primary/50 transition-colors">
              <Link to={`/portfolio/${entry.portfolioId}`}>
                <div className="space-y-4">
                  {/* User Info */}
                  <div className="flex items-center gap-3">
                    <div className="w-12 h-12 rounded-full bg-gradient-to-br from-primary to-secondary flex items-center justify-center text-lg font-bold">
                      {entry.username.charAt(0).toUpperCase()}
                    </div>
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2">
                        <div className="font-bold truncate">@{entry.username}</div>
                        {entry.verified && (
                          <Badge className="bg-primary/10 text-primary border-primary/20 text-xs">
                            ✓
                          </Badge>
                        )}
                      </div>
                      <div className="text-sm text-muted-foreground truncate">
                        {entry.portfolioName}
                      </div>
                    </div>
                  </div>

                  {/* Stats */}
                  <div className="grid grid-cols-2 gap-4">
                    <div>
                      <div className="text-xs text-muted-foreground mb-1">Return</div>
                      <div className={`text-xl font-bold ${entry.returnPercent >= 0 ? 'text-success' : 'text-destructive'}`}>
                        {formatPercent(entry.returnPercent)}
                      </div>
                      <div className={`text-xs ${entry.returnPercent >= 0 ? 'text-success' : 'text-destructive'}`}>
                        {formatCurrency(entry.return)}
                      </div>
                    </div>
                    <div>
                      <div className="text-xs text-muted-foreground mb-1">Win Rate</div>
                      <div className="text-xl font-bold">{entry.winRate.toFixed(1)}%</div>
                      <div className="text-xs text-muted-foreground">{entry.trades} trades</div>
                    </div>
                  </div>

                  {/* Follow Button */}
                  <Button className="w-full" variant="outline">
                    <Users className="w-4 h-4 mr-2" />
                    Follow
                  </Button>
                </div>
              </Link>
            </Card>
          ))}
        </div>

        {/* Load More */}
        <div className="text-center">
          <Button variant="outline">Load More</Button>
        </div>
      </div>
    </div>
  );
}
