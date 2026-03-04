import { Link } from "react-router";
import { ArrowLeft, Check, AlertCircle, Info, AlertTriangle } from "lucide-react";
import { Button } from "../components/ui/button";
import { Card } from "../components/ui/card";
import { Badge } from "../components/ui/badge";
import { Input } from "../components/ui/input";
import { Label } from "../components/ui/label";
import { Progress } from "../components/ui/progress";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "../components/ui/tabs";
import { Alert, AlertDescription, AlertTitle } from "../components/ui/alert";

export function DesignSystem() {
  return (
    <div className="min-h-screen bg-background">
      <div className="container mx-auto px-4 py-8 space-y-12">
        {/* Header */}
        <div>
          <Button variant="ghost" size="sm" asChild className="mb-4">
            <Link to="/">
              <ArrowLeft className="w-4 h-4 mr-2" />
              Back to Home
            </Link>
          </Button>
          <h1 className="text-4xl font-bold mb-2">PaperTradePro Design System</h1>
          <p className="text-muted-foreground">
            Neo-financial terminal aesthetic with emerald/cyan accents
          </p>
        </div>

        {/* Colors */}
        <section className="space-y-4">
          <h2 className="text-2xl font-bold">Color Palette</h2>
          <div className="grid md:grid-cols-4 gap-4">
            <Card className="p-4 glass-panel border-border">
              <div className="w-full h-24 rounded-lg bg-background mb-3 border border-border"></div>
              <div className="font-medium">Background</div>
              <div className="text-sm text-muted-foreground">#0a0a0f</div>
            </Card>
            <Card className="p-4 glass-panel border-border">
              <div className="w-full h-24 rounded-lg bg-primary mb-3"></div>
              <div className="font-medium">Primary (Emerald)</div>
              <div className="text-sm text-muted-foreground">#10b981</div>
            </Card>
            <Card className="p-4 glass-panel border-border">
              <div className="w-full h-24 rounded-lg bg-secondary mb-3"></div>
              <div className="font-medium">Secondary (Cyan)</div>
              <div className="text-sm text-muted-foreground">#22d3ee</div>
            </Card>
            <Card className="p-4 glass-panel border-border">
              <div className="w-full h-24 rounded-lg bg-destructive mb-3"></div>
              <div className="font-medium">Destructive</div>
              <div className="text-sm text-muted-foreground">#ef4444</div>
            </Card>
          </div>
        </section>

        {/* Typography */}
        <section className="space-y-4">
          <h2 className="text-2xl font-bold">Typography</h2>
          <Card className="p-6 glass-panel border-border space-y-4">
            <div>
              <h1>Heading 1 - 40px Bold</h1>
              <p className="text-sm text-muted-foreground">Used for page titles</p>
            </div>
            <div>
              <h2>Heading 2 - 32px Bold</h2>
              <p className="text-sm text-muted-foreground">Used for section headers</p>
            </div>
            <div>
              <h3>Heading 3 - 24px Medium</h3>
              <p className="text-sm text-muted-foreground">Used for card titles</p>
            </div>
            <div>
              <h4>Heading 4 - 20px Medium</h4>
              <p className="text-sm text-muted-foreground">Used for subsections</p>
            </div>
            <div>
              <p>Body text - 14px Regular</p>
              <p className="text-sm text-muted-foreground">Default text style</p>
            </div>
          </Card>
        </section>

        {/* Buttons */}
        <section className="space-y-4">
          <h2 className="text-2xl font-bold">Buttons</h2>
          <Card className="p-6 glass-panel border-border">
            <div className="flex flex-wrap gap-3">
              <Button>Default Button</Button>
              <Button variant="outline">Outline Button</Button>
              <Button variant="ghost">Ghost Button</Button>
              <Button variant="destructive">Destructive Button</Button>
              <Button className="bg-gradient-to-r from-primary to-secondary">
                Gradient Button
              </Button>
              <Button disabled>Disabled Button</Button>
            </div>
            <div className="flex flex-wrap gap-3 mt-4">
              <Button size="sm">Small</Button>
              <Button>Default</Button>
              <Button size="lg">Large</Button>
            </div>
          </Card>
        </section>

        {/* Badges */}
        <section className="space-y-4">
          <h2 className="text-2xl font-bold">Badges</h2>
          <Card className="p-6 glass-panel border-border">
            <div className="flex flex-wrap gap-3">
              <Badge>Default</Badge>
              <Badge variant="outline">Outline</Badge>
              <Badge variant="secondary">Secondary</Badge>
              <Badge className="bg-success/10 text-success border-success/20">Success</Badge>
              <Badge className="bg-destructive/10 text-destructive border-destructive/20">Error</Badge>
              <Badge className="bg-warning/10 text-warning border-warning/20">Warning</Badge>
              <Badge className="bg-primary/10 text-primary border-primary/20">Verified</Badge>
            </div>
          </Card>
        </section>

        {/* Inputs */}
        <section className="space-y-4">
          <h2 className="text-2xl font-bold">Form Elements</h2>
          <Card className="p-6 glass-panel border-border space-y-4">
            <div className="space-y-2 max-w-md">
              <Label htmlFor="email">Email</Label>
              <Input id="email" type="email" placeholder="you@example.com" />
            </div>
            <div className="space-y-2 max-w-md">
              <Label htmlFor="disabled">Disabled Input</Label>
              <Input id="disabled" disabled placeholder="Disabled input" />
            </div>
          </Card>
        </section>

        {/* Cards */}
        <section className="space-y-4">
          <h2 className="text-2xl font-bold">Cards</h2>
          <div className="grid md:grid-cols-2 gap-4">
            <Card className="p-6 glass-panel border-border">
              <h3 className="font-bold mb-2">Standard Card</h3>
              <p className="text-muted-foreground">
                Glass morphism effect with backdrop blur and subtle border
              </p>
            </Card>
            <Card className="p-6 glass-panel border-primary bg-gradient-to-br from-primary/5 to-transparent">
              <h3 className="font-bold mb-2">Highlighted Card</h3>
              <p className="text-muted-foreground">
                With primary border and gradient background
              </p>
            </Card>
          </div>
        </section>

        {/* Alerts */}
        <section className="space-y-4">
          <h2 className="text-2xl font-bold">Alerts</h2>
          <div className="space-y-4">
            <Alert>
              <Info className="h-4 w-4" />
              <AlertTitle>Information</AlertTitle>
              <AlertDescription>
                This is an informational alert message.
              </AlertDescription>
            </Alert>
            <Alert variant="destructive">
              <AlertCircle className="h-4 w-4" />
              <AlertTitle>Error</AlertTitle>
              <AlertDescription>
                This is an error alert message.
              </AlertDescription>
            </Alert>
            <Alert className="bg-warning/10 border-warning/20 text-warning">
              <AlertTriangle className="h-4 w-4" />
              <AlertTitle>Warning</AlertTitle>
              <AlertDescription className="text-warning">
                This is a warning alert message.
              </AlertDescription>
            </Alert>
            <Alert className="bg-success/10 border-success/20 text-success">
              <Check className="h-4 w-4" />
              <AlertTitle>Success</AlertTitle>
              <AlertDescription className="text-success">
                This is a success alert message.
              </AlertDescription>
            </Alert>
          </div>
        </section>

        {/* Progress */}
        <section className="space-y-4">
          <h2 className="text-2xl font-bold">Progress</h2>
          <Card className="p-6 glass-panel border-border space-y-4">
            <div className="space-y-2">
              <div className="flex justify-between text-sm">
                <span>Loading...</span>
                <span>25%</span>
              </div>
              <Progress value={25} />
            </div>
            <div className="space-y-2">
              <div className="flex justify-between text-sm">
                <span>Progress</span>
                <span>60%</span>
              </div>
              <Progress value={60} />
            </div>
            <div className="space-y-2">
              <div className="flex justify-between text-sm">
                <span>Complete</span>
                <span>100%</span>
              </div>
              <Progress value={100} />
            </div>
          </Card>
        </section>

        {/* Tabs */}
        <section className="space-y-4">
          <h2 className="text-2xl font-bold">Tabs</h2>
          <Tabs defaultValue="tab1">
            <TabsList className="glass-panel">
              <TabsTrigger value="tab1">Tab 1</TabsTrigger>
              <TabsTrigger value="tab2">Tab 2</TabsTrigger>
              <TabsTrigger value="tab3">Tab 3</TabsTrigger>
            </TabsList>
            <TabsContent value="tab1">
              <Card className="p-6 glass-panel border-border">
                <p>Content for Tab 1</p>
              </Card>
            </TabsContent>
            <TabsContent value="tab2">
              <Card className="p-6 glass-panel border-border">
                <p>Content for Tab 2</p>
              </Card>
            </TabsContent>
            <TabsContent value="tab3">
              <Card className="p-6 glass-panel border-border">
                <p>Content for Tab 3</p>
              </Card>
            </TabsContent>
          </Tabs>
        </section>

        {/* States */}
        <section className="space-y-4">
          <h2 className="text-2xl font-bold">States</h2>
          <div className="grid md:grid-cols-3 gap-4">
            <Card className="p-6 glass-panel border-border text-center">
              <div className="w-12 h-12 rounded-full bg-primary/10 flex items-center justify-center mx-auto mb-3">
                <div className="w-6 h-6 border-2 border-primary border-t-transparent rounded-full animate-spin"></div>
              </div>
              <h3 className="font-bold mb-1">Loading</h3>
              <p className="text-sm text-muted-foreground">Loading state indicator</p>
            </Card>
            <Card className="p-6 glass-panel border-border text-center">
              <div className="w-12 h-12 rounded-full bg-muted flex items-center justify-center mx-auto mb-3 text-muted-foreground">
                ?
              </div>
              <h3 className="font-bold mb-1">Empty</h3>
              <p className="text-sm text-muted-foreground">No data available</p>
            </Card>
            <Card className="p-6 glass-panel border-border text-center">
              <div className="w-12 h-12 rounded-full bg-destructive/10 flex items-center justify-center mx-auto mb-3">
                <AlertCircle className="w-6 h-6 text-destructive" />
              </div>
              <h3 className="font-bold mb-1">Error</h3>
              <p className="text-sm text-muted-foreground">Error state indicator</p>
            </Card>
          </div>
        </section>

        {/* Data Table */}
        <section className="space-y-4">
          <h2 className="text-2xl font-bold">Data Table</h2>
          <Card className="glass-panel border-border overflow-hidden">
            <div className="overflow-x-auto">
              <table className="w-full">
                <thead>
                  <tr className="border-b border-border">
                    <th className="text-left p-4 text-sm font-medium text-muted-foreground">Symbol</th>
                    <th className="text-right p-4 text-sm font-medium text-muted-foreground">Price</th>
                    <th className="text-right p-4 text-sm font-medium text-muted-foreground">Change</th>
                    <th className="text-center p-4 text-sm font-medium text-muted-foreground">Status</th>
                  </tr>
                </thead>
                <tbody>
                  <tr className="border-b border-border hover:bg-accent/50 transition-colors">
                    <td className="p-4 font-bold">AAPL</td>
                    <td className="p-4 text-right">$185.30</td>
                    <td className="p-4 text-right text-success">+1.37%</td>
                    <td className="p-4 text-center">
                      <Badge className="bg-success/10 text-success border-success/20">Active</Badge>
                    </td>
                  </tr>
                  <tr className="border-b border-border hover:bg-accent/50 transition-colors">
                    <td className="p-4 font-bold">TSLA</td>
                    <td className="p-4 text-right">$238.50</td>
                    <td className="p-4 text-right text-destructive">-1.32%</td>
                    <td className="p-4 text-center">
                      <Badge className="bg-warning/10 text-warning border-warning/20">Watch</Badge>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </Card>
        </section>
      </div>
    </div>
  );
}
