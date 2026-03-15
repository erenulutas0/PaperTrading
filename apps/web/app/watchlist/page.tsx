'use client';


import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import Link from 'next/link';
import { apiFetch } from '../../lib/api-client';
import MarketWorkspaceChart from '../../components/MarketWorkspaceChart';
import { decodeSharedLayout, encodeSharedLayout, SharedTerminalLayoutPayload } from '../../lib/market-terminal-share';
import {
    createTerminalLayout,
    deleteTerminalLayout,
    fetchTerminalLayouts,
    fetchUserPreferences,
    TerminalCompareBasketPayload,
    TerminalLayoutResponsePayload,
    TerminalScannerViewPayload,
    updateTerminalLayout,
    updateTerminalPreferences,
} from '../../lib/user-preferences';

interface WatchlistItem {
    id: string;
    symbol: string;
    currentPrice: number;
    changePercent24h: number;
    alertPriceAbove: string | number;
    alertPriceBelow: string | number;
    alertAboveTriggered: boolean;
    alertBelowTriggered: boolean;
    notes: string;
}

interface Watchlist {
    id: string;
    name: string;
    userId: string;
    createdAt: string;
    items: WatchlistItem[] | null;
}

interface InstrumentOption {
    symbol: string;
    displayName: string;
    assetType: string;
    market?: string;
    exchange?: string;
    currency?: string;
    sector?: string;
    delayLabel?: string;
    currentPrice: number;
    changePercent24h: number;
}

interface CandlePoint {
    openTime: number;
    open: number;
    high: number;
    low: number;
    close: number;
    volume: number;
}

interface AlertLine {
    id: string;
    label: string;
    price: number;
    color: string;
}

interface CompareSeriesInput {
    label: string;
    data: CandlePoint[];
    color: string;
}

interface CompareBasketPreset {
    id: string;
    name: string;
    market: MarketSelection;
    symbols: string[];
    updatedAt: string;
}

interface SuggestedCompareBasket {
    id: string;
    name: string;
    description: string;
    symbols: string[];
}

interface ScannerViewPreset {
    id: string;
    name: string;
    market: MarketSelection;
    quickFilter: UniverseQuickFilter;
    sortMode: UniverseSortMode;
    query: string;
    anchorSymbol?: string | null;
    updatedAt: string;
}

interface BuiltInCompareBasketTemplate {
    id: string;
    name: string;
    market: MarketSelection;
    description: string;
    symbols: string[];
}

interface ChartNote {
    id: string;
    body: string;
    pinned: boolean;
    createdAt: string;
}

interface AlertHistoryEntry {
    id: string;
    watchlistItemId: string;
    symbol: string;
    direction: 'ABOVE' | 'BELOW';
    thresholdPrice: number;
    triggeredPrice: number;
    message: string;
    triggeredAt: string;
}

type ChartRange = '1D' | '1W' | '1M' | '3M' | '6M' | '1Y' | 'ALL';
type ChartInterval = '1m' | '15m' | '30m' | '1h' | '4h' | '1d';
type DrawingMode = 'none' | 'horizontal' | 'trend';
type MarketSelection = 'CRYPTO' | 'BIST100';
type ChartNoteFilter = 'ALL' | 'PINNED' | 'UNPINNED';
type AlertHistoryFilter = 'ALL' | 'ABOVE' | 'BELOW';
type AlertHistoryWindow = 'ALL' | '24H' | '7D' | '30D';
type UniverseQuickFilter = 'ALL' | 'GAINERS' | 'LOSERS' | 'FAVORITES' | 'SECTOR';
type UniverseSortMode = 'MOVE_DESC' | 'MOVE_ASC' | 'PRICE_DESC' | 'ALPHA';

const RANGE_OPTIONS: ChartRange[] = ['1D', '1W', '1M', '3M', '6M', '1Y', 'ALL'];
const INTERVAL_OPTIONS: ChartInterval[] = ['1m', '15m', '30m', '1h', '4h', '1d'];
const MARKET_OPTIONS: MarketSelection[] = ['CRYPTO', 'BIST100'];
const ALL_HISTORY_CHUNK = 1000;
const MARKET_SESSION_STORAGE_KEY = 'market.terminal.session';
const COMPARE_BASKET_STORAGE_KEY = 'market.terminal.compare-baskets';
const SCANNER_VIEW_STORAGE_KEY = 'market.terminal.scanner-views';
const COMPARE_COLORS = ['#f59e0b', '#38bdf8', '#f472b6'];
const CRYPTO_CORE_COMPARE_SYMBOLS = ['BTCUSDT', 'ETHUSDT', 'BNBUSDT', 'SOLUSDT', 'AVAXUSDT'];
const BUILT_IN_COMPARE_BASKETS: BuiltInCompareBasketTemplate[] = [
    {
        id: 'crypto-majors',
        name: 'Crypto Majors',
        market: 'CRYPTO',
        description: 'Large-cap crypto benchmark basket.',
        symbols: ['BTCUSDT', 'ETHUSDT', 'BNBUSDT'],
    },
    {
        id: 'crypto-alt-beta',
        name: 'Alt Beta',
        market: 'CRYPTO',
        description: 'Higher-beta liquid alt basket.',
        symbols: ['SOLUSDT', 'AVAXUSDT', 'BNBUSDT'],
    },
    {
        id: 'bist-banks',
        name: 'BIST Banks',
        market: 'BIST100',
        description: 'Large Turkish banking names.',
        symbols: ['AKBNK', 'GARAN', 'ISCTR'],
    },
    {
        id: 'bist-holdings',
        name: 'BIST Holdings',
        market: 'BIST100',
        description: 'Large holding-company basket.',
        symbols: ['KCHOL', 'SAHOL', 'DOHOL'],
    },
];

interface PersistedMarketSession {
    selectedWatchlist: string | null;
    selectedSymbol: string;
    compareSymbols: string[];
    compareVisible: boolean;
    selectedMarket: MarketSelection;
    selectedRange: ChartRange;
    selectedInterval: ChartInterval;
}

function readPersistedMarketSession(): PersistedMarketSession | null {
    if (typeof window === 'undefined') {
        return null;
    }
    try {
        const stored = window.localStorage.getItem(MARKET_SESSION_STORAGE_KEY);
        if (!stored) {
            return null;
        }
        const parsed = JSON.parse(stored);
        if (!parsed || typeof parsed !== 'object') {
            return null;
        }
        const selectedMarket = MARKET_OPTIONS.includes(parsed.selectedMarket) ? parsed.selectedMarket : 'CRYPTO';
        const selectedRange = RANGE_OPTIONS.includes(parsed.selectedRange) ? parsed.selectedRange : '1D';
        const selectedInterval = INTERVAL_OPTIONS.includes(parsed.selectedInterval) ? parsed.selectedInterval : '1h';
        return {
            selectedWatchlist: typeof parsed.selectedWatchlist === 'string' ? parsed.selectedWatchlist : null,
            selectedSymbol: typeof parsed.selectedSymbol === 'string' && parsed.selectedSymbol ? parsed.selectedSymbol : 'BTCUSDT',
            compareSymbols: Array.isArray(parsed.compareSymbols)
                ? parsed.compareSymbols.filter((value: unknown): value is string => typeof value === 'string' && value.length > 0)
                : (typeof parsed.compareSymbol === 'string' && parsed.compareSymbol ? [parsed.compareSymbol] : []),
            compareVisible: parsed.compareVisible !== false,
            selectedMarket,
            selectedRange,
            selectedInterval,
        };
    } catch (error) {
        console.error(error);
        return null;
    }
}

function readPersistedCompareBaskets(): CompareBasketPreset[] {
    if (typeof window === 'undefined') {
        return [];
    }
    try {
        const stored = window.localStorage.getItem(COMPARE_BASKET_STORAGE_KEY);
        if (!stored) {
            return [];
        }
        const parsed = JSON.parse(stored);
        if (!Array.isArray(parsed)) {
            return [];
        }
        return parsed
            .filter((entry): entry is Record<string, unknown> => !!entry && typeof entry === 'object')
            .map((entry): CompareBasketPreset => ({
                id: typeof entry.id === 'string' ? entry.id : crypto.randomUUID(),
                name: typeof entry.name === 'string' && entry.name.trim() ? entry.name.trim() : 'Compare Basket',
                market: entry.market === 'BIST100' ? 'BIST100' : 'CRYPTO',
                symbols: Array.isArray(entry.symbols)
                    ? entry.symbols.filter((value): value is string => typeof value === 'string' && value.length > 0).slice(0, 3)
                    : [],
                updatedAt: typeof entry.updatedAt === 'string' ? entry.updatedAt : new Date().toISOString(),
            }))
            .filter((entry) => entry.symbols.length > 0)
            .slice(0, 12);
    } catch (error) {
        console.error(error);
        return [];
    }
}

function haveSameSymbols(left: string[], right: string[]) {
    if (left.length !== right.length) {
        return false;
    }
    const leftSorted = [...left].sort();
    const rightSorted = [...right].sort();
    return leftSorted.every((value, index) => value === rightSorted[index]);
}

function readPersistedScannerViews(): ScannerViewPreset[] {
    if (typeof window === 'undefined') {
        return [];
    }
    try {
        const stored = window.localStorage.getItem(SCANNER_VIEW_STORAGE_KEY);
        if (!stored) {
            return [];
        }
        const parsed = JSON.parse(stored);
        if (!Array.isArray(parsed)) {
            return [];
        }
        return parsed
            .filter((entry): entry is Record<string, unknown> => !!entry && typeof entry === 'object')
            .map((entry): ScannerViewPreset => ({
                id: typeof entry.id === 'string' ? entry.id : crypto.randomUUID(),
                name: typeof entry.name === 'string' && entry.name.trim() ? entry.name.trim() : 'Scanner View',
                market: entry.market === 'BIST100' ? 'BIST100' : 'CRYPTO',
                quickFilter: ['ALL', 'GAINERS', 'LOSERS', 'FAVORITES', 'SECTOR'].includes(String(entry.quickFilter)) ? entry.quickFilter as UniverseQuickFilter : 'ALL',
                sortMode: ['MOVE_DESC', 'MOVE_ASC', 'PRICE_DESC', 'ALPHA'].includes(String(entry.sortMode)) ? entry.sortMode as UniverseSortMode : 'MOVE_DESC',
                query: typeof entry.query === 'string' ? entry.query : '',
                anchorSymbol: typeof entry.anchorSymbol === 'string' && entry.anchorSymbol ? entry.anchorSymbol : null,
                updatedAt: typeof entry.updatedAt === 'string' ? entry.updatedAt : new Date().toISOString(),
            }))
            .slice(0, 12);
    } catch (error) {
        console.error(error);
        return [];
    }
}

function normalizeCompareBasketPayloads(
    baskets: TerminalCompareBasketPayload[] | CompareBasketPreset[] | null | undefined,
): CompareBasketPreset[] {
    if (!Array.isArray(baskets)) {
        return [];
    }
    return baskets
        .filter((basket): basket is TerminalCompareBasketPayload => !!basket && typeof basket === 'object')
        .map((basket): CompareBasketPreset => ({
            id: crypto.randomUUID(),
            name: typeof basket.name === 'string' && basket.name.trim() ? basket.name.trim() : 'Compare Basket',
            market: basket.market === 'BIST100' ? 'BIST100' : 'CRYPTO',
            symbols: Array.isArray(basket.symbols)
                ? basket.symbols.filter((value): value is string => typeof value === 'string' && value.length > 0).slice(0, 3)
                : [],
            updatedAt: typeof basket.updatedAt === 'string' ? basket.updatedAt : new Date().toISOString(),
        }))
        .filter((basket) => basket.symbols.length > 0)
        .slice(0, 12);
}

function normalizeScannerViewPayloads(
    views: TerminalScannerViewPayload[] | ScannerViewPreset[] | null | undefined,
): ScannerViewPreset[] {
    if (!Array.isArray(views)) {
        return [];
    }
    return views
        .filter((view): view is TerminalScannerViewPayload => !!view && typeof view === 'object')
        .map((view): ScannerViewPreset => ({
            id: crypto.randomUUID(),
            name: typeof view.name === 'string' && view.name.trim() ? view.name.trim() : 'Scanner View',
            market: view.market === 'BIST100' ? 'BIST100' : 'CRYPTO',
            quickFilter: ['ALL', 'GAINERS', 'LOSERS', 'FAVORITES', 'SECTOR'].includes(String(view.quickFilter)) ? view.quickFilter as UniverseQuickFilter : 'ALL',
            sortMode: ['MOVE_DESC', 'MOVE_ASC', 'PRICE_DESC', 'ALPHA'].includes(String(view.sortMode)) ? view.sortMode as UniverseSortMode : 'MOVE_DESC',
            query: typeof view.query === 'string' ? view.query : '',
            anchorSymbol: typeof view.anchorSymbol === 'string' && view.anchorSymbol ? view.anchorSymbol : null,
            updatedAt: typeof view.updatedAt === 'string' ? view.updatedAt : new Date().toISOString(),
        }))
        .slice(0, 12);
}

function getInitialAllHistoryChunkCount(interval: ChartInterval) {
    switch (interval) {
        case '1d':
            return 6;
        case '4h':
            return 5;
        case '1h':
            return 4;
        case '30m':
            return 3;
        case '15m':
            return 2;
        default:
            return 1;
    }
}

function formatMoney(value: number) {
    return `$${Number(value).toLocaleString(undefined, {
        minimumFractionDigits: 2,
        maximumFractionDigits: 2,
    })}`;
}

function formatPercent(value: number) {
    const normalized = Number(value ?? 0);
    return `${normalized >= 0 ? '+' : ''}${normalized.toFixed(2)}%`;
}

function sortChartNotes(notes: ChartNote[]) {
    return [...notes].sort((left, right) => {
        if (left.pinned !== right.pinned) {
            return left.pinned ? -1 : 1;
        }
        return new Date(right.createdAt).getTime() - new Date(left.createdAt).getTime();
    });
}


function escapeSvgText(value: string) {
    return value
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&apos;');
}

export default function WatchlistPage() {
    const [watchlists, setWatchlists] = useState<Watchlist[]>([]);
    const [selectedWatchlist, setSelectedWatchlist] = useState<string | null>(null);
    const [enrichedItems, setEnrichedItems] = useState<WatchlistItem[]>([]);
    const [instrumentUniverse, setInstrumentUniverse] = useState<InstrumentOption[]>([]);
    const [instrumentQuery, setInstrumentQuery] = useState('');
    const [universeQuickFilter, setUniverseQuickFilter] = useState<UniverseQuickFilter>('ALL');
    const [universeSortMode, setUniverseSortMode] = useState<UniverseSortMode>('MOVE_DESC');
    const [scannerViews, setScannerViews] = useState<ScannerViewPreset[]>([]);
    const [scannerViewNameDraft, setScannerViewNameDraft] = useState('');
    const [scannerViewMessage, setScannerViewMessage] = useState('');
    const [editingScannerViewId, setEditingScannerViewId] = useState<string | null>(null);
    const [editingScannerViewName, setEditingScannerViewName] = useState('');
    const [selectedMarket, setSelectedMarket] = useState<MarketSelection>('CRYPTO');
    const [selectedSymbol, setSelectedSymbol] = useState<string>('BTCUSDT');
    const [compareSymbols, setCompareSymbols] = useState<string[]>([]);
    const [compareCandidate, setCompareCandidate] = useState<string>('');
    const [compareVisible, setCompareVisible] = useState<boolean>(true);
    const [compareBaskets, setCompareBaskets] = useState<CompareBasketPreset[]>([]);
    const [compareBasketNameDraft, setCompareBasketNameDraft] = useState('');
    const [compareBasketMessage, setCompareBasketMessage] = useState('');
    const [editingCompareBasketId, setEditingCompareBasketId] = useState<string | null>(null);
    const [editingCompareBasketName, setEditingCompareBasketName] = useState('');
    const [selectedRange, setSelectedRange] = useState<ChartRange>('1D');
    const [selectedInterval, setSelectedInterval] = useState<ChartInterval>('1h');
    const [drawingMode, setDrawingMode] = useState<DrawingMode>('none');
    const [clearDrawingsToken, setClearDrawingsToken] = useState(0);
    const [favoriteSymbols, setFavoriteSymbols] = useState<string[]>([]);
    const [terminalLayouts, setTerminalLayouts] = useState<TerminalLayoutResponsePayload[]>([]);
    const [layoutNameDraft, setLayoutNameDraft] = useState('');
    const [editingLayoutId, setEditingLayoutId] = useState<string | null>(null);
    const [editingLayoutName, setEditingLayoutName] = useState('');
    const [activeLayoutId, setActiveLayoutId] = useState<string | null>(null);
    const [layoutImportMessage, setLayoutImportMessage] = useState<string>('');
    const [sharedLayout, setSharedLayout] = useState<SharedTerminalLayoutPayload | null>(null);
    const [sharedLayoutMessage, setSharedLayoutMessage] = useState<string>('');
    const [snapshotMessage, setSnapshotMessage] = useState<string>('');
    const [candles, setCandles] = useState<CandlePoint[]>([]);
    const [compareCandles, setCompareCandles] = useState<Record<string, CandlePoint[]>>({});
    const [chartActivePoint, setChartActivePoint] = useState<CandlePoint | null>(null);
    const [chartNotes, setChartNotes] = useState<ChartNote[]>([]);
    const [chartNoteDraft, setChartNoteDraft] = useState('');
    const [chartNoteQuery, setChartNoteQuery] = useState('');
    const [chartNoteFilter, setChartNoteFilter] = useState<ChartNoteFilter>('ALL');
    const [editingNoteId, setEditingNoteId] = useState<string | null>(null);
    const [editingNoteDraft, setEditingNoteDraft] = useState('');
    const [alertHistory, setAlertHistory] = useState<AlertHistoryEntry[]>([]);
    const [alertHistoryLoading, setAlertHistoryLoading] = useState(false);
    const [alertHistoryFilter, setAlertHistoryFilter] = useState<AlertHistoryFilter>('ALL');
    const [alertHistoryWindow, setAlertHistoryWindow] = useState<AlertHistoryWindow>('7D');
    const [loading, setLoading] = useState(true);
    const [chartLoading, setChartLoading] = useState(false);
    const [loadingMoreHistory, setLoadingMoreHistory] = useState(false);
    const [hasMoreHistory, setHasMoreHistory] = useState(true);
    const [sessionHydrated, setSessionHydrated] = useState(false);
    const [terminalPreferencesReady, setTerminalPreferencesReady] = useState(false);
    const [compareBasketsHydrated, setCompareBasketsHydrated] = useState(false);
    const [scannerViewsHydrated, setScannerViewsHydrated] = useState(false);
    const candlesRef = useRef<CandlePoint[]>([]);
    const layoutImportInputRef = useRef<HTMLInputElement | null>(null);
    const compareBasketImportInputRef = useRef<HTMLInputElement | null>(null);
    const scannerViewImportInputRef = useRef<HTMLInputElement | null>(null);

    const [newName, setNewName] = useState('');
    const [addSymbol, setAddSymbol] = useState('BTCUSDT');
    const [addAlertAbove, setAddAlertAbove] = useState('');
    const [addAlertBelow, setAddAlertBelow] = useState('');
    const [addNotes, setAddNotes] = useState('');
    const [showAddForm, setShowAddForm] = useState(false);

    const currentUserId = typeof window !== 'undefined' ? localStorage.getItem('userId') : null;

    const selectedInstrument = useMemo(() => {
        return instrumentUniverse.find((item) => item.symbol === selectedSymbol)
            ?? enrichedItems.find((item) => item.symbol === selectedSymbol)
            ?? null;
    }, [enrichedItems, instrumentUniverse, selectedSymbol]);

    const instrumentMap = useMemo(() => {
        return new Map(instrumentUniverse.map((instrument) => [instrument.symbol, instrument]));
    }, [instrumentUniverse]);

    const selectedDisplayName = useMemo(() => {
        if (!selectedInstrument) {
            return selectedSymbol;
        }
        if ('displayName' in selectedInstrument) {
            return selectedInstrument.displayName;
        }
        return selectedInstrument.symbol;
    }, [selectedInstrument, selectedSymbol]);

    const selectedAssetType = useMemo(() => {
        if (!selectedInstrument) {
            return 'CRYPTO';
        }
        if ('assetType' in selectedInstrument) {
            return selectedInstrument.assetType;
        }
        return 'WATCHLIST';
    }, [selectedInstrument]);

    const selectedInstrumentMetadata = useMemo(() => {
        if (!selectedInstrument || !('market' in selectedInstrument)) {
            return null;
        }
        return selectedInstrument;
    }, [selectedInstrument]);

    const selectedCompareInstruments = useMemo(() => {
        return compareSymbols
            .map((symbol) => instrumentUniverse.find((item) => item.symbol === symbol)
                ?? enrichedItems.find((item) => item.symbol === symbol)
                ?? null)
            .filter((item): item is InstrumentOption | WatchlistItem => item !== null);
    }, [compareSymbols, enrichedItems, instrumentUniverse]);

    const availableCompareBaskets = useMemo(() => {
        return compareBaskets.filter((basket) => basket.market === selectedMarket);
    }, [compareBaskets, selectedMarket]);

    const activeCompareBasketId = useMemo(() => {
        const active = availableCompareBaskets.find((basket) => haveSameSymbols(basket.symbols, compareSymbols));
        return active?.id ?? null;
    }, [availableCompareBaskets, compareSymbols]);

    const suggestedCompareBaskets = useMemo<SuggestedCompareBasket[]>(() => {
        const suggestions: SuggestedCompareBasket[] = [];
        const exclude = new Set([selectedSymbol]);
        const sameSectorSymbols = instrumentUniverse
            .filter((instrument) => instrument.symbol !== selectedSymbol)
            .filter((instrument) => selectedInstrumentMetadata?.sector && instrument.sector === selectedInstrumentMetadata.sector)
            .map((instrument) => instrument.symbol)
            .slice(0, 3);

        if (sameSectorSymbols.length > 0 && selectedInstrumentMetadata?.sector) {
            suggestions.push({
                id: 'sector-peers',
                name: 'Sector Peers',
                description: `${selectedInstrumentMetadata.sector} names around ${selectedSymbol}.`,
                symbols: sameSectorSymbols,
            });
            sameSectorSymbols.forEach((symbol) => exclude.add(symbol));
        }

        const favoritePeerSymbols = favoriteSymbols
            .filter((symbol) => symbol !== selectedSymbol)
            .filter((symbol) => instrumentMap.has(symbol))
            .filter((symbol) => !exclude.has(symbol))
            .slice(0, 3);

        if (favoritePeerSymbols.length > 0) {
            suggestions.push({
                id: 'favorites-blend',
                name: 'Favorites Blend',
                description: 'Use your starred symbols as a quick compare peer set.',
                symbols: favoritePeerSymbols,
            });
            favoritePeerSymbols.forEach((symbol) => exclude.add(symbol));
        }

        const marketCoreSymbols = (selectedMarket === 'CRYPTO'
            ? CRYPTO_CORE_COMPARE_SYMBOLS
            : instrumentUniverse.map((instrument) => instrument.symbol))
            .filter((symbol) => symbol !== selectedSymbol)
            .filter((symbol) => instrumentMap.has(symbol))
            .filter((symbol) => !exclude.has(symbol))
            .slice(0, 3);

        if (marketCoreSymbols.length > 0) {
            suggestions.push({
                id: 'market-core',
                name: selectedMarket === 'CRYPTO' ? 'Crypto Core' : 'BIST Core',
                description: selectedMarket === 'CRYPTO'
                    ? 'Fast benchmark against liquid crypto leaders.'
                    : 'Quick benchmark against the current BIST universe.',
                symbols: marketCoreSymbols,
            });
        }

        return suggestions.filter((suggestion) => suggestion.symbols.length > 0);
    }, [favoriteSymbols, instrumentMap, instrumentUniverse, selectedInstrumentMetadata?.sector, selectedMarket, selectedSymbol]);

    const builtInCompareBaskets = useMemo(() => {
        return BUILT_IN_COMPARE_BASKETS
            .filter((basket) => basket.market === selectedMarket)
            .map((basket) => ({
                ...basket,
                symbols: basket.symbols
                    .filter((symbol) => symbol !== selectedSymbol)
                    .filter((symbol) => instrumentMap.has(symbol))
                    .slice(0, 3),
            }))
            .filter((basket) => basket.symbols.length > 0);
    }, [instrumentMap, selectedMarket, selectedSymbol]);

    const canSaveCompareBasket = useMemo(() => {
        if (!compareBasketNameDraft.trim() || compareSymbols.length === 0) {
            return false;
        }
        const replacingExisting = compareBaskets.some((basket) => basket.market === selectedMarket && haveSameSymbols(basket.symbols, compareSymbols));
        return compareBaskets.length < 12 || replacingExisting;
    }, [compareBasketNameDraft, compareBaskets, compareSymbols, selectedMarket]);

    const selectedWatchlistItem = useMemo(() => {
        return enrichedItems.find((item) => item.symbol === selectedSymbol) ?? null;
    }, [enrichedItems, selectedSymbol]);

    const selectedWatchlistMeta = useMemo(() => {
        if (!selectedWatchlist) {
            return null;
        }
        return watchlists.find((watchlist) => watchlist.id === selectedWatchlist) ?? null;
    }, [selectedWatchlist, watchlists]);

    const selectedWatchlistItemCount = useMemo(() => {
        return Array.isArray(selectedWatchlistMeta?.items) ? selectedWatchlistMeta.items.length : 0;
    }, [selectedWatchlistMeta]);

    const describeCompareBasketSnapshot = useCallback((symbols: string[]) => {
        const instruments = symbols
            .map((symbol) => instrumentMap.get(symbol) ?? null)
            .filter((instrument): instrument is InstrumentOption => instrument !== null);
        if (instruments.length === 0) {
            return 'No live market snapshot';
        }
        const averageMove = instruments.reduce((sum, instrument) => sum + Number(instrument.changePercent24h ?? 0), 0) / instruments.length;
        const leader = [...instruments].sort((left, right) => Number(right.changePercent24h ?? 0) - Number(left.changePercent24h ?? 0))[0];
        return `Avg ${formatPercent(averageMove)} · Leader ${leader.symbol} ${formatPercent(Number(leader.changePercent24h ?? 0))}`;
    }, [instrumentMap]);

    const describeCompareBasketTone = useCallback((symbols: string[]) => {
        const instruments = symbols
            .map((symbol) => instrumentMap.get(symbol) ?? null)
            .filter((instrument): instrument is InstrumentOption => instrument !== null);
        if (instruments.length === 0) {
            return {
                label: 'Snapshot Pending',
                className: 'border-white/10 bg-white/[0.03] text-zinc-400',
            };
        }
        const averageMove = instruments.reduce((sum, instrument) => sum + Number(instrument.changePercent24h ?? 0), 0) / instruments.length;
        if (averageMove >= 1.5) {
            return {
                label: 'Strong Up',
                className: 'border-emerald-400/25 bg-emerald-400/10 text-emerald-300',
            };
        }
        if (averageMove > 0) {
            return {
                label: 'Positive',
                className: 'border-emerald-400/20 bg-emerald-400/10 text-emerald-200',
            };
        }
        if (averageMove <= -1.5) {
            return {
                label: 'Strong Down',
                className: 'border-red-400/25 bg-red-400/10 text-red-300',
            };
        }
        if (averageMove < 0) {
            return {
                label: 'Negative',
                className: 'border-red-400/20 bg-red-400/10 text-red-200',
            };
        }
        return {
            label: 'Mixed',
            className: 'border-white/10 bg-white/[0.03] text-zinc-300',
        };
    }, [instrumentMap]);

    const getCompareBasketSparklineModel = useCallback((symbols: string[]) => {
        const items = symbols
            .map((symbol) => {
                const instrument = instrumentMap.get(symbol) ?? null;
                if (!instrument) {
                    return null;
                }
                return {
                    symbol,
                    move: Number(instrument.changePercent24h ?? 0),
                };
            })
            .filter((item): item is { symbol: string; move: number } => item !== null);
        if (items.length === 0) {
            return null;
        }

        const min = items.reduce((current, item) => Math.min(current, item.move), items[0].move);
        const max = items.reduce((current, item) => Math.max(current, item.move), items[0].move);
        const range = Math.max(max - min, 1);
        const width = 96;
        const height = 28;
        const points = items.map((item, index) => {
            const x = items.length === 1 ? width / 2 : (index * width) / (items.length - 1);
            const y = height - (((item.move - min) / range) * height);
            return `${x},${y}`;
        }).join(' ');
        const baseline = max <= 0
            ? 0
            : min >= 0
                ? height
                : height - (((0 - min) / range) * height);
        const positive = items.reduce((sum, item) => sum + item.move, 0) >= 0;

        return {
            items,
            points,
            baseline,
            positive,
            width,
            height,
            min,
            range,
        };
    }, [instrumentMap]);

    const renderCompareBasketSparkline = useCallback((symbols: string[]) => {
        const model = getCompareBasketSparklineModel(symbols);
        if (!model) {
            return (
                <div className="mt-3 rounded-2xl border border-white/10 bg-black/25 px-3 py-2 text-[10px] uppercase tracking-[0.14em] text-zinc-500">
                    Strength Curve Pending
                </div>
            );
        }

        return (
            <div className="mt-3 rounded-2xl border border-white/10 bg-black/25 px-3 py-2">
                <div className="flex items-center justify-between gap-2 text-[10px] uppercase tracking-[0.14em] text-zinc-500">
                    <span>Strength Curve</span>
                    <span>{model.items.length} points</span>
                </div>
                <svg viewBox={`0 0 ${model.width} ${model.height}`} className="mt-2 h-10 w-full overflow-visible">
                    <line
                        x1="0"
                        x2={model.width}
                        y1={model.baseline}
                        y2={model.baseline}
                        stroke="rgba(255,255,255,0.16)"
                        strokeDasharray="3 3"
                    />
                    <polyline
                        fill="none"
                        stroke={model.positive ? '#34d399' : '#f87171'}
                        strokeWidth="2.5"
                        points={model.points}
                        strokeLinejoin="round"
                        strokeLinecap="round"
                    />
                    {model.items.map((item, index) => {
                        const x = model.items.length === 1 ? model.width / 2 : (index * model.width) / (model.items.length - 1);
                        const y = model.height - (((item.move - model.min) / model.range) * model.height);
                        return (
                            <circle
                                key={`${item.symbol}-spark-${index}`}
                                cx={x}
                                cy={y}
                                r="2.5"
                                fill={item.move >= 0 ? '#34d399' : '#f87171'}
                            />
                        );
                    })}
                </svg>
                <div className="mt-2 flex flex-wrap gap-1.5">
                    {model.items.map((item) => (
                        <span
                            key={`${item.symbol}-spark-label`}
                            className={`rounded-full border px-2 py-0.5 text-[10px] font-semibold uppercase tracking-[0.14em] ${item.move >= 0
                                ? 'border-emerald-400/20 bg-emerald-400/10 text-emerald-200'
                                : 'border-red-400/20 bg-red-400/10 text-red-200'}`}
                        >
                            {item.symbol} {formatPercent(item.move)}
                        </span>
                    ))}
                </div>
            </div>
        );
    }, [getCompareBasketSparklineModel]);

    const topPinnedNotes = useMemo(() => {
        return chartNotes.filter((note) => note.pinned).slice(0, 2);
    }, [chartNotes]);

    const filteredChartNotes = useMemo(() => {
        const query = chartNoteQuery.trim().toLowerCase();
        return chartNotes.filter((note) => {
            const matchesQuery = !query || note.body.toLowerCase().includes(query);
            const matchesPinFilter =
                chartNoteFilter === 'ALL'
                || (chartNoteFilter === 'PINNED' && note.pinned)
                || (chartNoteFilter === 'UNPINNED' && !note.pinned);
            return matchesQuery && matchesPinFilter;
        });
    }, [chartNoteFilter, chartNoteQuery, chartNotes]);

    const filteredAlertHistory = useMemo(() => {
        if (alertHistoryFilter === 'ALL') {
            return alertHistory;
        }
        return alertHistory.filter((entry) => entry.direction === alertHistoryFilter);
    }, [alertHistory, alertHistoryFilter]);

    const exportFilteredAlertHistory = useCallback(async () => {
        if (!selectedWatchlistItem || typeof window === 'undefined') {
            return;
        }
        try {
            const url = new URL(`/api/v1/watchlists/items/${selectedWatchlistItem.id}/alert-history/export`, window.location.origin);
            if (alertHistoryWindow === '24H') {
                url.searchParams.set('days', '1');
            } else if (alertHistoryWindow === '7D') {
                url.searchParams.set('days', '7');
            } else if (alertHistoryWindow === '30D') {
                url.searchParams.set('days', '30');
            }
            if (alertHistoryFilter !== 'ALL') {
                url.searchParams.set('direction', alertHistoryFilter);
            }

            const res = await apiFetch(`${url.pathname}${url.search}`, { cache: 'no-store' });
            if (!res.ok) {
                return;
            }
            const blob = await res.blob();
            const downloadUrl = window.URL.createObjectURL(blob);
            const anchor = document.createElement('a');
            anchor.href = downloadUrl;
            anchor.download = `alert-history-${selectedSymbol}-${alertHistoryWindow.toLowerCase()}-${alertHistoryFilter.toLowerCase()}.csv`;
            document.body.appendChild(anchor);
            anchor.click();
            document.body.removeChild(anchor);
            window.URL.revokeObjectURL(downloadUrl);
        } catch (error) {
            console.error(error);
        }
    }, [alertHistoryFilter, alertHistoryWindow, selectedSymbol, selectedWatchlistItem]);

    const chartAlertLines = useMemo<AlertLine[]>(() => {
        if (!selectedWatchlistItem) {
            return [];
        }
        const lines: AlertLine[] = [];
        const above = Number(selectedWatchlistItem.alertPriceAbove);
        const below = Number(selectedWatchlistItem.alertPriceBelow);
        if (Number.isFinite(above) && above > 0) {
            lines.push({
                id: `${selectedWatchlistItem.id}-above`,
                label: selectedWatchlistItem.alertAboveTriggered ? 'Above Triggered' : 'Alert Above',
                price: above,
                color: selectedWatchlistItem.alertAboveTriggered ? '#f97316' : '#22c55e',
            });
        }
        if (Number.isFinite(below) && below > 0) {
            lines.push({
                id: `${selectedWatchlistItem.id}-below`,
                label: selectedWatchlistItem.alertBelowTriggered ? 'Below Triggered' : 'Alert Below',
                price: below,
                color: selectedWatchlistItem.alertBelowTriggered ? '#f97316' : '#ef4444',
            });
        }
        return lines;
    }, [selectedWatchlistItem]);

    const loadedHistoryLabel = useMemo(() => {
        if (candles.length === 0) {
            return 'No candles loaded yet.';
        }
        const oldest = new Date(candles[0].openTime).toLocaleDateString();
        const newest = new Date(candles[candles.length - 1].openTime).toLocaleDateString();
        return `${oldest} → ${newest}`;
    }, [candles]);

    const compareSessionSummary = useMemo(() => {
        if (compareSymbols.length === 0 || candles.length === 0) {
            return [];
        }
        const primaryBase = candles[0].close || 1;
        const primaryLatest = candles[candles.length - 1].close;
        const primaryMovePercent = ((primaryLatest / primaryBase) - 1) * 100;

        return compareSymbols
            .map((symbol, index) => {
                const series = compareCandles[symbol] ?? [];
                if (series.length === 0) {
                    return null;
                }
                const compareBase = series[0].close || 1;
                const compareLatest = series[series.length - 1].close;
                const compareMovePercent = ((compareLatest / compareBase) - 1) * 100;
                const instrument = instrumentUniverse.find((item) => item.symbol === symbol)
                    ?? enrichedItems.find((item) => item.symbol === symbol)
                    ?? null;

                return {
                    symbol,
                    color: COMPARE_COLORS[index % COMPARE_COLORS.length],
                    displayName: instrument && 'displayName' in instrument ? instrument.displayName : symbol,
                    primaryMovePercent,
                    compareMovePercent,
                    relativeGapPercent: primaryMovePercent - compareMovePercent,
                };
            })
            .filter((item): item is NonNullable<typeof item> => item !== null);
    }, [candles, compareCandles, compareSymbols, enrichedItems, instrumentUniverse]);

    const compareSeries = useMemo<CompareSeriesInput[]>(() => {
        return compareSymbols.map((symbol, index) => ({
            label: symbol,
            data: compareCandles[symbol] ?? [],
            color: COMPARE_COLORS[index % COMPARE_COLORS.length],
        }));
    }, [compareCandles, compareSymbols]);

    const currentSnapshotPayload = useMemo<SharedTerminalLayoutPayload>(() => ({
        version: 1,
        name: `${selectedSymbol} ${selectedRange}/${selectedInterval}`,
        watchlistId: selectedWatchlist,
        market: selectedMarket,
        symbol: selectedSymbol,
        compareSymbols,
        compareVisible,
        range: selectedRange,
        interval: selectedInterval,
        favoriteSymbols,
    }), [
        compareSymbols,
        compareVisible,
        favoriteSymbols,
        selectedInterval,
        selectedMarket,
        selectedRange,
        selectedSymbol,
        selectedWatchlist,
    ]);

    const currentSnapshotSummary = useMemo(() => {
        const compareLine = compareSessionSummary.length > 0
            ? compareSessionSummary
                .map((summary) => `${summary.symbol} ${summary.relativeGapPercent >= 0 ? '+' : ''}${summary.relativeGapPercent.toFixed(2)}% gap`)
                .join(' | ')
            : 'No compare overlays';
        const notesLine = `${chartNotes.length} notes`;
        const pinnedNotesLine = topPinnedNotes.length > 0
            ? topPinnedNotes.map((note) => note.body).join(' | ')
            : 'No pinned notes';
        const alertsLine = selectedWatchlistItem
            ? `Above ${selectedWatchlistItem.alertPriceAbove || '-'} / Below ${selectedWatchlistItem.alertPriceBelow || '-'}`
            : 'No watchlist alert bound';
        const watchlistLine = selectedWatchlistMeta
            ? `${selectedWatchlistMeta.name} (${selectedWatchlistItemCount} items)`
            : 'No active watchlist';

        return [
            `Market Terminal Snapshot`,
            `${selectedDisplayName} (${selectedSymbol})`,
            `Market: ${selectedMarket} | Range: ${selectedRange} | Interval: ${selectedInterval}`,
            `Watchlist: ${watchlistLine}`,
            `Spot: ${formatMoney(Number(selectedInstrument?.currentPrice ?? 0))} | 24h: ${formatPercent(Number(selectedInstrument?.changePercent24h ?? 0))}`,
            `Compare: ${compareLine}`,
            `Favorites: ${favoriteSymbols.length}`,
            `Alerts: ${alertsLine}`,
            `Notes: ${notesLine}`,
            `Pinned Notes: ${pinnedNotesLine}`,
        ].join('\n');
    }, [
        chartNotes.length,
        compareSessionSummary,
        favoriteSymbols.length,
        selectedDisplayName,
        selectedInstrument?.changePercent24h,
        selectedInstrument?.currentPrice,
        selectedInterval,
        selectedMarket,
        selectedRange,
        selectedSymbol,
        selectedWatchlistItemCount,
        selectedWatchlistMeta,
        selectedWatchlistItem,
        topPinnedNotes,
    ]);

    useEffect(() => {
        candlesRef.current = candles;
    }, [candles]);

    useEffect(() => {
        setCompareSymbols((current) => current.filter((symbol) => symbol !== selectedSymbol));
    }, [selectedSymbol]);

    useEffect(() => {
        setDrawingMode('none');
    }, [selectedInterval, selectedRange, selectedSymbol]);

    useEffect(() => {
        const persistedSession = readPersistedMarketSession();
        if (persistedSession) {
            setSelectedWatchlist(persistedSession.selectedWatchlist);
            setSelectedSymbol(persistedSession.selectedSymbol);
            setCompareSymbols(persistedSession.compareSymbols);
            setCompareVisible(persistedSession.compareVisible);
            setSelectedMarket(persistedSession.selectedMarket);
            setSelectedRange(persistedSession.selectedRange);
            setSelectedInterval(persistedSession.selectedInterval);
        }
        setSessionHydrated(true);
    }, []);

    useEffect(() => {
        if (typeof window === 'undefined') {
            return;
        }
        const params = new URLSearchParams(window.location.search);
        const encoded = params.get('sharedLayout') || params.get('layout');
        if (!encoded) {
            return;
        }
        const decoded = decodeSharedLayout(encoded);
        if (!decoded) {
            setSharedLayoutMessage('Shared layout link is invalid.');
            return;
        }
        setSharedLayout(decoded);
    }, []);

    useEffect(() => {
        if (typeof window === 'undefined') {
            return;
        }
        const params = new URLSearchParams(window.location.search);
        const scannerFilter = params.get('scannerFilter');
        const scannerSort = params.get('scannerSort');
        const scannerQuery = params.get('scannerQuery');
        const scannerMarket = params.get('scannerMarket');
        const scannerSymbol = params.get('scannerSymbol');
        if (!scannerFilter && !scannerSort && !scannerQuery && !scannerMarket) {
            return;
        }
        if (scannerMarket === 'CRYPTO' || scannerMarket === 'BIST100') {
            setSelectedMarket(scannerMarket);
        }
        if (scannerFilter && ['ALL', 'GAINERS', 'LOSERS', 'FAVORITES', 'SECTOR'].includes(scannerFilter)) {
            setUniverseQuickFilter(scannerFilter as UniverseQuickFilter);
        }
        if (scannerSort && ['MOVE_DESC', 'MOVE_ASC', 'PRICE_DESC', 'ALPHA'].includes(scannerSort)) {
            setUniverseSortMode(scannerSort as UniverseSortMode);
        }
        if (typeof scannerQuery === 'string') {
            setInstrumentQuery(scannerQuery);
        }
        if (typeof scannerSymbol === 'string' && scannerSymbol) {
            setSelectedSymbol(scannerSymbol);
        }
    }, []);

    useEffect(() => {
        if (!sessionHydrated || !currentUserId) {
            setTerminalPreferencesReady(sessionHydrated);
            return;
        }

        let cancelled = false;

        const hydrateRemotePreferences = async () => {
            try {
                const response = await fetchUserPreferences(currentUserId);
                const terminal = response?.terminal;
                if (!terminal || cancelled) {
                    return;
                }
                setSelectedMarket(terminal.market);
                setSelectedSymbol(terminal.symbol);
                setCompareSymbols(Array.isArray(terminal.compareSymbols) ? terminal.compareSymbols.slice(0, 3) : []);
                setCompareVisible(terminal.compareVisible !== false);
                setSelectedRange(terminal.range);
                setSelectedInterval(terminal.interval);
                setFavoriteSymbols(Array.isArray(terminal.favoriteSymbols) ? terminal.favoriteSymbols : []);
                setCompareBaskets(normalizeCompareBasketPayloads(terminal.compareBaskets));
                setScannerViews(normalizeScannerViewPayloads(terminal.scannerViews));
            } catch (error) {
                console.error('Failed to hydrate terminal preferences:', error);
            } finally {
                if (!cancelled) {
                    setTerminalPreferencesReady(true);
                }
            }
        };

        hydrateRemotePreferences();

        return () => {
            cancelled = true;
        };
    }, [currentUserId, sessionHydrated]);

    useEffect(() => {
        if (compareSymbols.length === 0) {
            setCompareVisible(true);
        }
    }, [compareSymbols]);

    useEffect(() => {
        if (typeof window === 'undefined' || !sessionHydrated) {
            return;
        }
        const session: PersistedMarketSession = {
            selectedWatchlist,
            selectedSymbol,
            compareSymbols,
            compareVisible,
            selectedMarket,
            selectedRange,
            selectedInterval,
        };
        window.localStorage.setItem(MARKET_SESSION_STORAGE_KEY, JSON.stringify(session));
    }, [compareSymbols, compareVisible, selectedInterval, selectedMarket, selectedRange, selectedSymbol, selectedWatchlist, sessionHydrated]);

    useEffect(() => {
        if (typeof window === 'undefined') {
            return;
        }

        const handleStorage = (event: StorageEvent) => {
            if (event.key === MARKET_SESSION_STORAGE_KEY && event.newValue) {
                const next = readPersistedMarketSession();
                if (!next) {
                    return;
                }
                setSelectedWatchlist(next.selectedWatchlist);
                setSelectedSymbol(next.selectedSymbol);
                setCompareSymbols(next.compareSymbols);
                setCompareVisible(next.compareVisible);
                setSelectedMarket(next.selectedMarket);
                setSelectedRange(next.selectedRange);
                setSelectedInterval(next.selectedInterval);
            }

            if (event.key === 'market.favoriteSymbols' && event.newValue) {
                try {
                    const parsed = JSON.parse(event.newValue);
                    if (Array.isArray(parsed)) {
                        setFavoriteSymbols(parsed.filter((value) => typeof value === 'string'));
                    }
                } catch (error) {
                    console.error(error);
                }
            }

            if (event.key === COMPARE_BASKET_STORAGE_KEY) {
                setCompareBaskets(readPersistedCompareBaskets());
            }

            if (event.key === SCANNER_VIEW_STORAGE_KEY) {
                setScannerViews(readPersistedScannerViews());
            }
        };

        window.addEventListener('storage', handleStorage);
        return () => window.removeEventListener('storage', handleStorage);
    }, []);

    useEffect(() => {
        if (typeof window === 'undefined') {
            return;
        }
        try {
            const stored = window.localStorage.getItem('market.favoriteSymbols');
            if (!stored) {
                return;
            }
            const parsed = JSON.parse(stored);
            if (Array.isArray(parsed)) {
                setFavoriteSymbols(parsed.filter((value) => typeof value === 'string'));
            }
        } catch (error) {
            console.error(error);
        }
    }, []);

    useEffect(() => {
        if (typeof window === 'undefined') {
            return;
        }
        window.localStorage.setItem('market.favoriteSymbols', JSON.stringify(favoriteSymbols));
    }, [favoriteSymbols]);

    useEffect(() => {
        if (!terminalPreferencesReady || !currentUserId) {
            return;
        }

        const timeoutId = window.setTimeout(() => {
            updateTerminalPreferences(currentUserId, {
                market: selectedMarket,
                symbol: selectedSymbol,
                compareSymbols,
                compareVisible,
                range: selectedRange,
                interval: selectedInterval,
                favoriteSymbols,
                compareBaskets: compareBaskets.map((basket) => ({
                    name: basket.name,
                    market: basket.market,
                    symbols: basket.symbols,
                    updatedAt: basket.updatedAt,
                })),
                scannerViews: scannerViews.map((view) => ({
                    name: view.name,
                    market: view.market,
                    quickFilter: view.quickFilter,
                    sortMode: view.sortMode,
                    query: view.query,
                    anchorSymbol: view.anchorSymbol ?? null,
                    updatedAt: view.updatedAt,
                })),
            }).catch((error) => console.error('Failed to save terminal preferences:', error));
        }, 400);

        return () => window.clearTimeout(timeoutId);
    }, [
        compareSymbols,
        compareVisible,
        currentUserId,
        favoriteSymbols,
        compareBaskets,
        scannerViews,
        selectedInterval,
        selectedMarket,
        selectedRange,
        selectedSymbol,
        terminalPreferencesReady,
    ]);

    const filteredInstruments = useMemo(() => {
        const query = instrumentQuery.trim().toLowerCase();
        const quickFiltered = instrumentUniverse.filter((instrument) => {
            switch (universeQuickFilter) {
                case 'GAINERS':
                    return instrument.changePercent24h > 0;
                case 'LOSERS':
                    return instrument.changePercent24h < 0;
                case 'FAVORITES':
                    return favoriteSymbols.includes(instrument.symbol);
                case 'SECTOR':
                    return !!selectedInstrumentMetadata?.sector && instrument.symbol !== selectedSymbol && instrument.sector === selectedInstrumentMetadata.sector;
                default:
                    return true;
            }
        });
        const queryFiltered = !query
            ? quickFiltered
            : quickFiltered.filter((instrument) =>
            instrument.symbol.toLowerCase().includes(query)
            || instrument.displayName.toLowerCase().includes(query)
            || (instrument.market ?? '').toLowerCase().includes(query)
            || (instrument.exchange ?? '').toLowerCase().includes(query)
            || (instrument.currency ?? '').toLowerCase().includes(query)
            || (instrument.sector ?? '').toLowerCase().includes(query));

        return [...queryFiltered].sort((left, right) => {
            switch (universeSortMode) {
                case 'MOVE_ASC':
                    return left.changePercent24h - right.changePercent24h;
                case 'PRICE_DESC':
                    return right.currentPrice - left.currentPrice;
                case 'ALPHA':
                    return left.symbol.localeCompare(right.symbol);
                case 'MOVE_DESC':
                default:
                    return right.changePercent24h - left.changePercent24h;
            }
        });
    }, [favoriteSymbols, instrumentQuery, instrumentUniverse, selectedInstrumentMetadata?.sector, selectedSymbol, universeQuickFilter, universeSortMode]);

    const universeFilterOptions = useMemo<Array<{ key: UniverseQuickFilter; label: string; hint: string }>>(() => {
        const options: Array<{ key: UniverseQuickFilter; label: string; hint: string }> = [
            { key: 'ALL', label: 'All', hint: 'Full universe' },
            { key: 'GAINERS', label: 'Gainers', hint: 'Positive 24h move' },
            { key: 'LOSERS', label: 'Losers', hint: 'Negative 24h move' },
            { key: 'FAVORITES', label: 'Favorites', hint: 'Starred symbols' },
        ];
        if (selectedInstrumentMetadata?.sector) {
            options.push({
                key: 'SECTOR',
                label: 'Sector',
                hint: `${selectedInstrumentMetadata.sector} peers`,
            });
        }
        return options;
    }, [selectedInstrumentMetadata?.sector]);

    const universeSortOptions = useMemo<Array<{ key: UniverseSortMode; label: string }>>(() => ([
        { key: 'MOVE_DESC', label: 'Top Move' },
        { key: 'MOVE_ASC', label: 'Worst Move' },
        { key: 'PRICE_DESC', label: 'Highest Price' },
        { key: 'ALPHA', label: 'A-Z' },
    ]), []);

    const availableScannerViews = useMemo(() => {
        return scannerViews.filter((view) => view.market === selectedMarket);
    }, [scannerViews, selectedMarket]);

    const activeScannerViewId = useMemo(() => {
        const normalizedQuery = instrumentQuery.trim();
        const match = availableScannerViews.find((view) => (
            view.market === selectedMarket
            && view.quickFilter === universeQuickFilter
            && view.sortMode === universeSortMode
            && view.query.trim() === normalizedQuery
            && (view.anchorSymbol ?? '') === (selectedSymbol ?? '')
        ));
        return match?.id ?? null;
    }, [availableScannerViews, instrumentQuery, selectedMarket, selectedSymbol, universeQuickFilter, universeSortMode]);

    const activeScannerView = useMemo(() => {
        return availableScannerViews.find((view) => view.id === activeScannerViewId) ?? null;
    }, [activeScannerViewId, availableScannerViews]);

    const activeCompareBasket = useMemo(() => {
        return availableCompareBaskets.find((basket) => basket.id === activeCompareBasketId) ?? null;
    }, [activeCompareBasketId, availableCompareBaskets]);

    const sessionContextCards = useMemo(() => ([
        {
            label: 'Market',
            value: selectedMarket,
            detail: selectedInstrumentMetadata?.delayLabel || 'Active provider context',
            badge: 'Context',
            accent: 'border-sky-400/20 bg-sky-400/10 text-sky-300',
        },
        {
            label: 'Watchlist',
            value: selectedWatchlistMeta?.name || 'Detached',
            detail: selectedWatchlistMeta ? `${selectedWatchlistItemCount} items` : 'No active watchlist',
            badge: 'Linked',
            accent: 'border-emerald-400/20 bg-emerald-400/10 text-emerald-300',
        },
        {
            label: 'Compare',
            value: activeCompareBasket?.name || (compareSymbols.length > 0 ? `${compareSymbols.length} overlays` : 'Off'),
            detail: compareSymbols.length > 0
                ? (compareVisible ? 'Overlay visible' : 'Overlay hidden')
                : 'No compare basket active',
            badge: compareSymbols.length > 0 ? 'Active' : 'Idle',
            accent: 'border-amber-400/20 bg-amber-400/10 text-amber-300',
        },
        {
            label: 'Scanner',
            value: activeScannerView?.name || universeQuickFilter,
            detail: `${universeSortMode} · ${instrumentQuery.trim() || 'No search'}`,
            badge: activeScannerView ? 'Preset' : 'Live',
            accent: 'border-fuchsia-400/20 bg-fuchsia-400/10 text-fuchsia-300',
        },
        {
            label: 'Favorites',
            value: String(favoriteSymbols.length),
            detail: favoriteSymbols.length > 0 ? 'Starred working set' : 'No starred symbols',
            badge: favoriteSymbols.length > 0 ? 'On' : 'Off',
            accent: 'border-white/10 bg-white/[0.03] text-zinc-300',
        },
    ]), [
        activeCompareBasket?.name,
        activeScannerView?.name,
        compareSymbols.length,
        compareVisible,
        favoriteSymbols.length,
        instrumentQuery,
        selectedInstrumentMetadata?.delayLabel,
        selectedMarket,
        selectedWatchlistItemCount,
        selectedWatchlistMeta,
        universeQuickFilter,
        universeSortMode,
    ]);

    const topMoverInstruments = useMemo(() => {
        return [...instrumentUniverse]
            .sort((left, right) => right.changePercent24h - left.changePercent24h)
            .slice(0, 4);
    }, [instrumentUniverse]);

    const bottomMoverInstruments = useMemo(() => {
        return [...instrumentUniverse]
            .sort((left, right) => left.changePercent24h - right.changePercent24h)
            .slice(0, 4);
    }, [instrumentUniverse]);

    const heatmapInstruments = useMemo(() => {
        return [...instrumentUniverse]
            .sort((left, right) => Math.abs(right.changePercent24h) - Math.abs(left.changePercent24h))
            .slice(0, 8);
    }, [instrumentUniverse]);

    const sectorPulseGroups = useMemo(() => {
        const grouped = instrumentUniverse.reduce<Map<string, InstrumentOption[]>>((acc, instrument) => {
            const sector = instrument.sector?.trim();
            if (!sector) {
                return acc;
            }
            const current = acc.get(sector) ?? [];
            current.push(instrument);
            acc.set(sector, current);
            return acc;
        }, new Map());

        return [...grouped.entries()]
            .map(([sector, instruments]) => {
                const averageMove = instruments.reduce((sum, instrument) => sum + instrument.changePercent24h, 0) / instruments.length;
                const leader = [...instruments].sort((left, right) => right.changePercent24h - left.changePercent24h)[0];
                return {
                    sector,
                    averageMove,
                    count: instruments.length,
                    leader,
                };
            })
            .sort((left, right) => Math.abs(right.averageMove) - Math.abs(left.averageMove))
            .slice(0, 4);
    }, [instrumentUniverse]);

    const favoriteInstruments = useMemo(() => {
        const favoriteSet = new Set(favoriteSymbols);
        return instrumentUniverse.filter((instrument) => favoriteSet.has(instrument.symbol));
    }, [favoriteSymbols, instrumentUniverse]);

    useEffect(() => {
        setCompareBaskets(readPersistedCompareBaskets());
        setCompareBasketsHydrated(true);
    }, []);

    useEffect(() => {
        if (typeof window === 'undefined' || !compareBasketsHydrated) {
            return;
        }
        window.localStorage.setItem(COMPARE_BASKET_STORAGE_KEY, JSON.stringify(compareBaskets.slice(0, 12)));
    }, [compareBaskets, compareBasketsHydrated]);

    useEffect(() => {
        setScannerViews(readPersistedScannerViews());
        setScannerViewsHydrated(true);
    }, []);

    useEffect(() => {
        if (typeof window === 'undefined' || !scannerViewsHydrated) {
            return;
        }
        window.localStorage.setItem(SCANNER_VIEW_STORAGE_KEY, JSON.stringify(scannerViews.slice(0, 12)));
    }, [scannerViews, scannerViewsHydrated]);

    const availableSymbols = useMemo(() => {
        const existingSymbols = new Set(enrichedItems.map((item) => item.symbol));
        return instrumentUniverse.filter((item) => !existingSymbols.has(item.symbol) || item.symbol === addSymbol);
    }, [addSymbol, enrichedItems, instrumentUniverse]);

    const toggleFavoriteSymbol = useCallback((symbol: string) => {
        setFavoriteSymbols((current) => {
            if (current.includes(symbol)) {
                return current.filter((item) => item !== symbol);
            }
            return [...current, symbol];
        });
    }, []);

    const fetchWatchlists = useCallback(async () => {
        if (!currentUserId) {
            return;
        }
        try {
            const res = await apiFetch('/api/v1/watchlists');
            if (!res.ok) {
                return;
            }
            const data = await res.json();
            setWatchlists(data);
            if (data.length === 0) {
                setSelectedWatchlist(null);
                return;
            }
            const selectedStillExists = selectedWatchlist
                ? data.some((watchlist: Watchlist) => watchlist.id === selectedWatchlist)
                : false;
            if (!selectedStillExists) {
                setSelectedWatchlist(data[0].id);
            }
        } catch (error) {
            console.error(error);
        } finally {
            setLoading(false);
        }
    }, [currentUserId, selectedWatchlist]);

    const fetchInstrumentUniverse = useCallback(async () => {
        try {
            const url = new URL('/api/v1/market/instruments', window.location.origin);
            url.searchParams.set('market', selectedMarket);
            const res = await apiFetch(`${url.pathname}${url.search}`, { cache: 'no-store' });
            if (!res.ok) {
                return;
            }
            const data = await res.json();
            setInstrumentUniverse(data);
            if (Array.isArray(data) && data.length > 0) {
                setAddSymbol((current) => data.some((item: InstrumentOption) => item.symbol === current) ? current : data[0].symbol);
                setSelectedSymbol((current) => data.some((item: InstrumentOption) => item.symbol === current) ? current : data[0].symbol);
                setCompareSymbols((current) => current.filter((symbol) => data.some((item: InstrumentOption) => item.symbol === symbol)));
                setCompareCandidate((current) => data.some((item: InstrumentOption) => item.symbol === current) ? current : '');
            }
        } catch (error) {
            console.error(error);
        }
    }, [selectedMarket]);

    const fetchItems = useCallback(async () => {
        if (!selectedWatchlist || !currentUserId) {
            return;
        }
        try {
            const res = await apiFetch(`/api/v1/watchlists/${selectedWatchlist}/items`, { cache: 'no-store' });
            if (!res.ok) {
                return;
            }
            const data = await res.json();
            setEnrichedItems(data);
        } catch (error) {
            console.error(error);
        }
    }, [currentUserId, selectedWatchlist]);

    const fetchAlertHistory = useCallback(async (itemId: string) => {
        setAlertHistoryLoading(true);
        try {
            const url = new URL(`/api/v1/watchlists/items/${itemId}/alert-history`, window.location.origin);
            url.searchParams.set('limit', '20');
            if (alertHistoryWindow === '24H') {
                url.searchParams.set('days', '1');
            } else if (alertHistoryWindow === '7D') {
                url.searchParams.set('days', '7');
            } else if (alertHistoryWindow === '30D') {
                url.searchParams.set('days', '30');
            }

            if (alertHistoryFilter !== 'ALL') {
                url.searchParams.set('direction', alertHistoryFilter);
            }

            const res = await apiFetch(`${url.pathname}${url.search}`, { cache: 'no-store' });
            if (!res.ok) {
                setAlertHistory([]);
                return;
            }
            const data = await res.json();
            setAlertHistory(Array.isArray(data) ? data : []);
        } catch (error) {
            console.error(error);
            setAlertHistory([]);
        } finally {
            setAlertHistoryLoading(false);
        }
    }, [alertHistoryFilter, alertHistoryWindow]);

    const fetchChartNotes = useCallback(async () => {
        try {
            const url = new URL('/api/v1/market/chart-notes', window.location.origin);
            url.searchParams.set('market', selectedMarket);
            url.searchParams.set('symbol', selectedSymbol);

            const res = await apiFetch(`${url.pathname}${url.search}`, { cache: 'no-store' });
            if (!res.ok) {
                setChartNotes([]);
                return;
            }
            const data = await res.json();
            setChartNotes(sortChartNotes(Array.isArray(data) ? data : []));
        } catch (error) {
            console.error(error);
            setChartNotes([]);
        }
    }, [selectedMarket, selectedSymbol]);

    const fetchLayouts = useCallback(async () => {
        if (!currentUserId) {
            setTerminalLayouts([]);
            return;
        }
        const data = await fetchTerminalLayouts(currentUserId);
        setTerminalLayouts(Array.isArray(data) ? data : []);
    }, [currentUserId]);

    const updateSelectedSymbolAlerts = useCallback(async (nextAbove: number | null, nextBelow: number | null) => {
        if (!selectedWatchlistItem) {
            return;
        }
        try {
            const res = await apiFetch(`/api/v1/watchlists/items/${selectedWatchlistItem.id}/alerts`, {
                method: 'PUT',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({
                    alertPriceAbove: nextAbove,
                    alertPriceBelow: nextBelow,
                }),
            });
            if (!res.ok) {
                return;
            }
            await fetchItems();
        } catch (error) {
            console.error(error);
        }
    }, [fetchItems, selectedWatchlistItem]);

    const fetchCandleChunk = useCallback(async (
        symbol: string,
        range: ChartRange,
        interval: ChartInterval,
        beforeOpenTime?: number | null,
    ): Promise<CandlePoint[]> => {
        const url = new URL('/api/v1/market/candles', window.location.origin);
        url.searchParams.set('market', selectedMarket);
        url.searchParams.set('symbol', symbol);
        url.searchParams.set('range', range);
        url.searchParams.set('interval', interval);
        if (range === 'ALL') {
            url.searchParams.set('limit', String(ALL_HISTORY_CHUNK));
        }
        if (beforeOpenTime) {
            url.searchParams.set('beforeOpenTime', String(beforeOpenTime));
        }

        const res = await apiFetch(`${url.pathname}${url.search}`, { cache: 'no-store' });
        if (!res.ok) {
            return [];
        }
        return res.json();
    }, [selectedMarket]);

    const fetchCandles = useCallback(async (
        symbol: string,
        range: ChartRange,
        interval: ChartInterval,
        mode: 'reset' | 'prepend' = 'reset',
    ) => {
        if (!symbol) {
            return;
        }

        const beforeOpenTime = mode === 'prepend' && candlesRef.current.length > 0 ? candlesRef.current[0].openTime : null;

        if (mode === 'prepend') {
            setLoadingMoreHistory(true);
        } else {
            setChartLoading(true);
        }

        try {
            const nextCandles = await fetchCandleChunk(symbol, range, interval, beforeOpenTime);
            if (range === 'ALL') {
                setHasMoreHistory(nextCandles.length === ALL_HISTORY_CHUNK);
            } else {
                setHasMoreHistory(false);
            }

            if (mode === 'prepend') {
                setCandles((current) => {
                    const deduped = new Map<number, CandlePoint>();
                    [...nextCandles, ...current].forEach((candle) => deduped.set(candle.openTime, candle));
                    return Array.from(deduped.values()).sort((a, b) => a.openTime - b.openTime);
                });
                return;
            }

            if (range !== 'ALL') {
                setCandles(nextCandles);
                return;
            }

            let merged = [...nextCandles];
            let cursor = merged.length > 0 ? merged[0].openTime : null;
            let hasMore = nextCandles.length === ALL_HISTORY_CHUNK;
            let remainingChunks = getInitialAllHistoryChunkCount(interval) - 1;

            while (hasMore && cursor && remainingChunks > 0) {
                const olderChunk = await fetchCandleChunk(symbol, range, interval, cursor);
                if (olderChunk.length === 0) {
                    hasMore = false;
                    break;
                }
                const deduped = new Map<number, CandlePoint>();
                [...olderChunk, ...merged].forEach((candle) => deduped.set(candle.openTime, candle));
                merged = Array.from(deduped.values()).sort((a, b) => a.openTime - b.openTime);
                cursor = olderChunk[0]?.openTime ?? null;
                hasMore = olderChunk.length === ALL_HISTORY_CHUNK;
                remainingChunks -= 1;
            }

            setHasMoreHistory(hasMore);
            setCandles(merged);
        } catch (error) {
            console.error(error);
        } finally {
            setChartLoading(false);
            setLoadingMoreHistory(false);
        }
    }, [fetchCandleChunk]);

    const fetchCompareCandles = useCallback(async (
        symbols: string[],
        range: ChartRange,
        interval: ChartInterval,
    ) => {
        const normalizedSymbols = symbols.filter((symbol) => symbol && symbol !== selectedSymbol).slice(0, 3);
        if (normalizedSymbols.length === 0) {
            setCompareCandles({});
            return;
        }

        try {
            const entries = await Promise.all(
                normalizedSymbols.map(async (symbol) => [symbol, await fetchCandleChunk(symbol, range, interval)] as const),
            );
            setCompareCandles(Object.fromEntries(entries));
        } catch (error) {
            console.error(error);
            setCompareCandles({});
        }
    }, [fetchCandleChunk, selectedSymbol]);

    useEffect(() => {
        fetchWatchlists();
        fetchInstrumentUniverse();
    }, [fetchInstrumentUniverse, fetchWatchlists]);

    useEffect(() => {
        fetchLayouts();
    }, [fetchLayouts]);

    useEffect(() => {
        const interval = setInterval(fetchInstrumentUniverse, 10000);
        return () => clearInterval(interval);
    }, [fetchInstrumentUniverse]);

    useEffect(() => {
        if (!selectedWatchlist) {
            return;
        }
        fetchItems();
        const interval = setInterval(fetchItems, 5000);
        return () => clearInterval(interval);
    }, [fetchItems, selectedWatchlist]);

    useEffect(() => {
        setCompareSymbols([]);
        setCompareCandidate('');
        setCompareCandles({});
    }, [selectedMarket]);

    useEffect(() => {
        if (selectedRange === 'ALL' && selectedInterval === '1m') {
            setSelectedInterval('15m');
        }
    }, [selectedInterval, selectedRange]);

    useEffect(() => {
        if (!selectedWatchlistItem) {
            setAlertHistory([]);
            return;
        }
        fetchAlertHistory(selectedWatchlistItem.id);
    }, [fetchAlertHistory, selectedWatchlistItem]);

    useEffect(() => {
        fetchChartNotes();
    }, [fetchChartNotes]);

    useEffect(() => {
        fetchCandles(selectedSymbol, selectedRange, selectedInterval, 'reset');
    }, [fetchCandles, selectedInterval, selectedRange, selectedSymbol]);

    useEffect(() => {
        fetchCompareCandles(compareSymbols, selectedRange, selectedInterval);
    }, [compareSymbols, fetchCompareCandles, selectedInterval, selectedRange]);

    const handleCreateWatchlist = async (event: React.FormEvent) => {
        event.preventDefault();
        if (!currentUserId) {
            return;
        }
        try {
            const res = await apiFetch('/api/v1/watchlists', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ name: newName || 'My Watchlist' }),
            });
            if (!res.ok) {
                return;
            }
            setNewName('');
            await fetchWatchlists();
        } catch (error) {
            console.error(error);
        }
    };

    const handleAddItem = async (event: React.FormEvent) => {
        event.preventDefault();
        if (!selectedWatchlist || !currentUserId) {
            return;
        }
        try {
            const res = await apiFetch(`/api/v1/watchlists/${selectedWatchlist}/items`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    symbol: addSymbol,
                    alertPriceAbove: addAlertAbove ? parseFloat(addAlertAbove) : null,
                    alertPriceBelow: addAlertBelow ? parseFloat(addAlertBelow) : null,
                    notes: addNotes || null,
                }),
            });
            if (!res.ok) {
                return;
            }
            setAddAlertAbove('');
            setAddAlertBelow('');
            setAddNotes('');
            setShowAddForm(false);
            await fetchItems();
            setSelectedSymbol(addSymbol);
        } catch (error) {
            console.error(error);
        }
    };

    const handleRemoveItem = async (itemId: string) => {
        try {
            const res = await apiFetch(`/api/v1/watchlists/items/${itemId}`, { method: 'DELETE' });
            if (!res.ok) {
                return;
            }
            await fetchItems();
        } catch (error) {
            console.error(error);
        }
    };

    const handleDeleteWatchlist = async (id: string) => {
        if (!currentUserId) {
            return;
        }
        try {
            const res = await apiFetch(`/api/v1/watchlists/${id}`, { method: 'DELETE' });
            if (!res.ok) {
                return;
            }
            if (selectedWatchlist === id) {
                setSelectedWatchlist(null);
                setEnrichedItems([]);
            }
            await fetchWatchlists();
        } catch (error) {
            console.error(error);
        }
    };

    const handleAddChartNote = async (event: React.FormEvent) => {
        event.preventDefault();
        const trimmed = chartNoteDraft.trim();
        if (!trimmed) {
            return;
        }
        try {
            const res = await apiFetch('/api/v1/market/chart-notes', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    market: selectedMarket,
                    symbol: selectedSymbol,
                    body: trimmed,
                }),
            });
            if (!res.ok) {
                return;
            }
            const note = await res.json();
            setChartNotes((current) => sortChartNotes([note, ...current]).slice(0, 25));
            setChartNoteDraft('');
        } catch (error) {
            console.error(error);
        }
    };

    const handleDeleteChartNote = async (noteId: string) => {
        try {
            const res = await apiFetch(`/api/v1/market/chart-notes/${noteId}`, { method: 'DELETE' });
            if (!res.ok) {
                return;
            }
            setChartNotes((current) => current.filter((note) => note.id !== noteId));
        } catch (error) {
            console.error(error);
        }
    };

    const handleStartEditChartNote = (note: ChartNote) => {
        setEditingNoteId(note.id);
        setEditingNoteDraft(note.body);
    };

    const handleCancelEditChartNote = () => {
        setEditingNoteId(null);
        setEditingNoteDraft('');
    };

    const handleSaveEditChartNote = async (note: ChartNote) => {
        const trimmed = editingNoteDraft.trim();
        if (!trimmed) {
            return;
        }
        try {
            const res = await apiFetch(`/api/v1/market/chart-notes/${note.id}`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    market: selectedMarket,
                    symbol: selectedSymbol,
                    body: trimmed,
                    pinned: note.pinned,
                }),
            });
            if (!res.ok) {
                return;
            }
            const updated = await res.json();
            setChartNotes((current) => sortChartNotes(current.map((entry) => entry.id === note.id ? updated : entry)));
            setEditingNoteId(null);
            setEditingNoteDraft('');
        } catch (error) {
            console.error(error);
        }
    };

    const handleTogglePinChartNote = async (note: ChartNote) => {
        try {
            const res = await apiFetch(`/api/v1/market/chart-notes/${note.id}`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    market: selectedMarket,
                    symbol: selectedSymbol,
                    body: note.body,
                    pinned: !note.pinned,
                }),
            });
            if (!res.ok) {
                return;
            }
            const updated = await res.json();
            setChartNotes((current) => sortChartNotes(current.map((entry) => entry.id === note.id ? updated : entry)));
        } catch (error) {
            console.error(error);
        }
    };

    const handleLoadMoreHistory = useCallback((oldestOpenTime: number) => {
        if (selectedRange !== 'ALL' || loadingMoreHistory || !hasMoreHistory || oldestOpenTime <= 0) {
            return;
        }
        fetchCandles(selectedSymbol, selectedRange, selectedInterval, 'prepend');
    }, [fetchCandles, hasMoreHistory, loadingMoreHistory, selectedInterval, selectedRange, selectedSymbol]);

    const handleSaveCurrentCompareBasket = () => {
        const trimmed = compareBasketNameDraft.trim();
        if (!trimmed || compareSymbols.length === 0) {
            return;
        }

        const nextBasket: CompareBasketPreset = {
            id: crypto.randomUUID(),
            name: trimmed,
            market: selectedMarket,
            symbols: compareSymbols.slice(0, 3),
            updatedAt: new Date().toISOString(),
        };

        setCompareBaskets((current) => [
            nextBasket,
            ...current.filter((entry) => entry.market !== nextBasket.market || !haveSameSymbols(entry.symbols, nextBasket.symbols)),
        ].slice(0, 12));
        setCompareBasketNameDraft('');
        setCompareBasketMessage(`Saved compare basket: ${nextBasket.name}`);
    };

    const handleApplyCompareBasket = (basket: CompareBasketPreset) => {
        const filteredSymbols = basket.symbols
            .filter((symbol) => symbol !== selectedSymbol)
            .filter((symbol) => instrumentMap.has(symbol))
            .slice(0, 3);

        if (filteredSymbols.length === 0) {
            setCompareBasketMessage('This basket has no usable symbols for the current symbol or market universe.');
            return;
        }

        setCompareSymbols(filteredSymbols);
        setCompareVisible(true);
        setCompareCandidate('');
        setCompareBasketMessage(`Applied compare basket: ${basket.name}`);
    };

    const handleDeleteCompareBasket = (basketId: string) => {
        setCompareBaskets((current) => current.filter((entry) => entry.id !== basketId));
        if (editingCompareBasketId === basketId) {
            setEditingCompareBasketId(null);
            setEditingCompareBasketName('');
        }
        setCompareBasketMessage(activeCompareBasketId === basketId ? 'Removed active compare basket.' : 'Compare basket removed.');
    };

    const handleStartEditCompareBasket = (basket: CompareBasketPreset) => {
        setEditingCompareBasketId(basket.id);
        setEditingCompareBasketName(basket.name);
    };

    const handleCancelEditCompareBasket = () => {
        setEditingCompareBasketId(null);
        setEditingCompareBasketName('');
    };

    const handleSaveCompareBasketName = (basket: CompareBasketPreset) => {
        const trimmed = editingCompareBasketName.trim();
        if (!trimmed) {
            return;
        }
        setCompareBaskets((current) => current.map((entry) => (
            entry.id === basket.id
                ? { ...entry, name: trimmed, updatedAt: new Date().toISOString() }
                : entry
        )));
        setEditingCompareBasketId(null);
        setEditingCompareBasketName('');
        setCompareBasketMessage(`Renamed compare basket: ${trimmed}`);
    };

    const handleOverwriteCompareBasket = (basket: CompareBasketPreset) => {
        if (compareSymbols.length === 0) {
            return;
        }
        setCompareBaskets((current) => current.map((entry) => (
            entry.id === basket.id
                ? {
                    ...entry,
                    market: selectedMarket,
                    symbols: compareSymbols.slice(0, 3),
                    updatedAt: new Date().toISOString(),
                }
                : entry
        )));
        setCompareBasketMessage(`Overwrote compare basket: ${basket.name}`);
    };

    const handleExportCompareBaskets = () => {
        if (availableCompareBaskets.length === 0 || typeof window === 'undefined') {
            return;
        }
        const payload = availableCompareBaskets.map((basket) => ({
            name: basket.name,
            market: basket.market,
            symbols: basket.symbols,
            updatedAt: basket.updatedAt,
        }));
        const blob = new Blob([JSON.stringify(payload, null, 2)], { type: 'application/json;charset=utf-8' });
        const url = window.URL.createObjectURL(blob);
        const anchor = document.createElement('a');
        anchor.href = url;
        anchor.download = `compare-baskets-${selectedMarket.toLowerCase()}.json`;
        document.body.appendChild(anchor);
        anchor.click();
        document.body.removeChild(anchor);
        window.URL.revokeObjectURL(url);
        setCompareBasketMessage(`Exported ${availableCompareBaskets.length} compare basket${availableCompareBaskets.length === 1 ? '' : 's'}.`);
    };

    const handleImportCompareBaskets = async (event: React.ChangeEvent<HTMLInputElement>) => {
        const file = event.target.files?.[0];
        if (!file) {
            return;
        }
        try {
            const raw = await file.text();
            const parsed = JSON.parse(raw);
            if (!Array.isArray(parsed)) {
                setCompareBasketMessage('Compare basket import failed: file must contain an array.');
                return;
            }

            const imported = parsed
                .filter((entry): entry is Record<string, unknown> => !!entry && typeof entry === 'object')
                .map((entry): CompareBasketPreset | null => {
                    const symbols = Array.isArray(entry.symbols)
                        ? entry.symbols.filter((value): value is string => typeof value === 'string' && value.length > 0).slice(0, 3)
                        : [];
                    if (symbols.length === 0) {
                        return null;
                    }
                    return {
                        id: crypto.randomUUID(),
                        name: typeof entry.name === 'string' && entry.name.trim() ? entry.name.trim() : 'Imported Compare Basket',
                        market: entry.market === 'BIST100' ? 'BIST100' : 'CRYPTO',
                        symbols,
                        updatedAt: typeof entry.updatedAt === 'string' ? entry.updatedAt : new Date().toISOString(),
                    };
                })
                .filter((entry): entry is CompareBasketPreset => entry !== null);

            if (imported.length === 0) {
                setCompareBasketMessage('Compare basket import failed: no valid baskets found.');
                return;
            }

            setCompareBaskets((current) => {
                const merged = [...imported, ...current].reduce<CompareBasketPreset[]>((acc, basket) => {
                    const existingIndex = acc.findIndex((entry) => entry.market === basket.market && haveSameSymbols(entry.symbols, basket.symbols));
                    if (existingIndex >= 0) {
                        acc[existingIndex] = basket;
                        return acc;
                    }
                    acc.push(basket);
                    return acc;
                }, []);
                return merged.slice(0, 12);
            });
            setCompareBasketMessage(`Imported ${imported.length} compare basket${imported.length === 1 ? '' : 's'}.`);
        } catch (error) {
            console.error(error);
            setCompareBasketMessage('Compare basket import failed: invalid JSON.');
        } finally {
            if (compareBasketImportInputRef.current) {
                compareBasketImportInputRef.current.value = '';
            }
        }
    };

    const handleShareCompareBasket = async (basket: CompareBasketPreset) => {
        if (typeof window === 'undefined') {
            return;
        }
        const encoded = encodeSharedLayout({
            version: 1,
            name: `Compare Basket · ${basket.name}`,
            watchlistId: selectedWatchlist,
            market: basket.market,
            symbol: selectedSymbol,
            compareSymbols: basket.symbols,
            compareVisible: true,
            range: selectedRange,
            interval: selectedInterval,
            favoriteSymbols,
        });
        const shareUrl = `${window.location.origin}/watchlist/shared?layout=${encoded}`;
        try {
            await navigator.clipboard.writeText(shareUrl);
            setCompareBasketMessage(`Share link copied for ${basket.name}.`);
        } catch (error) {
            console.error(error);
            setCompareBasketMessage(shareUrl);
        }
    };

    const handleApplySuggestedCompareBasket = (basket: SuggestedCompareBasket) => {
        setCompareSymbols(basket.symbols.slice(0, 3));
        setCompareVisible(true);
        setCompareCandidate('');
        setCompareBasketMessage(`Applied suggested basket: ${basket.name}`);
    };

    const handleApplyBuiltInCompareBasket = (basket: BuiltInCompareBasketTemplate) => {
        setCompareSymbols(basket.symbols.slice(0, 3));
        setCompareVisible(true);
        setCompareCandidate('');
        setCompareBasketMessage(`Applied built-in basket: ${basket.name}`);
    };

    const handleSaveScannerView = () => {
        const trimmed = scannerViewNameDraft.trim();
        if (!trimmed) {
            return;
        }
        const nextView: ScannerViewPreset = {
            id: crypto.randomUUID(),
            name: trimmed,
            market: selectedMarket,
            quickFilter: universeQuickFilter,
            sortMode: universeSortMode,
            query: instrumentQuery,
            anchorSymbol: selectedSymbol,
            updatedAt: new Date().toISOString(),
        };
        setScannerViews((current) => [nextView, ...current].slice(0, 12));
        setScannerViewNameDraft('');
        setScannerViewMessage(`Saved scanner view: ${nextView.name}`);
    };

    const handleApplyScannerView = (view: ScannerViewPreset) => {
        if (view.anchorSymbol) {
            setSelectedSymbol(view.anchorSymbol);
        }
        setUniverseQuickFilter(view.quickFilter);
        setUniverseSortMode(view.sortMode);
        setInstrumentQuery(view.query);
        setScannerViewMessage(`Applied scanner view: ${view.name}`);
    };

    const handleDeleteScannerView = (viewId: string) => {
        setScannerViews((current) => current.filter((entry) => entry.id !== viewId));
        if (editingScannerViewId === viewId) {
            setEditingScannerViewId(null);
            setEditingScannerViewName('');
        }
        setScannerViewMessage('Scanner view removed.');
    };

    const handleStartEditScannerView = (view: ScannerViewPreset) => {
        setEditingScannerViewId(view.id);
        setEditingScannerViewName(view.name);
    };

    const handleCancelEditScannerView = () => {
        setEditingScannerViewId(null);
        setEditingScannerViewName('');
    };

    const handleSaveScannerViewName = (view: ScannerViewPreset) => {
        const trimmed = editingScannerViewName.trim();
        if (!trimmed) {
            return;
        }
        setScannerViews((current) => current.map((entry) => (
            entry.id === view.id
                ? { ...entry, name: trimmed, updatedAt: new Date().toISOString() }
                : entry
        )));
        setEditingScannerViewId(null);
        setEditingScannerViewName('');
        setScannerViewMessage(`Renamed scanner view: ${trimmed}`);
    };

    const handleOverwriteScannerView = (view: ScannerViewPreset) => {
        setScannerViews((current) => current.map((entry) => (
            entry.id === view.id
                ? {
                    ...entry,
                    market: selectedMarket,
                    quickFilter: universeQuickFilter,
                    sortMode: universeSortMode,
                    query: instrumentQuery,
                    anchorSymbol: selectedSymbol,
                    updatedAt: new Date().toISOString(),
                }
                : entry
        )));
        setScannerViewMessage(`Overwrote scanner view: ${view.name}`);
    };

    const handleExportScannerViews = () => {
        if (availableScannerViews.length === 0 || typeof window === 'undefined') {
            return;
        }
        const payload = availableScannerViews.map((view) => ({
            name: view.name,
            market: view.market,
            quickFilter: view.quickFilter,
            sortMode: view.sortMode,
            query: view.query,
            anchorSymbol: view.anchorSymbol ?? null,
            updatedAt: view.updatedAt,
        }));
        const blob = new Blob([JSON.stringify(payload, null, 2)], { type: 'application/json;charset=utf-8' });
        const url = window.URL.createObjectURL(blob);
        const anchor = document.createElement('a');
        anchor.href = url;
        anchor.download = `scanner-views-${selectedMarket.toLowerCase()}.json`;
        document.body.appendChild(anchor);
        anchor.click();
        document.body.removeChild(anchor);
        window.URL.revokeObjectURL(url);
        setScannerViewMessage(`Exported ${availableScannerViews.length} scanner view${availableScannerViews.length === 1 ? '' : 's'}.`);
    };

    const handleImportScannerViews = async (event: React.ChangeEvent<HTMLInputElement>) => {
        const file = event.target.files?.[0];
        if (!file) {
            return;
        }
        try {
            const raw = await file.text();
            const parsed = JSON.parse(raw);
            if (!Array.isArray(parsed)) {
                setScannerViewMessage('Scanner view import failed: file must contain an array.');
                return;
            }

            const imported = parsed
                .filter((entry): entry is Record<string, unknown> => !!entry && typeof entry === 'object')
                .map((entry): ScannerViewPreset => ({
                    id: typeof entry.id === 'string' ? entry.id : crypto.randomUUID(),
                    name: typeof entry.name === 'string' && entry.name.trim() ? entry.name.trim() : 'Imported Scanner View',
                    market: entry.market === 'BIST100' ? 'BIST100' : 'CRYPTO',
                    quickFilter: ['ALL', 'GAINERS', 'LOSERS', 'FAVORITES', 'SECTOR'].includes(String(entry.quickFilter)) ? entry.quickFilter as UniverseQuickFilter : 'ALL',
                    sortMode: ['MOVE_DESC', 'MOVE_ASC', 'PRICE_DESC', 'ALPHA'].includes(String(entry.sortMode)) ? entry.sortMode as UniverseSortMode : 'MOVE_DESC',
                    query: typeof entry.query === 'string' ? entry.query : '',
                    anchorSymbol: typeof entry.anchorSymbol === 'string' && entry.anchorSymbol ? entry.anchorSymbol : null,
                    updatedAt: typeof entry.updatedAt === 'string' ? entry.updatedAt : new Date().toISOString(),
                }))
                .slice(0, 12);

            if (imported.length === 0) {
                setScannerViewMessage('Scanner view import failed: no valid views found.');
                return;
            }

            setScannerViews((current) => {
                const merged = [...imported, ...current].reduce<ScannerViewPreset[]>((acc, view) => {
                    const existingIndex = acc.findIndex((entry) => (
                        entry.market === view.market
                        && entry.quickFilter === view.quickFilter
                        && entry.sortMode === view.sortMode
                        && entry.query.trim() === view.query.trim()
                    ));
                    if (existingIndex >= 0) {
                        acc[existingIndex] = view;
                        return acc;
                    }
                    acc.push(view);
                    return acc;
                }, []);
                return merged.slice(0, 12);
            });
            setScannerViewMessage(`Imported ${imported.length} scanner view${imported.length === 1 ? '' : 's'}.`);
        } catch (error) {
            console.error(error);
            setScannerViewMessage('Scanner view import failed: invalid JSON.');
        } finally {
            if (scannerViewImportInputRef.current) {
                scannerViewImportInputRef.current.value = '';
            }
        }
    };

    const handleShareScannerView = async (view: ScannerViewPreset) => {
        if (typeof window === 'undefined') {
            return;
        }
        const params = new URLSearchParams();
        params.set('scannerMarket', view.market);
        params.set('scannerFilter', view.quickFilter);
        params.set('scannerSort', view.sortMode);
        if (view.query.trim()) {
            params.set('scannerQuery', view.query.trim());
        }
        if (view.anchorSymbol) {
            params.set('scannerSymbol', view.anchorSymbol);
        }
        const shareUrl = `${window.location.origin}/watchlist?${params.toString()}`;
        try {
            await navigator.clipboard.writeText(shareUrl);
            setScannerViewMessage(`Share link copied for ${view.name}.`);
        } catch (error) {
            console.error(error);
            setScannerViewMessage(shareUrl);
        }
    };

    const handleSaveSectorPulseView = (sector: string, anchorSymbol: string) => {
        const nextView: ScannerViewPreset = {
            id: crypto.randomUUID(),
            name: `Sector Pulse · ${sector}`,
            market: selectedMarket,
            quickFilter: 'SECTOR',
            sortMode: 'MOVE_DESC',
            query: '',
            anchorSymbol,
            updatedAt: new Date().toISOString(),
        };
        setScannerViews((current) => {
            const merged = [nextView, ...current].reduce<ScannerViewPreset[]>((acc, view) => {
                const existingIndex = acc.findIndex((entry) => (
                    entry.market === view.market
                    && entry.quickFilter === view.quickFilter
                    && entry.sortMode === view.sortMode
                    && entry.query.trim() === view.query.trim()
                    && (entry.anchorSymbol ?? '') === (view.anchorSymbol ?? '')
                ));
                if (existingIndex >= 0) {
                    acc[existingIndex] = view;
                    return acc;
                }
                acc.push(view);
                return acc;
            }, []);
            return merged.slice(0, 12);
        });
        setScannerViewMessage(`Saved sector scanner view: ${sector}`);
    };

    const handleSaveCurrentLayout = async () => {
        const trimmed = layoutNameDraft.trim();
        if (!currentUserId || !trimmed) {
            return;
        }
        const layout = await createTerminalLayout(currentUserId, {
            name: trimmed,
            watchlistId: selectedWatchlist,
            market: selectedMarket,
            symbol: selectedSymbol,
            compareSymbols,
            compareVisible,
            range: selectedRange,
            interval: selectedInterval,
            favoriteSymbols,
        });
        if (!layout) {
            return;
        }
        setTerminalLayouts((current) => [layout, ...current].slice(0, 10));
        setLayoutNameDraft('');
    };

    const handleApplyLayout = (layout: TerminalLayoutResponsePayload) => {
        if (layout.watchlistId) {
            setSelectedWatchlist(layout.watchlistId);
        }
        setSelectedMarket(layout.market);
        setSelectedSymbol(layout.symbol);
        setCompareSymbols(layout.compareSymbols.slice(0, 3));
        setCompareVisible(layout.compareVisible !== false);
        setSelectedRange(layout.range);
        setSelectedInterval(layout.interval);
        setFavoriteSymbols(layout.favoriteSymbols);
        setActiveLayoutId(layout.id);
    };

    const handleDeleteLayout = async (layoutId: string) => {
        if (!currentUserId) {
            return;
        }
        const deleted = await deleteTerminalLayout(currentUserId, layoutId);
        if (!deleted) {
            return;
        }
        setTerminalLayouts((current) => current.filter((layout) => layout.id !== layoutId));
        if (activeLayoutId === layoutId) {
            setActiveLayoutId(null);
        }
        if (editingLayoutId === layoutId) {
            setEditingLayoutId(null);
            setEditingLayoutName('');
        }
    };

    const applySharedLayoutState = useCallback((layout: SharedTerminalLayoutPayload) => {
        if (layout.watchlistId) {
            setSelectedWatchlist(layout.watchlistId);
        }
        setSelectedMarket(layout.market);
        setSelectedSymbol(layout.symbol);
        setCompareSymbols(layout.compareSymbols.slice(0, 3));
        setCompareVisible(layout.compareVisible !== false);
        setSelectedRange(layout.range);
        setSelectedInterval(layout.interval);
        setFavoriteSymbols(layout.favoriteSymbols);
        setActiveLayoutId(null);
    }, []);

    const handleExportLayouts = () => {
        if (terminalLayouts.length === 0 || typeof window === 'undefined') {
            return;
        }
        const payload = terminalLayouts.map((layout) => ({
            name: layout.name,
            watchlistId: layout.watchlistId,
            market: layout.market,
            symbol: layout.symbol,
            compareSymbols: layout.compareSymbols,
            compareVisible: layout.compareVisible,
            range: layout.range,
            interval: layout.interval,
            favoriteSymbols: layout.favoriteSymbols,
        }));
        const blob = new Blob([JSON.stringify(payload, null, 2)], { type: 'application/json;charset=utf-8' });
        const url = window.URL.createObjectURL(blob);
        const anchor = document.createElement('a');
        anchor.href = url;
        anchor.download = 'market-terminal-layouts.json';
        document.body.appendChild(anchor);
        anchor.click();
        document.body.removeChild(anchor);
        window.URL.revokeObjectURL(url);
    };

    const handleStartEditLayout = (layout: TerminalLayoutResponsePayload) => {
        setEditingLayoutId(layout.id);
        setEditingLayoutName(layout.name);
    };

    const handleCancelEditLayout = () => {
        setEditingLayoutId(null);
        setEditingLayoutName('');
    };

    const handleSaveLayoutName = async (layout: TerminalLayoutResponsePayload) => {
        const trimmed = editingLayoutName.trim();
        if (!currentUserId || !trimmed) {
            return;
        }
        const updated = await updateTerminalLayout(currentUserId, layout.id, {
            name: trimmed,
            watchlistId: layout.watchlistId,
            market: layout.market,
            symbol: layout.symbol,
            compareSymbols: layout.compareSymbols,
            compareVisible: layout.compareVisible,
            range: layout.range,
            interval: layout.interval,
            favoriteSymbols: layout.favoriteSymbols,
        });
        if (!updated) {
            return;
        }
        setTerminalLayouts((current) => current.map((entry) => entry.id === layout.id ? updated : entry));
        setEditingLayoutId(null);
        setEditingLayoutName('');
    };

    const handleOverwriteLayout = async (layout: TerminalLayoutResponsePayload) => {
        if (!currentUserId) {
            return;
        }
        const updated = await updateTerminalLayout(currentUserId, layout.id, {
            name: layout.name,
            watchlistId: selectedWatchlist,
            market: selectedMarket,
            symbol: selectedSymbol,
            compareSymbols,
            compareVisible,
            range: selectedRange,
            interval: selectedInterval,
            favoriteSymbols,
        });
        if (!updated) {
            return;
        }
        setTerminalLayouts((current) => [updated, ...current.filter((entry) => entry.id !== layout.id)]);
        setActiveLayoutId(layout.id);
    };

    const handleImportLayouts = async (event: React.ChangeEvent<HTMLInputElement>) => {
        const file = event.target.files?.[0];
        if (!file || !currentUserId) {
            return;
        }

        try {
            const raw = await file.text();
            const parsed = JSON.parse(raw);
            if (!Array.isArray(parsed)) {
                setLayoutImportMessage('Import failed: file must contain a layout array.');
                return;
            }

            const remainingSlots = Math.max(0, 10 - terminalLayouts.length);
            const candidates = parsed
                .filter((entry): entry is Record<string, unknown> => !!entry && typeof entry === 'object')
                .slice(0, remainingSlots);

            let importedCount = 0;
            const importedLayouts: TerminalLayoutResponsePayload[] = [];

            for (const candidate of candidates) {
                if (typeof candidate.name !== 'string' || !candidate.name.trim()) {
                    continue;
                }
                const created = await createTerminalLayout(currentUserId, {
                    name: candidate.name,
                    watchlistId: typeof candidate.watchlistId === 'string' ? candidate.watchlistId : null,
                    market: candidate.market === 'BIST100' ? 'BIST100' : 'CRYPTO',
                    symbol: typeof candidate.symbol === 'string' ? candidate.symbol : selectedSymbol,
                    compareSymbols: Array.isArray(candidate.compareSymbols)
                        ? candidate.compareSymbols.filter((value): value is string => typeof value === 'string')
                        : [],
                    compareVisible: candidate.compareVisible !== false,
                    range: typeof candidate.range === 'string' ? candidate.range as ChartRange : selectedRange,
                    interval: typeof candidate.interval === 'string' ? candidate.interval as ChartInterval : selectedInterval,
                    favoriteSymbols: Array.isArray(candidate.favoriteSymbols)
                        ? candidate.favoriteSymbols.filter((value): value is string => typeof value === 'string')
                        : [],
                });
                if (created) {
                    importedLayouts.push(created);
                    importedCount += 1;
                }
            }

            if (importedLayouts.length > 0) {
                setTerminalLayouts((current) => [...importedLayouts, ...current].slice(0, 10));
            }
            setLayoutImportMessage(`Imported ${importedCount} layout${importedCount === 1 ? '' : 's'}.`);
        } catch (error) {
            console.error(error);
            setLayoutImportMessage('Import failed: invalid JSON.');
        } finally {
            if (layoutImportInputRef.current) {
                layoutImportInputRef.current.value = '';
            }
        }
    };

    const handleShareLayout = async (layout: TerminalLayoutResponsePayload) => {
        if (typeof window === 'undefined') {
            return;
        }
        const encoded = encodeSharedLayout({
            version: 1,
            name: layout.name,
            watchlistId: layout.watchlistId,
            market: layout.market,
            symbol: layout.symbol,
            compareSymbols: layout.compareSymbols,
            compareVisible: layout.compareVisible,
            range: layout.range,
            interval: layout.interval,
            favoriteSymbols: layout.favoriteSymbols,
        });
        if (!encoded) {
            return;
        }
        const shareUrl = `${window.location.origin}/watchlist/shared?layout=${encoded}`;
        try {
            await navigator.clipboard.writeText(shareUrl);
            setSharedLayoutMessage('Share link copied to clipboard.');
        } catch (error) {
            console.error(error);
            setSharedLayoutMessage(shareUrl);
        }
    };

    const handleCopySnapshotSummary = async () => {
        if (typeof window === 'undefined') {
            return;
        }
        try {
            await navigator.clipboard.writeText(currentSnapshotSummary);
            setSnapshotMessage('Snapshot summary copied.');
        } catch (error) {
            console.error(error);
            setSnapshotMessage(currentSnapshotSummary);
        }
    };

    const handleCopyCurrentStateShareLink = async () => {
        if (typeof window === 'undefined') {
            return;
        }
        const encoded = encodeSharedLayout(currentSnapshotPayload);
        if (!encoded) {
            return;
        }
        const shareUrl = `${window.location.origin}/watchlist/shared?layout=${encoded}`;
        try {
            await navigator.clipboard.writeText(shareUrl);
            setSnapshotMessage('Current-state share link copied.');
        } catch (error) {
            console.error(error);
            setSnapshotMessage(shareUrl);
        }
    };

    const handleDownloadSnapshotJson = () => {
        if (typeof window === 'undefined') {
            return;
        }
        const blob = new Blob([JSON.stringify(currentSnapshotPayload, null, 2)], { type: 'application/json;charset=utf-8' });
        const url = window.URL.createObjectURL(blob);
        const anchor = document.createElement('a');
        anchor.href = url;
        anchor.download = `terminal-snapshot-${selectedSymbol.toLowerCase()}-${selectedRange.toLowerCase()}-${selectedInterval}.json`;
        document.body.appendChild(anchor);
        anchor.click();
        document.body.removeChild(anchor);
        window.URL.revokeObjectURL(url);
        setSnapshotMessage('Snapshot JSON downloaded.');
    };

    const handleDownloadSnapshotSvg = () => {
        if (typeof window === 'undefined') {
            return;
        }

        const lines = currentSnapshotSummary.split('\n');
        const width = 1200;
        const headerHeight = 180;
        const lineHeight = 38;
        const footerHeight = 100;
        const height = headerHeight + (lines.length * lineHeight) + footerHeight;
        const renderedLines = lines.map((line, index) => `
            <text x="72" y="${220 + (index * lineHeight)}" fill="#E4E4E7" font-family="'Segoe UI', Arial, sans-serif" font-size="24">${escapeSvgText(line)}</text>
        `).join('');

        const pinnedPreview = topPinnedNotes.length > 0
            ? topPinnedNotes.map((note, index) => `
                <text x="72" y="${height - 110 + (index * 28)}" fill="#FCD34D" font-family="'Segoe UI', Arial, sans-serif" font-size="18">${escapeSvgText(note.body)}</text>
            `).join('')
            : `<text x="72" y="${height - 110}" fill="#71717A" font-family="'Segoe UI', Arial, sans-serif" font-size="18">No pinned notes</text>`;

        const svg = `
<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}" viewBox="0 0 ${width} ${height}">
  <defs>
    <linearGradient id="bg" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#09090B" />
      <stop offset="50%" stop-color="#111827" />
      <stop offset="100%" stop-color="#052E16" />
    </linearGradient>
    <linearGradient id="title" x1="0%" y1="0%" x2="100%" y2="0%">
      <stop offset="0%" stop-color="#F59E0B" />
      <stop offset="100%" stop-color="#22C55E" />
    </linearGradient>
  </defs>
  <rect width="${width}" height="${height}" rx="36" fill="url(#bg)" />
  <rect x="36" y="36" width="${width - 72}" height="${height - 72}" rx="28" fill="#0A0A0A" fill-opacity="0.42" stroke="#27272A" />
  <text x="72" y="108" fill="url(#title)" font-family="'Segoe UI', Arial, sans-serif" font-size="44" font-weight="700">PaperTradePro Market Snapshot</text>
  <text x="72" y="148" fill="#A1A1AA" font-family="'Segoe UI', Arial, sans-serif" font-size="20">Shared terminal state for ${escapeSvgText(selectedSymbol)} · ${escapeSvgText(selectedMarket)}</text>
  ${renderedLines}
  <line x1="72" y1="${height - 150}" x2="${width - 72}" y2="${height - 150}" stroke="#27272A" />
  <text x="72" y="${height - 125}" fill="#A1A1AA" font-family="'Segoe UI', Arial, sans-serif" font-size="18" font-weight="600">Pinned Note Preview</text>
  ${pinnedPreview}
</svg>`.trim();

        const blob = new Blob([svg], { type: 'image/svg+xml;charset=utf-8' });
        const url = window.URL.createObjectURL(blob);
        const anchor = document.createElement('a');
        anchor.href = url;
        anchor.download = `terminal-snapshot-${selectedSymbol.toLowerCase()}.svg`;
        document.body.appendChild(anchor);
        anchor.click();
        document.body.removeChild(anchor);
        window.URL.revokeObjectURL(url);
        setSnapshotMessage('Snapshot SVG card downloaded.');
    };

    const handleApplySharedLayout = () => {
        if (!sharedLayout) {
            return;
        }
        applySharedLayoutState(sharedLayout);
        setSharedLayoutMessage(`Applied shared layout: ${sharedLayout.name}`);
    };

    const handleSaveSharedLayout = async () => {
        if (!sharedLayout || !currentUserId) {
            return;
        }
        const created = await createTerminalLayout(currentUserId, {
            name: sharedLayout.name,
            watchlistId: sharedLayout.watchlistId,
            market: sharedLayout.market,
            symbol: sharedLayout.symbol,
            compareSymbols: sharedLayout.compareSymbols,
            compareVisible: sharedLayout.compareVisible,
            range: sharedLayout.range,
            interval: sharedLayout.interval,
            favoriteSymbols: sharedLayout.favoriteSymbols,
        });
        if (!created) {
            setSharedLayoutMessage('Failed to save shared layout.');
            return;
        }
        setTerminalLayouts((current) => [created, ...current].slice(0, 10));
        setSharedLayoutMessage(`Saved shared layout: ${sharedLayout.name}`);
    };

    return (
        <div className="min-h-screen bg-[radial-gradient(circle_at_top_left,_rgba(245,158,11,0.10),_transparent_28%),radial-gradient(circle_at_bottom_right,_rgba(34,197,94,0.10),_transparent_28%),#040404] text-white">
            <nav className="sticky top-0 z-50 flex items-center justify-between border-b border-white/10 bg-black/55 px-6 py-4 backdrop-blur-md">
                <Link href="/dashboard" className="flex items-center gap-2 text-xl font-bold tracking-tight">
                    <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-green-500 font-bold text-black">P</div>
                    <span>PaperTrade<span className="text-green-500">Pro</span></span>
                </Link>
                <div className="flex items-center gap-4 text-sm font-medium text-zinc-400">
                    <Link href="/dashboard" className="transition-colors hover:text-white">Dashboard</Link>
                    <Link href="/dashboard/leaderboard" className="transition-colors hover:text-white">Leaderboard</Link>
                    <Link href="/discover" className="transition-colors hover:text-white">Discover</Link>
                    <Link href="/watchlist" className="text-white">Markets</Link>
                </div>
            </nav>

            <div className="mx-auto max-w-[1600px] px-6 py-8">
                <div className="mb-6 flex flex-wrap items-center justify-between gap-4">
                    <div>
                        <h1 className="text-3xl font-bold">
                            <span className="bg-gradient-to-r from-amber-400 via-yellow-300 to-orange-500 bg-clip-text text-transparent">Markets</span> Terminal
                        </h1>
                        <p className="mt-2 text-sm text-zinc-400">
                            Enstruman degistir, interval sec, `ALL` history modunda sola kaydirarak daha eski mumlari yukle.
                        </p>
                    </div>
                    <form onSubmit={handleCreateWatchlist} className="flex gap-2">
                        <input
                            type="text"
                            value={newName}
                            onChange={(event) => setNewName(event.target.value)}
                            placeholder="New watchlist name..."
                            className="w-52 rounded-lg border border-zinc-700 bg-zinc-900 px-3 py-2 text-sm text-white outline-none transition-colors focus:border-amber-500"
                        />
                        <button type="submit" className="rounded-lg border border-amber-500/20 bg-amber-500/10 px-4 py-2 text-sm font-bold text-amber-400 transition-all hover:bg-amber-500/20">
                            + Create
                        </button>
                    </form>
                </div>

                {loading ? (
                    <div className="flex justify-center py-20">
                        <div className="h-8 w-8 animate-spin rounded-full border-2 border-amber-500 border-t-transparent" />
                    </div>
                ) : (
                    <div className="grid grid-cols-1 gap-6 xl:grid-cols-[minmax(0,1fr)_380px]">
                        <section className="space-y-5">
                            <div className="rounded-3xl border border-white/10 bg-black/40 p-5 shadow-[0_0_60px_rgba(0,0,0,0.4)] backdrop-blur-xl">
                                <div className="flex flex-col gap-5">
                                    <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-5">
                                        {sessionContextCards.map((card) => (
                                            <article key={card.label} className="rounded-2xl border border-white/10 bg-black/25 px-4 py-3">
                                                <div className="flex items-center justify-between gap-3">
                                                    <p className="text-[10px] uppercase tracking-[0.2em] text-zinc-500">{card.label}</p>
                                                    <span className={`rounded-full border px-2 py-0.5 text-[10px] font-bold uppercase tracking-[0.14em] ${card.accent}`}>
                                                        {card.badge}
                                                    </span>
                                                </div>
                                                <p className="mt-2 text-sm font-semibold text-white">{card.value}</p>
                                                <p className="mt-1 text-[11px] text-zinc-500">{card.detail}</p>
                                            </article>
                                        ))}
                                    </div>

                                    <div className="flex flex-wrap items-start justify-between gap-4">
                                        <div>
                                            <p className="text-[11px] uppercase tracking-[0.3em] text-zinc-500">Instrument</p>
                                            <div className="mt-2 flex items-center gap-3">
                                                <h2 className="text-3xl font-bold text-white">{selectedDisplayName}</h2>
                                                <button
                                                    onClick={() => toggleFavoriteSymbol(selectedSymbol)}
                                                    className={`rounded-full border px-3 py-1 text-[10px] font-bold uppercase tracking-[0.18em] transition ${favoriteSymbols.includes(selectedSymbol)
                                                        ? 'border-amber-400/40 bg-amber-400/10 text-amber-300'
                                                        : 'border-white/10 bg-white/[0.03] text-zinc-400 hover:text-white'}`}
                                                >
                                                    {favoriteSymbols.includes(selectedSymbol) ? 'Starred' : 'Star'}
                                                </button>
                                            </div>
                                            <p className="mt-1 text-sm text-zinc-500">{selectedSymbol} · {selectedAssetType}</p>
                                            {selectedInstrumentMetadata && (
                                                <div className="mt-3 flex flex-wrap gap-2">
                                                    {[selectedInstrumentMetadata.market, selectedInstrumentMetadata.exchange, selectedInstrumentMetadata.currency, selectedInstrumentMetadata.sector]
                                                        .filter(Boolean)
                                                        .map((chip) => (
                                                            <span
                                                                key={chip}
                                                                className="rounded-full border border-white/10 bg-white/[0.03] px-3 py-1 text-[10px] font-bold uppercase tracking-[0.16em] text-zinc-300"
                                                            >
                                                                {chip}
                                                            </span>
                                                        ))}
                                                    {selectedInstrumentMetadata.delayLabel && (
                                                        <span className="rounded-full border border-sky-400/25 bg-sky-400/10 px-3 py-1 text-[10px] font-bold uppercase tracking-[0.16em] text-sky-300">
                                                            {selectedInstrumentMetadata.delayLabel}
                                                        </span>
                                                    )}
                                                </div>
                                            )}
                                        </div>
                                        <div className="text-right">
                                            <p className="text-[11px] uppercase tracking-[0.3em] text-zinc-500">Spot</p>
                                            <p className="mt-2 font-mono text-3xl font-bold text-white">{formatMoney(Number(selectedInstrument?.currentPrice ?? 0))}</p>
                                            <p className={`mt-1 text-sm font-semibold ${Number(selectedInstrument?.changePercent24h ?? 0) >= 0 ? 'text-emerald-400' : 'text-red-400'}`}>
                                                {formatPercent(Number(selectedInstrument?.changePercent24h ?? 0))} 24h
                                            </p>
                                        </div>
                                    </div>

                                    <div className="grid gap-3 lg:grid-cols-[minmax(0,1fr)_auto_auto_auto_auto]">
                                        <input
                                            type="text"
                                            value={instrumentQuery}
                                            onChange={(event) => setInstrumentQuery(event.target.value)}
                                            placeholder="Search symbol, company, sector, exchange..."
                                            className="rounded-2xl border border-white/10 bg-zinc-950/70 px-4 py-3 text-sm text-white outline-none transition-colors focus:border-amber-400"
                                        />
                                        <label className="rounded-2xl border border-white/10 bg-zinc-950/70 px-4 py-3">
                                            <p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Market</p>
                                            <select
                                                value={selectedMarket}
                                                onChange={(event) => setSelectedMarket(event.target.value as MarketSelection)}
                                                className="mt-2 w-full bg-transparent text-sm font-semibold text-sky-300 outline-none"
                                            >
                                                <option value="CRYPTO" className="bg-zinc-950 text-white">Crypto</option>
                                                <option value="BIST100" className="bg-zinc-950 text-white">BIST 100</option>
                                            </select>
                                        </label>
                                        <label className="rounded-2xl border border-white/10 bg-zinc-950/70 px-4 py-3">
                                            <p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Range</p>
                                            <select
                                                value={selectedRange}
                                                onChange={(event) => setSelectedRange(event.target.value as ChartRange)}
                                                className="mt-2 w-full bg-transparent text-sm font-semibold text-amber-300 outline-none"
                                            >
                                                {RANGE_OPTIONS.map((range) => (
                                                    <option key={range} value={range} className="bg-zinc-950 text-white">
                                                        {range}
                                                    </option>
                                                ))}
                                            </select>
                                        </label>
                                        <label className="rounded-2xl border border-white/10 bg-zinc-950/70 px-4 py-3">
                                            <p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Interval</p>
                                            <select
                                                value={selectedInterval}
                                                onChange={(event) => setSelectedInterval(event.target.value as ChartInterval)}
                                                className="mt-2 w-full bg-transparent text-sm font-semibold text-green-300 outline-none"
                                            >
                                                {INTERVAL_OPTIONS.map((interval) => {
                                                    const disabled = selectedRange === 'ALL' && interval === '1m';
                                                    return (
                                                        <option
                                                            key={interval}
                                                            value={interval}
                                                            disabled={disabled}
                                                            className="bg-zinc-950 text-white"
                                                        >
                                                            {interval}
                                                        </option>
                                                    );
                                                })}
                                            </select>
                                        </label>
                                        <label className="rounded-2xl border border-white/10 bg-zinc-950/70 px-4 py-3">
                                            <p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Compare</p>
                                            <select
                                                value={compareCandidate}
                                                onChange={(event) => {
                                                    const nextSymbol = event.target.value;
                                                    setCompareCandidate('');
                                                    if (!nextSymbol) {
                                                        return;
                                                    }
                                                    setCompareSymbols((current) => {
                                                        if (current.includes(nextSymbol) || current.length >= 3) {
                                                            return current;
                                                        }
                                                        return [...current, nextSymbol];
                                                    });
                                                }}
                                                className="mt-2 w-full bg-transparent text-sm font-semibold text-sky-300 outline-none"
                                            >
                                                <option value="" className="bg-zinc-950 text-white">Add symbol</option>
                                                {instrumentUniverse
                                                    .filter((instrument) => instrument.symbol !== selectedSymbol && !compareSymbols.includes(instrument.symbol))
                                                    .map((instrument) => (
                                                        <option key={instrument.symbol} value={instrument.symbol} className="bg-zinc-950 text-white">
                                                            {instrument.symbol} · {instrument.displayName}
                                                        </option>
                                                    ))}
                                            </select>
                                        </label>
                                    </div>

                                    <div className="flex flex-wrap items-center gap-2 rounded-2xl border border-white/10 bg-white/[0.03] px-4 py-3">
                                        <p className="mr-2 text-[10px] uppercase tracking-[0.24em] text-zinc-500">Draw</p>
                                        <button
                                            onClick={() => setDrawingMode((current) => current === 'horizontal' ? 'none' : 'horizontal')}
                                            className={`rounded-full border px-4 py-2 text-[11px] font-bold uppercase tracking-[0.18em] transition ${drawingMode === 'horizontal'
                                                ? 'border-sky-400/30 bg-sky-400/10 text-sky-300'
                                                : 'border-white/10 bg-white/[0.03] text-zinc-300 hover:text-white'}`}
                                        >
                                            Horizontal
                                        </button>
                                        <button
                                            onClick={() => setDrawingMode((current) => current === 'trend' ? 'none' : 'trend')}
                                            className={`rounded-full border px-4 py-2 text-[11px] font-bold uppercase tracking-[0.18em] transition ${drawingMode === 'trend'
                                                ? 'border-pink-400/30 bg-pink-400/10 text-pink-300'
                                                : 'border-white/10 bg-white/[0.03] text-zinc-300 hover:text-white'}`}
                                        >
                                            Trend Line
                                        </button>
                                        <button
                                            onClick={() => {
                                                setDrawingMode('none');
                                                setClearDrawingsToken((current) => current + 1);
                                            }}
                                            className="rounded-full border border-white/10 bg-white/[0.03] px-4 py-2 text-[11px] font-bold uppercase tracking-[0.18em] text-zinc-300 transition hover:text-white"
                                        >
                                            Clear Drawings
                                        </button>
                                        <span className="ml-auto text-xs text-zinc-500">
                                            {drawingMode === 'none'
                                                ? 'Select a tool to place levels or trend lines.'
                                                : (drawingMode === 'horizontal'
                                                    ? 'Click once on the chart to place a level.'
                                                    : 'Click two points on the chart to place a trend line.')}
                                        </span>
                                    </div>

                                    <div className="flex flex-wrap items-center gap-2 rounded-2xl border border-white/10 bg-white/[0.03] px-4 py-3">
                                        <p className="mr-2 text-[10px] uppercase tracking-[0.24em] text-zinc-500">Alerts</p>
                                        <button
                                            onClick={() => updateSelectedSymbolAlerts(
                                                chartActivePoint?.close ?? null,
                                                Number(selectedWatchlistItem?.alertPriceBelow) > 0 ? Number(selectedWatchlistItem?.alertPriceBelow) : null,
                                            )}
                                            disabled={!selectedWatchlistItem || !chartActivePoint}
                                            className="rounded-full border border-emerald-400/30 bg-emerald-400/10 px-4 py-2 text-[11px] font-bold uppercase tracking-[0.18em] text-emerald-300 transition hover:bg-emerald-400/20 disabled:cursor-not-allowed disabled:opacity-40"
                                        >
                                            Set Above Here
                                        </button>
                                        <button
                                            onClick={() => updateSelectedSymbolAlerts(
                                                Number(selectedWatchlistItem?.alertPriceAbove) > 0 ? Number(selectedWatchlistItem?.alertPriceAbove) : null,
                                                chartActivePoint?.close ?? null,
                                            )}
                                            disabled={!selectedWatchlistItem || !chartActivePoint}
                                            className="rounded-full border border-red-400/30 bg-red-400/10 px-4 py-2 text-[11px] font-bold uppercase tracking-[0.18em] text-red-300 transition hover:bg-red-400/20 disabled:cursor-not-allowed disabled:opacity-40"
                                        >
                                            Set Below Here
                                        </button>
                                        <button
                                            onClick={() => updateSelectedSymbolAlerts(
                                                null,
                                                Number(selectedWatchlistItem?.alertPriceBelow) > 0 ? Number(selectedWatchlistItem?.alertPriceBelow) : null,
                                            )}
                                            disabled={!selectedWatchlistItem || !selectedWatchlistItem.alertPriceAbove}
                                            className="rounded-full border border-white/10 bg-white/[0.03] px-4 py-2 text-[11px] font-bold uppercase tracking-[0.18em] text-zinc-300 transition hover:text-white disabled:cursor-not-allowed disabled:opacity-40"
                                        >
                                            Clear Above
                                        </button>
                                        <button
                                            onClick={() => updateSelectedSymbolAlerts(
                                                Number(selectedWatchlistItem?.alertPriceAbove) > 0 ? Number(selectedWatchlistItem?.alertPriceAbove) : null,
                                                null,
                                            )}
                                            disabled={!selectedWatchlistItem || !selectedWatchlistItem.alertPriceBelow}
                                            className="rounded-full border border-white/10 bg-white/[0.03] px-4 py-2 text-[11px] font-bold uppercase tracking-[0.18em] text-zinc-300 transition hover:text-white disabled:cursor-not-allowed disabled:opacity-40"
                                        >
                                            Clear Below
                                        </button>
                                        <span className="ml-auto text-xs text-zinc-500">
                                            {selectedWatchlistItem
                                                ? (chartActivePoint ? `Anchor alerts to ${formatMoney(chartActivePoint.close)} from the active candle.` : 'Move the crosshair over a candle to anchor alerts.')
                                                : 'Add this symbol to the selected watchlist to manage alerts from the chart.'}
                                        </span>
                                    </div>

                                    {compareSymbols.length > 0 && (
                                        <div className="flex flex-wrap items-center justify-between gap-3 rounded-2xl border border-amber-400/15 bg-amber-400/5 px-4 py-3">
                                            <div>
                                                <p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Compare Session</p>
                                                <div className="mt-2 flex flex-wrap items-center gap-2">
                                                    <span className="rounded-full border border-amber-400/30 bg-amber-400/10 px-3 py-1 text-xs font-bold uppercase tracking-[0.16em] text-amber-300">
                                                        {selectedSymbol}
                                                    </span>
                                                    {selectedCompareInstruments.map((instrument, index) => (
                                                        <span
                                                            key={instrument.symbol}
                                                            className="rounded-full border px-3 py-1 text-xs font-bold uppercase tracking-[0.16em]"
                                                            style={{
                                                                borderColor: `${COMPARE_COLORS[index % COMPARE_COLORS.length]}55`,
                                                                backgroundColor: `${COMPARE_COLORS[index % COMPARE_COLORS.length]}22`,
                                                                color: COMPARE_COLORS[index % COMPARE_COLORS.length],
                                                            }}
                                                        >
                                                            {instrument.symbol}
                                                        </span>
                                                    ))}
                                                </div>
                                                <div className="mt-2 flex flex-col gap-1 text-sm text-zinc-400">
                                                    {compareSessionSummary.map((summary) => (
                                                        <p key={summary.symbol}>
                                                            {selectedSymbol} {formatPercent(summary.primaryMovePercent)} · {summary.symbol} {formatPercent(summary.compareMovePercent)}
                                                        </p>
                                                    ))}
                                                </div>
                                            </div>
                                            <div className="flex flex-wrap items-center gap-2">
                                                <button
                                                    onClick={() => setCompareVisible((current) => !current)}
                                                    className={`rounded-full border px-4 py-2 text-[11px] font-bold uppercase tracking-[0.18em] transition ${compareVisible
                                                        ? 'border-sky-400/30 bg-sky-400/10 text-sky-300 hover:bg-sky-400/20'
                                                        : 'border-white/10 bg-white/[0.03] text-zinc-300 hover:text-white'}`}
                                                >
                                                    {compareVisible ? 'Hide Overlay' : 'Show Overlay'}
                                                </button>
                                                <button
                                                    onClick={() => {
                                                        setCompareSymbols([]);
                                                        setCompareVisible(true);
                                                    }}
                                                    className="rounded-full border border-white/10 bg-white/[0.03] px-4 py-2 text-[11px] font-bold uppercase tracking-[0.18em] text-zinc-300 transition hover:text-white"
                                                >
                                                    Clear Compare
                                                </button>
                                            </div>
                                            <div className="flex w-full flex-wrap gap-2 border-t border-white/10 pt-3">
                                                {compareSessionSummary.map((summary) => (
                                                    <button
                                                        key={summary.symbol}
                                                        onClick={() => setCompareSymbols((current) => current.filter((symbol) => symbol !== summary.symbol))}
                                                        className="rounded-full border px-3 py-1 text-[11px] font-semibold"
                                                        style={{
                                                            borderColor: `${summary.color}55`,
                                                            backgroundColor: `${summary.color}18`,
                                                            color: summary.color,
                                                        }}
                                                    >
                                                        {summary.displayName} · {summary.relativeGapPercent >= 0 ? '+' : ''}{summary.relativeGapPercent.toFixed(2)}%
                                                    </button>
                                                ))}
                                            </div>
                                        </div>
                                    )}

                                    <div className="rounded-2xl border border-white/10 bg-white/[0.03] p-4">
                                        <div className="flex flex-wrap items-start justify-between gap-4">
                                            <div>
                                                <p className="text-[11px] uppercase tracking-[0.24em] text-zinc-500">Compare Baskets</p>
                                                <p className="mt-1 text-sm text-zinc-400">
                                                    Save small peer sets without storing the whole terminal. Faster than full layouts when you only want the compare overlay back.
                                                </p>
                                            </div>
                                            <div className="flex flex-wrap items-center gap-2">
                                                <button
                                                    onClick={handleExportCompareBaskets}
                                                    disabled={availableCompareBaskets.length === 0}
                                                    className="rounded-full border border-white/10 bg-white/[0.03] px-3 py-1 text-[10px] font-semibold uppercase tracking-[0.14em] text-zinc-300 transition hover:text-white disabled:cursor-not-allowed disabled:opacity-40"
                                                >
                                                    Export
                                                </button>
                                                <button
                                                    onClick={() => compareBasketImportInputRef.current?.click()}
                                                    className="rounded-full border border-white/10 bg-white/[0.03] px-3 py-1 text-[10px] font-semibold uppercase tracking-[0.14em] text-zinc-300 transition hover:text-white"
                                                >
                                                    Import
                                                </button>
                                                <span className="rounded-full border border-white/10 bg-white/[0.03] px-3 py-1 text-[10px] font-semibold uppercase tracking-[0.14em] text-zinc-400">
                                                    {availableCompareBaskets.length}/12
                                                </span>
                                            </div>
                                        </div>
                                        <input
                                            ref={compareBasketImportInputRef}
                                            type="file"
                                            accept="application/json"
                                            onChange={handleImportCompareBaskets}
                                            className="hidden"
                                        />
                                        <div className="mt-4 flex flex-col gap-3 lg:flex-row">
                                            <input
                                                type="text"
                                                value={compareBasketNameDraft}
                                                onChange={(event) => setCompareBasketNameDraft(event.target.value)}
                                                placeholder="Save current compare basket as..."
                                                className="flex-1 rounded-2xl border border-zinc-700 bg-black px-4 py-3 text-sm text-white outline-none focus:border-amber-400"
                                            />
                                            <button
                                                onClick={handleSaveCurrentCompareBasket}
                                                disabled={!canSaveCompareBasket}
                                                className="rounded-full border border-amber-400/30 bg-amber-400/10 px-4 py-3 text-[11px] font-bold uppercase tracking-[0.18em] text-amber-300 transition hover:bg-amber-400/20 disabled:cursor-not-allowed disabled:opacity-40"
                                            >
                                                Save Basket
                                            </button>
                                        </div>
                                        {compareBasketMessage && (
                                            <p className="mt-3 text-xs text-zinc-500">{compareBasketMessage}</p>
                                        )}
                                        {suggestedCompareBaskets.length > 0 && (
                                            <div className="mt-4 rounded-2xl border border-sky-400/15 bg-sky-400/5 px-4 py-3">
                                                <div className="flex flex-wrap items-center justify-between gap-3">
                                                    <div>
                                                        <p className="text-[10px] uppercase tracking-[0.24em] text-sky-300">Suggested Baskets</p>
                                                        <p className="mt-1 text-xs text-zinc-400">
                                                            Quick peer sets built from the current symbol, market, and your favorites.
                                                        </p>
                                                    </div>
                                                    <span className="rounded-full border border-sky-400/20 bg-sky-400/10 px-2.5 py-1 text-[10px] font-semibold uppercase tracking-[0.14em] text-sky-300">
                                                        {selectedSymbol}
                                                    </span>
                                                </div>
                                                <div className="mt-3 grid gap-2 xl:grid-cols-3">
                                                    {suggestedCompareBaskets.map((basket) => (
                                                        <button
                                                            key={basket.id}
                                                            onClick={() => handleApplySuggestedCompareBasket(basket)}
                                                            className="rounded-2xl border border-white/10 bg-black/30 px-4 py-3 text-left transition hover:border-sky-400/30 hover:bg-sky-400/10"
                                                        >
                                                            {(() => {
                                                                const tone = describeCompareBasketTone(basket.symbols);
                                                                return (
                                                                    <div className="mb-2">
                                                                        <span className={`rounded-full border px-2.5 py-1 text-[10px] font-bold uppercase tracking-[0.14em] ${tone.className}`}>
                                                                            {tone.label}
                                                                        </span>
                                                                    </div>
                                                                );
                                                            })()}
                                                            <div className="flex items-center justify-between gap-2">
                                                                <p className="text-sm font-semibold text-white">{basket.name}</p>
                                                                <span className="text-[10px] uppercase tracking-[0.14em] text-zinc-500">
                                                                    {basket.symbols.length} symbols
                                                                </span>
                                                            </div>
                                                            <p className="mt-1 text-xs text-zinc-400">{basket.description}</p>
                                                            <p className="mt-2 text-[11px] font-semibold text-zinc-300">{describeCompareBasketSnapshot(basket.symbols)}</p>
                                                            {renderCompareBasketSparkline(basket.symbols)}
                                                            <div className="mt-3 flex flex-wrap gap-2">
                                                                {basket.symbols.map((symbol, index) => (
                                                                    <span
                                                                        key={`${basket.id}-${symbol}`}
                                                                        className="rounded-full border px-2.5 py-1 text-[10px] font-semibold uppercase tracking-[0.14em]"
                                                                        style={{
                                                                            borderColor: `${COMPARE_COLORS[index % COMPARE_COLORS.length]}55`,
                                                                            backgroundColor: `${COMPARE_COLORS[index % COMPARE_COLORS.length]}20`,
                                                                            color: COMPARE_COLORS[index % COMPARE_COLORS.length],
                                                                        }}
                                                                    >
                                                                        {symbol}
                                                                    </span>
                                                                ))}
                                                            </div>
                                                        </button>
                                                    ))}
                                                </div>
                                            </div>
                                        )}
                                        {builtInCompareBaskets.length > 0 && (
                                            <div className="mt-4 rounded-2xl border border-amber-400/15 bg-amber-400/5 px-4 py-3">
                                                <div className="flex flex-wrap items-center justify-between gap-3">
                                                    <div>
                                                        <p className="text-[10px] uppercase tracking-[0.24em] text-amber-300">Built-In Baskets</p>
                                                        <p className="mt-1 text-xs text-zinc-400">
                                                            Reusable market-defined peer baskets for fast relative checks.
                                                        </p>
                                                    </div>
                                                    <span className="rounded-full border border-amber-400/20 bg-amber-400/10 px-2.5 py-1 text-[10px] font-semibold uppercase tracking-[0.14em] text-amber-300">
                                                        {selectedMarket}
                                                    </span>
                                                </div>
                                                <div className="mt-3 grid gap-2 xl:grid-cols-2">
                                                    {builtInCompareBaskets.map((basket) => (
                                                        <button
                                                            key={basket.id}
                                                            onClick={() => handleApplyBuiltInCompareBasket(basket)}
                                                            className="rounded-2xl border border-white/10 bg-black/30 px-4 py-3 text-left transition hover:border-amber-400/25 hover:bg-amber-400/10"
                                                        >
                                                            {(() => {
                                                                const tone = describeCompareBasketTone(basket.symbols);
                                                                return (
                                                                    <div className="mb-2">
                                                                        <span className={`rounded-full border px-2.5 py-1 text-[10px] font-bold uppercase tracking-[0.14em] ${tone.className}`}>
                                                                            {tone.label}
                                                                        </span>
                                                                    </div>
                                                                );
                                                            })()}
                                                            <div className="flex items-center justify-between gap-2">
                                                                <p className="text-sm font-semibold text-white">{basket.name}</p>
                                                                <span className="text-[10px] uppercase tracking-[0.14em] text-zinc-500">
                                                                    {basket.symbols.length} symbols
                                                                </span>
                                                            </div>
                                                            <p className="mt-1 text-xs text-zinc-400">{basket.description}</p>
                                                            <p className="mt-2 text-[11px] font-semibold text-zinc-300">{describeCompareBasketSnapshot(basket.symbols)}</p>
                                                            {renderCompareBasketSparkline(basket.symbols)}
                                                            <div className="mt-3 flex flex-wrap gap-2">
                                                                {basket.symbols.map((symbol, index) => (
                                                                    <span
                                                                        key={`${basket.id}-${symbol}`}
                                                                        className="rounded-full border px-2.5 py-1 text-[10px] font-semibold uppercase tracking-[0.14em]"
                                                                        style={{
                                                                            borderColor: `${COMPARE_COLORS[index % COMPARE_COLORS.length]}55`,
                                                                            backgroundColor: `${COMPARE_COLORS[index % COMPARE_COLORS.length]}20`,
                                                                            color: COMPARE_COLORS[index % COMPARE_COLORS.length],
                                                                        }}
                                                                    >
                                                                        {symbol}
                                                                    </span>
                                                                ))}
                                                            </div>
                                                        </button>
                                                    ))}
                                                </div>
                                            </div>
                                        )}
                                        <div className="mt-4 space-y-2">
                                            {availableCompareBaskets.length === 0 ? (
                                                <div className="rounded-2xl border border-dashed border-white/10 px-4 py-5 text-sm text-zinc-500">
                                                    No compare baskets saved for {selectedMarket} yet. Build a compare session first, then save it as a reusable peer set.
                                                </div>
                                            ) : (
                                                availableCompareBaskets.map((basket) => (
                                                    <div key={basket.id} className="rounded-2xl border border-white/10 bg-black/35 px-4 py-3">
                                                        {(() => {
                                                            const tone = describeCompareBasketTone(basket.symbols);
                                                            return (
                                                        <div className="flex flex-wrap items-start justify-between gap-3">
                                                            <div className="min-w-0 flex-1">
                                                                {editingCompareBasketId !== basket.id && (
                                                                    <div className="mb-2">
                                                                        <span className={`rounded-full border px-2.5 py-1 text-[10px] font-bold uppercase tracking-[0.14em] ${tone.className}`}>
                                                                            {tone.label}
                                                                        </span>
                                                                    </div>
                                                                )}
                                                                {editingCompareBasketId === basket.id ? (
                                                                    <div className="space-y-3">
                                                                        <input
                                                                            type="text"
                                                                            value={editingCompareBasketName}
                                                                            onChange={(event) => setEditingCompareBasketName(event.target.value)}
                                                                            className="w-full rounded-2xl border border-zinc-700 bg-black px-4 py-3 text-sm text-white outline-none focus:border-amber-400"
                                                                        />
                                                                        <div className="flex flex-wrap gap-2">
                                                                            <button
                                                                                onClick={() => handleSaveCompareBasketName(basket)}
                                                                                className="rounded-full border border-emerald-400/30 bg-emerald-400/10 px-3 py-1.5 text-[11px] font-bold uppercase tracking-[0.16em] text-emerald-300 transition hover:bg-emerald-400/20"
                                                                            >
                                                                                Save
                                                                            </button>
                                                                            <button
                                                                                onClick={handleCancelEditCompareBasket}
                                                                                className="rounded-full border border-white/10 bg-white/[0.03] px-3 py-1.5 text-[11px] font-bold uppercase tracking-[0.16em] text-zinc-400 transition hover:text-white"
                                                                            >
                                                                                Cancel
                                                                            </button>
                                                                        </div>
                                                                    </div>
                                                                ) : (
                                                                    <div className="flex flex-wrap items-center gap-2">
                                                                        <p className="text-sm font-semibold text-white">{basket.name}</p>
                                                                        {activeCompareBasketId === basket.id && (
                                                                            <span className="rounded-full border border-amber-400/25 bg-amber-400/10 px-2 py-1 text-[10px] font-bold uppercase tracking-[0.14em] text-amber-300">
                                                                                Active
                                                                            </span>
                                                                        )}
                                                                        <span className="rounded-full border border-white/10 bg-white/[0.03] px-2 py-1 text-[10px] font-semibold uppercase tracking-[0.14em] text-zinc-400">
                                                                            {basket.market}
                                                                        </span>
                                                                    </div>
                                                                )}
                                                                <div className="mt-2 flex flex-wrap gap-2">
                                                                    {basket.symbols.map((symbol, index) => (
                                                                        <span
                                                                            key={`${basket.id}-${symbol}`}
                                                                            className="rounded-full border px-2.5 py-1 text-[10px] font-semibold uppercase tracking-[0.14em]"
                                                                            style={{
                                                                                borderColor: `${COMPARE_COLORS[index % COMPARE_COLORS.length]}55`,
                                                                                backgroundColor: `${COMPARE_COLORS[index % COMPARE_COLORS.length]}20`,
                                                                                color: COMPARE_COLORS[index % COMPARE_COLORS.length],
                                                                            }}
                                                                        >
                                                                            {symbol}
                                                                        </span>
                                                                    ))}
                                                                </div>
                                                                <p className="mt-2 text-[11px] text-zinc-500">
                                                                    Updated {new Date(basket.updatedAt).toLocaleString()}
                                                                </p>
                                                                <p className="mt-2 text-[11px] font-semibold text-zinc-300">
                                                                    {describeCompareBasketSnapshot(basket.symbols)}
                                                                </p>
                                                                {renderCompareBasketSparkline(basket.symbols)}
                                                            </div>
                                                            {editingCompareBasketId !== basket.id && (
                                                                <div className="flex flex-wrap gap-2">
                                                                    <button
                                                                        onClick={() => handleApplyCompareBasket(basket)}
                                                                        className="rounded-full border border-sky-400/20 bg-sky-400/10 px-3 py-1.5 text-[10px] font-bold uppercase tracking-[0.16em] text-sky-300 transition hover:bg-sky-400/20"
                                                                    >
                                                                        Apply
                                                                    </button>
                                                                    <button
                                                                        onClick={() => handleOverwriteCompareBasket(basket)}
                                                                        disabled={compareSymbols.length === 0}
                                                                        className="rounded-full border border-amber-400/20 bg-amber-400/10 px-3 py-1.5 text-[10px] font-bold uppercase tracking-[0.16em] text-amber-300 transition hover:bg-amber-400/20 disabled:cursor-not-allowed disabled:opacity-40"
                                                                    >
                                                                        Overwrite
                                                                    </button>
                                                                    <button
                                                                        onClick={() => handleShareCompareBasket(basket)}
                                                                        className="rounded-full border border-emerald-400/20 bg-emerald-400/10 px-3 py-1.5 text-[10px] font-bold uppercase tracking-[0.16em] text-emerald-300 transition hover:bg-emerald-400/20"
                                                                    >
                                                                        Share
                                                                    </button>
                                                                    <button
                                                                        onClick={() => handleStartEditCompareBasket(basket)}
                                                                        className="rounded-full border border-white/10 bg-white/[0.03] px-3 py-1.5 text-[10px] font-bold uppercase tracking-[0.16em] text-zinc-300 transition hover:text-white"
                                                                    >
                                                                        Rename
                                                                    </button>
                                                                    <button
                                                                        onClick={() => handleDeleteCompareBasket(basket.id)}
                                                                        className="rounded-full border border-white/10 bg-white/[0.03] px-3 py-1.5 text-[10px] font-bold uppercase tracking-[0.16em] text-zinc-300 transition hover:text-white"
                                                                    >
                                                                        Remove
                                                                    </button>
                                                                </div>
                                                            )}
                                                        </div>
                                                            );
                                                        })()}
                                                    </div>
                                                ))
                                            )}
                                        </div>
                                    </div>

                                    {favoriteInstruments.length > 0 && (
                                        <div className="rounded-2xl border border-white/10 bg-white/[0.03] px-4 py-3">
                                            <p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Favorites</p>
                                            <div className="mt-3 flex flex-wrap gap-2">
                                                {favoriteInstruments.map((instrument) => (
                                                    <button
                                                        key={instrument.symbol}
                                                        onClick={() => setSelectedSymbol(instrument.symbol)}
                                                        className={`rounded-full border px-4 py-2 text-xs font-bold uppercase tracking-[0.18em] transition ${selectedSymbol === instrument.symbol
                                                            ? 'border-amber-400/40 bg-amber-400/10 text-amber-300'
                                                            : 'border-white/10 bg-white/[0.03] text-zinc-300 hover:text-white'}`}
                                                    >
                                                        {instrument.symbol}
                                                    </button>
                                                ))}
                                            </div>
                                        </div>
                                    )}

                                    <div className="rounded-2xl border border-white/10 bg-white/[0.03] p-4">
                                        <div className="flex items-start justify-between gap-3">
                                            <div>
                                                <p className="text-[11px] uppercase tracking-[0.24em] text-zinc-500">Saved Layouts</p>
                                                <p className="mt-1 text-sm text-zinc-400">
                                                    Current market, symbol, compare basket, watchlist context, range, interval, and favorites snapshot.
                                                </p>
                                            </div>
                                            <div className="flex items-center gap-2">
                                                <button
                                                    onClick={handleExportLayouts}
                                                    disabled={terminalLayouts.length === 0}
                                                    className="rounded-full border border-white/10 bg-white/[0.03] px-3 py-1 text-[10px] font-semibold uppercase tracking-[0.16em] text-zinc-300 transition hover:text-white disabled:cursor-not-allowed disabled:opacity-40"
                                                >
                                                    Export
                                                </button>
                                                <button
                                                    onClick={() => layoutImportInputRef.current?.click()}
                                                    disabled={terminalLayouts.length >= 10}
                                                    className="rounded-full border border-white/10 bg-white/[0.03] px-3 py-1 text-[10px] font-semibold uppercase tracking-[0.16em] text-zinc-300 transition hover:text-white disabled:cursor-not-allowed disabled:opacity-40"
                                                >
                                                    Import
                                                </button>
                                                <span className="rounded-full border border-white/10 bg-white/[0.03] px-3 py-1 text-[10px] font-semibold uppercase tracking-[0.16em] text-zinc-400">
                                                    {terminalLayouts.length}/10
                                                </span>
                                            </div>
                                        </div>
                                        {sharedLayout && (
                                            <div className="mt-4 rounded-2xl border border-sky-400/20 bg-sky-400/8 px-4 py-3">
                                                <div className="flex flex-wrap items-center justify-between gap-3">
                                                    <div>
                                                        <p className="text-xs font-bold uppercase tracking-[0.18em] text-sky-300">Shared Layout</p>
                                                        <p className="mt-1 text-sm text-zinc-200">{sharedLayout.name}</p>
                                                        <p className="mt-1 text-xs text-zinc-400">
                                                            {sharedLayout.market} · {sharedLayout.symbol} · {sharedLayout.range} / {sharedLayout.interval}
                                                        </p>
                                                        <div className="mt-2 flex flex-wrap gap-2">
                                                            {sharedLayout.compareSymbols.length > 0 && (
                                                                <span className="rounded-full border border-sky-400/20 bg-sky-400/10 px-2 py-1 text-[10px] font-semibold uppercase tracking-[0.14em] text-sky-300">
                                                                    Compare {sharedLayout.compareSymbols.join(', ')}
                                                                </span>
                                                            )}
                                                            <span className="rounded-full border border-white/10 bg-white/[0.03] px-2 py-1 text-[10px] font-semibold uppercase tracking-[0.14em] text-zinc-300">
                                                                Favorites {sharedLayout.favoriteSymbols.length}
                                                            </span>
                                                            {sharedLayout.watchlistId && (
                                                                <span className="rounded-full border border-emerald-400/20 bg-emerald-400/10 px-2 py-1 text-[10px] font-semibold uppercase tracking-[0.14em] text-emerald-300">
                                                                    Watchlist linked
                                                                </span>
                                                            )}
                                                        </div>
                                                    </div>
                                                    <div className="flex gap-2">
                                                        <button
                                                            onClick={handleApplySharedLayout}
                                                            className="rounded-full border border-sky-400/25 bg-sky-400/10 px-3 py-1.5 text-[10px] font-bold uppercase tracking-[0.16em] text-sky-300 transition hover:bg-sky-400/20"
                                                        >
                                                            Apply Shared
                                                        </button>
                                                        <button
                                                            onClick={handleSaveSharedLayout}
                                                            disabled={terminalLayouts.length >= 10}
                                                            className="rounded-full border border-amber-400/25 bg-amber-400/10 px-3 py-1.5 text-[10px] font-bold uppercase tracking-[0.16em] text-amber-300 transition hover:bg-amber-400/20 disabled:cursor-not-allowed disabled:opacity-40"
                                                        >
                                                            Save As New
                                                        </button>
                                                        <button
                                                            onClick={() => setSharedLayout(null)}
                                                            className="rounded-full border border-white/10 bg-white/[0.03] px-3 py-1.5 text-[10px] font-bold uppercase tracking-[0.16em] text-zinc-300 transition hover:text-white"
                                                        >
                                                            Dismiss
                                                        </button>
                                                    </div>
                                                </div>
                                            </div>
                                        )}
                                        <input
                                            ref={layoutImportInputRef}
                                            type="file"
                                            accept="application/json"
                                            onChange={handleImportLayouts}
                                            className="hidden"
                                        />
                                        <div className="mt-4 flex flex-col gap-3 lg:flex-row">
                                            <input
                                                type="text"
                                                value={layoutNameDraft}
                                                onChange={(event) => setLayoutNameDraft(event.target.value)}
                                                placeholder="Save current layout as..."
                                                className="flex-1 rounded-2xl border border-zinc-700 bg-black px-4 py-3 text-sm text-white outline-none focus:border-amber-400"
                                            />
                                            <button
                                                onClick={handleSaveCurrentLayout}
                                                disabled={!layoutNameDraft.trim() || terminalLayouts.length >= 10}
                                                className="rounded-full border border-amber-400/30 bg-amber-400/10 px-4 py-3 text-[11px] font-bold uppercase tracking-[0.18em] text-amber-300 transition hover:bg-amber-400/20 disabled:cursor-not-allowed disabled:opacity-40"
                                            >
                                                Save Current
                                            </button>
                                        </div>
                                        {layoutImportMessage && (
                                            <p className="mt-3 text-xs text-zinc-500">{layoutImportMessage}</p>
                                        )}
                                        {sharedLayoutMessage && (
                                            <p className="mt-2 break-all text-xs text-zinc-500">{sharedLayoutMessage}</p>
                                        )}
                                        <div className="mt-4 space-y-2">
                                            {terminalLayouts.length === 0 ? (
                                                <div className="rounded-2xl border border-dashed border-white/10 px-4 py-5 text-sm text-zinc-500">
                                                    No saved layouts yet. Capture a setup once and restore it later with one click.
                                                </div>
                                            ) : (
                                                terminalLayouts.map((layout) => (
                                                    <div key={layout.id} className="rounded-2xl border border-white/10 bg-black/35 px-4 py-3">
                                                        <div className="flex items-start justify-between gap-3">
                                                            <div className="min-w-0 flex-1">
                                                                {editingLayoutId === layout.id ? (
                                                                    <div className="space-y-3">
                                                                        <input
                                                                            type="text"
                                                                            value={editingLayoutName}
                                                                            onChange={(event) => setEditingLayoutName(event.target.value)}
                                                                            className="w-full rounded-2xl border border-zinc-700 bg-black px-4 py-3 text-sm text-white outline-none focus:border-amber-400"
                                                                        />
                                                                        <div className="flex gap-2">
                                                                            <button
                                                                                onClick={() => handleSaveLayoutName(layout)}
                                                                                className="rounded-full border border-emerald-400/30 bg-emerald-400/10 px-3 py-1.5 text-[11px] font-bold uppercase tracking-[0.16em] text-emerald-300 transition hover:bg-emerald-400/20"
                                                                            >
                                                                                Save
                                                                            </button>
                                                                            <button
                                                                                onClick={handleCancelEditLayout}
                                                                                className="rounded-full border border-white/10 bg-white/[0.03] px-3 py-1.5 text-[11px] font-bold uppercase tracking-[0.16em] text-zinc-400 transition hover:text-white"
                                                                            >
                                                                                Cancel
                                                                            </button>
                                                                        </div>
                                                                    </div>
                                                                ) : (
                                                                    <>
                                                                        <div className="flex items-center gap-2">
                                                                            <p className="text-sm font-semibold text-white">{layout.name}</p>
                                                                            {activeLayoutId === layout.id && (
                                                                                <span className="rounded-full border border-amber-400/25 bg-amber-400/10 px-2 py-1 text-[10px] font-bold uppercase tracking-[0.14em] text-amber-300">
                                                                                    Active
                                                                                </span>
                                                                            )}
                                                                        </div>
                                                                        <div className="mt-2 flex flex-wrap gap-2">
                                                                            <span className="rounded-full border border-white/10 bg-white/[0.03] px-2 py-1 text-[10px] font-semibold uppercase tracking-[0.14em] text-zinc-300">
                                                                                {layout.market}
                                                                            </span>
                                                                            <span className="rounded-full border border-white/10 bg-white/[0.03] px-2 py-1 text-[10px] font-semibold uppercase tracking-[0.14em] text-zinc-300">
                                                                                {layout.symbol}
                                                                            </span>
                                                                            <span className="rounded-full border border-white/10 bg-white/[0.03] px-2 py-1 text-[10px] font-semibold uppercase tracking-[0.14em] text-zinc-300">
                                                                                {layout.range} · {layout.interval}
                                                                            </span>
                                                                            {layout.compareSymbols.length > 0 && (
                                                                                <span className="rounded-full border border-sky-400/20 bg-sky-400/10 px-2 py-1 text-[10px] font-semibold uppercase tracking-[0.14em] text-sky-300">
                                                                                    Compare {layout.compareSymbols.join(', ')}
                                                                                </span>
                                                                            )}
                                                                            {layout.watchlistId && (
                                                                                <span className="rounded-full border border-emerald-400/20 bg-emerald-400/10 px-2 py-1 text-[10px] font-semibold uppercase tracking-[0.14em] text-emerald-300">
                                                                                    Watchlist linked
                                                                                </span>
                                                                            )}
                                                                        </div>
                                                                    </>
                                                                )}
                                                            </div>
                                                            {editingLayoutId !== layout.id && (
                                                                <div className="flex shrink-0 gap-3">
                                                                    <button
                                                                        onClick={() => handleApplyLayout(layout)}
                                                                        className="text-xs text-amber-300 transition hover:text-amber-200"
                                                                    >
                                                                        Apply
                                                                    </button>
                                                                    <button
                                                                        onClick={() => handleOverwriteLayout(layout)}
                                                                        className="text-xs text-sky-300 transition hover:text-sky-200"
                                                                    >
                                                                        Overwrite
                                                                    </button>
                                                                    <button
                                                                        onClick={() => handleShareLayout(layout)}
                                                                        className="text-xs text-emerald-300 transition hover:text-emerald-200"
                                                                    >
                                                                        Share
                                                                    </button>
                                                                    <button
                                                                        onClick={() => handleStartEditLayout(layout)}
                                                                        className="text-xs text-zinc-500 transition hover:text-amber-300"
                                                                    >
                                                                        Rename
                                                                    </button>
                                                                    <button
                                                                        onClick={() => handleDeleteLayout(layout.id)}
                                                                        className="text-xs text-zinc-500 transition hover:text-red-400"
                                                                    >
                                                                        Remove
                                                                    </button>
                                                                </div>
                                                            )}
                                                        </div>
                                                        <p className="mt-3 text-[10px] uppercase tracking-[0.16em] text-zinc-500">
                                                            Updated {new Date(layout.updatedAt).toLocaleString()}
                                                        </p>
                                                    </div>
                                                ))
                                            )}
                                        </div>
                                    </div>

                                    <div className="grid gap-3 lg:grid-cols-[minmax(0,1fr)_320px]">
                                        <div className="rounded-3xl border border-white/8 bg-zinc-950/60 p-4">
                                            {(chartLoading && candles.length === 0) ? (
                                                <div className="flex h-[520px] items-center justify-center">
                                                    <div className="h-8 w-8 animate-spin rounded-full border-2 border-amber-400 border-t-transparent" />
                                                </div>
                                            ) : (
                                                <MarketWorkspaceChart
                                                    data={candles}
                                                    compareSeries={compareSeries}
                                                    compareVisible={compareVisible}
                                                    drawingMode={drawingMode}
                                                    drawingStorageKey={selectedSymbol}
                                                    clearDrawingsToken={clearDrawingsToken}
                                                    onDrawingComplete={() => setDrawingMode('none')}
                                                    onActivePointChange={setChartActivePoint}
                                                    alertLines={chartAlertLines}
                                                    resetKey={`${selectedSymbol}-${selectedRange}-${selectedInterval}`}
                                                    onReachStart={handleLoadMoreHistory}
                                                />
                                            )}
                                        </div>

                                        <div className="rounded-3xl border border-white/8 bg-zinc-950/55 p-4">
                                            <p className="text-[11px] uppercase tracking-[0.3em] text-zinc-500">Instrument Universe</p>
                                            <p className="mt-2 text-sm text-zinc-400">Chart sembolunu watchlist'e eklemeden de dogrudan degistirebilirsin.</p>
                                            <div className="mt-4 grid gap-3 xl:grid-cols-2">
                                                <div className="rounded-2xl border border-emerald-400/15 bg-emerald-400/5 p-3">
                                                    <div className="flex items-center justify-between gap-2">
                                                        <p className="text-[10px] uppercase tracking-[0.18em] text-emerald-300">Top Movers</p>
                                                        <button
                                                            onClick={() => {
                                                                setUniverseQuickFilter('GAINERS');
                                                                setUniverseSortMode('MOVE_DESC');
                                                            }}
                                                            className="text-[10px] font-semibold uppercase tracking-[0.14em] text-emerald-300 transition hover:text-emerald-200"
                                                        >
                                                            Open
                                                        </button>
                                                    </div>
                                                    <div className="mt-3 space-y-2">
                                                        {topMoverInstruments.map((instrument) => (
                                                            <button
                                                                key={`top-${instrument.symbol}`}
                                                                onClick={() => setSelectedSymbol(instrument.symbol)}
                                                                className="flex w-full items-center justify-between rounded-xl border border-white/10 bg-black/25 px-3 py-2 text-left transition hover:border-emerald-400/25"
                                                            >
                                                                <div>
                                                                    <p className="text-xs font-semibold text-white">{instrument.symbol}</p>
                                                                    <p className="text-[10px] text-zinc-500">{instrument.displayName}</p>
                                                                </div>
                                                                <span className="text-xs font-semibold text-emerald-300">{formatPercent(instrument.changePercent24h)}</span>
                                                            </button>
                                                        ))}
                                                    </div>
                                                </div>
                                                <div className="rounded-2xl border border-red-400/15 bg-red-400/5 p-3">
                                                    <div className="flex items-center justify-between gap-2">
                                                        <p className="text-[10px] uppercase tracking-[0.18em] text-red-300">Bottom Movers</p>
                                                        <button
                                                            onClick={() => {
                                                                setUniverseQuickFilter('LOSERS');
                                                                setUniverseSortMode('MOVE_ASC');
                                                            }}
                                                            className="text-[10px] font-semibold uppercase tracking-[0.14em] text-red-300 transition hover:text-red-200"
                                                        >
                                                            Open
                                                        </button>
                                                    </div>
                                                    <div className="mt-3 space-y-2">
                                                        {bottomMoverInstruments.map((instrument) => (
                                                            <button
                                                                key={`bottom-${instrument.symbol}`}
                                                                onClick={() => setSelectedSymbol(instrument.symbol)}
                                                                className="flex w-full items-center justify-between rounded-xl border border-white/10 bg-black/25 px-3 py-2 text-left transition hover:border-red-400/25"
                                                            >
                                                                <div>
                                                                    <p className="text-xs font-semibold text-white">{instrument.symbol}</p>
                                                                    <p className="text-[10px] text-zinc-500">{instrument.displayName}</p>
                                                                </div>
                                                                <span className="text-xs font-semibold text-red-300">{formatPercent(instrument.changePercent24h)}</span>
                                                            </button>
                                                        ))}
                                                    </div>
                                                </div>
                                            </div>
                                            <div className="mt-4 rounded-2xl border border-white/10 bg-black/25 p-3">
                                                <div className="flex flex-wrap items-center justify-between gap-3">
                                                    <div>
                                                        <p className="text-[10px] uppercase tracking-[0.18em] text-zinc-500">Heatmap Slice</p>
                                                        <p className="mt-1 text-xs text-zinc-400">Fast visual snapshot of the strongest movers in the current market universe.</p>
                                                    </div>
                                                    <button
                                                        onClick={() => {
                                                            setUniverseQuickFilter('ALL');
                                                            setUniverseSortMode('MOVE_DESC');
                                                            setInstrumentQuery('');
                                                        }}
                                                        className="rounded-full border border-white/10 bg-white/[0.03] px-3 py-1.5 text-[10px] font-bold uppercase tracking-[0.16em] text-zinc-300 transition hover:text-white"
                                                    >
                                                        Reset Slice
                                                    </button>
                                                </div>
                                                <div className="mt-3 grid gap-2 sm:grid-cols-2 xl:grid-cols-4">
                                                    {heatmapInstruments.map((instrument) => {
                                                        const positive = instrument.changePercent24h >= 0;
                                                        const intensity = Math.min(Math.abs(instrument.changePercent24h) / 10, 1);
                                                        const backgroundColor = positive
                                                            ? `rgba(16, 185, 129, ${0.16 + intensity * 0.3})`
                                                            : `rgba(248, 113, 113, ${0.16 + intensity * 0.3})`;
                                                        const borderColor = positive
                                                            ? `rgba(16, 185, 129, ${0.2 + intensity * 0.35})`
                                                            : `rgba(248, 113, 113, ${0.2 + intensity * 0.35})`;
                                                        return (
                                                            <button
                                                                key={`heatmap-${instrument.symbol}`}
                                                                onClick={() => setSelectedSymbol(instrument.symbol)}
                                                                className="rounded-2xl border p-3 text-left transition hover:scale-[1.01]"
                                                                style={{ backgroundColor, borderColor }}
                                                            >
                                                                <div className="flex items-start justify-between gap-3">
                                                                    <div>
                                                                        <p className="text-xs font-semibold text-white">{instrument.symbol}</p>
                                                                        <p className="mt-1 line-clamp-2 text-[10px] text-zinc-200/80">{instrument.displayName}</p>
                                                                    </div>
                                                                    <span className={`rounded-full px-2 py-0.5 text-[10px] font-bold uppercase tracking-[0.14em] ${positive ? 'bg-emerald-950/60 text-emerald-200' : 'bg-red-950/60 text-red-200'}`}>
                                                                        {formatPercent(instrument.changePercent24h)}
                                                                    </span>
                                                                </div>
                                                                <div className="mt-3 flex items-center justify-between text-[10px] text-zinc-100/80">
                                                                    <span>{instrument.sector || instrument.exchange || instrument.market || 'Universe'}</span>
                                                                    <span className="font-mono">{formatMoney(instrument.currentPrice)}</span>
                                                                </div>
                                                            </button>
                                                        );
                                                    })}
                                                </div>
                                            </div>
                                            {sectorPulseGroups.length > 0 && (
                                                <div className="mt-4 rounded-2xl border border-white/10 bg-black/25 p-3">
                                                    <div className="flex flex-wrap items-center justify-between gap-3">
                                                        <div>
                                                            <p className="text-[10px] uppercase tracking-[0.18em] text-zinc-500">Sector Pulse</p>
                                                            <p className="mt-1 text-xs text-zinc-400">Highest-pressure sectors in the active market universe. Click to pivot the scanner into that peer group.</p>
                                                        </div>
                                                        <span className="rounded-full border border-white/10 bg-white/[0.03] px-2.5 py-1 text-[10px] font-semibold uppercase tracking-[0.14em] text-zinc-400">
                                                            {sectorPulseGroups.length} groups
                                                        </span>
                                                    </div>
                                                    <div className="mt-3 grid gap-2 xl:grid-cols-2">
                                                        {sectorPulseGroups.map((group) => {
                                                            const positive = group.averageMove >= 0;
                                                            return (
                                                                <button
                                                                    key={`sector-pulse-${group.sector}`}
                                                                    onClick={() => {
                                                                        setSelectedSymbol(group.leader.symbol);
                                                                        setUniverseQuickFilter('SECTOR');
                                                                        setUniverseSortMode('MOVE_DESC');
                                                                        setInstrumentQuery('');
                                                                    }}
                                                                    className={`rounded-2xl border px-4 py-3 text-left transition ${positive
                                                                        ? 'border-emerald-400/20 bg-emerald-400/5 hover:bg-emerald-400/10'
                                                                        : 'border-red-400/20 bg-red-400/5 hover:bg-red-400/10'}`}
                                                                >
                                                                    <div className="flex items-center justify-between gap-3">
                                                                        <div>
                                                                            <p className="text-sm font-semibold text-white">{group.sector}</p>
                                                                            <p className="mt-1 text-[11px] text-zinc-400">
                                                                                {group.count} symbols · leader {group.leader.symbol}
                                                                            </p>
                                                                        </div>
                                                                        <div className="flex flex-col items-end gap-2">
                                                                            <span className={`rounded-full px-2 py-0.5 text-[10px] font-bold uppercase tracking-[0.14em] ${positive ? 'bg-emerald-950/60 text-emerald-200' : 'bg-red-950/60 text-red-200'}`}>
                                                                                {formatPercent(group.averageMove)}
                                                                            </span>
                                                                            <button
                                                                                onClick={(event) => {
                                                                                    event.stopPropagation();
                                                                                    handleSaveSectorPulseView(group.sector, group.leader.symbol);
                                                                                }}
                                                                                className="rounded-full border border-white/10 bg-white/[0.03] px-2.5 py-1 text-[10px] font-bold uppercase tracking-[0.14em] text-zinc-300 transition hover:text-white"
                                                                            >
                                                                                Save View
                                                                            </button>
                                                                        </div>
                                                                    </div>
                                                                    <div className="mt-3 flex items-center justify-between text-[10px] text-zinc-100/80">
                                                                        <span>{group.leader.displayName}</span>
                                                                        <span className={`font-semibold ${group.leader.changePercent24h >= 0 ? 'text-emerald-300' : 'text-red-300'}`}>
                                                                            {group.leader.symbol} {formatPercent(group.leader.changePercent24h)}
                                                                        </span>
                                                                    </div>
                                                                </button>
                                                            );
                                                        })}
                                                    </div>
                                                </div>
                                            )}
                                            <div className="mt-4 rounded-2xl border border-white/10 bg-black/25 p-3">
                                                <div className="flex flex-wrap items-start justify-between gap-3">
                                                    <div>
                                                        <p className="text-[10px] uppercase tracking-[0.18em] text-zinc-500">Saved Scanner Views</p>
                                                        <p className="mt-1 text-xs text-zinc-400">Capture a filter + sort + search combination for this market.</p>
                                                    </div>
                                                    <div className="flex flex-wrap items-center gap-2">
                                                        <span className="rounded-full border border-white/10 bg-white/[0.03] px-2.5 py-1 text-[10px] font-semibold uppercase tracking-[0.14em] text-zinc-400">
                                                            {availableScannerViews.length}/12
                                                        </span>
                                                        <button
                                                            onClick={handleExportScannerViews}
                                                            disabled={availableScannerViews.length === 0}
                                                            className="rounded-full border border-sky-400/20 bg-sky-400/10 px-3 py-1.5 text-[10px] font-bold uppercase tracking-[0.16em] text-sky-300 transition hover:bg-sky-400/20 disabled:cursor-not-allowed disabled:opacity-40"
                                                        >
                                                            Export
                                                        </button>
                                                        <button
                                                            onClick={() => scannerViewImportInputRef.current?.click()}
                                                            className="rounded-full border border-white/10 bg-white/[0.03] px-3 py-1.5 text-[10px] font-bold uppercase tracking-[0.16em] text-zinc-300 transition hover:text-white"
                                                        >
                                                            Import
                                                        </button>
                                                    </div>
                                                </div>
                                                <input
                                                    ref={scannerViewImportInputRef}
                                                    type="file"
                                                    accept="application/json"
                                                    className="hidden"
                                                    onChange={handleImportScannerViews}
                                                />
                                                <div className="mt-3 flex flex-col gap-3 lg:flex-row">
                                                    <input
                                                        type="text"
                                                        value={scannerViewNameDraft}
                                                        onChange={(event) => setScannerViewNameDraft(event.target.value)}
                                                        placeholder="Save current scanner view as..."
                                                        className="flex-1 rounded-2xl border border-zinc-700 bg-black px-4 py-3 text-sm text-white outline-none focus:border-amber-400"
                                                    />
                                                    <button
                                                        onClick={handleSaveScannerView}
                                                        disabled={!scannerViewNameDraft.trim() || availableScannerViews.length >= 12}
                                                        className="rounded-full border border-amber-400/30 bg-amber-400/10 px-4 py-3 text-[11px] font-bold uppercase tracking-[0.18em] text-amber-300 transition hover:bg-amber-400/20 disabled:cursor-not-allowed disabled:opacity-40"
                                                    >
                                                        Save View
                                                    </button>
                                                </div>
                                                {scannerViewMessage && (
                                                    <p className="mt-3 text-xs text-zinc-500">{scannerViewMessage}</p>
                                                )}
                                                <div className="mt-3 space-y-2">
                                                    {availableScannerViews.length === 0 ? (
                                                        <div className="rounded-2xl border border-dashed border-white/10 px-4 py-4 text-sm text-zinc-500">
                                                            No saved scanner views for {selectedMarket} yet.
                                                        </div>
                                                    ) : (
                                                        availableScannerViews.map((view) => (
                                                            <div key={view.id} className="flex flex-wrap items-center justify-between gap-3 rounded-2xl border border-white/10 bg-white/[0.03] px-4 py-3">
                                                                <div>
                                                                    {editingScannerViewId === view.id ? (
                                                                        <div className="space-y-3">
                                                                            <input
                                                                                type="text"
                                                                                value={editingScannerViewName}
                                                                                onChange={(event) => setEditingScannerViewName(event.target.value)}
                                                                                className="w-full rounded-2xl border border-zinc-700 bg-black px-4 py-3 text-sm text-white outline-none focus:border-amber-400"
                                                                            />
                                                                            <div className="flex flex-wrap gap-2">
                                                                                <button
                                                                                    onClick={() => handleSaveScannerViewName(view)}
                                                                                    className="rounded-full border border-emerald-400/30 bg-emerald-400/10 px-3 py-1.5 text-[10px] font-bold uppercase tracking-[0.16em] text-emerald-300 transition hover:bg-emerald-400/20"
                                                                                >
                                                                                    Save
                                                                                </button>
                                                                                <button
                                                                                    onClick={handleCancelEditScannerView}
                                                                                    className="rounded-full border border-white/10 bg-white/[0.03] px-3 py-1.5 text-[10px] font-bold uppercase tracking-[0.16em] text-zinc-300 transition hover:text-white"
                                                                                >
                                                                                    Cancel
                                                                                </button>
                                                                            </div>
                                                                        </div>
                                                                    ) : (
                                                                        <>
                                                                            <div className="flex flex-wrap items-center gap-2">
                                                                                <p className="text-sm font-semibold text-white">{view.name}</p>
                                                                                {activeScannerViewId === view.id && (
                                                                                    <span className="rounded-full border border-emerald-400/20 bg-emerald-400/10 px-2 py-0.5 text-[10px] font-bold uppercase tracking-[0.14em] text-emerald-300">
                                                                                        Active
                                                                                    </span>
                                                                                )}
                                                                            </div>
                                                                            <p className="mt-1 text-[11px] text-zinc-500">
                                                                                {view.quickFilter} · {view.sortMode} · {view.query || 'No search'}
                                                                            </p>
                                                                            {view.anchorSymbol && (
                                                                                <p className="mt-1 text-[10px] uppercase tracking-[0.14em] text-zinc-600">
                                                                                    Anchor {view.anchorSymbol}
                                                                                </p>
                                                                            )}
                                                                        </>
                                                                    )}
                                                                </div>
                                                                <div className="flex flex-wrap gap-2">
                                                                    {editingScannerViewId !== view.id && (
                                                                        <>
                                                                            <button
                                                                                onClick={() => handleApplyScannerView(view)}
                                                                                className="rounded-full border border-sky-400/20 bg-sky-400/10 px-3 py-1.5 text-[10px] font-bold uppercase tracking-[0.16em] text-sky-300 transition hover:bg-sky-400/20"
                                                                            >
                                                                                Apply
                                                                            </button>
                                                                            <button
                                                                                onClick={() => handleOverwriteScannerView(view)}
                                                                                className="rounded-full border border-emerald-400/20 bg-emerald-400/10 px-3 py-1.5 text-[10px] font-bold uppercase tracking-[0.16em] text-emerald-300 transition hover:bg-emerald-400/20"
                                                                            >
                                                                                Overwrite
                                                                            </button>
                                                                            <button
                                                                                onClick={() => handleStartEditScannerView(view)}
                                                                                className="rounded-full border border-amber-400/20 bg-amber-400/10 px-3 py-1.5 text-[10px] font-bold uppercase tracking-[0.16em] text-amber-300 transition hover:bg-amber-400/20"
                                                                            >
                                                                                Rename
                                                                            </button>
                                                                            <button
                                                                                onClick={() => handleShareScannerView(view)}
                                                                                className="rounded-full border border-white/10 bg-white/[0.03] px-3 py-1.5 text-[10px] font-bold uppercase tracking-[0.16em] text-zinc-300 transition hover:text-white"
                                                                            >
                                                                                Share
                                                                            </button>
                                                                            <button
                                                                                onClick={() => handleDeleteScannerView(view.id)}
                                                                                className="rounded-full border border-white/10 bg-white/[0.03] px-3 py-1.5 text-[10px] font-bold uppercase tracking-[0.16em] text-zinc-300 transition hover:text-white"
                                                                            >
                                                                                Remove
                                                                            </button>
                                                                        </>
                                                                    )}
                                                                </div>
                                                            </div>
                                                        ))
                                                    )}
                                                </div>
                                            </div>
                                            <div className="mt-4 flex flex-wrap gap-2">
                                                {universeFilterOptions.map((option) => (
                                                    <button
                                                        key={option.key}
                                                        onClick={() => setUniverseQuickFilter(option.key)}
                                                        className={`rounded-full border px-3 py-1.5 text-[10px] font-semibold uppercase tracking-[0.14em] transition ${universeQuickFilter === option.key
                                                            ? 'border-sky-400/25 bg-sky-400/10 text-sky-300'
                                                            : 'border-white/10 bg-white/[0.03] text-zinc-400 hover:text-white'}`}
                                                    >
                                                        {option.label}
                                                    </button>
                                                ))}
                                            </div>
                                            <div className="mt-3 flex flex-wrap gap-2">
                                                {universeSortOptions.map((option) => (
                                                    <button
                                                        key={option.key}
                                                        onClick={() => setUniverseSortMode(option.key)}
                                                        className={`rounded-full border px-3 py-1.5 text-[10px] font-semibold uppercase tracking-[0.14em] transition ${universeSortMode === option.key
                                                            ? 'border-amber-400/25 bg-amber-400/10 text-amber-300'
                                                            : 'border-white/10 bg-white/[0.03] text-zinc-400 hover:text-white'}`}
                                                    >
                                                        {option.label}
                                                    </button>
                                                ))}
                                            </div>
                                            <div className="mt-3 flex items-center justify-between gap-3 text-[11px] text-zinc-500">
                                                <span>
                                                    {universeFilterOptions.find((option) => option.key === universeQuickFilter)?.hint ?? 'Full universe'}
                                                </span>
                                                <span>{filteredInstruments.length} symbols</span>
                                            </div>
                                            <div className="mt-4 max-h-[520px] space-y-2 overflow-y-auto pr-1">
                                                {filteredInstruments.map((instrument) => (
                                                    <div
                                                        key={instrument.symbol}
                                                        className={`w-full rounded-2xl border p-3 text-left transition ${selectedSymbol === instrument.symbol
                                                            ? 'border-amber-400/35 bg-amber-400/10'
                                                            : 'border-white/10 bg-white/[0.02] hover:border-white/20'}`}
                                                    >
                                                        <div className="flex items-center justify-between gap-3">
                                                            <div>
                                                                <div className="flex items-center gap-2">
                                                                    <button onClick={() => setSelectedSymbol(instrument.symbol)} className="text-left">
                                                                        <p className="text-sm font-semibold text-white">{instrument.displayName}</p>
                                                                    </button>
                                                                    <button
                                                                        onClick={() => toggleFavoriteSymbol(instrument.symbol)}
                                                                        className={`rounded-full border px-2 py-0.5 text-[10px] font-bold uppercase tracking-[0.14em] transition ${favoriteSymbols.includes(instrument.symbol)
                                                                            ? 'border-amber-400/40 bg-amber-400/10 text-amber-300'
                                                                            : 'border-white/10 bg-white/[0.03] text-zinc-500 hover:text-white'}`}
                                                                    >
                                                                        ★
                                                                    </button>
                                                                </div>
                                                                <p className="text-[11px] font-mono text-zinc-500">{instrument.symbol}</p>
                                                                <div className="mt-2 flex flex-wrap gap-1.5">
                                                                    {[instrument.market, instrument.exchange, instrument.currency]
                                                                        .filter(Boolean)
                                                                        .map((chip) => (
                                                                            <span
                                                                                key={`${instrument.symbol}-${chip}`}
                                                                                className="rounded-full border border-white/10 bg-white/[0.03] px-2 py-0.5 text-[10px] font-semibold uppercase tracking-[0.14em] text-zinc-400"
                                                                            >
                                                                                {chip}
                                                                            </span>
                                                                        ))}
                                                                    {instrument.sector && (
                                                                        <span className="rounded-full border border-emerald-400/20 bg-emerald-400/10 px-2 py-0.5 text-[10px] font-semibold uppercase tracking-[0.14em] text-emerald-300">
                                                                            {instrument.sector}
                                                                        </span>
                                                                    )}
                                                                </div>
                                                            </div>
                                                            <div className="text-right">
                                                                <p className="font-mono text-sm text-white">{formatMoney(instrument.currentPrice)}</p>
                                                                <p className={`text-[11px] font-semibold ${instrument.changePercent24h >= 0 ? 'text-emerald-400' : 'text-red-400'}`}>
                                                                    {formatPercent(instrument.changePercent24h)}
                                                                </p>
                                                                {instrument.delayLabel && (
                                                                    <p className="mt-2 text-[10px] uppercase tracking-[0.14em] text-zinc-500">{instrument.delayLabel}</p>
                                                                )}
                                                            </div>
                                                        </div>
                                                    </div>
                                                ))}
                                            </div>
                                        </div>
                                    </div>

                                    <div className="rounded-2xl border border-white/10 bg-white/[0.03] p-4">
                                        <div className="flex flex-wrap items-start justify-between gap-4">
                                            <div>
                                                <p className="text-[11px] uppercase tracking-[0.24em] text-zinc-500">Snapshot</p>
                                                <p className="mt-1 text-sm text-zinc-400">
                                                    Current terminal state summary and quick-share tools for this exact setup.
                                                </p>
                                            </div>
                                            <div className="flex flex-wrap gap-2">
                                                <button
                                                    onClick={handleCopySnapshotSummary}
                                                    className="rounded-full border border-white/10 bg-white/[0.03] px-3 py-1.5 text-[10px] font-bold uppercase tracking-[0.16em] text-zinc-300 transition hover:text-white"
                                                >
                                                    Copy Summary
                                                </button>
                                                <button
                                                    onClick={handleCopyCurrentStateShareLink}
                                                    className="rounded-full border border-emerald-400/20 bg-emerald-400/10 px-3 py-1.5 text-[10px] font-bold uppercase tracking-[0.16em] text-emerald-300 transition hover:bg-emerald-400/20"
                                                >
                                                    Copy Share Link
                                                </button>
                                                <button
                                                    onClick={handleDownloadSnapshotJson}
                                                    className="rounded-full border border-sky-400/20 bg-sky-400/10 px-3 py-1.5 text-[10px] font-bold uppercase tracking-[0.16em] text-sky-300 transition hover:bg-sky-400/20"
                                                >
                                                    Download JSON
                                                </button>
                                                <button
                                                    onClick={handleDownloadSnapshotSvg}
                                                    className="rounded-full border border-amber-400/20 bg-amber-400/10 px-3 py-1.5 text-[10px] font-bold uppercase tracking-[0.16em] text-amber-300 transition hover:bg-amber-400/20"
                                                >
                                                    Download SVG Card
                                                </button>
                                            </div>
                                        </div>
                                        <div className="mt-4 flex flex-wrap gap-2">
                                            {selectedWatchlistMeta && (
                                                <span className="rounded-full border border-emerald-400/20 bg-emerald-400/10 px-3 py-1 text-[10px] font-semibold uppercase tracking-[0.14em] text-emerald-300">
                                                    {selectedWatchlistMeta.name}
                                                </span>
                                            )}
                                            {compareSymbols.length > 0 && (
                                                <span className="rounded-full border border-sky-400/20 bg-sky-400/10 px-3 py-1 text-[10px] font-semibold uppercase tracking-[0.14em] text-sky-300">
                                                    Compare {compareSymbols.join(', ')}
                                                </span>
                                            )}
                                            <span className="rounded-full border border-white/10 bg-white/[0.03] px-3 py-1 text-[10px] font-semibold uppercase tracking-[0.14em] text-zinc-300">
                                                Favorites {favoriteSymbols.length}
                                            </span>
                                            <span className="rounded-full border border-white/10 bg-white/[0.03] px-3 py-1 text-[10px] font-semibold uppercase tracking-[0.14em] text-zinc-300">
                                                Notes {chartNotes.length}
                                            </span>
                                        </div>
                                        <pre className="mt-4 whitespace-pre-wrap rounded-2xl border border-white/10 bg-black/35 px-4 py-3 text-xs leading-6 text-zinc-300">{currentSnapshotSummary}</pre>
                                        {topPinnedNotes.length > 0 && (
                                            <div className="mt-4 rounded-2xl border border-amber-400/15 bg-amber-400/5 px-4 py-3">
                                                <p className="text-[10px] font-bold uppercase tracking-[0.16em] text-amber-300">Pinned Note Preview</p>
                                                <div className="mt-2 space-y-2">
                                                    {topPinnedNotes.map((note) => (
                                                        <p key={note.id} className="text-xs text-zinc-300">
                                                            {note.body}
                                                        </p>
                                                    ))}
                                                </div>
                                            </div>
                                        )}
                                        {snapshotMessage && (
                                            <p className="mt-3 break-all text-xs text-zinc-500">{snapshotMessage}</p>
                                        )}
                                    </div>

                                    <div className="grid gap-4 md:grid-cols-6">
                                        <article className="rounded-2xl border border-white/10 bg-black/35 p-4 backdrop-blur-xl">
                                            <p className="text-[11px] uppercase tracking-[0.25em] text-zinc-500">Range</p>
                                            <p className="mt-2 text-xl font-semibold text-white">{selectedRange}</p>
                                            <p className="mt-1 text-xs text-zinc-500">Quick window switch.</p>
                                        </article>
                                        <article className="rounded-2xl border border-white/10 bg-black/35 p-4 backdrop-blur-xl">
                                            <p className="text-[11px] uppercase tracking-[0.25em] text-zinc-500">Interval</p>
                                            <p className="mt-2 text-xl font-semibold text-white">{selectedInterval}</p>
                                            <p className="mt-1 text-xs text-zinc-500">TradingView-style resolution control.</p>
                                        </article>
                                        <article className="rounded-2xl border border-white/10 bg-black/35 p-4 backdrop-blur-xl">
                                            <p className="text-[11px] uppercase tracking-[0.25em] text-zinc-500">Watchlists</p>
                                            <p className="mt-2 text-xl font-semibold text-white">{watchlists.length}</p>
                                            <p className="mt-1 text-xs text-zinc-500">Separate baskets by thesis or regime.</p>
                                        </article>
                                        <article className="rounded-2xl border border-white/10 bg-black/35 p-4 backdrop-blur-xl">
                                            <p className="text-[11px] uppercase tracking-[0.25em] text-zinc-500">Compare</p>
                                            <p className="mt-2 text-xl font-semibold text-white">{compareSymbols.length > 0 ? compareSymbols.join(', ') : 'Off'}</p>
                                            <p className="mt-1 text-xs text-zinc-500">
                                                {compareSymbols.length > 0
                                                    ? (compareVisible ? 'Normalized overlay line is visible.' : 'Overlay selected but hidden.')
                                                    : 'Normalized overlay line.'}
                                            </p>
                                            {compareSessionSummary.length > 0 && (
                                                <p className="mt-2 text-xs font-semibold text-zinc-300">
                                                    {compareSessionSummary.length} overlay{compareSessionSummary.length === 1 ? '' : 's'} active
                                                </p>
                                            )}
                                        </article>
                                        <article className="rounded-2xl border border-white/10 bg-black/35 p-4 backdrop-blur-xl">
                                            <p className="text-[11px] uppercase tracking-[0.25em] text-zinc-500">Draw Mode</p>
                                            <p className="mt-2 text-xl font-semibold text-white">{drawingMode === 'none' ? 'Off' : drawingMode}</p>
                                            <p className="mt-1 text-xs text-zinc-500">Horizontal levels and trend lines.</p>
                                        </article>
                                        <article className="rounded-2xl border border-white/10 bg-black/35 p-4 backdrop-blur-xl">
                                            <p className="text-[11px] uppercase tracking-[0.25em] text-zinc-500">History</p>
                                            <p className="mt-2 text-xl font-semibold text-white">{candles.length}</p>
                                            <p className="mt-1 text-xs text-zinc-500">
                                                {selectedRange === 'ALL'
                                                    ? (hasMoreHistory ? 'Scroll left to load older candles.' : 'Loaded oldest available chunk.')
                                                    : 'Window sized to selected range.'}
                                            </p>
                                            <p className="mt-2 text-[11px] font-mono text-zinc-600">{loadedHistoryLabel}</p>
                                        </article>
                                    </div>

                                    {selectedRange === 'ALL' && (
                                        <div className="flex flex-wrap items-center justify-between gap-3 rounded-2xl border border-white/10 bg-white/[0.03] px-4 py-3">
                                            <div>
                                                <p className="text-xs font-semibold uppercase tracking-[0.22em] text-zinc-400">All History Mode</p>
                                                <p className="mt-1 text-sm text-zinc-500">
                                                    Finer intervals load progressively. Use `Load older` for deeper history when you need more candles.
                                                </p>
                                            </div>
                                            <button
                                                onClick={() => fetchCandles(selectedSymbol, selectedRange, selectedInterval, 'prepend')}
                                                disabled={loadingMoreHistory || !hasMoreHistory}
                                                className="rounded-full border border-amber-400/30 bg-amber-400/10 px-4 py-2 text-xs font-bold uppercase tracking-[0.2em] text-amber-300 transition hover:bg-amber-400/20 disabled:cursor-not-allowed disabled:opacity-40"
                                            >
                                                {loadingMoreHistory ? 'Loading...' : (hasMoreHistory ? 'Load Older' : 'No More Data')}
                                            </button>
                                        </div>
                                    )}

                                    <div className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_320px]">
                                        <div className="rounded-2xl border border-white/10 bg-white/[0.03] p-4">
                                            <div className="flex items-start justify-between gap-3">
                                                <div>
                                                    <p className="text-[11px] uppercase tracking-[0.24em] text-zinc-500">Chart Notes</p>
                                                    <p className="mt-1 text-sm text-zinc-400">
                                                        Symbol-scoped quick notes for {selectedSymbol}. Stored on your account and available across sessions.
                                                    </p>
                                                </div>
                                                <span className="rounded-full border border-white/10 bg-white/[0.03] px-3 py-1 text-[10px] font-semibold uppercase tracking-[0.16em] text-zinc-400">
                                                    {chartNotes.length} saved
                                                </span>
                                            </div>
                                            <form onSubmit={handleAddChartNote} className="mt-4 space-y-3">
                                                <textarea
                                                    value={chartNoteDraft}
                                                    onChange={(event) => setChartNoteDraft(event.target.value)}
                                                    placeholder={`Write a quick note for ${selectedSymbol}...`}
                                                    className="min-h-[96px] w-full rounded-2xl border border-zinc-700 bg-black px-4 py-3 text-sm text-white outline-none focus:border-amber-400"
                                                />
                                                <div className="flex items-center justify-between gap-3">
                                                    <p className="text-xs text-zinc-500">
                                                        Notes follow the symbol across refresh, terminal revisits, and account sessions.
                                                    </p>
                                                    <button
                                                        type="submit"
                                                        className="rounded-full border border-amber-400/30 bg-amber-400/10 px-4 py-2 text-[11px] font-bold uppercase tracking-[0.18em] text-amber-300 transition hover:bg-amber-400/20"
                                                    >
                                                        Save Note
                                                    </button>
                                                </div>
                                            </form>
                                            <div className="mt-4">
                                                <input
                                                    type="text"
                                                    value={chartNoteQuery}
                                                    onChange={(event) => setChartNoteQuery(event.target.value)}
                                                    placeholder="Search saved notes..."
                                                    className="w-full rounded-2xl border border-zinc-700 bg-black px-4 py-3 text-sm text-white outline-none focus:border-amber-400"
                                                />
                                                <div className="mt-3 flex flex-wrap gap-2">
                                                    {(['ALL', 'PINNED', 'UNPINNED'] as ChartNoteFilter[]).map((filter) => (
                                                        <button
                                                            key={filter}
                                                            onClick={() => setChartNoteFilter(filter)}
                                                            className={`rounded-full border px-3 py-1.5 text-[10px] font-bold uppercase tracking-[0.16em] transition ${
                                                                chartNoteFilter === filter
                                                                    ? 'border-amber-400/30 bg-amber-400/10 text-amber-300'
                                                                    : 'border-white/10 bg-white/[0.03] text-zinc-400 hover:text-white'
                                                            }`}
                                                        >
                                                            {filter === 'ALL' ? 'All' : filter === 'PINNED' ? 'Pinned' : 'Unpinned'}
                                                        </button>
                                                    ))}
                                                </div>
                                            </div>
                                        </div>

                                        <div className="rounded-2xl border border-white/10 bg-white/[0.03] p-4">
                                            <div className="flex items-start justify-between gap-3">
                                                <div>
                                                    <p className="text-[11px] uppercase tracking-[0.24em] text-zinc-500">Recent Alert Activity</p>
                                                    <p className="mt-1 text-sm text-zinc-400">
                                                        {selectedWatchlistItem
                                                            ? `Triggered alert events for ${selectedWatchlistItem.symbol} in the selected basket.`
                                                            : 'Add the current symbol to the selected watchlist to collect trigger history here.'}
                                                    </p>
                                                </div>
                                                <div className="flex items-center gap-2">
                                                    <select
                                                        value={alertHistoryFilter}
                                                        onChange={(event) => setAlertHistoryFilter(event.target.value as AlertHistoryFilter)}
                                                        className="rounded-full border border-white/10 bg-black px-3 py-2 text-[11px] font-semibold uppercase tracking-[0.16em] text-zinc-300 outline-none"
                                                    >
                                                        <option value="ALL">All</option>
                                                        <option value="ABOVE">Above</option>
                                                        <option value="BELOW">Below</option>
                                                    </select>
                                                    <button
                                                        onClick={exportFilteredAlertHistory}
                                                        disabled={filteredAlertHistory.length === 0}
                                                        className="rounded-full border border-white/10 bg-white/[0.03] px-3 py-2 text-[11px] font-semibold uppercase tracking-[0.16em] text-zinc-300 transition hover:text-white disabled:cursor-not-allowed disabled:opacity-40"
                                                    >
                                                        Export CSV
                                                    </button>
                                                </div>
                                            </div>
                                            <div className="mt-3 flex flex-wrap gap-2">
                                                {(['24H', '7D', '30D', 'ALL'] as AlertHistoryWindow[]).map((window) => (
                                                    <button
                                                        key={window}
                                                        onClick={() => setAlertHistoryWindow(window)}
                                                        className={`rounded-full border px-3 py-1.5 text-[10px] font-bold uppercase tracking-[0.16em] transition ${
                                                            alertHistoryWindow === window
                                                                ? 'border-amber-400/30 bg-amber-400/10 text-amber-300'
                                                                : 'border-white/10 bg-white/[0.03] text-zinc-400 hover:text-white'
                                                        }`}
                                                    >
                                                        {window}
                                                    </button>
                                                ))}
                                            </div>
                                            <div className="mt-4 space-y-2">
                                                {!selectedWatchlistItem ? (
                                                    <div className="rounded-2xl border border-dashed border-white/10 px-4 py-5 text-sm text-zinc-500">
                                                        Alert history becomes available when the current symbol exists in the active watchlist.
                                                    </div>
                                                ) : alertHistoryLoading ? (
                                                    <div className="rounded-2xl border border-white/10 px-4 py-5 text-sm text-zinc-500">
                                                        Loading alert history...
                                                    </div>
                                                ) : filteredAlertHistory.length === 0 ? (
                                                    <div className="rounded-2xl border border-dashed border-white/10 px-4 py-5 text-sm text-zinc-500">
                                                        {alertHistory.length === 0
                                                            ? 'No triggered alerts yet for this symbol.'
                                                            : 'No alert events match the selected filter.'}
                                                    </div>
                                                ) : (
                                                    filteredAlertHistory.map((entry) => (
                                                        <div key={entry.id} className="rounded-2xl border border-white/10 bg-black/35 px-4 py-3">
                                                            <div className="flex items-start justify-between gap-3">
                                                                <div>
                                                                    <p className={`text-sm font-semibold ${entry.direction === 'ABOVE' ? 'text-emerald-300' : 'text-red-300'}`}>
                                                                        {entry.direction === 'ABOVE' ? 'Above trigger' : 'Below trigger'}
                                                                    </p>
                                                                    <p className="mt-1 text-xs text-zinc-400">{entry.message}</p>
                                                                </div>
                                                                <span className="text-[10px] uppercase tracking-[0.16em] text-zinc-500">
                                                                    {new Date(entry.triggeredAt).toLocaleString()}
                                                                </span>
                                                            </div>
                                                            <div className="mt-3 flex flex-wrap gap-2 text-[11px]">
                                                                <span className="rounded-full border border-white/10 bg-white/[0.03] px-3 py-1 text-zinc-300">
                                                                    Threshold {formatMoney(Number(entry.thresholdPrice))}
                                                                </span>
                                                                <span className="rounded-full border border-white/10 bg-white/[0.03] px-3 py-1 text-zinc-300">
                                                                    Triggered {formatMoney(Number(entry.triggeredPrice))}
                                                                </span>
                                                            </div>
                                                        </div>
                                                    ))
                                                )}
                                            </div>
                                        </div>
                                    </div>

                                    {chartNotes.length > 0 && (
                                        <div className="rounded-2xl border border-white/10 bg-white/[0.03] p-4">
                                            <div className="flex items-center justify-between gap-3">
                                                <div>
                                                    <p className="text-[11px] uppercase tracking-[0.24em] text-zinc-500">Saved Notes</p>
                                                    <p className="mt-1 text-sm text-zinc-400">Reusable terminal notes for this exact symbol. Pinned notes stay on top.</p>
                                                </div>
                                                <span className="text-xs text-zinc-500">
                                                    {filteredChartNotes.length}/{chartNotes.length} · {selectedSymbol}
                                                </span>
                                            </div>
                                            <div className="mt-4 space-y-2">
                                                {filteredChartNotes.length === 0 ? (
                                                    <div className="rounded-2xl border border-dashed border-white/10 px-4 py-5 text-sm text-zinc-500">
                                                        No saved notes match the current search.
                                                    </div>
                                                ) : filteredChartNotes.map((note) => (
                                                    <div key={note.id} className={`rounded-2xl border px-4 py-3 ${note.pinned ? 'border-amber-400/25 bg-amber-400/5' : 'border-white/10 bg-black/35'}`}>
                                                        <div className="flex items-start justify-between gap-3">
                                                            {editingNoteId === note.id ? (
                                                                <div className="w-full space-y-3">
                                                                    <textarea
                                                                        value={editingNoteDraft}
                                                                        onChange={(event) => setEditingNoteDraft(event.target.value)}
                                                                        className="min-h-[88px] w-full rounded-2xl border border-zinc-700 bg-black px-4 py-3 text-sm text-white outline-none focus:border-amber-400"
                                                                    />
                                                                    <div className="flex gap-2">
                                                                        <button
                                                                            onClick={() => handleSaveEditChartNote(note)}
                                                                            className="rounded-full border border-emerald-400/30 bg-emerald-400/10 px-3 py-1.5 text-[11px] font-bold uppercase tracking-[0.16em] text-emerald-300 transition hover:bg-emerald-400/20"
                                                                        >
                                                                            Save
                                                                        </button>
                                                                        <button
                                                                            onClick={handleCancelEditChartNote}
                                                                            className="rounded-full border border-white/10 bg-white/[0.03] px-3 py-1.5 text-[11px] font-bold uppercase tracking-[0.16em] text-zinc-400 transition hover:text-white"
                                                                        >
                                                                            Cancel
                                                                        </button>
                                                                    </div>
                                                                </div>
                                                            ) : (
                                                                <>
                                                                    <div className="space-y-2">
                                                                        {note.pinned && (
                                                                            <span className="inline-flex rounded-full border border-amber-400/25 bg-amber-400/10 px-2 py-1 text-[10px] font-bold uppercase tracking-[0.16em] text-amber-300">
                                                                                Pinned
                                                                            </span>
                                                                        )}
                                                                        <p className="whitespace-pre-wrap text-sm text-zinc-200">{note.body}</p>
                                                                    </div>
                                                                    <div className="flex shrink-0 gap-3">
                                                                        <button
                                                                            onClick={() => handleTogglePinChartNote(note)}
                                                                            className={`text-xs transition ${note.pinned ? 'text-amber-300 hover:text-amber-200' : 'text-zinc-500 hover:text-amber-300'}`}
                                                                        >
                                                                            {note.pinned ? 'Unpin' : 'Pin'}
                                                                        </button>
                                                                        <button
                                                                            onClick={() => handleStartEditChartNote(note)}
                                                                            className="text-xs text-zinc-500 transition hover:text-amber-300"
                                                                        >
                                                                            Edit
                                                                        </button>
                                                                        <button
                                                                            onClick={() => handleDeleteChartNote(note.id)}
                                                                            className="text-xs text-zinc-500 transition hover:text-red-400"
                                                                        >
                                                                            Remove
                                                                        </button>
                                                                    </div>
                                                                </>
                                                            )}
                                                        </div>
                                                        <p className="mt-3 text-[10px] uppercase tracking-[0.16em] text-zinc-500">
                                                            {new Date(note.createdAt).toLocaleString()}
                                                        </p>
                                                    </div>
                                                ))}
                                            </div>
                                        </div>
                                    )}
                                </div>
                            </div>
                        </section>

                        <aside className="rounded-3xl border border-white/10 bg-black/45 p-5 shadow-[0_0_60px_rgba(0,0,0,0.4)] backdrop-blur-xl">
                            <div className="mb-5 flex items-start justify-between gap-4">
                                <div>
                                    <p className="text-[11px] uppercase tracking-[0.3em] text-zinc-500">Watch Basket</p>
                                    <h2 className="mt-2 text-xl font-bold text-white">Right Rail</h2>
                                </div>
                                <button
                                    onClick={() => setShowAddForm(!showAddForm)}
                                    className="rounded-full border border-green-500/20 bg-green-500/10 px-3 py-2 text-[11px] font-bold uppercase tracking-[0.2em] text-green-400 transition hover:bg-green-500/20"
                                >
                                    + Add
                                </button>
                            </div>

                            <div className="space-y-2">
                                {watchlists.length === 0 ? (
                                    <p className="text-sm italic text-zinc-500">No watchlists yet. Create one.</p>
                                ) : (
                                    watchlists.map((watchlist) => (
                                        <div
                                            key={watchlist.id}
                                            className={`group flex cursor-pointer items-center justify-between rounded-2xl border p-3 transition ${selectedWatchlist === watchlist.id
                                                ? 'border-amber-400/30 bg-amber-400/10'
                                                : 'border-white/10 hover:border-white/20 hover:bg-white/[0.03]'}`}
                                            onClick={() => setSelectedWatchlist(watchlist.id)}
                                        >
                                            <div>
                                                <p className={`text-sm font-semibold ${selectedWatchlist === watchlist.id ? 'text-amber-300' : 'text-zinc-200'}`}>{watchlist.name}</p>
                                                <p className="text-[10px] font-mono text-zinc-500">{new Date(watchlist.createdAt).toLocaleDateString()}</p>
                                            </div>
                                            <button
                                                onClick={(event) => {
                                                    event.stopPropagation();
                                                    handleDeleteWatchlist(watchlist.id);
                                                }}
                                                className="text-xs text-red-500 opacity-0 transition group-hover:opacity-100 hover:text-red-400"
                                            >
                                                X
                                            </button>
                                        </div>
                                    ))
                                )}
                            </div>

                            {showAddForm && selectedWatchlist && (
                                <form onSubmit={handleAddItem} className="mt-5 space-y-3 rounded-2xl border border-white/10 bg-zinc-950/60 p-4">
                                    <select
                                        value={addSymbol}
                                        onChange={(event) => setAddSymbol(event.target.value)}
                                        className="w-full rounded-xl border border-zinc-700 bg-black px-3 py-2 text-sm text-white outline-none focus:border-amber-400"
                                    >
                                        {availableSymbols.map((instrument) => (
                                            <option key={instrument.symbol} value={instrument.symbol}>
                                                {instrument.displayName} ({instrument.symbol})
                                            </option>
                                        ))}
                                    </select>
                                    <div className="grid grid-cols-2 gap-3">
                                        <input
                                            type="number"
                                            step="any"
                                            value={addAlertAbove}
                                            onChange={(event) => setAddAlertAbove(event.target.value)}
                                            placeholder="Alert above"
                                            className="rounded-xl border border-zinc-700 bg-black px-3 py-2 text-sm text-white outline-none focus:border-amber-400"
                                        />
                                        <input
                                            type="number"
                                            step="any"
                                            value={addAlertBelow}
                                            onChange={(event) => setAddAlertBelow(event.target.value)}
                                            placeholder="Alert below"
                                            className="rounded-xl border border-zinc-700 bg-black px-3 py-2 text-sm text-white outline-none focus:border-amber-400"
                                        />
                                    </div>
                                    <input
                                        type="text"
                                        value={addNotes}
                                        onChange={(event) => setAddNotes(event.target.value)}
                                        placeholder="Why are you tracking it?"
                                        className="w-full rounded-xl border border-zinc-700 bg-black px-3 py-2 text-sm text-white outline-none focus:border-amber-400"
                                    />
                                    <button type="submit" className="w-full rounded-xl bg-amber-500 px-4 py-2 text-sm font-bold text-black transition hover:bg-amber-400">
                                        Add to basket
                                    </button>
                                </form>
                            )}

                            <div className="mt-6 space-y-2">
                                {selectedWatchlist ? (
                                    enrichedItems.length > 0 ? (
                                        enrichedItems.map((item) => (
                                            <button
                                                key={item.id}
                                                onClick={() => setSelectedSymbol(item.symbol)}
                                                className={`w-full rounded-2xl border p-4 text-left transition ${selectedSymbol === item.symbol
                                                    ? 'border-amber-400/35 bg-amber-400/10'
                                                    : 'border-white/10 bg-white/[0.02] hover:border-white/20'}`}
                                            >
                                                <div className="flex items-start justify-between gap-3">
                                                    <div>
                                                        <p className="text-sm font-bold text-white">{item.symbol}</p>
                                                        <p className="mt-1 text-xs text-zinc-400">{instrumentMap.get(item.symbol)?.displayName ?? item.notes ?? 'No note yet'}</p>
                                                        <p className="mt-1 text-[10px] uppercase tracking-[0.14em] text-zinc-500">
                                                            {instrumentMap.get(item.symbol)?.exchange ?? 'WATCHLIST'}
                                                            {instrumentMap.get(item.symbol)?.delayLabel ? ` · ${instrumentMap.get(item.symbol)?.delayLabel}` : ''}
                                                        </p>
                                                    </div>
                                                    <button
                                                        onClick={(event) => {
                                                            event.stopPropagation();
                                                            handleRemoveItem(item.id);
                                                        }}
                                                        className="text-xs text-red-500/70 hover:text-red-400"
                                                    >
                                                        Remove
                                                    </button>
                                                </div>
                                                <div className="mt-4 flex items-end justify-between gap-3">
                                                    <div>
                                                        <p className="font-mono text-lg font-bold text-white">{formatMoney(Number(item.currentPrice))}</p>
                                                        <p className={`text-xs font-semibold ${Number(item.changePercent24h) >= 0 ? 'text-emerald-400' : 'text-red-400'}`}>
                                                            {formatPercent(Number(item.changePercent24h))} today
                                                        </p>
                                                    </div>
                                                    <div className="text-right text-[10px] uppercase tracking-[0.2em] text-zinc-500">
                                                        {item.alertPriceAbove ? <p>UP {item.alertPriceAbove}</p> : <p>UP -</p>}
                                                        {item.alertPriceBelow ? <p className="mt-1">DOWN {item.alertPriceBelow}</p> : <p className="mt-1">DOWN -</p>}
                                                    </div>
                                                </div>
                                            </button>
                                        ))
                                    ) : (
                                        <div className="rounded-2xl border border-dashed border-white/10 p-8 text-center">
                                            <p className="text-zinc-500">This basket is empty.</p>
                                            <p className="mt-1 text-xs text-zinc-600">Add one of the supported instruments and it will appear here.</p>
                                        </div>
                                    )
                                ) : (
                                    <div className="rounded-2xl border border-dashed border-white/10 p-8 text-center">
                                        <p className="text-zinc-500">Select or create a watchlist.</p>
                                    </div>
                                )}
                            </div>

                            {loadingMoreHistory && (
                                <div className="mt-4 rounded-2xl border border-white/10 bg-white/[0.03] px-4 py-3 text-xs text-zinc-400">
                                    Loading older candles...
                                </div>
                            )}
                        </aside>
                    </div>
                )}
            </div>
        </div>
    );
}
