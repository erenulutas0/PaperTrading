import { Link } from "react-router";
import { Plus, TrendingUp, TrendingDown, Minus, ThumbsUp, MessageSquare, CheckCircle2, XCircle, Clock } from "lucide-react";
import { Button } from "../components/ui/button";
import { Card } from "../components/ui/card";
import { Badge } from "../components/ui/badge";
import { mockAnalyses } from "../data/mockData";
import { formatRelativeTime } from "../utils/formatters";

export function AnalysisHub() {
  return (
    <div className="min-h-screen bg-background">
      <div className="container mx-auto px-4 py-6 space-y-6">
        {/* Header */}
        <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4">
          <div>
            <h1 className="text-3xl font-bold">Analysis Hub</h1>
            <p className="text-muted-foreground">Immutable market analysis and predictions</p>
          </div>
          <Button asChild className="bg-gradient-to-r from-primary to-secondary">
            <Link to="/analysis/new">
              <Plus className="w-4 h-4 mr-2" />
              Post Analysis
            </Link>
          </Button>
        </div>

        {/* Filters */}
        <div className="flex items-center gap-2 overflow-x-auto pb-2">
          <Button variant="outline" size="sm" className="bg-primary/10 text-primary border-primary/20">
            All
          </Button>
          <Button variant="outline" size="sm">
            Bullish
          </Button>
          <Button variant="outline" size="sm">
            Bearish
          </Button>
          <Button variant="outline" size="sm">
            Neutral
          </Button>
          <Button variant="outline" size="sm">
            Verified
          </Button>
        </div>

        {/* Analysis Grid */}
        <div className="grid md:grid-cols-2 lg:grid-cols-3 gap-6">
          {mockAnalyses.map((analysis) => (
            <Card key={analysis.id} className="p-6 glass-panel border-border hover:border-primary/50 transition-colors">
              <Link to={`/analysis/${analysis.id}`}>
                <div className="space-y-4">
                  {/* Author */}
                  <div className="flex items-center gap-3">
                    <div className="w-10 h-10 rounded-full bg-gradient-to-br from-primary to-secondary flex items-center justify-center text-sm font-bold">
                      {analysis.username.charAt(0).toUpperCase()}
                    </div>
                    <div className="flex-1 min-w-0">
                      <div className="font-medium truncate">@{analysis.username}</div>
                      <div className="text-xs text-muted-foreground">
                        {formatRelativeTime(analysis.createdAt)}
                      </div>
                    </div>
                  </div>

                  {/* Title */}
                  <h3 className="font-bold text-lg leading-tight">{analysis.title}</h3>

                  {/* Content Preview */}
                  <p className="text-sm text-muted-foreground line-clamp-3">
                    {analysis.content}
                  </p>

                  {/* Symbols & Sentiment */}
                  <div className="flex items-center gap-2 flex-wrap">
                    {analysis.symbols.map((symbol) => (
                      <Badge key={symbol} variant="outline" className="text-xs">
                        {symbol}
                      </Badge>
                    ))}
                    <Badge 
                      className={
                        analysis.sentiment === 'BULLISH' 
                          ? 'bg-success/10 text-success border-success/20' 
                          : analysis.sentiment === 'BEARISH'
                          ? 'bg-destructive/10 text-destructive border-destructive/20'
                          : 'bg-muted text-muted-foreground'
                      }
                    >
                      {analysis.sentiment === 'BULLISH' ? (
                        <TrendingUp className="w-3 h-3 mr-1" />
                      ) : analysis.sentiment === 'BEARISH' ? (
                        <TrendingDown className="w-3 h-3 mr-1" />
                      ) : (
                        <Minus className="w-3 h-3 mr-1" />
                      )}
                      {analysis.sentiment}
                    </Badge>
                    {analysis.outcome && (
                      <Badge 
                        className={
                          analysis.outcome === 'CORRECT'
                            ? 'bg-success/10 text-success border-success/20'
                            : analysis.outcome === 'INCORRECT'
                            ? 'bg-destructive/10 text-destructive border-destructive/20'
                            : 'bg-warning/10 text-warning border-warning/20'
                        }
                      >
                        {analysis.outcome === 'CORRECT' ? (
                          <CheckCircle2 className="w-3 h-3 mr-1" />
                        ) : analysis.outcome === 'INCORRECT' ? (
                          <XCircle className="w-3 h-3 mr-1" />
                        ) : (
                          <Clock className="w-3 h-3 mr-1" />
                        )}
                        {analysis.outcome}
                      </Badge>
                    )}
                  </div>

                  {/* Engagement */}
                  <div className="flex items-center gap-4 pt-2 border-t border-border">
                    <div className="flex items-center gap-1 text-sm text-muted-foreground">
                      <ThumbsUp className="w-4 h-4" />
                      <span>{analysis.likes}</span>
                    </div>
                    <div className="flex items-center gap-1 text-sm text-muted-foreground">
                      <MessageSquare className="w-4 h-4" />
                      <span>{analysis.comments}</span>
                    </div>
                    {analysis.immutable && (
                      <div className="ml-auto">
                        <Badge variant="outline" className="text-xs">
                          <Clock className="w-3 h-3 mr-1" />
                          Immutable
                        </Badge>
                      </div>
                    )}
                  </div>
                </div>
              </Link>
            </Card>
          ))}
        </div>
      </div>
    </div>
  );
}
