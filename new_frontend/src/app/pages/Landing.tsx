import { Link } from "react-router";
import { TrendingUp, Shield, Users, Trophy, BarChart3, Lock, Clock, CheckCircle2 } from "lucide-react";
import { Button } from "../components/ui/button";
import { Card } from "../components/ui/card";

export function Landing() {
  return (
    <div className="min-h-screen bg-background noise-bg">
      {/* Header */}
      <header className="border-b border-border glass-panel sticky top-0 z-50">
        <div className="container mx-auto px-4 py-4 flex items-center justify-between">
          <div className="flex items-center gap-2">
            <div className="w-8 h-8 bg-gradient-to-br from-primary to-secondary rounded-lg flex items-center justify-center">
              <TrendingUp className="w-5 h-5 text-background" />
            </div>
            <span className="text-xl font-bold bg-gradient-to-r from-primary to-secondary bg-clip-text text-transparent">
              PaperTradePro
            </span>
          </div>
          
          <div className="flex items-center gap-3">
            <Button variant="ghost" asChild>
              <Link to="/login">Login</Link>
            </Button>
            <Button asChild className="bg-gradient-to-r from-primary to-secondary hover:opacity-90">
              <Link to="/register">Get Started</Link>
            </Button>
          </div>
        </div>
      </header>

      {/* Hero Section */}
      <section className="container mx-auto px-4 py-16 md:py-24">
        <div className="max-w-4xl mx-auto text-center space-y-6">
          <div className="inline-flex items-center gap-2 px-4 py-2 bg-primary/10 border border-primary/20 rounded-full text-sm text-primary">
            <Shield className="w-4 h-4" />
            <span>Paper Trading Only • Risk-Free Learning</span>
          </div>
          
          <h1 className="text-4xl md:text-6xl font-bold leading-tight">
            Master Trading with
            <br />
            <span className="bg-gradient-to-r from-primary to-secondary bg-clip-text text-transparent">
              Verifiable Performance
            </span>
          </h1>
          
          <p className="text-xl text-muted-foreground max-w-2xl mx-auto">
            Build your trading portfolio, share immutable analysis, and compete in tournaments. 
            All timestamped and verified—no real money, no risk.
          </p>
          
          <div className="flex flex-col sm:flex-row items-center justify-center gap-4 pt-4">
            <Button size="lg" asChild className="bg-gradient-to-r from-primary to-secondary hover:opacity-90 w-full sm:w-auto">
              <Link to="/register">Start Trading Free</Link>
            </Button>
            <Button size="lg" variant="outline" asChild className="w-full sm:w-auto">
              <Link to="/design-system">View Design System</Link>
            </Button>
          </div>

          <div className="flex items-center justify-center gap-8 pt-8 text-sm text-muted-foreground">
            <div className="flex items-center gap-2">
              <CheckCircle2 className="w-4 h-4 text-primary" />
              <span>No credit card</span>
            </div>
            <div className="flex items-center gap-2">
              <CheckCircle2 className="w-4 h-4 text-primary" />
              <span>Free forever</span>
            </div>
            <div className="flex items-center gap-2">
              <CheckCircle2 className="w-4 h-4 text-primary" />
              <span>Immutable records</span>
            </div>
          </div>
        </div>
      </section>

      {/* Features Section */}
      <section className="container mx-auto px-4 py-16">
        <div className="grid md:grid-cols-3 gap-6">
          <Card className="p-6 glass-panel border-border">
            <div className="w-12 h-12 rounded-lg bg-primary/10 flex items-center justify-center mb-4">
              <Lock className="w-6 h-6 text-primary" />
            </div>
            <h3 className="text-xl font-bold mb-2">Immutable Posts</h3>
            <p className="text-muted-foreground">
              Every analysis is server-timestamped and permanent. Build trust with verifiable predictions.
            </p>
          </Card>

          <Card className="p-6 glass-panel border-border">
            <div className="w-12 h-12 rounded-lg bg-secondary/10 flex items-center justify-center mb-4">
              <BarChart3 className="w-6 h-6 text-secondary" />
            </div>
            <h3 className="text-xl font-bold mb-2">Real-Time Data</h3>
            <p className="text-muted-foreground">
              Trade with live market data. Track performance with professional-grade analytics.
            </p>
          </Card>

          <Card className="p-6 glass-panel border-border">
            <div className="w-12 h-12 rounded-lg bg-primary/10 flex items-center justify-center mb-4">
              <Users className="w-6 h-6 text-primary" />
            </div>
            <h3 className="text-xl font-bold mb-2">Social Trading</h3>
            <p className="text-muted-foreground">
              Share portfolios, follow top traders, and learn from the community's best strategies.
            </p>
          </Card>

          <Card className="p-6 glass-panel border-border">
            <div className="w-12 h-12 rounded-lg bg-secondary/10 flex items-center justify-center mb-4">
              <Trophy className="w-6 h-6 text-secondary" />
            </div>
            <h3 className="text-xl font-bold mb-2">Tournaments</h3>
            <p className="text-muted-foreground">
              Compete in trading tournaments with leaderboards. Prove your skills against others.
            </p>
          </Card>

          <Card className="p-6 glass-panel border-border">
            <div className="w-12 h-12 rounded-lg bg-primary/10 flex items-center justify-center mb-4">
              <Clock className="w-6 h-6 text-primary" />
            </div>
            <h3 className="text-xl font-bold mb-2">Performance History</h3>
            <p className="text-muted-foreground">
              Full trade history with P&L tracking. See exactly how your strategies perform over time.
            </p>
          </Card>

          <Card className="p-6 glass-panel border-border">
            <div className="w-12 h-12 rounded-lg bg-secondary/10 flex items-center justify-center mb-4">
              <Shield className="w-6 h-6 text-secondary" />
            </div>
            <h3 className="text-xl font-bold mb-2">Verified Metrics</h3>
            <p className="text-muted-foreground">
              All performance metrics are verified and cannot be manipulated. Build genuine credibility.
            </p>
          </Card>
        </div>
      </section>

      {/* CTA Section */}
      <section className="container mx-auto px-4 py-16">
        <Card className="p-8 md:p-12 glass-panel border-primary/20 bg-gradient-to-br from-primary/5 to-secondary/5">
          <div className="max-w-3xl mx-auto text-center space-y-6">
            <h2 className="text-3xl md:text-4xl font-bold">
              Ready to Start Your Trading Journey?
            </h2>
            <p className="text-lg text-muted-foreground">
              Join thousands of traders learning and improving their skills in a risk-free environment.
            </p>
            <Button size="lg" asChild className="bg-gradient-to-r from-primary to-secondary hover:opacity-90">
              <Link to="/register">Create Free Account</Link>
            </Button>
          </div>
        </Card>
      </section>

      {/* Footer */}
      <footer className="border-t border-border mt-16">
        <div className="container mx-auto px-4 py-8">
          <div className="flex flex-col md:flex-row items-center justify-between gap-4">
            <div className="flex items-center gap-2">
              <div className="w-6 h-6 bg-gradient-to-br from-primary to-secondary rounded-lg flex items-center justify-center">
                <TrendingUp className="w-4 h-4 text-background" />
              </div>
              <span className="font-bold text-sm">PaperTradePro</span>
            </div>
            <p className="text-sm text-muted-foreground">
              © 2026 PaperTradePro. Paper trading platform for educational purposes.
            </p>
          </div>
        </div>
      </footer>
    </div>
  );
}
