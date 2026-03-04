// Mock data for PaperTradePro

export interface Portfolio {
  id: string;
  name: string;
  userId: string;
  username: string;
  isPublic: boolean;
  value: number;
  cash: number;
  dayChange: number;
  dayChangePercent: number;
  totalReturn: number;
  totalReturnPercent: number;
  createdAt: string;
}

export interface Position {
  id: string;
  symbol: string;
  companyName: string;
  quantity: number;
  avgPrice: number;
  currentPrice: number;
  value: number;
  unrealizedPL: number;
  unrealizedPLPercent: number;
  dayChange: number;
  dayChangePercent: number;
}

export interface Trade {
  id: string;
  portfolioId: string;
  symbol: string;
  type: 'BUY' | 'SELL';
  quantity: number;
  price: number;
  total: number;
  timestamp: string;
  status: 'FILLED' | 'PENDING' | 'CANCELLED';
}

export interface Analysis {
  id: string;
  userId: string;
  username: string;
  userAvatar?: string;
  title: string;
  content: string;
  symbols: string[];
  sentiment: 'BULLISH' | 'BEARISH' | 'NEUTRAL';
  targetPrice?: number;
  timeHorizon?: string;
  createdAt: string;
  immutable: boolean;
  likes: number;
  comments: number;
  outcome?: 'CORRECT' | 'INCORRECT' | 'PENDING';
}

export interface Tournament {
  id: string;
  name: string;
  description: string;
  startDate: string;
  endDate: string;
  status: 'UPCOMING' | 'LIVE' | 'ENDED';
  participants: number;
  prizePool?: string;
  rules: string;
}

export interface LeaderboardEntry {
  rank: number;
  previousRank?: number;
  userId: string;
  username: string;
  avatar?: string;
  portfolioId: string;
  portfolioName: string;
  return: number;
  returnPercent: number;
  trades: number;
  winRate: number;
  verified: boolean;
}

export interface Notification {
  id: string;
  type: 'TRADE' | 'ANALYSIS' | 'TOURNAMENT' | 'SOCIAL';
  title: string;
  message: string;
  timestamp: string;
  read: boolean;
  link?: string;
}

// Mock Portfolios
export const mockPortfolios: Portfolio[] = [
  {
    id: '1',
    name: 'Tech Growth',
    userId: '1',
    username: 'johndoe',
    isPublic: true,
    value: 125840.50,
    cash: 15840.50,
    dayChange: 2450.30,
    dayChangePercent: 1.98,
    totalReturn: 25840.50,
    totalReturnPercent: 25.84,
    createdAt: '2025-01-15T10:00:00Z',
  },
  {
    id: '2',
    name: 'Value Stocks',
    userId: '1',
    username: 'johndoe',
    isPublic: false,
    value: 87250.75,
    cash: 12250.75,
    dayChange: -450.20,
    dayChangePercent: -0.51,
    totalReturn: -12749.25,
    totalReturnPercent: -12.75,
    createdAt: '2025-02-01T14:30:00Z',
  },
];

// Mock Positions
export const mockPositions: Position[] = [
  {
    id: '1',
    symbol: 'AAPL',
    companyName: 'Apple Inc.',
    quantity: 50,
    avgPrice: 178.50,
    currentPrice: 185.30,
    value: 9265.00,
    unrealizedPL: 340.00,
    unrealizedPLPercent: 3.81,
    dayChange: 2.50,
    dayChangePercent: 1.37,
  },
  {
    id: '2',
    symbol: 'MSFT',
    companyName: 'Microsoft Corporation',
    quantity: 30,
    avgPrice: 415.20,
    currentPrice: 425.80,
    value: 12774.00,
    unrealizedPL: 318.00,
    unrealizedPLPercent: 2.55,
    dayChange: 5.20,
    dayChangePercent: 1.24,
  },
  {
    id: '3',
    symbol: 'NVDA',
    companyName: 'NVIDIA Corporation',
    quantity: 25,
    avgPrice: 920.50,
    currentPrice: 985.75,
    value: 24643.75,
    unrealizedPL: 1631.25,
    unrealizedPLPercent: 7.09,
    dayChange: 12.30,
    dayChangePercent: 1.26,
  },
  {
    id: '4',
    symbol: 'TSLA',
    companyName: 'Tesla, Inc.',
    quantity: 40,
    avgPrice: 245.80,
    currentPrice: 238.50,
    value: 9540.00,
    unrealizedPL: -292.00,
    unrealizedPLPercent: -2.97,
    dayChange: -3.20,
    dayChangePercent: -1.32,
  },
];

// Mock Trades
export const mockTrades: Trade[] = [
  {
    id: '1',
    portfolioId: '1',
    symbol: 'NVDA',
    type: 'BUY',
    quantity: 10,
    price: 920.50,
    total: 9205.00,
    timestamp: '2026-03-04T09:30:00Z',
    status: 'FILLED',
  },
  {
    id: '2',
    portfolioId: '1',
    symbol: 'AAPL',
    type: 'BUY',
    quantity: 25,
    price: 178.50,
    total: 4462.50,
    timestamp: '2026-03-03T14:15:00Z',
    status: 'FILLED',
  },
  {
    id: '3',
    portfolioId: '1',
    symbol: 'TSLA',
    type: 'SELL',
    quantity: 15,
    price: 242.30,
    total: 3634.50,
    timestamp: '2026-03-02T11:45:00Z',
    status: 'FILLED',
  },
];

// Mock Analysis
export const mockAnalyses: Analysis[] = [
  {
    id: '1',
    userId: '1',
    username: 'johndoe',
    title: 'NVDA Breaking Out - Target $1100',
    content: 'NVIDIA showing strong momentum with AI chip demand exceeding expectations. Technical breakout above $950 resistance confirms bullish continuation pattern. Entry: $985, Target: $1100, Stop: $920.',
    symbols: ['NVDA'],
    sentiment: 'BULLISH',
    targetPrice: 1100,
    timeHorizon: '3 months',
    createdAt: '2026-03-04T08:00:00Z',
    immutable: true,
    likes: 234,
    comments: 45,
    outcome: 'PENDING',
  },
  {
    id: '2',
    userId: '2',
    username: 'tradequeen',
    title: 'Tech Correction Incoming',
    content: 'Major tech stocks showing signs of exhaustion. RSI overbought across the board, volume declining. Expecting 5-8% pullback in the next 2 weeks. Reducing exposure to AAPL, MSFT, GOOGL.',
    symbols: ['AAPL', 'MSFT', 'GOOGL'],
    sentiment: 'BEARISH',
    timeHorizon: '2 weeks',
    createdAt: '2026-03-03T15:30:00Z',
    immutable: true,
    likes: 156,
    comments: 67,
    outcome: 'INCORRECT',
  },
  {
    id: '3',
    userId: '3',
    username: 'valueinvestor',
    title: 'JPM Undervalued at Current Levels',
    content: 'JPMorgan trading below historical P/E ratio despite strong earnings. Banking sector recovery play with solid dividend yield. Long-term hold with 20%+ upside.',
    symbols: ['JPM'],
    sentiment: 'BULLISH',
    targetPrice: 185,
    timeHorizon: '6 months',
    createdAt: '2026-03-02T10:15:00Z',
    immutable: true,
    likes: 89,
    comments: 23,
    outcome: 'CORRECT',
  },
];

// Mock Tournaments
export const mockTournaments: Tournament[] = [
  {
    id: '1',
    name: 'Spring Trading Championship 2026',
    description: 'Compete for the highest return in 30 days. All participants start with $100,000 virtual cash.',
    startDate: '2026-03-01T00:00:00Z',
    endDate: '2026-03-31T23:59:59Z',
    status: 'LIVE',
    participants: 1247,
    prizePool: 'Badges & Recognition',
    rules: 'No leverage allowed. Must make at least 10 trades. Portfolio must be public.',
  },
  {
    id: '2',
    name: 'Tech Stock Challenge',
    description: 'Trade only tech stocks (NASDAQ-100). Who can generate the best risk-adjusted returns?',
    startDate: '2026-03-15T00:00:00Z',
    endDate: '2026-04-15T23:59:59Z',
    status: 'UPCOMING',
    participants: 523,
    prizePool: 'Badges & Recognition',
    rules: 'NASDAQ-100 stocks only. Max 5 positions. No crypto or forex.',
  },
];

// Mock Leaderboard
export const mockLeaderboard: LeaderboardEntry[] = [
  {
    rank: 1,
    previousRank: 2,
    userId: '5',
    username: 'alphatrader',
    portfolioId: '5',
    portfolioName: 'Momentum Master',
    return: 18750.50,
    returnPercent: 18.75,
    trades: 87,
    winRate: 68.5,
    verified: true,
  },
  {
    rank: 2,
    previousRank: 1,
    userId: '8',
    username: 'marketwizard',
    portfolioId: '8',
    portfolioName: 'AI Stocks Only',
    return: 16890.25,
    returnPercent: 16.89,
    trades: 62,
    winRate: 71.2,
    verified: true,
  },
  {
    rank: 3,
    previousRank: 4,
    userId: '12',
    username: 'swingking',
    portfolioId: '12',
    portfolioName: 'Swing Strategy',
    return: 15320.75,
    returnPercent: 15.32,
    trades: 143,
    winRate: 64.8,
    verified: true,
  },
  {
    rank: 4,
    userId: '1',
    username: 'johndoe',
    portfolioId: '1',
    portfolioName: 'Tech Growth',
    return: 25840.50,
    returnPercent: 25.84,
    trades: 45,
    winRate: 62.2,
    verified: true,
  },
];

// Mock Notifications
export const mockNotifications: Notification[] = [
  {
    id: '1',
    type: 'TRADE',
    title: 'Trade Executed',
    message: 'Your buy order for 10 shares of NVDA at $920.50 has been filled.',
    timestamp: '2026-03-04T09:30:00Z',
    read: false,
    link: '/portfolio/1',
  },
  {
    id: '2',
    type: 'SOCIAL',
    title: 'New Follower',
    message: 'tradequeen started following your portfolio "Tech Growth"',
    timestamp: '2026-03-04T08:15:00Z',
    read: false,
    link: '/profile/tradequeen',
  },
  {
    id: '3',
    type: 'ANALYSIS',
    title: 'Analysis Update',
    message: 'Your analysis "NVDA Breaking Out" received 50 likes',
    timestamp: '2026-03-03T16:30:00Z',
    read: true,
    link: '/analysis/1',
  },
  {
    id: '4',
    type: 'TOURNAMENT',
    title: 'Tournament Starting Soon',
    message: 'Spring Trading Championship starts in 2 hours',
    timestamp: '2026-03-03T10:00:00Z',
    read: true,
    link: '/tournament/1',
  },
];

// Market Overview Data
export const mockMarketData = {
  spy: { price: 512.45, change: 2.34, changePercent: 0.46 },
  qqq: { price: 445.67, change: -1.23, changePercent: -0.28 },
  dia: { price: 389.12, change: 0.89, changePercent: 0.23 },
  vix: { price: 14.56, change: -0.45, changePercent: -3.00 },
};

// Equity Chart Data
export const mockEquityData = [
  { date: '2026-01-15', value: 100000 },
  { date: '2026-01-22', value: 102500 },
  { date: '2026-01-29', value: 105200 },
  { date: '2026-02-05', value: 103800 },
  { date: '2026-02-12', value: 108500 },
  { date: '2026-02-19', value: 112300 },
  { date: '2026-02-26', value: 115800 },
  { date: '2026-03-04', value: 125840.50 },
];
