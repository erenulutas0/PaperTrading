import { useParams, Link } from "react-router";
import { ArrowLeft, ThumbsUp, MessageSquare, TrendingUp, TrendingDown, Minus, Clock, Share2 } from "lucide-react";
import { Button } from "../components/ui/button";
import { Card } from "../components/ui/card";
import { Badge } from "../components/ui/badge";
import { mockAnalyses } from "../data/mockData";
import { formatDateTime } from "../utils/formatters";

export function AnalysisDetail() {
  const { id } = useParams();
  const analysis = mockAnalyses.find(a => a.id === id) || mockAnalyses[0];

  return (
    <div className="min-h-screen bg-background">
      <div className="container mx-auto px-4 py-6">
        <div className="max-w-4xl mx-auto space-y-6">
          {/* Back Button */}
          <Button variant="ghost" size="sm" asChild>
            <Link to="/analysis">
              <ArrowLeft className="w-4 h-4 mr-2" />
              Back to Analysis Hub
            </Link>
          </Button>

          {/* Analysis Card */}
          <Card className="p-6 md:p-8 glass-panel border-border">
            {/* Author */}
            <div className="flex items-start justify-between mb-6">
              <div className="flex items-center gap-3">
                <div className="w-12 h-12 rounded-full bg-gradient-to-br from-primary to-secondary flex items-center justify-center text-lg font-bold">
                  {analysis.username.charAt(0).toUpperCase()}
                </div>
                <div>
                  <Link to={`/profile/${analysis.username}`} className="font-bold hover:text-primary">
                    @{analysis.username}
                  </Link>
                  <div className="text-sm text-muted-foreground">
                    {formatDateTime(analysis.createdAt)}
                  </div>
                </div>
              </div>
              <Button variant="ghost" size="icon">
                <Share2 className="w-5 h-5" />
              </Button>
            </div>

            {/* Title */}
            <h1 className="text-3xl font-bold mb-4">{analysis.title}</h1>

            {/* Symbols & Sentiment */}
            <div className="flex items-center gap-2 flex-wrap mb-6">
              {analysis.symbols.map((symbol) => (
                <Badge key={symbol} variant="outline">
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
                  <TrendingUp className="w-4 h-4 mr-1" />
                ) : analysis.sentiment === 'BEARISH' ? (
                  <TrendingDown className="w-4 h-4 mr-1" />
                ) : (
                  <Minus className="w-4 h-4 mr-1" />
                )}
                {analysis.sentiment}
              </Badge>
              <Badge variant="outline" className="border-primary/20 text-primary">
                <Clock className="w-3 h-3 mr-1" />
                Immutable
              </Badge>
            </div>

            {/* Content */}
            <div className="prose prose-invert max-w-none mb-6">
              <p className="text-foreground">{analysis.content}</p>
            </div>

            {/* Targets */}
            {analysis.targetPrice && (
              <div className="p-4 rounded-lg bg-accent/50 border border-border mb-6">
                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <div className="text-sm text-muted-foreground mb-1">Target Price</div>
                    <div className="text-2xl font-bold text-primary">
                      ${analysis.targetPrice}
                    </div>
                  </div>
                  <div>
                    <div className="text-sm text-muted-foreground mb-1">Time Horizon</div>
                    <div className="text-lg font-medium">
                      {analysis.timeHorizon}
                    </div>
                  </div>
                </div>
              </div>
            )}

            {/* Engagement */}
            <div className="flex items-center gap-4 pt-4 border-t border-border">
              <Button variant="ghost" className="gap-2">
                <ThumbsUp className="w-4 h-4" />
                <span>{analysis.likes}</span>
              </Button>
              <Button variant="ghost" className="gap-2">
                <MessageSquare className="w-4 h-4" />
                <span>{analysis.comments} Comments</span>
              </Button>
            </div>
          </Card>

          {/* Comments Section */}
          <Card className="p-6 glass-panel border-border">
            <h2 className="text-xl font-bold mb-4">Comments</h2>
            <div className="text-center py-8 text-muted-foreground">
              <MessageSquare className="w-12 h-12 mx-auto mb-2 opacity-50" />
              <p>No comments yet. Be the first to comment!</p>
            </div>
          </Card>
        </div>
      </div>
    </div>
  );
}
