import { useParams, Link } from "react-router";
import { ArrowLeft, TrendingUp, Plus, MessageSquare } from "lucide-react";
import { Button } from "../components/ui/button";
import { Card } from "../components/ui/card";
import { Badge } from "../components/ui/badge";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "../components/ui/tabs";
import { LineChart, Line, XAxis, YAxis, Tooltip, ResponsiveContainer, CartesianGrid } from "recharts";
import { mockPortfolios, mockPositions, mockTrades, mockEquityData } from "../data/mockData";
import { formatCurrency, formatPercent, formatDateTime } from "../utils/formatters";

export function PortfolioDetail() {
  const { id } = useParams();
  const portfolio = mockPortfolios.find(p => p.id === id) || mockPortfolios[0];

  return (
    <div className="min-h-screen bg-background">
      <div className="container mx-auto px-4 py-6 space-y-6">
        {/* Header */}
        <div className="flex items-center gap-4">
          <Button variant="ghost" size="icon" asChild>
            <Link to="/dashboard">
              <ArrowLeft className="w-5 h-5" />
            </Link>
          </Button>
          <div className="flex-1">
            <div className="flex items-center gap-2">
              <h1 className="text-3xl font-bold">{portfolio.name}</h1>
              {portfolio.isPublic ? (
                <Badge className="bg-primary/10 text-primary border-primary/20">Public</Badge>
              ) : (
                <Badge variant="secondary">Private</Badge>
              )}
            </div>
            <p className="text-muted-foreground">
              Created {new Date(portfolio.createdAt).toLocaleDateString()}
            </p>
          </div>
          <Button className="bg-gradient-to-r from-primary to-secondary">
            <Plus className="w-4 h-4 mr-2" />
            New Trade
          </Button>
        </div>

        {/* Portfolio Summary */}
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
          <Card className="p-4 glass-panel border-border">
            <div className="text-sm text-muted-foreground mb-1">Total Value</div>
            <div className="text-2xl font-bold">{formatCurrency(portfolio.value)}</div>
          </Card>
          <Card className="p-4 glass-panel border-border">
            <div className="text-sm text-muted-foreground mb-1">Day Change</div>
            <div className={`text-2xl font-bold ${portfolio.dayChange >= 0 ? 'text-success' : 'text-destructive'}`}>
              {formatCurrency(portfolio.dayChange)}
            </div>
            <div className={`text-sm ${portfolio.dayChange >= 0 ? 'text-success' : 'text-destructive'}`}>
              {formatPercent(portfolio.dayChangePercent)}
            </div>
          </Card>
          <Card className="p-4 glass-panel border-border">
            <div className="text-sm text-muted-foreground mb-1">Total Return</div>
            <div className={`text-2xl font-bold ${portfolio.totalReturn >= 0 ? 'text-success' : 'text-destructive'}`}>
              {formatCurrency(portfolio.totalReturn)}
            </div>
            <div className={`text-sm ${portfolio.totalReturn >= 0 ? 'text-success' : 'text-destructive'}`}>
              {formatPercent(portfolio.totalReturnPercent)}
            </div>
          </Card>
          <Card className="p-4 glass-panel border-border">
            <div className="text-sm text-muted-foreground mb-1">Buying Power</div>
            <div className="text-2xl font-bold">{formatCurrency(portfolio.cash)}</div>
          </Card>
        </div>

        {/* Equity Chart */}
        <Card className="p-6 glass-panel border-border">
          <h2 className="text-xl font-bold mb-4">Portfolio Performance</h2>
          <ResponsiveContainer width="100%" height={300}>
            <LineChart data={mockEquityData}>
              <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.1)" />
              <XAxis 
                dataKey="date" 
                stroke="#a1a1aa"
                tick={{ fontSize: 12 }}
                tickFormatter={(value) => new Date(value).toLocaleDateString('en-US', { month: 'short', day: 'numeric' })}
              />
              <YAxis 
                stroke="#a1a1aa"
                tick={{ fontSize: 12 }}
                tickFormatter={(value) => `$${(value / 1000).toFixed(0)}k`}
              />
              <Tooltip 
                contentStyle={{ 
                  backgroundColor: '#1a1a24', 
                  border: '1px solid #26262f',
                  borderRadius: '8px',
                }}
                formatter={(value: number) => [formatCurrency(value), 'Value']}
                labelFormatter={(label) => new Date(label).toLocaleDateString()}
              />
              <Line 
                type="monotone" 
                dataKey="value" 
                stroke="#10b981" 
                strokeWidth={2}
                dot={false}
              />
            </LineChart>
          </ResponsiveContainer>
        </Card>

        {/* Tabs */}
        <Tabs defaultValue="positions" className="space-y-4">
          <TabsList className="glass-panel">
            <TabsTrigger value="positions">Positions</TabsTrigger>
            <TabsTrigger value="history">Trade History</TabsTrigger>
            <TabsTrigger value="comments">Comments</TabsTrigger>
          </TabsList>

          <TabsContent value="positions">
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
                      <th className="text-right p-4 text-sm font-medium text-muted-foreground">Day Change</th>
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
                        <td className="p-4 text-right">
                          <div className={position.dayChange >= 0 ? 'text-success' : 'text-destructive'}>
                            <div className="font-medium">{formatCurrency(position.dayChange)}</div>
                            <div className="text-sm">{formatPercent(position.dayChangePercent)}</div>
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </Card>
          </TabsContent>

          <TabsContent value="history">
            <Card className="glass-panel border-border">
              <div className="divide-y divide-border">
                {mockTrades.map((trade) => (
                  <div key={trade.id} className="p-4 hover:bg-accent/50 transition-colors">
                    <div className="flex items-center justify-between">
                      <div className="flex items-center gap-3">
                        <Badge 
                          className={trade.type === 'BUY' ? 'bg-success/10 text-success border-success/20' : 'bg-destructive/10 text-destructive border-destructive/20'}
                        >
                          {trade.type}
                        </Badge>
                        <div>
                          <div className="font-bold">{trade.symbol}</div>
                          <div className="text-sm text-muted-foreground">
                            {trade.quantity} shares @ {formatCurrency(trade.price)}
                          </div>
                        </div>
                      </div>
                      <div className="text-right">
                        <div className="font-medium">{formatCurrency(trade.total)}</div>
                        <div className="text-sm text-muted-foreground">
                          {formatDateTime(trade.timestamp)}
                        </div>
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            </Card>
          </TabsContent>

          <TabsContent value="comments">
            <Card className="p-6 glass-panel border-border">
              <div className="text-center py-8 text-muted-foreground">
                <MessageSquare className="w-12 h-12 mx-auto mb-2 opacity-50" />
                <p>No comments yet. Be the first to comment!</p>
              </div>
            </Card>
          </TabsContent>
        </Tabs>
      </div>
    </div>
  );
}
