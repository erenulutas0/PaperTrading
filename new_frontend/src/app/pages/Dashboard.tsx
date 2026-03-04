import { Link } from "react-router";
import { Plus, TrendingUp, TrendingDown, Activity, DollarSign, BarChart3 } from "lucide-react";
import { Button } from "../components/ui/button";
import { Card } from "../components/ui/card";
import { Badge } from "../components/ui/badge";
import { mockPortfolios, mockPositions, mockMarketData, mockTrades } from "../data/mockData";
import { formatCurrency, formatPercent } from "../utils/formatters";

export function Dashboard() {
  const activePortfolio = mockPortfolios[0];
  const recentTrades = mockTrades.slice(0, 5);

  return (
    <div className="min-h-screen bg-background">
      <div className="container mx-auto px-4 py-6 space-y-6">
        {/* Header */}
        <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4">
          <div>
            <h1 className="text-3xl font-bold">Trading Terminal</h1>
            <p className="text-muted-foreground">Monitor markets and manage your portfolios</p>
          </div>
          <div className="flex items-center gap-2">
            <Button asChild>
              <Link to="/portfolio/1" className="flex items-center gap-2">
                <Plus className="w-4 h-4" />
                New Trade
              </Link>
            </Button>
            <Button variant="outline" asChild>
              <Link to="/analysis/new">New Analysis</Link>
            </Button>
          </div>
        </div>

        {/* Market Overview */}
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
          <Card className="p-4 glass-panel border-border">
            <div className="space-y-1">
              <div className="text-sm text-muted-foreground">S&P 500</div>
              <div className="text-2xl font-bold">{formatCurrency(mockMarketData.spy.price, 0)}</div>
              <div className={`flex items-center gap-1 text-sm ${mockMarketData.spy.change >= 0 ? 'text-success' : 'text-destructive'}`}>
                {mockMarketData.spy.change >= 0 ? <TrendingUp className="w-3 h-3" /> : <TrendingDown className="w-3 h-3" />}
                <span>{formatPercent(mockMarketData.spy.changePercent)}</span>
              </div>
            </div>
          </Card>

          <Card className="p-4 glass-panel border-border">
            <div className="space-y-1">
              <div className="text-sm text-muted-foreground">NASDAQ</div>
              <div className="text-2xl font-bold">{formatCurrency(mockMarketData.qqq.price, 0)}</div>
              <div className={`flex items-center gap-1 text-sm ${mockMarketData.qqq.change >= 0 ? 'text-success' : 'text-destructive'}`}>
                {mockMarketData.qqq.change >= 0 ? <TrendingUp className="w-3 h-3" /> : <TrendingDown className="w-3 h-3" />}
                <span>{formatPercent(mockMarketData.qqq.changePercent)}</span>
              </div>
            </div>
          </Card>

          <Card className="p-4 glass-panel border-border">
            <div className="space-y-1">
              <div className="text-sm text-muted-foreground">DOW</div>
              <div className="text-2xl font-bold">{formatCurrency(mockMarketData.dia.price, 0)}</div>
              <div className={`flex items-center gap-1 text-sm ${mockMarketData.dia.change >= 0 ? 'text-success' : 'text-destructive'}`}>
                {mockMarketData.dia.change >= 0 ? <TrendingUp className="w-3 h-3" /> : <TrendingDown className="w-3 h-3" />}
                <span>{formatPercent(mockMarketData.dia.changePercent)}</span>
              </div>
            </div>
          </Card>

          <Card className="p-4 glass-panel border-border">
            <div className="space-y-1">
              <div className="text-sm text-muted-foreground">VIX</div>
              <div className="text-2xl font-bold">{mockMarketData.vix.price.toFixed(2)}</div>
              <div className={`flex items-center gap-1 text-sm ${mockMarketData.vix.change >= 0 ? 'text-destructive' : 'text-success'}`}>
                {mockMarketData.vix.change >= 0 ? <TrendingUp className="w-3 h-3" /> : <TrendingDown className="w-3 h-3" />}
                <span>{formatPercent(mockMarketData.vix.changePercent)}</span>
              </div>
            </div>
          </Card>
        </div>

        <div className="grid md:grid-cols-3 gap-6">
          {/* Portfolio Cards */}
          <div className="md:col-span-2 space-y-4">
            <div className="flex items-center justify-between">
              <h2 className="text-xl font-bold">Your Portfolios</h2>
              <Button variant="outline" size="sm">
                <Plus className="w-4 h-4 mr-2" />
                Create Portfolio
              </Button>
            </div>

            {mockPortfolios.map((portfolio) => (
              <Card key={portfolio.id} className="p-6 glass-panel border-border hover:border-primary/50 transition-colors">
                <Link to={`/portfolio/${portfolio.id}`}>
                  <div className="space-y-4">
                    <div className="flex items-start justify-between">
                      <div>
                        <div className="flex items-center gap-2">
                          <h3 className="text-lg font-bold">{portfolio.name}</h3>
                          {portfolio.isPublic ? (
                            <Badge variant="outline" className="text-xs">Public</Badge>
                          ) : (
                            <Badge variant="secondary" className="text-xs">Private</Badge>
                          )}
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
                          {formatCurrency(portfolio.dayChange)} ({formatPercent(portfolio.dayChangePercent)})
                        </div>
                      </div>
                      <div>
                        <div className="text-xs text-muted-foreground">Total Return</div>
                        <div className={`font-medium ${portfolio.totalReturn >= 0 ? 'text-success' : 'text-destructive'}`}>
                          {formatCurrency(portfolio.totalReturn)} ({formatPercent(portfolio.totalReturnPercent)})
                        </div>
                      </div>
                      <div>
                        <div className="text-xs text-muted-foreground">Cash</div>
                        <div className="font-medium">{formatCurrency(portfolio.cash)}</div>
                      </div>
                      <div>
                        <div className="text-xs text-muted-foreground">Invested</div>
                        <div className="font-medium">{formatCurrency(portfolio.value - portfolio.cash)}</div>
                      </div>
                    </div>
                  </div>
                </Link>
              </Card>
            ))}
          </div>

          {/* Activity Feed */}
          <div className="space-y-4">
            <h2 className="text-xl font-bold">Recent Activity</h2>
            <Card className="p-4 glass-panel border-border">
              <div className="space-y-3">
                {recentTrades.map((trade) => (
                  <div key={trade.id} className="flex items-start gap-3 pb-3 border-b border-border last:border-0 last:pb-0">
                    <div className={`w-8 h-8 rounded-lg flex items-center justify-center ${
                      trade.type === 'BUY' ? 'bg-success/10' : 'bg-destructive/10'
                    }`}>
                      {trade.type === 'BUY' ? (
                        <TrendingUp className={`w-4 h-4 text-success`} />
                      ) : (
                        <TrendingDown className={`w-4 h-4 text-destructive`} />
                      )}
                    </div>
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2">
                        <span className="font-medium">{trade.type}</span>
                        <Badge variant="outline" className="text-xs">{trade.symbol}</Badge>
                      </div>
                      <div className="text-sm text-muted-foreground">
                        {trade.quantity} shares @ {formatCurrency(trade.price)}
                      </div>
                      <div className="text-xs text-muted-foreground">
                        {new Date(trade.timestamp).toLocaleString()}
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            </Card>

            {/* Quick Actions */}
            <Card className="p-4 glass-panel border-border">
              <h3 className="font-bold mb-3">Quick Actions</h3>
              <div className="space-y-2">
                <Button variant="outline" className="w-full justify-start" asChild>
                  <Link to="/analysis/new">
                    <Activity className="w-4 h-4 mr-2" />
                    Post Analysis
                  </Link>
                </Button>
                <Button variant="outline" className="w-full justify-start" asChild>
                  <Link to="/tournaments">
                    <BarChart3 className="w-4 h-4 mr-2" />
                    Join Tournament
                  </Link>
                </Button>
                <Button variant="outline" className="w-full justify-start" asChild>
                  <Link to="/discover">
                    <TrendingUp className="w-4 h-4 mr-2" />
                    Discover Traders
                  </Link>
                </Button>
              </div>
            </Card>
          </div>
        </div>

        {/* Top Positions */}
        <div className="space-y-4">
          <h2 className="text-xl font-bold">Top Positions</h2>
          <Card className="glass-panel border-border overflow-hidden">
            <div className="overflow-x-auto">
              <table className="w-full">
                <thead>
                  <tr className="border-b border-border">
                    <th className="text-left p-4 text-sm font-medium text-muted-foreground">Symbol</th>
                    <th className="text-left p-4 text-sm font-medium text-muted-foreground">Company</th>
                    <th className="text-right p-4 text-sm font-medium text-muted-foreground">Shares</th>
                    <th className="text-right p-4 text-sm font-medium text-muted-foreground">Avg Price</th>
                    <th className="text-right p-4 text-sm font-medium text-muted-foreground">Current</th>
                    <th className="text-right p-4 text-sm font-medium text-muted-foreground">Value</th>
                    <th className="text-right p-4 text-sm font-medium text-muted-foreground">P&L</th>
                  </tr>
                </thead>
                <tbody>
                  {mockPositions.map((position) => (
                    <tr key={position.id} className="border-b border-border last:border-0 hover:bg-accent/50 transition-colors">
                      <td className="p-4">
                        <div className="font-bold">{position.symbol}</div>
                      </td>
                      <td className="p-4">
                        <div className="text-sm text-muted-foreground">{position.companyName}</div>
                      </td>
                      <td className="p-4 text-right">{position.quantity}</td>
                      <td className="p-4 text-right">{formatCurrency(position.avgPrice)}</td>
                      <td className="p-4 text-right">{formatCurrency(position.currentPrice)}</td>
                      <td className="p-4 text-right font-medium">{formatCurrency(position.value)}</td>
                      <td className="p-4 text-right">
                        <div className={position.unrealizedPL >= 0 ? 'text-success' : 'text-destructive'}>
                          <div className="font-medium">{formatCurrency(position.unrealizedPL)}</div>
                          <div className="text-sm">{formatPercent(position.unrealizedPLPercent)}</div>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </Card>
        </div>
      </div>
    </div>
  );
}
