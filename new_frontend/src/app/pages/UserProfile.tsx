import { useParams, Link } from "react-router";
import { Settings, TrendingUp, Users, BarChart3, Award } from "lucide-react";
import { Button } from "../components/ui/button";
import { Card } from "../components/ui/card";
import { Badge } from "../components/ui/badge";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "../components/ui/tabs";
import { mockPortfolios, mockAnalyses } from "../data/mockData";
import { formatCurrency, formatPercent, formatRelativeTime } from "../utils/formatters";

export function UserProfile() {
  const { username } = useParams();
  const isOwnProfile = username === 'johndoe';

  return (
    <div className="min-h-screen bg-background">
      <div className="container mx-auto px-4 py-6 space-y-6">
        {/* Profile Header */}
        <Card className="p-6 glass-panel border-border">
          <div className="flex flex-col md:flex-row items-start md:items-center gap-6">
            <div className="w-24 h-24 rounded-full bg-gradient-to-br from-primary to-secondary flex items-center justify-center text-4xl font-bold">
              {username?.charAt(0).toUpperCase()}
            </div>
            <div className="flex-1">
              <div className="flex items-center gap-3 mb-2">
                <h1 className="text-3xl font-bold">@{username}</h1>
                <Badge className="bg-primary/10 text-primary border-primary/20">
                  ✓ Verified
                </Badge>
              </div>
              <p className="text-muted-foreground mb-4">
                Active trader since Jan 2025 • Trading stocks and options
              </p>
              <div className="flex items-center gap-6 text-sm">
                <div>
                  <span className="font-bold">128</span> <span className="text-muted-foreground">Followers</span>
                </div>
                <div>
                  <span className="font-bold">45</span> <span className="text-muted-foreground">Following</span>
                </div>
                <div>
                  <span className="font-bold">12</span> <span className="text-muted-foreground">Analyses</span>
                </div>
              </div>
            </div>
            {isOwnProfile ? (
              <Button variant="outline">
                <Settings className="w-4 h-4 mr-2" />
                Edit Profile
              </Button>
            ) : (
              <Button className="bg-gradient-to-r from-primary to-secondary">
                <Users className="w-4 h-4 mr-2" />
                Follow
              </Button>
            )}
          </div>
        </Card>

        {/* Stats Overview */}
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
          <Card className="p-4 glass-panel border-border">
            <div className="text-sm text-muted-foreground mb-1">Total Return</div>
            <div className="text-2xl font-bold text-success">+25.84%</div>
            <div className="text-sm text-success">+$25,840.50</div>
          </Card>
          <Card className="p-4 glass-panel border-border">
            <div className="text-sm text-muted-foreground mb-1">Win Rate</div>
            <div className="text-2xl font-bold">62.2%</div>
            <div className="text-sm text-muted-foreground">45 trades</div>
          </Card>
          <Card className="p-4 glass-panel border-border">
            <div className="text-sm text-muted-foreground mb-1">Best Rank</div>
            <div className="text-2xl font-bold text-primary">4th</div>
            <div className="text-sm text-muted-foreground">All-time</div>
          </Card>
          <Card className="p-4 glass-panel border-border">
            <div className="text-sm text-muted-foreground mb-1">Tournaments</div>
            <div className="text-2xl font-bold">3</div>
            <div className="text-sm text-muted-foreground">Participated</div>
          </Card>
        </div>

        {/* Tabs */}
        <Tabs defaultValue="portfolios" className="space-y-4">
          <TabsList className="glass-panel">
            <TabsTrigger value="portfolios">Portfolios</TabsTrigger>
            <TabsTrigger value="analyses">Analyses</TabsTrigger>
            <TabsTrigger value="achievements">Achievements</TabsTrigger>
          </TabsList>

          <TabsContent value="portfolios" className="space-y-4">
            {mockPortfolios.filter(p => p.isPublic).map((portfolio) => (
              <Card key={portfolio.id} className="p-6 glass-panel border-border hover:border-primary/50 transition-colors">
                <Link to={`/portfolio/${portfolio.id}`}>
                  <div className="space-y-4">
                    <div className="flex items-start justify-between">
                      <div>
                        <div className="flex items-center gap-2">
                          <h3 className="text-lg font-bold">{portfolio.name}</h3>
                          <Badge variant="outline" className="text-xs">Public</Badge>
                        </div>
                        <p className="text-sm text-muted-foreground">
                          Created {new Date(portfolio.createdAt).toLocaleDateString()}
                        </p>
                      </div>
                      <div className="text-right">
                        <div className="text-2xl font-bold">{formatCurrency(portfolio.value)}</div>
                      </div>
                    </div>

                    <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
                      <div>
                        <div className="text-xs text-muted-foreground">Day Change</div>
                        <div className={`font-medium ${portfolio.dayChange >= 0 ? 'text-success' : 'text-destructive'}`}>
                          {formatPercent(portfolio.dayChangePercent)}
                        </div>
                      </div>
                      <div>
                        <div className="text-xs text-muted-foreground">Total Return</div>
                        <div className={`font-medium ${portfolio.totalReturn >= 0 ? 'text-success' : 'text-destructive'}`}>
                          {formatPercent(portfolio.totalReturnPercent)}
                        </div>
                      </div>
                      <div>
                        <div className="text-xs text-muted-foreground">Cash</div>
                        <div className="font-medium">{formatCurrency(portfolio.cash)}</div>
                      </div>
                      <div>
                        <div className="text-xs text-muted-foreground">Followers</div>
                        <div className="font-medium">34</div>
                      </div>
                    </div>
                  </div>
                </Link>
              </Card>
            ))}
          </TabsContent>

          <TabsContent value="analyses" className="space-y-4">
            <div className="grid md:grid-cols-2 gap-4">
              {mockAnalyses.slice(0, 4).map((analysis) => (
                <Card key={analysis.id} className="p-4 glass-panel border-border hover:border-primary/50 transition-colors">
                  <Link to={`/analysis/${analysis.id}`}>
                    <div className="space-y-3">
                      <h3 className="font-bold leading-tight">{analysis.title}</h3>
                      <p className="text-sm text-muted-foreground line-clamp-2">
                        {analysis.content}
                      </p>
                      <div className="flex items-center justify-between text-sm">
                        <div className="text-muted-foreground">
                          {formatRelativeTime(analysis.createdAt)}
                        </div>
                        <div className="flex items-center gap-3">
                          <span>{analysis.likes} likes</span>
                          <span>{analysis.comments} comments</span>
                        </div>
                      </div>
                    </div>
                  </Link>
                </Card>
              ))}
            </div>
          </TabsContent>

          <TabsContent value="achievements" className="space-y-4">
            <div className="grid md:grid-cols-3 gap-4">
              <Card className="p-6 glass-panel border-border text-center">
                <Award className="w-12 h-12 mx-auto mb-3 text-primary" />
                <h3 className="font-bold mb-1">First Trade</h3>
                <p className="text-sm text-muted-foreground">Executed your first trade</p>
              </Card>
              <Card className="p-6 glass-panel border-border text-center">
                <Award className="w-12 h-12 mx-auto mb-3 text-secondary" />
                <h3 className="font-bold mb-1">Top 10 Trader</h3>
                <p className="text-sm text-muted-foreground">Ranked in top 10 on leaderboard</p>
              </Card>
              <Card className="p-6 glass-panel border-border text-center">
                <Award className="w-12 h-12 mx-auto mb-3 text-warning" />
                <h3 className="font-bold mb-1">Tournament Finalist</h3>
                <p className="text-sm text-muted-foreground">Top 5 in a tournament</p>
              </Card>
            </div>
          </TabsContent>
        </Tabs>
      </div>
    </div>
  );
}
