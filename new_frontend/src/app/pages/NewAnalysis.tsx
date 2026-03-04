import { Link, useNavigate } from "react-router";
import { ArrowLeft, TrendingUp, TrendingDown, Minus } from "lucide-react";
import { Button } from "../components/ui/button";
import { Card } from "../components/ui/card";
import { Input } from "../components/ui/input";
import { Label } from "../components/ui/label";
import { Textarea } from "../components/ui/textarea";
import { useState } from "react";

export function NewAnalysis() {
  const navigate = useNavigate();
  const [title, setTitle] = useState("");
  const [content, setContent] = useState("");
  const [symbols, setSymbols] = useState("");
  const [sentiment, setSentiment] = useState<'BULLISH' | 'BEARISH' | 'NEUTRAL'>('NEUTRAL');
  const [targetPrice, setTargetPrice] = useState("");
  const [timeHorizon, setTimeHorizon] = useState("");

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    // Mock submission - redirect to analysis hub
    navigate("/analysis");
  };

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

          {/* Form */}
          <Card className="p-6 md:p-8 glass-panel border-border">
            <div className="mb-6">
              <h1 className="text-3xl font-bold mb-2">Post Analysis</h1>
              <p className="text-muted-foreground">
                Share your market analysis. All posts are immutable and server-timestamped.
              </p>
            </div>

            <form onSubmit={handleSubmit} className="space-y-6">
              {/* Title */}
              <div className="space-y-2">
                <Label htmlFor="title">Title</Label>
                <Input
                  id="title"
                  placeholder="e.g., AAPL Breaking Out - Target $200"
                  value={title}
                  onChange={(e) => setTitle(e.target.value)}
                  required
                />
              </div>

              {/* Symbols */}
              <div className="space-y-2">
                <Label htmlFor="symbols">Symbols</Label>
                <Input
                  id="symbols"
                  placeholder="e.g., AAPL, MSFT, NVDA (comma-separated)"
                  value={symbols}
                  onChange={(e) => setSymbols(e.target.value)}
                  required
                />
                <p className="text-xs text-muted-foreground">
                  Enter stock symbols separated by commas
                </p>
              </div>

              {/* Sentiment */}
              <div className="space-y-2">
                <Label>Sentiment</Label>
                <div className="flex gap-2">
                  <Button
                    type="button"
                    variant={sentiment === 'BULLISH' ? 'default' : 'outline'}
                    className={sentiment === 'BULLISH' ? 'bg-success hover:bg-success/90' : ''}
                    onClick={() => setSentiment('BULLISH')}
                  >
                    <TrendingUp className="w-4 h-4 mr-2" />
                    Bullish
                  </Button>
                  <Button
                    type="button"
                    variant={sentiment === 'BEARISH' ? 'default' : 'outline'}
                    className={sentiment === 'BEARISH' ? 'bg-destructive hover:bg-destructive/90' : ''}
                    onClick={() => setSentiment('BEARISH')}
                  >
                    <TrendingDown className="w-4 h-4 mr-2" />
                    Bearish
                  </Button>
                  <Button
                    type="button"
                    variant={sentiment === 'NEUTRAL' ? 'default' : 'outline'}
                    onClick={() => setSentiment('NEUTRAL')}
                  >
                    <Minus className="w-4 h-4 mr-2" />
                    Neutral
                  </Button>
                </div>
              </div>

              {/* Content */}
              <div className="space-y-2">
                <Label htmlFor="content">Analysis</Label>
                <Textarea
                  id="content"
                  placeholder="Share your detailed analysis, reasoning, and trade setup..."
                  value={content}
                  onChange={(e) => setContent(e.target.value)}
                  rows={8}
                  required
                />
              </div>

              {/* Optional Fields */}
              <div className="grid md:grid-cols-2 gap-4">
                <div className="space-y-2">
                  <Label htmlFor="targetPrice">Target Price (Optional)</Label>
                  <Input
                    id="targetPrice"
                    type="number"
                    step="0.01"
                    placeholder="e.g., 200.00"
                    value={targetPrice}
                    onChange={(e) => setTargetPrice(e.target.value)}
                  />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="timeHorizon">Time Horizon (Optional)</Label>
                  <Input
                    id="timeHorizon"
                    placeholder="e.g., 3 months"
                    value={timeHorizon}
                    onChange={(e) => setTimeHorizon(e.target.value)}
                  />
                </div>
              </div>

              {/* Warning */}
              <div className="p-4 rounded-lg bg-warning/10 border border-warning/20">
                <p className="text-sm text-warning">
                  <strong>Important:</strong> Once posted, your analysis cannot be edited or deleted. 
                  It will be permanently timestamped and publicly visible.
                </p>
              </div>

              {/* Submit */}
              <div className="flex gap-3">
                <Button
                  type="submit"
                  className="bg-gradient-to-r from-primary to-secondary hover:opacity-90"
                >
                  Publish Analysis
                </Button>
                <Button type="button" variant="outline" asChild>
                  <Link to="/analysis">Cancel</Link>
                </Button>
              </div>
            </form>
          </Card>
        </div>
      </div>
    </div>
  );
}
