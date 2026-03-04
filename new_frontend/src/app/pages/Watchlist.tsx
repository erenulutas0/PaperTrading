import { Plus, Star, Search, TrendingUp, TrendingDown, Trash2 } from "lucide-react";
import { Button } from "../components/ui/button";
import { Card } from "../components/ui/card";
import { Badge } from "../components/ui/badge";
import { Input } from "../components/ui/input";
import { mockPositions } from "../data/mockData";
import { formatCurrency, formatPercent } from "../utils/formatters";

export function Watchlist() {
  return (
    <div className="min-h-screen bg-background">
      <div className="container mx-auto px-4 py-6 space-y-6">
        {/* Header */}
        <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4">
          <div>
            <h1 className="text-3xl font-bold flex items-center gap-2">
              <Star className="w-8 h-8 text-primary" />
              Watchlist
            </h1>
            <p className="text-muted-foreground">Track your favorite stocks and portfolios</p>
          </div>
          <Button className="bg-gradient-to-r from-primary to-secondary">
            <Plus className="w-4 h-4 mr-2" />
            Add Symbol
          </Button>
        </div>

        {/* Search */}
        <div className="relative max-w-md">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
          <Input placeholder="Search symbols..." className="pl-10" />
        </div>

        {/* Watchlist Table */}
        <Card className="glass-panel border-border overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead>
                <tr className="border-b border-border">
                  <th className="text-left p-4 text-sm font-medium text-muted-foreground">Symbol</th>
                  <th className="text-left p-4 text-sm font-medium text-muted-foreground">Company</th>
                  <th className="text-right p-4 text-sm font-medium text-muted-foreground">Price</th>
                  <th className="text-right p-4 text-sm font-medium text-muted-foreground">Change</th>
                  <th className="text-right p-4 text-sm font-medium text-muted-foreground">Day %</th>
                  <th className="text-right p-4 text-sm font-medium text-muted-foreground">Volume</th>
                  <th className="text-center p-4 text-sm font-medium text-muted-foreground">Actions</th>
                </tr>
              </thead>
              <tbody>
                {mockPositions.map((position) => (
                  <tr key={position.id} className="border-b border-border last:border-0 hover:bg-accent/50 transition-colors">
                    <td className="p-4">
                      <div className="flex items-center gap-2">
                        <Star className="w-4 h-4 text-primary fill-primary" />
                        <span className="font-bold">{position.symbol}</span>
                      </div>
                    </td>
                    <td className="p-4">
                      <div className="text-sm text-muted-foreground">{position.companyName}</div>
                    </td>
                    <td className="p-4 text-right font-bold">{formatCurrency(position.currentPrice)}</td>
                    <td className="p-4 text-right">
                      <div className={position.dayChange >= 0 ? 'text-success' : 'text-destructive'}>
                        {formatCurrency(position.dayChange)}
                      </div>
                    </td>
                    <td className="p-4 text-right">
                      <Badge 
                        className={
                          position.dayChange >= 0 
                            ? 'bg-success/10 text-success border-success/20' 
                            : 'bg-destructive/10 text-destructive border-destructive/20'
                        }
                      >
                        {position.dayChange >= 0 ? (
                          <TrendingUp className="w-3 h-3 mr-1" />
                        ) : (
                          <TrendingDown className="w-3 h-3 mr-1" />
                        )}
                        {formatPercent(position.dayChangePercent)}
                      </Badge>
                    </td>
                    <td className="p-4 text-right text-muted-foreground">
                      {(Math.random() * 10000000).toFixed(0).replace(/\B(?=(\d{3})+(?!\d))/g, ',')}
                    </td>
                    <td className="p-4">
                      <div className="flex items-center justify-center gap-2">
                        <Button size="sm" variant="outline">
                          Trade
                        </Button>
                        <Button size="sm" variant="ghost">
                          <Trash2 className="w-4 h-4" />
                        </Button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Card>

        {/* Quick Stats */}
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
          <Card className="p-4 glass-panel border-border">
            <div className="text-sm text-muted-foreground mb-1">Watching</div>
            <div className="text-2xl font-bold">{mockPositions.length}</div>
          </Card>
          <Card className="p-4 glass-panel border-border">
            <div className="text-sm text-muted-foreground mb-1">Up Today</div>
            <div className="text-2xl font-bold text-success">
              {mockPositions.filter(p => p.dayChange >= 0).length}
            </div>
          </Card>
          <Card className="p-4 glass-panel border-border">
            <div className="text-sm text-muted-foreground mb-1">Down Today</div>
            <div className="text-2xl font-bold text-destructive">
              {mockPositions.filter(p => p.dayChange < 0).length}
            </div>
          </Card>
          <Card className="p-4 glass-panel border-border">
            <div className="text-sm text-muted-foreground mb-1">Avg Change</div>
            <div className={`text-2xl font-bold ${
              mockPositions.reduce((sum, p) => sum + p.dayChangePercent, 0) / mockPositions.length >= 0 
                ? 'text-success' 
                : 'text-destructive'
            }`}>
              {formatPercent(mockPositions.reduce((sum, p) => sum + p.dayChangePercent, 0) / mockPositions.length)}
            </div>
          </Card>
        </div>
      </div>
    </div>
  );
}
