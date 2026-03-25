'use client';

import Link from 'next/link';
import { usePathname, useRouter, useSearchParams } from 'next/navigation';
import { ChangeEvent, FormEvent, useEffect, useMemo, useRef, useState } from 'react';
import { apiFetch } from '../../../lib/api-client';
import { extractContent } from '../../../lib/page';

type Tab = 'OVERVIEW' | 'BOTS' | 'RUNS';
type RunDetailTab = 'SUMMARY' | 'FILLS' | 'EVENTS' | 'EQUITY' | 'RECONCILIATION' | 'RAW';
type BotStatus = 'DRAFT' | 'READY' | 'ARCHIVED';
type RunMode = 'BACKTEST' | 'FORWARD_TEST';
type BotBoardSort = 'AVG_RETURN' | 'AVG_NET_PNL' | 'TOTAL_RUNS' | 'AVG_WIN_RATE' | 'AVG_PROFIT_FACTOR' | 'LATEST_REQUESTED_AT';
type BotBoardRunMode = 'ALL' | RunMode;
type BotBoardLookback = 'ALL' | '7' | '30' | '90';
type BotBoardPresetId = 'ALL_TIME_EDGE' | 'BACKTEST_QUALITY' | 'LIVE_FORWARD' | 'RUN_DENSITY';
type BotBoardSavedView = {
    id: string;
    name: string;
    sortBy: BotBoardSort;
    direction: 'ASC' | 'DESC';
    runMode: BotBoardRunMode;
    lookbackDays: BotBoardLookback;
    updatedAt: string;
};
type RuleOperator = 'all' | 'any';
type RuleTemplateId =
    | 'price_above_ma'
    | 'price_below_ma'
    | 'rsi_above'
    | 'rsi_below'
    | 'breakout_high'
    | 'breakdown_low'
    | 'volume_above_sma'
    | 'stop_loss_hit'
    | 'take_profit_hit'
    | 'custom';
type RuleBuilderScope = 'ENTRY' | 'EXIT';
type RuleTemplate = {
    id: RuleTemplateId;
    label: string;
    description: string;
    parameterKind: 'window' | 'threshold' | 'none' | 'custom';
    defaultValue: string;
    scope: RuleBuilderScope | 'BOTH';
};
type RuleDraft = {
    templateId: RuleTemplateId;
    value: string;
    token: string;
};
type ParsedRuleSet = {
    operator: RuleOperator;
    rules: RuleDraft[];
    error: string | null;
};

type PortfolioOption = { id: string; name: string; balance: number; visibility?: 'PUBLIC' | 'PRIVATE' };
type StrategyBot = {
    id: string; linkedPortfolioId: string; name: string; description?: string; status: BotStatus;
    market: string; symbol: string; timeframe: string; entryRules: unknown; exitRules: unknown;
    maxPositionSizePercent?: number; stopLossPercent?: number; takeProfitPercent?: number; cooldownMinutes?: number; updatedAt: string;
};
type StrategyBotRun = {
    id: string; runMode: RunMode; status: 'QUEUED' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'CANCELLED'; requestedAt: string;
    fromDate?: string; toDate?: string; startedAt?: string | null; completedAt?: string | null; errorMessage?: string | null;
    summary?: { phase?: string; status?: string; previousStatus?: string; cancelledAt?: string; executionEngineReady?: boolean; unsupportedRules?: string[]; warnings?: string[]; supportedFeatures?: string[]; fills?: unknown[]; equityCurve?: unknown[]; endingEquity?: number; netPnl?: number; returnPercent?: number; tradeCount?: number; eventCount?: number; maxDrawdownPercent?: number; avgWinPnl?: number | null; avgLossPnl?: number | null; profitFactor?: number | null; expectancyPerTrade?: number | null; bestTradePnl?: number | null; worstTradePnl?: number | null; avgHoldHours?: number | null; maxHoldHours?: number | null; timeInMarketPercent?: number | null; avgExposurePercent?: number | null; entryReasonCounts?: Record<string, number>; exitReasonCounts?: Record<string, number>; linkedPortfolioId?: string; linkedPortfolioName?: string; linkedPortfolioBalance?: number | null; linkedPortfolioReferenceEquity?: number | null; linkedPortfolioDrift?: number | null; linkedPortfolioDriftPercent?: number | null; linkedPortfolioReconciliationBaseline?: string; linkedPortfolioAligned?: boolean; lastEvaluatedOpenTime?: number | null; positionOpen?: boolean; openQuantity?: number | null; openEntryPrice?: number | null } | null;
};
type StrategyBotRunEvent = {
    id: string; sequenceNo: number; openTime: number; phase: string; action: string;
    closePrice: number; cashBalance: number; positionQuantity: number; equity: number;
    matchedRules: string[]; details?: Record<string, unknown> | null;
};
type StrategyBotRunFill = {
    id: string; sequenceNo: number; side: 'ENTRY' | 'EXIT'; openTime: number;
    price: number; quantity: number; realizedPnl: number; matchedRules: string[];
};
type StrategyBotRunEquityPoint = {
    id: string; sequenceNo: number; openTime: number; closePrice: number; equity: number;
};
type StrategyBotRunScorecard = {
    id: string; runMode: RunMode; status: 'QUEUED' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'CANCELLED';
    requestedAt: string; completedAt?: string | null; returnPercent?: number | null; netPnl?: number | null;
    maxDrawdownPercent?: number | null; winRate?: number | null; tradeCount?: number | null; profitFactor?: number | null;
    expectancyPerTrade?: number | null; timeInMarketPercent?: number | null; linkedPortfolioAligned?: boolean | null;
    executionEngineReady?: boolean | null; lastEvaluatedOpenTime?: number | null; errorMessage?: string | null;
};
type StrategyBotAnalytics = {
    strategyBotId: string; totalRuns: number; backtestRuns: number; forwardTestRuns: number; completedRuns: number; runningRuns: number; failedRuns: number; cancelledRuns: number;
    compilerReadyRuns: number; positiveCompletedRuns: number; negativeCompletedRuns: number; totalSimulatedTrades: number;
    avgReturnPercent?: number | null; avgNetPnl?: number | null; avgMaxDrawdownPercent?: number | null; avgWinRate?: number | null; avgTradeCount?: number | null;
    avgProfitFactor?: number | null; avgExpectancyPerTrade?: number | null;
    bestRun?: StrategyBotRunScorecard | null; worstRun?: StrategyBotRunScorecard | null; latestCompletedRun?: StrategyBotRunScorecard | null; activeForwardRun?: StrategyBotRunScorecard | null;
    entryDriverTotals: Record<string, number>; exitDriverTotals: Record<string, number>; recentScorecards: StrategyBotRunScorecard[];
};
type StrategyBotBoardEntry = {
    strategyBotId: string; name: string; status: BotStatus; market: string; symbol: string; timeframe: string; linkedPortfolioId?: string | null;
    totalRuns: number; completedRuns: number; runningRuns: number; failedRuns: number; cancelledRuns: number; totalSimulatedTrades: number; positiveCompletedRuns: number; negativeCompletedRuns: number;
    avgReturnPercent?: number | null; avgNetPnl?: number | null; avgMaxDrawdownPercent?: number | null; avgWinRate?: number | null; avgProfitFactor?: number | null; avgExpectancyPerTrade?: number | null;
    latestRequestedAt?: string | null; bestRun?: StrategyBotRunScorecard | null; latestCompletedRun?: StrategyBotRunScorecard | null; activeForwardRun?: StrategyBotRunScorecard | null;
};
type StrategyBotRunReconciliationPlan = {
    runId: string; strategyBotId: string; linkedPortfolioId: string; linkedPortfolioName: string; symbol: string; runStatus: string;
    targetPositionOpen: boolean; targetQuantity: number; targetAveragePrice: number; targetLastPrice: number; targetCashBalance: number; targetEquity: number;
    currentCashBalance: number; currentQuantity: number; currentAveragePrice: number; quantityDelta: number; cashDelta: number;
    cashAligned: boolean; quantityAligned: boolean; portfolioAligned: boolean; extraSymbolCount: number; warnings: string[];
};
type SlicePageMeta = {
    pageNumber: number;
    totalPages: number;
    totalElements: number;
};

const defaultEntryRules = JSON.stringify({ all: ['price_above_ma_20', 'rsi_below_35'] }, null, 2);
const defaultExitRules = JSON.stringify({ any: ['take_profit_hit', 'stop_loss_hit'] }, null, 2);
const BOT_BOARD_VIEW_STORAGE_KEY = 'dashboard.strategy-bot.board-views.v1';
const emptyRuleSetJson = JSON.stringify({ all: [] }, null, 2);
const ruleTemplates: RuleTemplate[] = [
    { id: 'price_above_ma', label: 'Price > MA', description: 'Close price crosses above a moving average window.', parameterKind: 'window', defaultValue: '20', scope: 'BOTH' },
    { id: 'price_below_ma', label: 'Price < MA', description: 'Close price drops below a moving average window.', parameterKind: 'window', defaultValue: '20', scope: 'BOTH' },
    { id: 'rsi_above', label: 'RSI Above', description: 'Momentum stays above a threshold.', parameterKind: 'threshold', defaultValue: '55', scope: 'BOTH' },
    { id: 'rsi_below', label: 'RSI Below', description: 'Momentum pulls back below a threshold.', parameterKind: 'threshold', defaultValue: '35', scope: 'BOTH' },
    { id: 'breakout_high', label: 'Breakout High', description: 'Close breaks above the highest high of a lookback window.', parameterKind: 'window', defaultValue: '20', scope: 'BOTH' },
    { id: 'breakdown_low', label: 'Breakdown Low', description: 'Close breaks below the lowest low of a lookback window.', parameterKind: 'window', defaultValue: '20', scope: 'BOTH' },
    { id: 'volume_above_sma', label: 'Volume > SMA', description: 'Volume expands above its moving average.', parameterKind: 'window', defaultValue: '20', scope: 'BOTH' },
    { id: 'stop_loss_hit', label: 'Stop Loss', description: 'Risk exit uses the bot stop-loss setting.', parameterKind: 'none', defaultValue: '', scope: 'EXIT' },
    { id: 'take_profit_hit', label: 'Take Profit', description: 'Risk exit uses the bot take-profit setting.', parameterKind: 'none', defaultValue: '', scope: 'EXIT' },
    { id: 'custom', label: 'Custom Token', description: 'Keep a raw token when the builder library is not enough.', parameterKind: 'custom', defaultValue: '', scope: 'BOTH' },
];
const boardComparisonPresets: Array<{
    id: BotBoardPresetId;
    label: string;
    description: string;
    sortBy: BotBoardSort;
    direction: 'ASC' | 'DESC';
    runMode: BotBoardRunMode;
    lookbackDays: BotBoardLookback;
}> = [
    {
        id: 'ALL_TIME_EDGE',
        label: 'All-Time Edge',
        description: 'Global return leaders across all recorded runs.',
        sortBy: 'AVG_RETURN',
        direction: 'DESC',
        runMode: 'ALL',
        lookbackDays: 'ALL',
    },
    {
        id: 'BACKTEST_QUALITY',
        label: 'Backtest Quality',
        description: 'Backtests ranked by payoff quality over the last 90 days.',
        sortBy: 'AVG_PROFIT_FACTOR',
        direction: 'DESC',
        runMode: 'BACKTEST',
        lookbackDays: '90',
    },
    {
        id: 'LIVE_FORWARD',
        label: 'Live Forward',
        description: 'Fresh forward-test activity ordered by latest live evaluation.',
        sortBy: 'LATEST_REQUESTED_AT',
        direction: 'DESC',
        runMode: 'FORWARD_TEST',
        lookbackDays: '30',
    },
    {
        id: 'RUN_DENSITY',
        label: 'Run Density',
        description: 'Most exercised bots over the recent 90-day window.',
        sortBy: 'TOTAL_RUNS',
        direction: 'DESC',
        runMode: 'ALL',
        lookbackDays: '90',
    },
];

function numberOrUndefined(value: string) {
    if (!value.trim()) return undefined;
    const parsed = Number(value);
    if (Number.isNaN(parsed)) throw new Error(`Invalid numeric value: ${value}`);
    return parsed;
}

function fmtDate(value?: string | null) { return value ? new Date(value).toLocaleString() : 'N/A'; }
function fmtEpoch(value?: number | null) { return value === undefined || value === null ? 'N/A' : new Date(value).toLocaleString(); }
function fmtCurrency(value?: number | null) { return value === undefined || value === null || Number.isNaN(value) ? 'N/A' : `$${value.toLocaleString(undefined, { maximumFractionDigits: 2 })}`; }
function fmtPercent(value?: number | null) { return value === undefined || value === null || Number.isNaN(value) ? 'N/A' : `${value.toFixed(2)}%`; }
function pretty(value: unknown) { try { return JSON.stringify(value ?? {}, null, 2); } catch { return '{}'; } }
function err(error: unknown) { return error instanceof Error ? error.message : 'Unexpected request failure'; }
function downloadNameFromDisposition(disposition: string | null, fallback: string) {
    const match = disposition?.match(/filename="?([^"]+)"?/i);
    return match?.[1] ?? fallback;
}

function parseTab(value: string | null): Tab {
    return value === 'BOTS' || value === 'RUNS' ? value : 'OVERVIEW';
}

function parseBoardSort(value: string | null): BotBoardSort {
    return value === 'AVG_NET_PNL'
        || value === 'TOTAL_RUNS'
        || value === 'AVG_WIN_RATE'
        || value === 'AVG_PROFIT_FACTOR'
        || value === 'LATEST_REQUESTED_AT'
        ? value
        : 'AVG_RETURN';
}

function parseBoardDirection(value: string | null): 'ASC' | 'DESC' {
    return value === 'ASC' ? 'ASC' : 'DESC';
}

function parseBoardRunMode(value: string | null): BotBoardRunMode {
    return value === 'BACKTEST' || value === 'FORWARD_TEST' ? value : 'ALL';
}

function parseBoardLookback(value: string | null): BotBoardLookback {
    return value === '7' || value === '30' || value === '90' ? value : 'ALL';
}

function getRuleTemplate(templateId: RuleTemplateId) {
    return ruleTemplates.find((template) => template.id === templateId) ?? ruleTemplates[ruleTemplates.length - 1];
}

function ruleTemplatesForScope(scope: RuleBuilderScope) {
    return ruleTemplates.filter((template) => template.scope === 'BOTH' || template.scope === scope);
}

function normalizeRuleNumber(raw: string, fallback: string, integerOnly: boolean) {
    const trimmed = raw.trim();
    if (!trimmed) return fallback;
    const parsed = Number(trimmed);
    if (!Number.isFinite(parsed) || parsed <= 0) return fallback;
    return integerOnly ? String(Math.max(1, Math.round(parsed))) : String(parsed);
}

function buildRuleToken(templateId: RuleTemplateId, rawValue: string) {
    const template = getRuleTemplate(templateId);
    switch (templateId) {
        case 'price_above_ma':
            return `price_above_ma_${normalizeRuleNumber(rawValue, template.defaultValue, true)}`;
        case 'price_below_ma':
            return `price_below_ma_${normalizeRuleNumber(rawValue, template.defaultValue, true)}`;
        case 'rsi_above':
            return `rsi_above_${normalizeRuleNumber(rawValue, template.defaultValue, false)}`;
        case 'rsi_below':
            return `rsi_below_${normalizeRuleNumber(rawValue, template.defaultValue, false)}`;
        case 'breakout_high':
            return `breakout_high_${normalizeRuleNumber(rawValue, template.defaultValue, true)}`;
        case 'breakdown_low':
            return `breakdown_low_${normalizeRuleNumber(rawValue, template.defaultValue, true)}`;
        case 'volume_above_sma':
            return `volume_above_sma_${normalizeRuleNumber(rawValue, template.defaultValue, true)}`;
        case 'stop_loss_hit':
            return 'stop_loss_hit';
        case 'take_profit_hit':
            return 'take_profit_hit';
        case 'custom':
            return rawValue.trim().toLowerCase();
        default:
            return rawValue.trim().toLowerCase();
    }
}

function decodeRuleToken(rawToken: string): RuleDraft {
    const token = rawToken.trim().toLowerCase();
    const matchers: Array<[RuleTemplateId, RegExp]> = [
        ['price_above_ma', /^price_above_ma_(\d+)$/],
        ['price_below_ma', /^price_below_ma_(\d+)$/],
        ['rsi_above', /^rsi_above_([0-9]+(?:\.[0-9]+)?)$/],
        ['rsi_below', /^rsi_below_([0-9]+(?:\.[0-9]+)?)$/],
        ['breakout_high', /^breakout_high_(\d+)$/],
        ['breakdown_low', /^breakdown_low_(\d+)$/],
        ['volume_above_sma', /^volume_above_sma_(\d+)$/],
    ];
    for (const [templateId, pattern] of matchers) {
        const match = token.match(pattern);
        if (match) {
            return { templateId, value: match[1], token };
        }
    }
    if (token === 'stop_loss_hit' || token === 'take_profit_hit') {
        return { templateId: token, value: '', token };
    }
    return { templateId: 'custom', value: token, token };
}

function stringifyRuleSet(operator: RuleOperator, rules: RuleDraft[]) {
    const serializedRules = rules
        .map((rule) => buildRuleToken(rule.templateId, rule.value))
        .filter((token) => !!token);
    return JSON.stringify({ [operator]: serializedRules }, null, 2);
}

function parseRuleSetText(raw: string): ParsedRuleSet {
    try {
        const parsed = JSON.parse(raw || '{}') as unknown;
        if (Array.isArray(parsed)) {
            const stringRules = parsed.filter((item): item is string => typeof item === 'string');
            return {
                operator: 'all',
                rules: stringRules.map(decodeRuleToken),
                error: stringRules.length !== parsed.length ? 'Builder can only edit string rule tokens. Non-string items were ignored.' : null,
            };
        }
        if (!parsed || typeof parsed !== 'object') {
            return { operator: 'all', rules: [], error: 'Builder supports { "all": [...] } or { "any": [...] } JSON payloads.' };
        }
        const payload = parsed as { all?: unknown; any?: unknown };
        const hasAll = Array.isArray(payload.all);
        const hasAny = Array.isArray(payload.any);
        if (!hasAll && !hasAny) {
            return { operator: 'all', rules: [], error: 'Builder supports { "all": [...] } or { "any": [...] } JSON payloads.' };
        }
        const operator: RuleOperator = hasAll ? 'all' : 'any';
        const source = (hasAll ? payload.all : payload.any) as unknown[];
        const stringRules = source.filter((item): item is string => typeof item === 'string');
        let error: string | null = null;
        if (hasAll && hasAny) {
            error = 'Raw JSON contains both "all" and "any". Builder is editing the "all" branch.';
        } else if (stringRules.length !== source.length) {
            error = 'Builder can only edit string rule tokens. Non-string items were ignored.';
        }
        return {
            operator,
            rules: stringRules.map(decodeRuleToken),
            error,
        };
    } catch {
        return { operator: 'all', rules: [], error: 'Rule JSON is invalid. Fix the raw payload or reset the builder.' };
    }
}

function runStatusClasses(status: StrategyBotRun['status'] | StrategyBotRunScorecard['status']) {
    if (status === 'COMPLETED') return 'bg-emerald-500/15 text-emerald-200';
    if (status === 'FAILED') return 'bg-red-500/15 text-red-200';
    if (status === 'RUNNING') return 'bg-cyan-500/15 text-cyan-200';
    if (status === 'CANCELLED') return 'bg-zinc-500/15 text-zinc-300';
    return 'bg-amber-500/15 text-amber-200';
}

function isRunCancellable(status: StrategyBotRun['status']) {
    return status === 'QUEUED' || status === 'RUNNING';
}

function resolveActiveBoardPreset(
    sortBy: BotBoardSort,
    direction: 'ASC' | 'DESC',
    runMode: BotBoardRunMode,
    lookbackDays: BotBoardLookback,
): BotBoardPresetId | null {
    const preset = boardComparisonPresets.find((candidate) =>
        candidate.sortBy === sortBy
        && candidate.direction === direction
        && candidate.runMode === runMode
        && candidate.lookbackDays === lookbackDays);
    return preset?.id ?? null;
}

function readPersistedBoardViews(): BotBoardSavedView[] {
    if (typeof window === 'undefined') {
        return [];
    }
    try {
        const stored = window.localStorage.getItem(BOT_BOARD_VIEW_STORAGE_KEY);
        if (!stored) {
            return [];
        }
        const parsed = JSON.parse(stored);
        if (!Array.isArray(parsed)) {
            return [];
        }
        return parsed
            .filter((entry): entry is Record<string, unknown> => !!entry && typeof entry === 'object')
            .map((entry): BotBoardSavedView => ({
                id: typeof entry.id === 'string' ? entry.id : crypto.randomUUID(),
                name: typeof entry.name === 'string' && entry.name.trim() ? entry.name.trim() : 'Saved View',
                sortBy: parseBoardSort(typeof entry.sortBy === 'string' ? entry.sortBy : null),
                direction: parseBoardDirection(typeof entry.direction === 'string' ? entry.direction : null),
                runMode: parseBoardRunMode(typeof entry.runMode === 'string' ? entry.runMode : null),
                lookbackDays: parseBoardLookback(typeof entry.lookbackDays === 'string' ? entry.lookbackDays : null),
                updatedAt: typeof entry.updatedAt === 'string' ? entry.updatedAt : new Date().toISOString(),
            }))
            .slice(0, 12);
    } catch (error) {
        console.error(error);
        return [];
    }
}

function normalizeBoardViewPayloads(payload: unknown): BotBoardSavedView[] {
    if (!Array.isArray(payload)) {
        return [];
    }
    return payload
        .filter((entry): entry is Record<string, unknown> => !!entry && typeof entry === 'object')
        .map((entry): BotBoardSavedView => ({
            id: typeof entry.id === 'string' ? entry.id : crypto.randomUUID(),
            name: typeof entry.name === 'string' && entry.name.trim() ? entry.name.trim() : 'Imported Board View',
            sortBy: parseBoardSort(typeof entry.sortBy === 'string' ? entry.sortBy : null),
            direction: parseBoardDirection(typeof entry.direction === 'string' ? entry.direction : null),
            runMode: parseBoardRunMode(typeof entry.runMode === 'string' ? entry.runMode : null),
            lookbackDays: parseBoardLookback(typeof entry.lookbackDays === 'string' ? entry.lookbackDays : null),
            updatedAt: typeof entry.updatedAt === 'string' ? entry.updatedAt : new Date().toISOString(),
        }))
        .slice(0, 12);
}

function buildSparkline(points: StrategyBotRunEquityPoint[]) {
    if (points.length < 2) return '';
    const width = 320;
    const height = 96;
    const padding = 8;
    const values = points.map((point) => point.equity);
    const min = Math.min(...values);
    const max = Math.max(...values);
    const range = max - min || 1;
    return points
        .map((point, index) => {
            const x = padding + (index * (width - padding * 2)) / (points.length - 1);
            const y = height - padding - ((point.equity - min) / range) * (height - padding * 2);
            return `${x},${y}`;
        })
        .join(' ');
}

function readSlicePageMeta(payload: unknown): SlicePageMeta {
    if (!payload || typeof payload !== 'object') {
        return { pageNumber: 0, totalPages: 0, totalElements: 0 };
    }
    const page = (payload as { page?: { number?: number; totalPages?: number; totalElements?: number } }).page;
    return {
        pageNumber: typeof page?.number === 'number' ? page.number : 0,
        totalPages: typeof page?.totalPages === 'number' ? page.totalPages : 0,
        totalElements: typeof page?.totalElements === 'number' ? page.totalElements : 0,
    };
}

function RuleBuilderPanel({
    scope,
    title,
    description,
    rawValue,
    parsed,
    accentClass,
    onOperatorChange,
    onRuleChange,
    onAddRule,
    onRemoveRule,
    onRawChange,
    onReset,
}: {
    scope: RuleBuilderScope;
    title: string;
    description: string;
    rawValue: string;
    parsed: ParsedRuleSet;
    accentClass: string;
    onOperatorChange: (operator: RuleOperator) => void;
    onRuleChange: (index: number, next: Partial<Pick<RuleDraft, 'templateId' | 'value'>>) => void;
    onAddRule: (templateId: RuleTemplateId) => void;
    onRemoveRule: (index: number) => void;
    onRawChange: (next: string) => void;
    onReset: () => void;
}) {
    const availableTemplates = ruleTemplatesForScope(scope);
    return (
        <div className="rounded-3xl border border-white/10 bg-black/20 p-4">
            <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
                <div>
                    <p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">{title}</p>
                    <p className="mt-2 max-w-xl text-sm text-zinc-400">{description}</p>
                </div>
                <label className="flex items-center gap-3 rounded-full border border-white/10 bg-white/5 px-3 py-2 text-[11px] font-semibold uppercase tracking-[0.18em] text-zinc-300">
                    Operator
                    <select
                        value={parsed.operator}
                        onChange={(event) => onOperatorChange(event.target.value as RuleOperator)}
                        className="bg-transparent text-cyan-100 outline-none"
                    >
                        <option value="all">ALL</option>
                        <option value="any">ANY</option>
                    </select>
                </label>
            </div>
            {parsed.error && (
                <div className="mt-4 rounded-2xl border border-amber-500/20 bg-amber-500/10 px-4 py-3 text-sm text-amber-100">
                    <p>{parsed.error}</p>
                    <button
                        type="button"
                        onClick={onReset}
                        className="mt-3 rounded-full border border-amber-500/20 bg-black/20 px-3 py-1.5 text-[11px] font-semibold uppercase tracking-[0.18em] text-amber-50"
                    >
                        Reset To Empty Builder
                    </button>
                </div>
            )}
            <div className="mt-4 flex flex-wrap gap-2">
                {availableTemplates.map((template) => (
                    <button
                        key={`${scope}-${template.id}`}
                        type="button"
                        onClick={() => onAddRule(template.id)}
                        className={`rounded-full border px-3 py-1.5 text-[11px] font-semibold uppercase tracking-[0.18em] ${accentClass}`}
                        title={template.description}
                    >
                        + {template.label}
                    </button>
                ))}
            </div>
            <div className="mt-4 space-y-3">
                {parsed.rules.length === 0 ? (
                    <div className="rounded-2xl border border-dashed border-white/10 bg-black/20 px-4 py-6 text-sm text-zinc-500">
                        No {scope.toLowerCase()} rules configured yet. Add tokens from the builder chips above.
                    </div>
                ) : parsed.rules.map((rule, index) => {
                    const template = getRuleTemplate(rule.templateId);
                    const tokenPreview = buildRuleToken(rule.templateId, rule.value);
                    return (
                        <div key={`${scope}-${index}-${rule.token}`} className="rounded-2xl border border-white/10 bg-zinc-950/60 p-4">
                            <div className="grid gap-3 xl:grid-cols-[1.2fr_0.6fr_auto]">
                                <div className="space-y-2">
                                    <select
                                        value={rule.templateId}
                                        onChange={(event) => onRuleChange(index, { templateId: event.target.value as RuleTemplateId })}
                                        className="w-full rounded-2xl border border-white/10 bg-black/30 px-4 py-3 text-sm text-white outline-none focus:border-cyan-500/40"
                                    >
                                        {availableTemplates.map((option) => (
                                            <option key={option.id} value={option.id}>{option.label}</option>
                                        ))}
                                    </select>
                                    <p className="text-xs text-zinc-500">{template.description}</p>
                                </div>
                                <div>
                                    {template.parameterKind === 'none' ? (
                                        <div className="rounded-2xl border border-white/10 bg-black/20 px-4 py-3 text-sm text-zinc-500">No parameter</div>
                                    ) : (
                                        <input
                                            value={rule.value}
                                            onChange={(event) => onRuleChange(index, { value: event.target.value })}
                                            placeholder={template.parameterKind === 'custom' ? 'custom_token_name' : template.parameterKind === 'window' ? 'Window' : 'Threshold'}
                                            className="w-full rounded-2xl border border-white/10 bg-black/30 px-4 py-3 text-sm text-white outline-none focus:border-cyan-500/40"
                                        />
                                    )}
                                    <p className="mt-2 text-xs text-zinc-500">Token preview: <span className="font-mono text-zinc-300">{tokenPreview || 'empty'}</span></p>
                                </div>
                                <div className="flex items-start justify-end">
                                    <button
                                        type="button"
                                        onClick={() => onRemoveRule(index)}
                                        className="rounded-full border border-red-500/20 bg-red-500/10 px-3 py-2 text-[11px] font-semibold uppercase tracking-[0.18em] text-red-100"
                                    >
                                        Remove
                                    </button>
                                </div>
                            </div>
                        </div>
                    );
                })}
            </div>
            <details className="mt-4 rounded-2xl border border-white/10 bg-black/20 p-4" open={!!parsed.error}>
                <summary className="cursor-pointer text-[11px] font-semibold uppercase tracking-[0.18em] text-zinc-400">Raw JSON Fallback</summary>
                <p className="mt-3 text-sm text-zinc-500">Builder edits stay synced with raw JSON. Keep this for advanced/custom tokens or direct paste flows.</p>
                <textarea
                    value={rawValue}
                    onChange={(event) => onRawChange(event.target.value)}
                    className="mt-3 min-h-40 w-full rounded-2xl border border-white/10 bg-zinc-950/70 px-4 py-3 font-mono text-xs text-white outline-none focus:border-cyan-500/40"
                />
            </details>
        </div>
    );
}

export default function StrategyBotsPage() {
    const router = useRouter();
    const pathname = usePathname();
    const searchParams = useSearchParams();
    const boardViewImportInputRef = useRef<HTMLInputElement | null>(null);
    const [tab, setTab] = useState<Tab>(() => parseTab(searchParams.get('tab')));
    const [userId, setUserId] = useState<string | null>(null);
    const [loading, setLoading] = useState(true);
    const [saving, setSaving] = useState(false);
    const [requestingRun, setRequestingRun] = useState(false);
    const [executingRunId, setExecutingRunId] = useState<string | null>(null);
    const [refreshingRunId, setRefreshingRunId] = useState<string | null>(null);
    const [cancellingRunId, setCancellingRunId] = useState<string | null>(null);
    const [applyingRunId, setApplyingRunId] = useState<string | null>(null);
    const [exportingFormat, setExportingFormat] = useState<'csv' | 'json' | null>(null);
    const [exportingBoardFormat, setExportingBoardFormat] = useState<'csv' | 'json' | null>(null);
    const [exportingRunFormat, setExportingRunFormat] = useState<'csv' | 'json' | null>(null);
    const [outputLoadingState, setOutputLoadingState] = useState({ events: false, fills: false, equity: false });
    const [runDetailTab, setRunDetailTab] = useState<RunDetailTab>('SUMMARY');
    const [selectedRunEventsLoaded, setSelectedRunEventsLoaded] = useState(false);
    const [selectedRunFillsLoaded, setSelectedRunFillsLoaded] = useState(false);
    const [selectedRunEquityCurveLoaded, setSelectedRunEquityCurveLoaded] = useState(false);
    const [selectedRunReconciliationLoaded, setSelectedRunReconciliationLoaded] = useState(false);
    const [pageError, setPageError] = useState<string | null>(null);
    const [actionError, setActionError] = useState<string | null>(null);
    const [notice, setNotice] = useState<string | null>(null);
    const [botBoard, setBotBoard] = useState<StrategyBotBoardEntry[]>([]);
    const [boardSortBy, setBoardSortBy] = useState<BotBoardSort>(() => parseBoardSort(searchParams.get('boardSort')));
    const [boardDirection, setBoardDirection] = useState<'ASC' | 'DESC'>(() => parseBoardDirection(searchParams.get('boardDirection')));
    const [boardRunMode, setBoardRunMode] = useState<BotBoardRunMode>(() => parseBoardRunMode(searchParams.get('boardRunMode')));
    const [boardLookbackDays, setBoardLookbackDays] = useState<BotBoardLookback>(() => parseBoardLookback(searchParams.get('boardLookback')));
    const [savedBoardViews, setSavedBoardViews] = useState<BotBoardSavedView[]>([]);
    const [boardViewsHydrated, setBoardViewsHydrated] = useState(false);
    const [boardViewName, setBoardViewName] = useState('');
    const [portfolios, setPortfolios] = useState<PortfolioOption[]>([]);
    const [bots, setBots] = useState<StrategyBot[]>([]);
    const [runs, setRuns] = useState<StrategyBotRun[]>([]);
    const [selectedBotId, setSelectedBotId] = useState(() => searchParams.get('bot') ?? '');
    const [selectedRunId, setSelectedRunId] = useState(() => searchParams.get('run') ?? '');
    const [selectedRunEvents, setSelectedRunEvents] = useState<StrategyBotRunEvent[]>([]);
    const [selectedRunFills, setSelectedRunFills] = useState<StrategyBotRunFill[]>([]);
    const [selectedRunEquityCurve, setSelectedRunEquityCurve] = useState<StrategyBotRunEquityPoint[]>([]);
    const [selectedRunEventsPageMeta, setSelectedRunEventsPageMeta] = useState<SlicePageMeta>({ pageNumber: 0, totalPages: 0, totalElements: 0 });
    const [selectedRunFillsPageMeta, setSelectedRunFillsPageMeta] = useState<SlicePageMeta>({ pageNumber: 0, totalPages: 0, totalElements: 0 });
    const [selectedRunEquityCurvePageMeta, setSelectedRunEquityCurvePageMeta] = useState<SlicePageMeta>({ pageNumber: 0, totalPages: 0, totalElements: 0 });
    const [selectedRunReconciliation, setSelectedRunReconciliation] = useState<StrategyBotRunReconciliationPlan | null>(null);
    const [selectedBotAnalytics, setSelectedBotAnalytics] = useState<StrategyBotAnalytics | null>(null);
    const [editingBotId, setEditingBotId] = useState<string | null>(null);
    const [botForm, setBotForm] = useState({ name: '', description: '', linkedPortfolioId: '', market: 'CRYPTO', symbol: 'BTCUSDT', timeframe: '1h', status: 'DRAFT' as BotStatus, maxPositionSizePercent: '20', stopLossPercent: '3', takeProfitPercent: '8', cooldownMinutes: '60', entryRulesText: defaultEntryRules, exitRulesText: defaultExitRules });
    const [runForm, setRunForm] = useState({ runMode: 'BACKTEST' as RunMode, initialCapital: '', fromDate: '', toDate: '' });

    const selectedBot = useMemo(() => bots.find((bot) => bot.id === selectedBotId) ?? null, [bots, selectedBotId]);
    const latestRun = runs[0] ?? null;
    const selectedRun = useMemo(() => runs.find((run) => run.id === selectedRunId) ?? latestRun ?? null, [runs, selectedRunId, latestRun]);
    const hasMoreRunEvents = selectedRunEventsLoaded && selectedRunEventsPageMeta.pageNumber + 1 < selectedRunEventsPageMeta.totalPages;
    const hasMoreRunFills = selectedRunFillsLoaded && selectedRunFillsPageMeta.pageNumber + 1 < selectedRunFillsPageMeta.totalPages;
    const hasMoreRunEquityCurve = selectedRunEquityCurveLoaded && selectedRunEquityCurvePageMeta.pageNumber + 1 < selectedRunEquityCurvePageMeta.totalPages;
    const activeBoardPreset = useMemo(
        () => resolveActiveBoardPreset(boardSortBy, boardDirection, boardRunMode, boardLookbackDays),
        [boardSortBy, boardDirection, boardRunMode, boardLookbackDays],
    );
    const activeBoardPresetMeta = useMemo(
        () => boardComparisonPresets.find((preset) => preset.id === activeBoardPreset) ?? null,
        [activeBoardPreset],
    );
    const activeSavedBoardView = useMemo(
        () => savedBoardViews.find((view) =>
            view.sortBy === boardSortBy
            && view.direction === boardDirection
            && view.runMode === boardRunMode
            && view.lookbackDays === boardLookbackDays) ?? null,
        [savedBoardViews, boardSortBy, boardDirection, boardRunMode, boardLookbackDays],
    );
    const entryRuleSet = useMemo(() => parseRuleSetText(botForm.entryRulesText), [botForm.entryRulesText]);
    const exitRuleSet = useMemo(() => parseRuleSetText(botForm.exitRulesText), [botForm.exitRulesText]);

    useEffect(() => {
        const currentUserId = localStorage.getItem('userId');
        if (!currentUserId) { router.push('/auth/login'); return; }
        setUserId(currentUserId);
        void bootstrap(currentUserId);
    }, [router]);

    useEffect(() => {
        if (selectedBotId) {
            void loadRuns(selectedBotId);
            void loadBotAnalytics(selectedBotId, boardRunMode, boardLookbackDays);
        } else {
            setRuns([]);
            setSelectedRunId('');
            setSelectedRunEvents([]);
            setSelectedRunEventsLoaded(false);
            setSelectedRunEventsPageMeta({ pageNumber: 0, totalPages: 0, totalElements: 0 });
            setSelectedRunFills([]);
            setSelectedRunFillsLoaded(false);
            setSelectedRunFillsPageMeta({ pageNumber: 0, totalPages: 0, totalElements: 0 });
            setSelectedRunEquityCurve([]);
            setSelectedRunEquityCurveLoaded(false);
            setSelectedRunEquityCurvePageMeta({ pageNumber: 0, totalPages: 0, totalElements: 0 });
            setSelectedBotAnalytics(null);
            setSelectedRunReconciliation(null);
            setSelectedRunReconciliationLoaded(false);
        }
    }, [selectedBotId, boardRunMode, boardLookbackDays]);

    useEffect(() => {
        if (!selectedBotId || !selectedRunId) {
            setSelectedRunEvents([]);
            setSelectedRunEventsLoaded(false);
            setSelectedRunEventsPageMeta({ pageNumber: 0, totalPages: 0, totalElements: 0 });
            setSelectedRunFills([]);
            setSelectedRunFillsLoaded(false);
            setSelectedRunFillsPageMeta({ pageNumber: 0, totalPages: 0, totalElements: 0 });
            setSelectedRunEquityCurve([]);
            setSelectedRunEquityCurveLoaded(false);
            setSelectedRunEquityCurvePageMeta({ pageNumber: 0, totalPages: 0, totalElements: 0 });
            setSelectedRunReconciliation(null);
            setSelectedRunReconciliationLoaded(false);
            return;
        }
        if (runDetailTab === 'FILLS' && !selectedRunFillsLoaded) {
            void loadRunFills(selectedBotId, selectedRunId);
        }
        if (runDetailTab === 'EVENTS' && !selectedRunEventsLoaded) {
            void loadRunEvents(selectedBotId, selectedRunId);
        }
        if (runDetailTab === 'EQUITY' && !selectedRunEquityCurveLoaded) {
            void loadRunEquityCurve(selectedBotId, selectedRunId);
        }
        if (runDetailTab === 'RECONCILIATION' && !selectedRunReconciliationLoaded) {
            void loadRunReconciliation(selectedBotId, selectedRunId);
        }
        if (runDetailTab === 'RAW') {
            if (!selectedRunEventsLoaded) {
                void loadRunEvents(selectedBotId, selectedRunId);
            }
            if (!selectedRunFillsLoaded) {
                void loadRunFills(selectedBotId, selectedRunId);
            }
            if (!selectedRunEquityCurveLoaded) {
                void loadRunEquityCurve(selectedBotId, selectedRunId);
            }
            if (!selectedRunReconciliationLoaded) {
                void loadRunReconciliation(selectedBotId, selectedRunId);
            }
        }
    }, [selectedBotId, selectedRunId, runDetailTab, selectedRunEventsLoaded, selectedRunFillsLoaded, selectedRunEquityCurveLoaded, selectedRunReconciliationLoaded]);

    useEffect(() => {
        if (userId) {
            void loadBotBoard(boardSortBy, boardDirection, boardRunMode, boardLookbackDays);
        }
    }, [userId, boardSortBy, boardDirection, boardRunMode, boardLookbackDays]);

    useEffect(() => {
        setSavedBoardViews(readPersistedBoardViews());
        setBoardViewsHydrated(true);
    }, []);

    useEffect(() => {
        if (typeof window === 'undefined' || !boardViewsHydrated) {
            return;
        }
        window.localStorage.setItem(BOT_BOARD_VIEW_STORAGE_KEY, JSON.stringify(savedBoardViews.slice(0, 12)));
    }, [savedBoardViews, boardViewsHydrated]);

    useEffect(() => {
        const params = new URLSearchParams();
        if (tab !== 'OVERVIEW') params.set('tab', tab);
        if (selectedBotId) params.set('bot', selectedBotId);
        if (selectedRunId) params.set('run', selectedRunId);
        if (boardSortBy !== 'AVG_RETURN') params.set('boardSort', boardSortBy);
        if (boardDirection !== 'DESC') params.set('boardDirection', boardDirection);
        if (boardRunMode !== 'ALL') params.set('boardRunMode', boardRunMode);
        if (boardLookbackDays !== 'ALL') params.set('boardLookback', boardLookbackDays);
        const next = params.toString();
        const nextUrl = next ? `${pathname}?${next}` : pathname;
        window.history.replaceState(null, '', nextUrl);
    }, [pathname, tab, selectedBotId, selectedRunId, boardSortBy, boardDirection, boardRunMode, boardLookbackDays]);

    async function bootstrap(currentUserId: string) {
        setLoading(true); setPageError(null);
        try {
            const [portfolioRes, botRes] = await Promise.all([
                apiFetch(`/api/v1/portfolios?ownerId=${encodeURIComponent(currentUserId)}&size=50`, { cache: 'no-store' }),
                apiFetch('/api/v1/strategy-bots?size=50', { cache: 'no-store' }),
            ]);
            if (!portfolioRes.ok) throw new Error(`Failed to load portfolios (${portfolioRes.status})`);
            if (!botRes.ok) throw new Error(`Failed to load strategy bots (${botRes.status})`);
            const nextPortfolios = extractContent<PortfolioOption>(await portfolioRes.json());
            const nextBots = extractContent<StrategyBot>(await botRes.json());
            setPortfolios(nextPortfolios);
            setBots(nextBots);
            setSelectedBotId((current) => current && nextBots.some((bot) => bot.id === current) ? current : nextBots[0]?.id ?? '');
            setBotForm((current) => current.linkedPortfolioId ? current : { ...current, linkedPortfolioId: nextPortfolios[0]?.id ?? '' });
            await loadBotBoard(boardSortBy, boardDirection, boardRunMode, boardLookbackDays);
        } catch (error) { setPageError(err(error)); } finally { setLoading(false); }
    }

    async function loadBotBoard(
        sortBy = boardSortBy,
        direction = boardDirection,
        runMode = boardRunMode,
        lookbackDays = boardLookbackDays,
    ) {
        try {
            const query = new URLSearchParams({
                size: '24',
                sortBy,
                direction,
                runMode,
            });
            if (lookbackDays !== 'ALL') query.set('lookbackDays', lookbackDays);
            const response = await apiFetch(`/api/v1/strategy-bots/board?${query.toString()}`, { cache: 'no-store' });
            if (!response.ok) throw new Error(`Failed to load bot board (${response.status})`);
            setBotBoard(extractContent<StrategyBotBoardEntry>(await response.json()));
        } catch (error) {
            setActionError(err(error));
        }
    }

    function resetSelectedRunArtifacts(resetDetailTab = true) {
        setSelectedRunEvents([]);
        setSelectedRunEventsLoaded(false);
        setSelectedRunEventsPageMeta({ pageNumber: 0, totalPages: 0, totalElements: 0 });
        setSelectedRunFills([]);
        setSelectedRunFillsLoaded(false);
        setSelectedRunFillsPageMeta({ pageNumber: 0, totalPages: 0, totalElements: 0 });
        setSelectedRunEquityCurve([]);
        setSelectedRunEquityCurveLoaded(false);
        setSelectedRunEquityCurvePageMeta({ pageNumber: 0, totalPages: 0, totalElements: 0 });
        setSelectedRunReconciliation(null);
        setSelectedRunReconciliationLoaded(false);
        if (resetDetailTab) {
            setRunDetailTab('SUMMARY');
        }
    }

    function selectRun(runId: string, resetDetailTab = true) {
        resetSelectedRunArtifacts(resetDetailTab);
        setSelectedRunId(runId);
    }

    async function loadRuns(botId: string) {
        try {
            const response = await apiFetch(`/api/v1/strategy-bots/${botId}/runs?size=20`, { cache: 'no-store' });
            if (!response.ok) throw new Error(`Failed to load bot runs (${response.status})`);
            const nextRuns = extractContent<StrategyBotRun>(await response.json());
            setRuns(nextRuns);
            setSelectedRunId((current) => {
                const nextRunId = current && nextRuns.some((run) => run.id === current) ? current : nextRuns[0]?.id ?? '';
                if (nextRunId !== current) {
                    resetSelectedRunArtifacts();
                }
                return nextRunId;
            });
        } catch (error) { setActionError(err(error)); }
    }

    async function loadRunEvents(botId: string, runId: string, page = 0, append = false) {
        setOutputLoadingState((current) => ({ ...current, events: true }));
        try {
            const eventsRes = await apiFetch(`/api/v1/strategy-bots/${botId}/runs/${runId}/events?page=${page}&size=60`, { cache: 'no-store' });
            if (!eventsRes.ok) throw new Error(`Failed to load run events (${eventsRes.status})`);
            const payload = await eventsRes.json();
            const content = extractContent<StrategyBotRunEvent>(payload);
            setSelectedRunEvents((current) => append ? [...current, ...content] : content);
            setSelectedRunEventsPageMeta(readSlicePageMeta(payload));
            setSelectedRunEventsLoaded(true);
        } catch (error) {
            setActionError(err(error));
        } finally {
            setOutputLoadingState((current) => ({ ...current, events: false }));
        }
    }

    async function loadRunFills(botId: string, runId: string, page = 0, append = false) {
        setOutputLoadingState((current) => ({ ...current, fills: true }));
        try {
            const fillsRes = await apiFetch(`/api/v1/strategy-bots/${botId}/runs/${runId}/fills?page=${page}&size=40`, { cache: 'no-store' });
            if (!fillsRes.ok) throw new Error(`Failed to load run fills (${fillsRes.status})`);
            const payload = await fillsRes.json();
            const content = extractContent<StrategyBotRunFill>(payload);
            setSelectedRunFills((current) => append ? [...current, ...content] : content);
            setSelectedRunFillsPageMeta(readSlicePageMeta(payload));
            setSelectedRunFillsLoaded(true);
        } catch (error) {
            setActionError(err(error));
        } finally {
            setOutputLoadingState((current) => ({ ...current, fills: false }));
        }
    }

    async function loadRunEquityCurve(botId: string, runId: string, page = 0, append = false) {
        setOutputLoadingState((current) => ({ ...current, equity: true }));
        try {
            const curveRes = await apiFetch(`/api/v1/strategy-bots/${botId}/runs/${runId}/equity-curve?page=${page}&size=120`, { cache: 'no-store' });
            if (!curveRes.ok) throw new Error(`Failed to load run equity curve (${curveRes.status})`);
            const payload = await curveRes.json();
            const content = extractContent<StrategyBotRunEquityPoint>(payload);
            setSelectedRunEquityCurve((current) => append ? [...current, ...content] : content);
            setSelectedRunEquityCurvePageMeta(readSlicePageMeta(payload));
            setSelectedRunEquityCurveLoaded(true);
        } catch (error) {
            setActionError(err(error));
        } finally {
            setOutputLoadingState((current) => ({ ...current, equity: false }));
        }
    }

    async function loadRunReconciliation(botId: string, runId: string) {
        try {
            const response = await apiFetch(`/api/v1/strategy-bots/${botId}/runs/${runId}/reconciliation-plan`, { cache: 'no-store' });
            if (!response.ok) { setSelectedRunReconciliation(null); setSelectedRunReconciliationLoaded(true); return; }
            setSelectedRunReconciliation(await response.json() as StrategyBotRunReconciliationPlan);
            setSelectedRunReconciliationLoaded(true);
        } catch {
            setSelectedRunReconciliation(null);
            setSelectedRunReconciliationLoaded(true);
        }
    }

    async function loadBotAnalytics(
        botId: string,
        runMode = boardRunMode,
        lookbackDays = boardLookbackDays,
    ) {
        try {
            const query = new URLSearchParams({ runMode });
            if (lookbackDays !== 'ALL') query.set('lookbackDays', lookbackDays);
            const response = await apiFetch(`/api/v1/strategy-bots/${botId}/analytics?${query.toString()}`, { cache: 'no-store' });
            if (!response.ok) {
                setSelectedBotAnalytics(null);
                return;
            }
            setSelectedBotAnalytics(await response.json() as StrategyBotAnalytics);
        } catch {
            setSelectedBotAnalytics(null);
        }
    }

    async function exportBotAnalytics(format: 'csv' | 'json') {
        if (!selectedBotId) {
            setActionError('Select a strategy bot before exporting analytics');
            return;
        }
        setExportingFormat(format);
        setActionError(null);
        setNotice(null);
        try {
            const query = new URLSearchParams({
                format,
                runMode: boardRunMode,
            });
            if (boardLookbackDays !== 'ALL') query.set('lookbackDays', boardLookbackDays);
            const response = await apiFetch(`/api/v1/strategy-bots/${selectedBotId}/analytics/export?${query.toString()}`, {
                cache: 'no-store',
            });
            if (!response.ok) throw new Error(await response.text() || `Strategy bot analytics export failed (${response.status})`);
            const blob = await response.blob();
            const url = window.URL.createObjectURL(blob);
            const anchor = document.createElement('a');
            anchor.href = url;
            anchor.download = downloadNameFromDisposition(
                response.headers.get('Content-Disposition'),
                `strategy-bot-analytics.${format}`,
            );
            anchor.click();
            window.URL.revokeObjectURL(url);
            setNotice(format === 'csv' ? 'Bot analytics CSV exported' : 'Bot analytics JSON exported');
        } catch (error) {
            setActionError(err(error));
        } finally {
            setExportingFormat(null);
        }
    }

    async function exportBoard(format: 'csv' | 'json') {
        setExportingBoardFormat(format);
        setActionError(null);
        setNotice(null);
        try {
            const query = new URLSearchParams({
                format,
                sortBy: boardSortBy,
                direction: boardDirection,
                runMode: boardRunMode,
            });
            if (boardLookbackDays !== 'ALL') query.set('lookbackDays', boardLookbackDays);
            const response = await apiFetch(`/api/v1/strategy-bots/board/export?${query.toString()}`, {
                cache: 'no-store',
            });
            if (!response.ok) throw new Error(await response.text() || `Strategy bot board export failed (${response.status})`);
            const blob = await response.blob();
            const url = window.URL.createObjectURL(blob);
            const anchor = document.createElement('a');
            anchor.href = url;
            anchor.download = downloadNameFromDisposition(
                response.headers.get('Content-Disposition'),
                `strategy-bot-board.${format}`,
            );
            anchor.click();
            window.URL.revokeObjectURL(url);
            setNotice(format === 'csv' ? 'Board CSV exported' : 'Board JSON exported');
        } catch (error) {
            setActionError(err(error));
        } finally {
            setExportingBoardFormat(null);
        }
    }

    async function exportRun(format: 'csv' | 'json') {
        if (!selectedBotId || !selectedRun) {
            setActionError('Select a strategy bot run before exporting');
            return;
        }
        setExportingRunFormat(format);
        setActionError(null);
        setNotice(null);
        try {
            const response = await apiFetch(`/api/v1/strategy-bots/${selectedBotId}/runs/${selectedRun.id}/export?format=${format}`, {
                cache: 'no-store',
            });
            if (!response.ok) throw new Error(await response.text() || `Strategy bot run export failed (${response.status})`);
            const blob = await response.blob();
            const url = window.URL.createObjectURL(blob);
            const anchor = document.createElement('a');
            anchor.href = url;
            anchor.download = downloadNameFromDisposition(
                response.headers.get('Content-Disposition'),
                `strategy-bot-run.${format}`,
            );
            anchor.click();
            window.URL.revokeObjectURL(url);
            setNotice(format === 'csv' ? 'Run CSV exported' : 'Run JSON exported');
        } catch (error) {
            setActionError(err(error));
        } finally {
            setExportingRunFormat(null);
        }
    }

    function applyBoardPreset(presetId: BotBoardPresetId) {
        const preset = boardComparisonPresets.find((candidate) => candidate.id === presetId);
        if (!preset) return;
        setBoardSortBy(preset.sortBy);
        setBoardDirection(preset.direction);
        setBoardRunMode(preset.runMode);
        setBoardLookbackDays(preset.lookbackDays);
    }

    function saveCurrentBoardView() {
        const trimmedName = boardViewName.trim();
        if (!trimmedName) {
            setActionError('Name the saved board view before storing it');
            return;
        }
        setActionError(null);
        setNotice(null);
        setSavedBoardViews((current) => {
            const existing = current.find((view) => view.name.toLowerCase() === trimmedName.toLowerCase());
            const nextView: BotBoardSavedView = {
                id: existing?.id ?? crypto.randomUUID(),
                name: trimmedName,
                sortBy: boardSortBy,
                direction: boardDirection,
                runMode: boardRunMode,
                lookbackDays: boardLookbackDays,
                updatedAt: new Date().toISOString(),
            };
            const remaining = current.filter((view) => view.id !== nextView.id).slice(0, 11);
            return [nextView, ...remaining];
        });
        setBoardViewName('');
        setNotice('Board view saved');
    }

    function applySavedBoardView(view: BotBoardSavedView) {
        setBoardSortBy(view.sortBy);
        setBoardDirection(view.direction);
        setBoardRunMode(view.runMode);
        setBoardLookbackDays(view.lookbackDays);
        setNotice(`Applied ${view.name}`);
        setActionError(null);
    }

    function deleteSavedBoardView(viewId: string) {
        setSavedBoardViews((current) => current.filter((view) => view.id !== viewId));
        setNotice('Board view removed');
        setActionError(null);
    }

    function exportSavedBoardViews() {
        if (typeof window === 'undefined') {
            return;
        }
        if (savedBoardViews.length === 0) {
            setActionError('No saved board views available to export');
            return;
        }
        const payload = savedBoardViews.map((view) => ({
            id: view.id,
            name: view.name,
            sortBy: view.sortBy,
            direction: view.direction,
            runMode: view.runMode,
            lookbackDays: view.lookbackDays,
            updatedAt: view.updatedAt,
        }));
        const blob = new Blob([JSON.stringify(payload, null, 2)], { type: 'application/json;charset=utf-8' });
        const url = window.URL.createObjectURL(blob);
        const anchor = document.createElement('a');
        anchor.href = url;
        anchor.download = 'strategy-bot-board-views.json';
        document.body.appendChild(anchor);
        anchor.click();
        document.body.removeChild(anchor);
        window.URL.revokeObjectURL(url);
        setNotice(`Exported ${savedBoardViews.length} board view${savedBoardViews.length === 1 ? '' : 's'}.`);
        setActionError(null);
    }

    async function importSavedBoardViews(event: ChangeEvent<HTMLInputElement>) {
        const file = event.target.files?.[0];
        if (!file) {
            return;
        }
        try {
            const raw = await file.text();
            const parsed = JSON.parse(raw);
            const imported = normalizeBoardViewPayloads(parsed);
            if (imported.length === 0) {
                setActionError('Board view import failed: no valid views found');
                return;
            }
            setSavedBoardViews((current) => {
                const merged = [...imported, ...current].reduce<BotBoardSavedView[]>((acc, view) => {
                    const existingIndex = acc.findIndex((entry) => entry.name.toLowerCase() === view.name.toLowerCase());
                    if (existingIndex >= 0) {
                        acc[existingIndex] = view;
                        return acc;
                    }
                    acc.push(view);
                    return acc;
                }, []);
                return merged.slice(0, 12);
            });
            setNotice(`Imported ${imported.length} board view${imported.length === 1 ? '' : 's'}.`);
            setActionError(null);
        } catch {
            setActionError('Board view import failed: invalid JSON');
        } finally {
            if (boardViewImportInputRef.current) {
                boardViewImportInputRef.current.value = '';
            }
        }
    }

    async function shareSavedBoardView(view: BotBoardSavedView) {
        if (typeof window === 'undefined') {
            return;
        }
        const params = new URLSearchParams();
        params.set('tab', 'OVERVIEW');
        if (view.sortBy !== 'AVG_RETURN') params.set('boardSort', view.sortBy);
        if (view.direction !== 'DESC') params.set('boardDirection', view.direction);
        if (view.runMode !== 'ALL') params.set('boardRunMode', view.runMode);
        if (view.lookbackDays !== 'ALL') params.set('boardLookback', view.lookbackDays);
        const shareUrl = `${window.location.origin}${pathname}?${params.toString()}`;
        await navigator.clipboard.writeText(shareUrl);
        setNotice(`Copied board view link: ${view.name}`);
        setActionError(null);
    }

    function resetBotForm() {
        setEditingBotId(null);
        setBotForm({ name: '', description: '', linkedPortfolioId: portfolios[0]?.id ?? '', market: 'CRYPTO', symbol: 'BTCUSDT', timeframe: '1h', status: 'DRAFT', maxPositionSizePercent: '20', stopLossPercent: '3', takeProfitPercent: '8', cooldownMinutes: '60', entryRulesText: defaultEntryRules, exitRulesText: defaultExitRules });
    }

    function updateRuleSetField(field: 'entryRulesText' | 'exitRulesText', parsed: ParsedRuleSet, mutate: (current: ParsedRuleSet) => ParsedRuleSet) {
        const next = mutate(parsed);
        setBotForm((current) => ({ ...current, [field]: stringifyRuleSet(next.operator, next.rules) }));
    }

    function changeRuleOperator(field: 'entryRulesText' | 'exitRulesText', parsed: ParsedRuleSet, operator: RuleOperator) {
        updateRuleSetField(field, parsed, (current) => ({ ...current, operator }));
    }

    function addRuleDraft(field: 'entryRulesText' | 'exitRulesText', parsed: ParsedRuleSet, templateId: RuleTemplateId) {
        const template = getRuleTemplate(templateId);
        updateRuleSetField(field, parsed, (current) => ({
            ...current,
            rules: [...current.rules, { templateId, value: template.defaultValue, token: buildRuleToken(templateId, template.defaultValue) }],
        }));
    }

    function patchRuleDraft(field: 'entryRulesText' | 'exitRulesText', parsed: ParsedRuleSet, index: number, next: Partial<Pick<RuleDraft, 'templateId' | 'value'>>) {
        updateRuleSetField(field, parsed, (current) => ({
            ...current,
            rules: current.rules.map((rule, ruleIndex) => {
                if (ruleIndex !== index) return rule;
                const templateId = next.templateId ?? rule.templateId;
                const template = getRuleTemplate(templateId);
                const value = next.value ?? (next.templateId ? template.defaultValue : rule.value);
                return {
                    templateId,
                    value,
                    token: buildRuleToken(templateId, value),
                };
            }),
        }));
    }

    function removeRuleDraft(field: 'entryRulesText' | 'exitRulesText', parsed: ParsedRuleSet, index: number) {
        updateRuleSetField(field, parsed, (current) => ({
            ...current,
            rules: current.rules.filter((_, ruleIndex) => ruleIndex !== index),
        }));
    }

    async function saveBot(event: FormEvent<HTMLFormElement>) {
        event.preventDefault(); setSaving(true); setActionError(null); setNotice(null);
        try {
            const response = await apiFetch(editingBotId ? `/api/v1/strategy-bots/${editingBotId}` : '/api/v1/strategy-bots', {
                method: editingBotId ? 'PUT' : 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ name: botForm.name.trim(), description: botForm.description.trim() || null, linkedPortfolioId: botForm.linkedPortfolioId, market: botForm.market.trim(), symbol: botForm.symbol.trim().toUpperCase(), timeframe: botForm.timeframe.trim(), status: botForm.status, maxPositionSizePercent: numberOrUndefined(botForm.maxPositionSizePercent), stopLossPercent: numberOrUndefined(botForm.stopLossPercent), takeProfitPercent: numberOrUndefined(botForm.takeProfitPercent), cooldownMinutes: numberOrUndefined(botForm.cooldownMinutes), entryRules: JSON.parse(botForm.entryRulesText), exitRules: JSON.parse(botForm.exitRulesText) }),
            });
            if (!response.ok) throw new Error(await response.text() || `Strategy bot save failed (${response.status})`);
            const bot = await response.json() as StrategyBot;
            if (userId) await bootstrap(userId);
            setSelectedBotId(bot.id); setNotice(editingBotId ? 'Strategy bot updated' : 'Strategy bot created'); resetBotForm(); setTab('RUNS');
        } catch (error) { setActionError(err(error)); } finally { setSaving(false); }
    }

    async function deleteBot(botId: string) {
        if (!confirm('Delete this strategy bot and its run journal?')) return;
        setActionError(null); setNotice(null);
        try {
            const response = await apiFetch(`/api/v1/strategy-bots/${botId}`, { method: 'DELETE' });
            if (!response.ok) throw new Error(await response.text() || `Strategy bot delete failed (${response.status})`);
            if (userId) await bootstrap(userId);
            if (editingBotId === botId) resetBotForm();
            setNotice('Strategy bot deleted');
        } catch (error) { setActionError(err(error)); }
    }

    async function requestRun(event: FormEvent<HTMLFormElement>) {
        event.preventDefault(); if (!selectedBotId) { setActionError('Select a strategy bot before requesting a run'); return; }
        setRequestingRun(true); setActionError(null); setNotice(null);
        try {
            const response = await apiFetch(`/api/v1/strategy-bots/${selectedBotId}/runs`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ runMode: runForm.runMode, initialCapital: numberOrUndefined(runForm.initialCapital), fromDate: runForm.fromDate || null, toDate: runForm.toDate || null }) });
            if (!response.ok) throw new Error(await response.text() || `Run request failed (${response.status})`);
            const createdRun = await response.json() as StrategyBotRun;
            setRunForm({ runMode: 'BACKTEST', initialCapital: '', fromDate: '', toDate: '' });
            await loadRuns(selectedBotId);
            await loadBotAnalytics(selectedBotId, boardRunMode, boardLookbackDays);
            await loadBotBoard();
            selectRun(createdRun.id);
            setNotice('Run queued');
        } catch (error) { setActionError(err(error)); } finally { setRequestingRun(false); }
    }

    async function executeRun(runId: string) {
        if (!selectedBotId) return;
        setExecutingRunId(runId); setActionError(null); setNotice(null);
        try {
            const response = await apiFetch(`/api/v1/strategy-bots/${selectedBotId}/runs/${runId}/execute`, { method: 'POST' });
            if (!response.ok) throw new Error(await response.text() || `Run execute failed (${response.status})`);
            await loadRuns(selectedBotId);
            await loadBotAnalytics(selectedBotId, boardRunMode, boardLookbackDays);
            await loadBotBoard();
            selectRun(runId);
            setNotice('Run executed');
        } catch (error) { setActionError(err(error)); } finally { setExecutingRunId(null); }
    }

    async function refreshRun(runId: string) {
        if (!selectedBotId) return;
        setRefreshingRunId(runId); setActionError(null); setNotice(null);
        try {
            const response = await apiFetch(`/api/v1/strategy-bots/${selectedBotId}/runs/${runId}/refresh`, { method: 'POST' });
            if (!response.ok) throw new Error(await response.text() || `Run refresh failed (${response.status})`);
            await loadRuns(selectedBotId);
            await loadBotAnalytics(selectedBotId, boardRunMode, boardLookbackDays);
            await loadBotBoard();
            selectRun(runId, false);
            setNotice('Forward test refreshed');
        } catch (error) { setActionError(err(error)); } finally { setRefreshingRunId(null); }
    }

    async function cancelRun(runId: string) {
        if (!selectedBotId) return;
        if (!confirm('Cancel this strategy bot run?')) return;
        setCancellingRunId(runId); setActionError(null); setNotice(null);
        try {
            const response = await apiFetch(`/api/v1/strategy-bots/${selectedBotId}/runs/${runId}/cancel`, { method: 'POST' });
            if (!response.ok) throw new Error(await response.text() || `Run cancel failed (${response.status})`);
            await loadRuns(selectedBotId);
            await loadBotAnalytics(selectedBotId, boardRunMode, boardLookbackDays);
            await loadBotBoard();
            selectRun(runId, false);
            setNotice('Run cancelled');
        } catch (error) { setActionError(err(error)); } finally { setCancellingRunId(null); }
    }

    async function applyReconciliation(runId: string) {
        if (!selectedBotId) return;
        setApplyingRunId(runId); setActionError(null); setNotice(null);
        try {
            const response = await apiFetch(`/api/v1/strategy-bots/${selectedBotId}/runs/${runId}/apply-reconciliation`, { method: 'POST' });
            if (!response.ok) throw new Error(await response.text() || `Reconciliation apply failed (${response.status})`);
            setSelectedRunReconciliation(await response.json() as StrategyBotRunReconciliationPlan);
            await loadRuns(selectedBotId);
            await loadBotAnalytics(selectedBotId, boardRunMode, boardLookbackDays);
            await loadBotBoard();
            selectRun(runId, false);
            setNotice('Linked portfolio synced to run snapshot');
        } catch (error) { setActionError(err(error)); } finally { setApplyingRunId(null); }
    }

    async function copyWorkspaceLink() {
        try {
            await navigator.clipboard.writeText(window.location.href);
            setNotice('Bot workspace link copied');
            setActionError(null);
        } catch {
            setActionError('Failed to copy workspace link');
        }
    }

    return (
        <div className="p-8 pb-20 text-white">
            <header className="rounded-3xl border border-white/10 bg-black/40 p-6 shadow-2xl backdrop-blur-xl">
                <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
                    <div>
                        <p className="text-[11px] uppercase tracking-[0.32em] text-zinc-500">Strategy Workspace</p>
                        <h1 className="mt-2 bg-gradient-to-r from-cyan-300 via-emerald-300 to-lime-300 bg-clip-text text-3xl font-black text-transparent">Paper bots, deterministic rules, audited runs.</h1>
                        <p className="mt-3 max-w-3xl text-sm leading-7 text-zinc-400">Build rule-based paper bots, link them to owned portfolios, queue backtests, and inspect compiler plus execution summaries from one dashboard surface.</p>
                    </div>
                    <div className="flex flex-wrap gap-3">
                        <button
                            type="button"
                            onClick={() => void copyWorkspaceLink()}
                            className="rounded-full border border-cyan-500/20 bg-cyan-500/10 px-4 py-2 text-xs font-semibold uppercase tracking-[0.24em] text-cyan-100 transition hover:bg-cyan-500/15"
                        >
                            Copy Link
                        </button>
                        <Link href="/dashboard" className="rounded-full border border-white/10 bg-white/5 px-4 py-2 text-xs font-semibold uppercase tracking-[0.24em] text-zinc-300 transition hover:text-white">Dashboard</Link>
                        <Link href="/watchlist" className="rounded-full border border-white/10 bg-white/5 px-4 py-2 text-xs font-semibold uppercase tracking-[0.24em] text-zinc-300 transition hover:text-white">Markets</Link>
                    </div>
                </div>
            </header>

            <section className="mt-8 grid gap-4 md:grid-cols-2 xl:grid-cols-4">
                <div className="rounded-2xl border border-white/10 bg-black/35 px-5 py-4 backdrop-blur-xl">
                    <p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Bots</p>
                    <p className="mt-2 text-2xl font-bold text-white">{bots.length}</p>
                    <p className="mt-1 text-[11px] text-zinc-500">{bots.filter((bot) => bot.status === 'READY').length} ready for runs</p>
                </div>
                <div className="rounded-2xl border border-white/10 bg-black/35 px-5 py-4 backdrop-blur-xl">
                    <p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Runs</p>
                    <p className="mt-2 text-2xl font-bold text-white">{runs.length}</p>
                    <p className="mt-1 text-[11px] text-zinc-500">
                        {runs.filter((run) => run.status === 'COMPLETED').length} completed
                        {' · '}
                        {runs.filter((run) => run.status === 'CANCELLED').length} cancelled
                    </p>
                </div>
                <div className="rounded-2xl border border-white/10 bg-black/35 px-5 py-4 backdrop-blur-xl">
                    <p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Portfolios</p>
                    <p className="mt-2 text-2xl font-bold text-white">{portfolios.length}</p>
                    <p className="mt-1 text-[11px] text-zinc-500">paper accounts available</p>
                </div>
                <div className="rounded-2xl border border-white/10 bg-black/35 px-5 py-4 backdrop-blur-xl">
                    <p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Selection</p>
                    <p className="mt-2 truncate text-xl font-bold text-white">{selectedBot?.name ?? 'None selected'}</p>
                    <p className="mt-1 text-[11px] text-zinc-500">{selectedBot ? `${selectedBot.symbol} ${selectedBot.timeframe}` : 'choose a bot'}</p>
                </div>
            </section>

            <section className="mt-8 rounded-2xl border border-white/10 bg-black/30 p-6 backdrop-blur-xl">
                <div className="flex flex-wrap gap-2">
                    {(['OVERVIEW', 'BOTS', 'RUNS'] as const).map((item) => (
                        <button
                            key={item}
                            type="button"
                            onClick={() => setTab(item)}
                            className={`rounded-full border px-4 py-2 text-xs font-semibold uppercase tracking-[0.22em] transition ${
                                tab === item
                                    ? 'border-cyan-500/35 bg-cyan-500/15 text-cyan-100'
                                    : 'border-white/10 bg-white/5 text-zinc-400 hover:text-white'
                            }`}
                        >
                            {item}
                        </button>
                    ))}
                </div>
            </section>

            {(pageError || actionError || notice) && (
                <section className="mt-6 space-y-3">
                    {pageError && <div className="rounded-2xl border border-red-500/20 bg-red-500/10 px-4 py-3 text-sm text-red-100">{pageError}</div>}
                    {actionError && <div className="rounded-2xl border border-amber-500/20 bg-amber-500/10 px-4 py-3 text-sm text-amber-100">{actionError}</div>}
                    {notice && <div className="rounded-2xl border border-emerald-500/20 bg-emerald-500/10 px-4 py-3 text-sm text-emerald-100">{notice}</div>}
                </section>
            )}

            {loading && (
                <section className="mt-8 grid gap-6 lg:grid-cols-2">
                    <div className="h-96 animate-pulse rounded-3xl bg-white/5" />
                    <div className="h-96 animate-pulse rounded-3xl bg-white/5" />
                </section>
            )}

            {!loading && tab === 'OVERVIEW' && (
                <section className="mt-8 space-y-6">
                    <div className="grid gap-6 xl:grid-cols-2">
                        <div className="rounded-3xl border border-white/10 bg-black/35 p-6 backdrop-blur-xl">
                            <p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Workflow</p>
                            <h2 className="mt-3 text-xl font-bold text-white">Deterministic before autonomous.</h2>
                            <div className="mt-5 space-y-3 text-sm leading-7 text-zinc-300">
                                <p>1. Create a bot with supported rule tokens.</p>
                                <p>2. Link it to an owned paper portfolio and move it to `READY` when the risk profile is sane.</p>
                                <p>3. Queue a backtest, review compiler warnings, then execute and inspect fills plus equity curve summary.</p>
                            </div>
                        </div>
                        <div className="rounded-3xl border border-white/10 bg-black/35 p-6 backdrop-blur-xl">
                            <p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Latest Snapshot</p>
                            <h2 className="mt-3 text-xl font-bold text-white">{selectedBot?.name ?? 'No bot selected'}</h2>
                            <div className="mt-5 grid gap-4 md:grid-cols-2">
                                <div className="rounded-2xl border border-white/5 bg-black/20 p-4">
                                    <p className="text-xs uppercase tracking-wide text-zinc-500">Run Status</p>
                                    <p className="mt-2 text-lg font-bold text-white">{latestRun?.status ?? 'No run yet'}</p>
                                    <p className="mt-1 text-xs text-zinc-500">{latestRun ? fmtDate(latestRun.requestedAt) : 'Request the first run from Runs tab.'}</p>
                                </div>
                                <div className="rounded-2xl border border-white/5 bg-black/20 p-4">
                                    <p className="text-xs uppercase tracking-wide text-zinc-500">Return</p>
                                    <p className="mt-2 text-lg font-bold text-emerald-300">{fmtPercent(latestRun?.summary?.returnPercent)}</p>
                                    <p className="mt-1 text-xs text-zinc-500">PnL {fmtCurrency(latestRun?.summary?.netPnl)}</p>
                                </div>
                                <div className="rounded-2xl border border-white/5 bg-black/20 p-4">
                                    <p className="text-xs uppercase tracking-wide text-zinc-500">Compiler</p>
                                    <p className="mt-2 text-lg font-bold text-white">{latestRun?.summary?.executionEngineReady ? 'Executable' : 'Review warnings'}</p>
                                    <p className="mt-1 text-xs text-zinc-500">{(latestRun?.summary?.unsupportedRules ?? []).length} unsupported rules</p>
                                </div>
                                <div className="rounded-2xl border border-white/5 bg-black/20 p-4">
                                    <p className="text-xs uppercase tracking-wide text-zinc-500">Drawdown</p>
                                    <p className="mt-2 text-lg font-bold text-amber-300">{fmtPercent(latestRun?.summary?.maxDrawdownPercent)}</p>
                                    <p className="mt-1 text-xs text-zinc-500">{latestRun?.summary?.tradeCount ?? 0} trades</p>
                                </div>
                            </div>
                        </div>
                    </div>
                    <div className="grid gap-6 xl:grid-cols-[0.8fr_1.2fr]">
                        <div className="space-y-6">
                            <div className="rounded-3xl border border-white/10 bg-black/35 p-6 backdrop-blur-xl">
                                <div className="flex flex-wrap items-start justify-between gap-4">
                                    <div>
                                        <p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Bot Analytics</p>
                                        <h2 className="mt-3 text-xl font-bold text-white">Run-level quality at the bot surface.</h2>
                                        <p className="mt-2 text-xs text-zinc-500">
                                            Scope: {boardRunMode === 'ALL' ? 'All runs' : boardRunMode === 'BACKTEST' ? 'Backtests only' : 'Forward tests only'}
                                            {' · '}
                                            {boardLookbackDays === 'ALL' ? 'All time' : `${boardLookbackDays} day lookback`}
                                            {activeBoardPresetMeta ? ` · Preset ${activeBoardPresetMeta.label}` : ''}
                                            {activeSavedBoardView ? ` · Saved ${activeSavedBoardView.name}` : ''}
                                        </p>
                                    </div>
                                    <div className="flex flex-wrap gap-2">
                                        <button
                                            type="button"
                                            onClick={() => void exportBotAnalytics('csv')}
                                            disabled={!selectedBotId || exportingFormat !== null}
                                            className="rounded-xl border border-cyan-500/20 bg-cyan-500/10 px-3 py-2 text-xs font-semibold text-cyan-100 disabled:cursor-not-allowed disabled:opacity-50"
                                        >
                                            {exportingFormat === 'csv' ? 'Exporting CSV...' : 'Export CSV'}
                                        </button>
                                        <button
                                            type="button"
                                            onClick={() => void exportBotAnalytics('json')}
                                            disabled={!selectedBotId || exportingFormat !== null}
                                            className="rounded-xl border border-white/10 bg-white/5 px-3 py-2 text-xs font-semibold text-zinc-200 disabled:cursor-not-allowed disabled:opacity-50"
                                        >
                                            {exportingFormat === 'json' ? 'Exporting JSON...' : 'Export JSON'}
                                        </button>
                                    </div>
                                </div>
                                {selectedBotAnalytics ? (
                                    <div className="mt-5 grid gap-4 sm:grid-cols-2">
                                        <div className="rounded-2xl border border-white/5 bg-black/20 p-4">
                                            <p className="text-xs uppercase tracking-wide text-zinc-500">Completed Runs</p>
                                            <p className="mt-2 text-lg font-bold text-white">{selectedBotAnalytics.completedRuns} / {selectedBotAnalytics.totalRuns}</p>
                                            <p className="mt-1 text-xs text-zinc-500">{selectedBotAnalytics.runningRuns} running, {selectedBotAnalytics.failedRuns} failed</p>
                                        </div>
                                        <div className="rounded-2xl border border-white/5 bg-black/20 p-4">
                                            <p className="text-xs uppercase tracking-wide text-zinc-500">Average Return</p>
                                            <p className="mt-2 text-lg font-bold text-emerald-300">{fmtPercent(selectedBotAnalytics.avgReturnPercent)}</p>
                                            <p className="mt-1 text-xs text-zinc-500">Avg PnL {fmtCurrency(selectedBotAnalytics.avgNetPnl)}</p>
                                        </div>
                                        <div className="rounded-2xl border border-white/5 bg-black/20 p-4">
                                            <p className="text-xs uppercase tracking-wide text-zinc-500">Average Drawdown</p>
                                            <p className="mt-2 text-lg font-bold text-amber-300">{fmtPercent(selectedBotAnalytics.avgMaxDrawdownPercent)}</p>
                                            <p className="mt-1 text-xs text-zinc-500">Avg win rate {fmtPercent(selectedBotAnalytics.avgWinRate)}</p>
                                        </div>
                                        <div className="rounded-2xl border border-white/5 bg-black/20 p-4">
                                            <p className="text-xs uppercase tracking-wide text-zinc-500">Payoff Shape</p>
                                            <p className="mt-2 text-lg font-bold text-cyan-200">{selectedBotAnalytics.avgProfitFactor?.toFixed(2) ?? 'N/A'} PF</p>
                                            <p className="mt-1 text-xs text-zinc-500">Expectancy {fmtCurrency(selectedBotAnalytics.avgExpectancyPerTrade)}</p>
                                        </div>
                                        <div className="rounded-2xl border border-white/5 bg-black/20 p-4">
                                            <p className="text-xs uppercase tracking-wide text-zinc-500">Completed Bias</p>
                                            <p className="mt-2 text-lg font-bold text-white">{selectedBotAnalytics.positiveCompletedRuns} up / {selectedBotAnalytics.negativeCompletedRuns} down</p>
                                            <p className="mt-1 text-xs text-zinc-500">{selectedBotAnalytics.totalSimulatedTrades} simulated trades</p>
                                        </div>
                                        <div className="rounded-2xl border border-white/5 bg-black/20 p-4">
                                            <p className="text-xs uppercase tracking-wide text-zinc-500">Live Forward State</p>
                                            <p className="mt-2 text-lg font-bold text-white">{selectedBotAnalytics.activeForwardRun ? selectedBotAnalytics.activeForwardRun.status : 'Idle'}</p>
                                            <p className="mt-1 text-xs text-zinc-500">
                                                {selectedBotAnalytics.activeForwardRun
                                                    ? fmtEpoch(selectedBotAnalytics.activeForwardRun.lastEvaluatedOpenTime)
                                                    : `${selectedBotAnalytics.cancelledRuns} cancelled · no running forward test`}
                                            </p>
                                        </div>
                                    </div>
                                ) : (
                                    <p className="mt-4 text-sm text-zinc-500">No bot analytics loaded yet.</p>
                                )}
                            </div>
                            <div className="rounded-3xl border border-white/10 bg-black/35 p-6 backdrop-blur-xl">
                                <p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Driver Totals</p>
                                {selectedBotAnalytics ? (
                                    <div className="mt-5 grid gap-4 sm:grid-cols-2">
                                        <div className="rounded-2xl border border-white/5 bg-black/20 p-4">
                                            <p className="text-xs uppercase tracking-wide text-zinc-500">Entry Drivers</p>
                                            <div className="mt-3 flex flex-wrap gap-2">
                                                {Object.entries(selectedBotAnalytics.entryDriverTotals ?? {}).length === 0 ? (
                                                    <span className="text-sm text-zinc-500">No entry signals aggregated yet.</span>
                                                ) : Object.entries(selectedBotAnalytics.entryDriverTotals).map(([rule, count]) => (
                                                    <span key={rule} className="rounded-full border border-cyan-500/20 bg-cyan-500/10 px-2 py-1 text-[11px] text-cyan-100">{rule} x{count}</span>
                                                ))}
                                            </div>
                                        </div>
                                        <div className="rounded-2xl border border-white/5 bg-black/20 p-4">
                                            <p className="text-xs uppercase tracking-wide text-zinc-500">Exit Drivers</p>
                                            <div className="mt-3 flex flex-wrap gap-2">
                                                {Object.entries(selectedBotAnalytics.exitDriverTotals ?? {}).length === 0 ? (
                                                    <span className="text-sm text-zinc-500">No exit signals aggregated yet.</span>
                                                ) : Object.entries(selectedBotAnalytics.exitDriverTotals).map(([rule, count]) => (
                                                    <span key={rule} className="rounded-full border border-emerald-500/20 bg-emerald-500/10 px-2 py-1 text-[11px] text-emerald-100">{rule} x{count}</span>
                                                ))}
                                            </div>
                                        </div>
                                    </div>
                                ) : (
                                    <p className="mt-4 text-sm text-zinc-500">Select a bot with runs to see aggregated driver totals.</p>
                                )}
                            </div>
                        </div>
                        <div className="rounded-3xl border border-white/10 bg-black/35 p-6 backdrop-blur-xl">
                            <div className="flex items-start justify-between gap-4">
                                <div>
                                    <p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Run Scorecards</p>
                                    <h2 className="mt-2 text-xl font-bold text-white">Recent reporting table for the selected bot.</h2>
                                </div>
                                {selectedBotAnalytics?.bestRun ? (
                                    <div className="rounded-2xl border border-emerald-500/20 bg-emerald-500/10 px-4 py-3 text-right text-xs text-emerald-100">
                                        <p className="uppercase tracking-[0.2em] text-emerald-200/80">Best Run</p>
                                        <p className="mt-1 text-sm font-bold">{fmtPercent(selectedBotAnalytics.bestRun.returnPercent)}</p>
                                        <p className="mt-1 text-[11px]">{selectedBotAnalytics.bestRun.runMode}</p>
                                    </div>
                                ) : null}
                            </div>
                            {selectedBotAnalytics?.recentScorecards?.length ? (
                                <div className="mt-5 overflow-hidden rounded-2xl border border-white/5 bg-black/20">
                                    <div className="grid grid-cols-[1.1fr_0.9fr_0.9fr_0.9fr_0.9fr_0.8fr] gap-3 border-b border-white/5 px-4 py-3 text-[10px] uppercase tracking-[0.2em] text-zinc-500">
                                        <span>Run</span>
                                        <span>Return</span>
                                        <span>PnL</span>
                                        <span>Drawdown</span>
                                        <span>Trades</span>
                                        <span>State</span>
                                    </div>
                                    {selectedBotAnalytics.recentScorecards.map((scorecard) => (
                                        <div key={scorecard.id} className="grid grid-cols-[1.1fr_0.9fr_0.9fr_0.9fr_0.9fr_0.8fr] gap-3 border-b border-white/5 px-4 py-3 text-xs text-zinc-300 last:border-b-0">
                                            <div className="min-w-0">
                                                <p className="truncate font-semibold text-white">{scorecard.runMode}</p>
                                                <p className="mt-1 text-[11px] text-zinc-500">{fmtDate(scorecard.requestedAt)}</p>
                                            </div>
                                            <div className={scorecard.returnPercent != null && scorecard.returnPercent >= 0 ? 'font-semibold text-emerald-200' : 'font-semibold text-red-200'}>
                                                {fmtPercent(scorecard.returnPercent)}
                                            </div>
                                            <div>{fmtCurrency(scorecard.netPnl)}</div>
                                            <div>{fmtPercent(scorecard.maxDrawdownPercent)}</div>
                                            <div>{scorecard.tradeCount ?? 0}</div>
                                            <div>
                                                <p className="font-semibold text-white">{scorecard.status}</p>
                                                <p className="mt-1 text-[11px] text-zinc-500">{scorecard.executionEngineReady ? 'ready' : 'review'}</p>
                                            </div>
                                        </div>
                                    ))}
                                </div>
                            ) : (
                                <p className="mt-5 text-sm text-zinc-500">No recent scorecards yet for the selected bot.</p>
                            )}
                        </div>
                    </div>
                    <div className="rounded-3xl border border-white/10 bg-black/35 p-6 backdrop-blur-xl">
                        <div className="flex flex-wrap items-start justify-between gap-4">
                            <div>
                                <p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Bot Board</p>
                                <h2 className="mt-2 text-xl font-bold text-white">Account-wide comparison surface for deterministic bots.</h2>
                                <p className="mt-2 text-sm text-zinc-500">Sort by one quality axis, then jump directly into the bot you want to inspect.</p>
                                <p className="mt-2 text-xs text-zinc-500">
                                    Scope: {boardRunMode === 'ALL' ? 'All runs' : boardRunMode === 'BACKTEST' ? 'Backtests only' : 'Forward tests only'}
                                    {' · '}
                                    {boardLookbackDays === 'ALL' ? 'All time' : `${boardLookbackDays} day lookback`}
                                    {activeBoardPresetMeta ? ` · Preset ${activeBoardPresetMeta.label}` : ''}
                                    {activeSavedBoardView ? ` · Saved ${activeSavedBoardView.name}` : ''}
                                </p>
                                <div className="mt-4 grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
                                    {boardComparisonPresets.map((preset) => (
                                        <button
                                            key={preset.id}
                                            type="button"
                                            onClick={() => applyBoardPreset(preset.id)}
                                            className={`min-w-0 rounded-2xl border px-4 py-3 text-left transition ${
                                                activeBoardPreset === preset.id
                                                    ? 'border-cyan-500/30 bg-cyan-500/10 text-cyan-100'
                                                    : 'border-white/10 bg-white/[0.03] text-zinc-200 hover:bg-white/[0.06]'
                                            }`}
                                        >
                                            <p className="truncate text-xs font-semibold uppercase tracking-[0.18em]">{preset.label}</p>
                                            <p className="mt-2 line-clamp-2 text-[11px] text-zinc-400">{preset.description}</p>
                                        </button>
                                    ))}
                                </div>
                                <div className="mt-4 rounded-2xl border border-white/10 bg-black/20 p-4">
                                    <div className="flex flex-wrap items-center gap-3">
                                        <input
                                            value={boardViewName}
                                            onChange={(event) => setBoardViewName(event.target.value)}
                                            placeholder="Save current board lens"
                                            className="min-w-[220px] flex-1 rounded-xl border border-white/10 bg-zinc-950/70 px-3 py-2 text-sm text-white outline-none focus:border-cyan-500/40"
                                        />
                                        <button
                                            type="button"
                                            onClick={saveCurrentBoardView}
                                            className="rounded-xl border border-cyan-500/20 bg-cyan-500/10 px-3 py-2 text-xs font-semibold text-cyan-100"
                                        >
                                            Save View
                                        </button>
                                        <button
                                            type="button"
                                            onClick={exportSavedBoardViews}
                                            disabled={savedBoardViews.length === 0}
                                            className="rounded-xl border border-white/10 bg-white/5 px-3 py-2 text-xs font-semibold text-zinc-200 disabled:cursor-not-allowed disabled:opacity-50"
                                        >
                                            Export Views
                                        </button>
                                        <button
                                            type="button"
                                            onClick={() => boardViewImportInputRef.current?.click()}
                                            className="rounded-xl border border-white/10 bg-white/5 px-3 py-2 text-xs font-semibold text-zinc-200"
                                        >
                                            Import Views
                                        </button>
                                    </div>
                                    <input
                                        ref={boardViewImportInputRef}
                                        type="file"
                                        accept="application/json"
                                        className="hidden"
                                        onChange={importSavedBoardViews}
                                    />
                                    <div className="mt-4 flex flex-wrap gap-2">
                                        {savedBoardViews.length === 0 ? (
                                            <span className="text-xs text-zinc-500">No saved board views yet.</span>
                                        ) : savedBoardViews.map((view) => (
                                            <div
                                                key={view.id}
                                                className={`flex items-center gap-2 rounded-full border px-3 py-1.5 text-[11px] ${
                                                    activeSavedBoardView?.id === view.id
                                                        ? 'border-cyan-500/30 bg-cyan-500/10 text-cyan-100'
                                                        : 'border-white/10 bg-white/[0.03] text-zinc-300'
                                                }`}
                                            >
                                                <button
                                                    type="button"
                                                    onClick={() => applySavedBoardView(view)}
                                                    className="truncate font-semibold"
                                                >
                                                    {view.name}
                                                </button>
                                                <span className="text-zinc-500">{view.runMode} · {view.lookbackDays}</span>
                                                <button
                                                    type="button"
                                                    onClick={() => deleteSavedBoardView(view.id)}
                                                    className="rounded-full border border-white/10 px-2 py-0.5 text-[10px] uppercase tracking-[0.18em] text-zinc-400"
                                                >
                                                    Remove
                                                </button>
                                                <button
                                                    type="button"
                                                    onClick={() => void shareSavedBoardView(view)}
                                                    className="rounded-full border border-cyan-500/20 px-2 py-0.5 text-[10px] uppercase tracking-[0.18em] text-cyan-300"
                                                >
                                                    Share
                                                </button>
                                            </div>
                                        ))}
                                    </div>
                                </div>
                            </div>
                            <div className="flex flex-wrap gap-2">
                                {([
                                    ['AVG_RETURN', 'Avg Return'],
                                    ['AVG_NET_PNL', 'Avg PnL'],
                                    ['TOTAL_RUNS', 'Runs'],
                                    ['AVG_WIN_RATE', 'Win Rate'],
                                    ['AVG_PROFIT_FACTOR', 'Profit Factor'],
                                    ['LATEST_REQUESTED_AT', 'Latest'],
                                ] as const).map(([value, label]) => (
                                    <button
                                        key={value}
                                        type="button"
                                        onClick={() => setBoardSortBy(value)}
                                        className={`rounded-full border px-3 py-1.5 text-[11px] font-semibold uppercase tracking-[0.18em] ${
                                            boardSortBy === value
                                                ? 'border-cyan-500/30 bg-cyan-500/10 text-cyan-100'
                                                : 'border-white/10 bg-white/5 text-zinc-300'
                                        }`}
                                    >
                                    {label}
                                </button>
                            ))}
                                <button
                                    type="button"
                                    onClick={() => void exportBoard('csv')}
                                    disabled={exportingBoardFormat !== null}
                                    className="rounded-full border border-cyan-500/20 bg-cyan-500/10 px-3 py-1.5 text-[11px] font-semibold uppercase tracking-[0.18em] text-cyan-100 disabled:cursor-not-allowed disabled:opacity-50"
                                >
                                    {exportingBoardFormat === 'csv' ? 'Exporting Board CSV...' : 'Export Board CSV'}
                                </button>
                                <button
                                    type="button"
                                    onClick={() => void exportBoard('json')}
                                    disabled={exportingBoardFormat !== null}
                                    className="rounded-full border border-white/10 bg-white/5 px-3 py-1.5 text-[11px] font-semibold uppercase tracking-[0.18em] text-zinc-300 disabled:cursor-not-allowed disabled:opacity-50"
                                >
                                    {exportingBoardFormat === 'json' ? 'Exporting Board JSON...' : 'Export Board JSON'}
                                </button>
                                <button
                                    type="button"
                                    onClick={() => setBoardDirection((current) => current === 'DESC' ? 'ASC' : 'DESC')}
                                    className="rounded-full border border-white/10 bg-white/5 px-3 py-1.5 text-[11px] font-semibold uppercase tracking-[0.18em] text-zinc-300"
                                >
                                    {boardDirection}
                                </button>
                            </div>
                            <div className="mt-3 flex flex-wrap items-center gap-2">
                                {[
                                    ['ALL', 'All Runs'],
                                    ['BACKTEST', 'Backtests'],
                                    ['FORWARD_TEST', 'Forward Tests'],
                                ].map(([value, label]) => (
                                    <button
                                        key={value}
                                        type="button"
                                        onClick={() => setBoardRunMode(value as BotBoardRunMode)}
                                        className={`rounded-full border px-3 py-1.5 text-[11px] font-semibold uppercase tracking-[0.18em] transition ${
                                            boardRunMode === value
                                                ? 'border-emerald-500/30 bg-emerald-500/10 text-emerald-100'
                                                : 'border-white/10 bg-white/5 text-zinc-300'
                                        }`}
                                    >
                                        {label}
                                    </button>
                                ))}
                                {[
                                    ['ALL', 'All Time'],
                                    ['7', '7D'],
                                    ['30', '30D'],
                                    ['90', '90D'],
                                ].map(([value, label]) => (
                                    <button
                                        key={value}
                                        type="button"
                                        onClick={() => setBoardLookbackDays(value as BotBoardLookback)}
                                        className={`rounded-full border px-3 py-1.5 text-[11px] font-semibold uppercase tracking-[0.18em] transition ${
                                            boardLookbackDays === value
                                                ? 'border-fuchsia-500/30 bg-fuchsia-500/10 text-fuchsia-100'
                                                : 'border-white/10 bg-white/5 text-zinc-300'
                                        }`}
                                    >
                                        {label}
                                    </button>
                                ))}
                            </div>
                        </div>
                        {botBoard.length ? (
                            <div className="mt-5 overflow-hidden rounded-2xl border border-white/5 bg-black/20">
                                <div className="grid grid-cols-[1.3fr_0.8fr_0.8fr_0.7fr_0.7fr_0.9fr] gap-3 border-b border-white/5 px-4 py-3 text-[10px] uppercase tracking-[0.2em] text-zinc-500">
                                    <span>Bot</span>
                                    <span>Avg Return</span>
                                    <span>Avg PnL</span>
                                    <span>Runs</span>
                                    <span>PF</span>
                                    <span>Latest</span>
                                </div>
                                {botBoard.map((entry) => (
                                    <button
                                        key={entry.strategyBotId}
                                        type="button"
                                        onClick={() => setSelectedBotId(entry.strategyBotId)}
                                        className={`grid w-full grid-cols-[1.3fr_0.8fr_0.8fr_0.7fr_0.7fr_0.9fr] gap-3 border-b border-white/5 px-4 py-3 text-left text-xs text-zinc-300 transition last:border-b-0 hover:bg-white/[0.03] ${selectedBotId === entry.strategyBotId ? 'bg-cyan-500/10' : ''}`}
                                    >
                                        <div className="min-w-0">
                                            <p className="truncate font-semibold text-white">{entry.name}</p>
                                            <p className="mt-1 text-[11px] text-zinc-500">{entry.symbol} · {entry.timeframe} · {entry.status}</p>
                                        </div>
                                        <div className={entry.avgReturnPercent != null && entry.avgReturnPercent >= 0 ? 'font-semibold text-emerald-200' : 'font-semibold text-red-200'}>
                                            {fmtPercent(entry.avgReturnPercent)}
                                        </div>
                                        <div>{fmtCurrency(entry.avgNetPnl)}</div>
                                        <div>
                                            <p className="font-semibold text-white">{entry.completedRuns} / {entry.totalRuns}</p>
                                            <p className="mt-1 text-[11px] text-zinc-500">{entry.totalSimulatedTrades} trades · {entry.cancelledRuns} cancelled</p>
                                        </div>
                                        <div>{entry.avgProfitFactor?.toFixed(2) ?? 'N/A'}</div>
                                        <div>
                                            <p className="font-semibold text-white">{fmtDate(entry.latestRequestedAt)}</p>
                                            <p className="mt-1 text-[11px] text-zinc-500">{entry.activeForwardRun ? 'live forward' : entry.bestRun ? `best ${fmtPercent(entry.bestRun.returnPercent)}` : 'no live run'}</p>
                                        </div>
                                    </button>
                                ))}
                            </div>
                        ) : (
                            <p className="mt-5 text-sm text-zinc-500">No strategy bots available for board comparison yet.</p>
                        )}
                    </div>
                </section>
            )}

            {!loading && tab === 'BOTS' && (
                <section className="mt-8 grid gap-6 xl:grid-cols-[0.95fr_1.05fr]">
                    <div className="rounded-3xl border border-white/10 bg-black/35 p-6 backdrop-blur-xl">
                        <div className="flex items-start justify-between gap-4">
                            <div>
                                <p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Builder</p>
                                <h2 className="mt-2 text-xl font-bold text-white">{editingBotId ? 'Edit bot' : 'Create bot'}</h2>
                            </div>
                            {editingBotId && <button type="button" onClick={resetBotForm} className="rounded-full border border-white/10 bg-white/5 px-3 py-1.5 text-[11px] font-bold uppercase tracking-[0.2em] text-zinc-300">Reset</button>}
                        </div>
                        <form className="mt-6 space-y-4" onSubmit={saveBot}>
                            <div className="grid gap-4 md:grid-cols-2">
                                <input value={botForm.name} onChange={(event) => setBotForm((current) => ({ ...current, name: event.target.value }))} className="rounded-2xl border border-white/10 bg-zinc-950/70 px-4 py-3 text-sm text-white outline-none focus:border-cyan-500/40" placeholder="BTC pullback bot" required />
                                <select value={botForm.linkedPortfolioId} onChange={(event) => setBotForm((current) => ({ ...current, linkedPortfolioId: event.target.value }))} className="rounded-2xl border border-white/10 bg-zinc-950/70 px-4 py-3 text-sm text-white outline-none focus:border-cyan-500/40" required>
                                    {portfolios.map((portfolio) => <option key={portfolio.id} value={portfolio.id}>{portfolio.name} ({portfolio.visibility ?? 'PRIVATE'})</option>)}
                                </select>
                            </div>
                            <textarea value={botForm.description} onChange={(event) => setBotForm((current) => ({ ...current, description: event.target.value }))} className="min-h-20 w-full rounded-2xl border border-white/10 bg-zinc-950/70 px-4 py-3 text-sm text-white outline-none focus:border-cyan-500/40" placeholder="Mean reversion with explicit risk exits." />
                            <div className="grid gap-4 md:grid-cols-4">
                                <input value={botForm.market} onChange={(event) => setBotForm((current) => ({ ...current, market: event.target.value.toUpperCase() }))} className="rounded-2xl border border-white/10 bg-zinc-950/70 px-4 py-3 text-sm text-white outline-none focus:border-cyan-500/40" placeholder="Market" required />
                                <input value={botForm.symbol} onChange={(event) => setBotForm((current) => ({ ...current, symbol: event.target.value.toUpperCase() }))} className="rounded-2xl border border-white/10 bg-zinc-950/70 px-4 py-3 text-sm text-white outline-none focus:border-cyan-500/40" placeholder="Symbol" required />
                                <input value={botForm.timeframe} onChange={(event) => setBotForm((current) => ({ ...current, timeframe: event.target.value }))} className="rounded-2xl border border-white/10 bg-zinc-950/70 px-4 py-3 text-sm text-white outline-none focus:border-cyan-500/40" placeholder="Timeframe" required />
                                <select value={botForm.status} onChange={(event) => setBotForm((current) => ({ ...current, status: event.target.value as BotStatus }))} className="rounded-2xl border border-white/10 bg-zinc-950/70 px-4 py-3 text-sm text-white outline-none focus:border-cyan-500/40">
                                    <option value="DRAFT">DRAFT</option>
                                    <option value="READY">READY</option>
                                    <option value="ARCHIVED">ARCHIVED</option>
                                </select>
                            </div>
                            <div className="grid gap-4 md:grid-cols-4">
                                <input value={botForm.maxPositionSizePercent} onChange={(event) => setBotForm((current) => ({ ...current, maxPositionSizePercent: event.target.value }))} className="rounded-2xl border border-white/10 bg-zinc-950/70 px-4 py-3 text-sm text-white outline-none focus:border-cyan-500/40" placeholder="Max position %" />
                                <input value={botForm.stopLossPercent} onChange={(event) => setBotForm((current) => ({ ...current, stopLossPercent: event.target.value }))} className="rounded-2xl border border-white/10 bg-zinc-950/70 px-4 py-3 text-sm text-white outline-none focus:border-cyan-500/40" placeholder="Stop loss %" />
                                <input value={botForm.takeProfitPercent} onChange={(event) => setBotForm((current) => ({ ...current, takeProfitPercent: event.target.value }))} className="rounded-2xl border border-white/10 bg-zinc-950/70 px-4 py-3 text-sm text-white outline-none focus:border-cyan-500/40" placeholder="Take profit %" />
                                <input value={botForm.cooldownMinutes} onChange={(event) => setBotForm((current) => ({ ...current, cooldownMinutes: event.target.value }))} className="rounded-2xl border border-white/10 bg-zinc-950/70 px-4 py-3 text-sm text-white outline-none focus:border-cyan-500/40" placeholder="Cooldown min" />
                            </div>
                            <div className="space-y-4">
                                <RuleBuilderPanel
                                    scope="ENTRY"
                                    title="Entry Rules"
                                    description="Compose deterministic entry logic from the supported signal library. The builder writes the same JSON payload the backend already stores."
                                    rawValue={botForm.entryRulesText}
                                    parsed={entryRuleSet}
                                    accentClass="border-cyan-500/20 bg-cyan-500/10 text-cyan-100"
                                    onOperatorChange={(operator) => changeRuleOperator('entryRulesText', entryRuleSet, operator)}
                                    onRuleChange={(index, next) => patchRuleDraft('entryRulesText', entryRuleSet, index, next)}
                                    onAddRule={(templateId) => addRuleDraft('entryRulesText', entryRuleSet, templateId)}
                                    onRemoveRule={(index) => removeRuleDraft('entryRulesText', entryRuleSet, index)}
                                    onRawChange={(next) => setBotForm((current) => ({ ...current, entryRulesText: next }))}
                                    onReset={() => setBotForm((current) => ({ ...current, entryRulesText: emptyRuleSetJson }))}
                                />
                                <RuleBuilderPanel
                                    scope="EXIT"
                                    title="Exit Rules"
                                    description="Mix indicator exits with stop-loss and take-profit guards. Risk-exit tokens use the numeric controls above."
                                    rawValue={botForm.exitRulesText}
                                    parsed={exitRuleSet}
                                    accentClass="border-emerald-500/20 bg-emerald-500/10 text-emerald-100"
                                    onOperatorChange={(operator) => changeRuleOperator('exitRulesText', exitRuleSet, operator)}
                                    onRuleChange={(index, next) => patchRuleDraft('exitRulesText', exitRuleSet, index, next)}
                                    onAddRule={(templateId) => addRuleDraft('exitRulesText', exitRuleSet, templateId)}
                                    onRemoveRule={(index) => removeRuleDraft('exitRulesText', exitRuleSet, index)}
                                    onRawChange={(next) => setBotForm((current) => ({ ...current, exitRulesText: next }))}
                                    onReset={() => setBotForm((current) => ({ ...current, exitRulesText: emptyRuleSetJson }))}
                                />
                            </div>
                            <div className="flex flex-wrap gap-3">
                                <button type="submit" disabled={saving || portfolios.length === 0} className="rounded-2xl border border-cyan-500/30 bg-cyan-500/15 px-5 py-3 text-sm font-bold text-cyan-100 disabled:opacity-60">{saving ? 'Saving...' : editingBotId ? 'Update Bot' : 'Create Bot'}</button>
                                <button type="button" onClick={resetBotForm} className="rounded-2xl border border-white/10 bg-white/5 px-5 py-3 text-sm font-semibold text-zinc-300">Clear</button>
                            </div>
                        </form>
                    </div>
                    <div className="rounded-3xl border border-white/10 bg-black/35 p-6 backdrop-blur-xl">
                        <div className="flex items-start justify-between gap-4">
                            <div>
                                <p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Bot List</p>
                                <h2 className="mt-2 text-xl font-bold text-white">Drafts, ready bots, archives</h2>
                            </div>
                            <button type="button" onClick={() => userId && void bootstrap(userId)} className="rounded-full border border-white/10 bg-white/5 px-3 py-1.5 text-[11px] font-bold uppercase tracking-[0.2em] text-zinc-300">Refresh</button>
                        </div>
                        <div className="mt-6 space-y-4">
                            {bots.length === 0 ? (
                                <div className="rounded-2xl border border-dashed border-white/10 bg-black/20 px-5 py-8 text-sm text-zinc-400">No strategy bots yet.</div>
                            ) : bots.map((bot) => (
                                <div key={bot.id} className={`rounded-2xl border px-5 py-4 ${selectedBotId === bot.id ? 'border-cyan-500/35 bg-cyan-500/10' : 'border-white/10 bg-black/20'}`}>
                                    <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
                                        <div className="min-w-0">
                                            <div className="flex flex-wrap items-center gap-2">
                                                <p className="text-lg font-bold text-white">{bot.name}</p>
                                                <span className={`rounded-full px-2 py-0.5 text-[10px] font-bold ${bot.status === 'READY' ? 'bg-emerald-500/15 text-emerald-200' : bot.status === 'ARCHIVED' ? 'bg-zinc-500/15 text-zinc-300' : 'bg-amber-500/15 text-amber-200'}`}>{bot.status}</span>
                                            </div>
                                            <p className="mt-1 text-sm text-zinc-400">{bot.description || 'No description yet.'}</p>
                                            <p className="mt-2 text-xs text-zinc-500">{bot.market} / {bot.symbol} / {bot.timeframe} / updated {fmtDate(bot.updatedAt)}</p>
                                        </div>
                                        <div className="flex flex-wrap gap-2">
                                            <button type="button" onClick={() => { setSelectedBotId(bot.id); setTab('RUNS'); }} className="rounded-xl border border-cyan-500/20 bg-cyan-500/10 px-3 py-2 text-xs font-semibold text-cyan-100">Runs</button>
                                            <button type="button" onClick={() => { setEditingBotId(bot.id); setBotForm({ name: bot.name, description: bot.description ?? '', linkedPortfolioId: bot.linkedPortfolioId, market: bot.market, symbol: bot.symbol, timeframe: bot.timeframe, status: bot.status, maxPositionSizePercent: bot.maxPositionSizePercent?.toString() ?? '', stopLossPercent: bot.stopLossPercent?.toString() ?? '', takeProfitPercent: bot.takeProfitPercent?.toString() ?? '', cooldownMinutes: bot.cooldownMinutes?.toString() ?? '', entryRulesText: pretty(bot.entryRules), exitRulesText: pretty(bot.exitRules) }); setNotice(`Editing ${bot.name}`); setActionError(null); }} className="rounded-xl border border-white/10 bg-white/5 px-3 py-2 text-xs font-semibold text-zinc-200">Edit</button>
                                            <button type="button" onClick={() => void deleteBot(bot.id)} className="rounded-xl border border-red-500/20 bg-red-500/10 px-3 py-2 text-xs font-semibold text-red-100">Delete</button>
                                        </div>
                                    </div>
                                </div>
                            ))}
                        </div>
                    </div>
                </section>
            )}

            {!loading && tab === 'RUNS' && (
                <section className="mt-8 grid gap-6 xl:grid-cols-[0.85fr_1.15fr]">
                    <div className="rounded-3xl border border-white/10 bg-black/35 p-6 backdrop-blur-xl">
                        <p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Run Control</p>
                        <h2 className="mt-2 text-xl font-bold text-white">{selectedBot?.name ?? 'Select a bot first'}</h2>
                        <div className="mt-5 space-y-4">
                            <select value={selectedBotId} onChange={(event) => setSelectedBotId(event.target.value)} className="w-full rounded-2xl border border-white/10 bg-zinc-950/70 px-4 py-3 text-sm text-white outline-none focus:border-cyan-500/40">
                                <option value="">Select bot</option>
                                {bots.map((bot) => <option key={bot.id} value={bot.id}>{bot.name} ({bot.status})</option>)}
                            </select>
                            {selectedBot && (
                                <div className="grid gap-3 md:grid-cols-2">
                                    <div className="rounded-2xl border border-white/5 bg-black/20 p-4">
                                        <p className="text-xs uppercase tracking-wide text-zinc-500">Risk Envelope</p>
                                        <p className="mt-2 text-sm text-zinc-300">{fmtPercent(selectedBot.maxPositionSizePercent)} max position, stop {fmtPercent(selectedBot.stopLossPercent)}, take {fmtPercent(selectedBot.takeProfitPercent)}</p>
                                    </div>
                                    <div className="rounded-2xl border border-white/5 bg-black/20 p-4">
                                        <p className="text-xs uppercase tracking-wide text-zinc-500">Compiler Input</p>
                                        <p className="mt-2 text-sm text-zinc-300">{pretty(selectedBot.entryRules).split('\n')[0]}</p>
                                    </div>
                                </div>
                            )}
                            <form className="space-y-4" onSubmit={requestRun}>
                                <div className="grid gap-4 md:grid-cols-2">
                                    <select value={runForm.runMode} onChange={(event) => setRunForm((current) => ({ ...current, runMode: event.target.value as RunMode }))} className="rounded-2xl border border-white/10 bg-zinc-950/70 px-4 py-3 text-sm text-white outline-none focus:border-cyan-500/40">
                                        <option value="BACKTEST">BACKTEST</option>
                                        <option value="FORWARD_TEST">FORWARD_TEST</option>
                                    </select>
                                    <input value={runForm.initialCapital} onChange={(event) => setRunForm((current) => ({ ...current, initialCapital: event.target.value }))} className="rounded-2xl border border-white/10 bg-zinc-950/70 px-4 py-3 text-sm text-white outline-none focus:border-cyan-500/40" placeholder="Initial capital (optional)" />
                                </div>
                                <div className="grid gap-4 md:grid-cols-2">
                                    <input type="date" value={runForm.fromDate} onChange={(event) => setRunForm((current) => ({ ...current, fromDate: event.target.value }))} className="rounded-2xl border border-white/10 bg-zinc-950/70 px-4 py-3 text-sm text-white outline-none focus:border-cyan-500/40" />
                                    <input type="date" value={runForm.toDate} onChange={(event) => setRunForm((current) => ({ ...current, toDate: event.target.value }))} className="rounded-2xl border border-white/10 bg-zinc-950/70 px-4 py-3 text-sm text-white outline-none focus:border-cyan-500/40" />
                                </div>
                                <div className="flex flex-wrap gap-3">
                                    <button type="submit" disabled={!selectedBotId || requestingRun} className="rounded-2xl border border-emerald-500/30 bg-emerald-500/15 px-5 py-3 text-sm font-bold text-emerald-100 disabled:opacity-60">{requestingRun ? 'Queueing...' : 'Request Run'}</button>
                                    <button type="button" onClick={() => setRunForm({ runMode: 'BACKTEST', initialCapital: '', fromDate: '', toDate: '' })} className="rounded-2xl border border-white/10 bg-white/5 px-5 py-3 text-sm font-semibold text-zinc-300">Reset</button>
                                </div>
                            </form>
                        </div>
                    </div>
                    <div className="rounded-3xl border border-white/10 bg-black/35 p-6 backdrop-blur-xl">
                        <div className="flex items-start justify-between gap-4">
                            <div>
                                <p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Run Journal</p>
                                <h2 className="mt-2 text-xl font-bold text-white">Queued, executed, and summarized runs</h2>
                            </div>
                            <button type="button" onClick={() => selectedBotId && void loadRuns(selectedBotId)} className="rounded-full border border-white/10 bg-white/5 px-3 py-1.5 text-[11px] font-bold uppercase tracking-[0.2em] text-zinc-300">Refresh</button>
                        </div>
                        <div className="mt-6 space-y-4">
                            {!selectedBotId ? (
                                <div className="rounded-2xl border border-dashed border-white/10 bg-black/20 px-5 py-8 text-sm text-zinc-400">Select a bot to load runs.</div>
                            ) : runs.length === 0 ? (
                                <div className="rounded-2xl border border-dashed border-white/10 bg-black/20 px-5 py-8 text-sm text-zinc-400">No runs yet for this bot.</div>
                            ) : runs.map((run) => (
                                <div key={run.id} className={`rounded-2xl border p-5 ${selectedRunId === run.id ? 'border-cyan-500/35 bg-cyan-500/10' : 'border-white/10 bg-black/20'}`}>
                                    <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
                                        <div>
                                            <div className="flex flex-wrap items-center gap-2">
                                                <p className="text-lg font-bold text-white">{run.runMode}</p>
                                                <span className={`rounded-full px-2 py-0.5 text-[10px] font-bold ${runStatusClasses(run.status)}`}>{run.status}</span>
                                            </div>
                                            <p className="mt-1 text-xs text-zinc-500">Requested {fmtDate(run.requestedAt)}</p>
                                            {run.completedAt && (
                                                <p className="mt-1 text-xs text-zinc-500">
                                                    {run.status === 'CANCELLED' ? 'Cancelled' : run.status === 'FAILED' ? 'Ended' : 'Completed'} {fmtDate(run.completedAt)}
                                                </p>
                                            )}
                                            <p className="mt-2 text-sm text-zinc-400">Window {run.fromDate ?? 'auto'} to {run.toDate ?? 'auto'} | Equity {fmtCurrency(run.summary?.endingEquity)}</p>
                                        </div>
                                        <div className="flex flex-wrap gap-2">
                                            {run.status === 'QUEUED' && (
                                                <button type="button" onClick={() => void executeRun(run.id)} disabled={executingRunId === run.id || cancellingRunId === run.id} className="rounded-xl border border-cyan-500/20 bg-cyan-500/10 px-3 py-2 text-xs font-semibold text-cyan-100 disabled:opacity-60">
                                                    {executingRunId === run.id ? 'Executing...' : run.runMode === 'FORWARD_TEST' ? 'Start Forward Test' : 'Execute Backtest'}
                                                </button>
                                            )}
                                            {run.runMode === 'FORWARD_TEST' && run.status === 'RUNNING' && (
                                                <button type="button" onClick={() => void refreshRun(run.id)} disabled={refreshingRunId === run.id || cancellingRunId === run.id} className="rounded-xl border border-emerald-500/20 bg-emerald-500/10 px-3 py-2 text-xs font-semibold text-emerald-100 disabled:opacity-60">
                                                    {refreshingRunId === run.id ? 'Refreshing...' : 'Refresh Snapshot'}
                                                </button>
                                            )}
                                            {isRunCancellable(run.status) && (
                                                <button
                                                    type="button"
                                                    onClick={() => void cancelRun(run.id)}
                                                    disabled={cancellingRunId === run.id || executingRunId === run.id || refreshingRunId === run.id}
                                                    className="rounded-xl border border-zinc-500/20 bg-zinc-500/10 px-3 py-2 text-xs font-semibold text-zinc-200 disabled:opacity-60"
                                                >
                                                    {cancellingRunId === run.id ? 'Cancelling...' : 'Cancel Run'}
                                                </button>
                                            )}
                                            <button type="button" onClick={() => selectRun(run.id)} className="rounded-xl border border-white/10 bg-white/5 px-3 py-2 text-xs font-semibold text-zinc-200">
                                                {selectedRunId === run.id ? 'Selected' : 'Inspect'}
                                            </button>
                                        </div>
                                    </div>
                                    {run.errorMessage && <div className="mt-4 rounded-xl border border-red-500/20 bg-red-500/10 px-4 py-3 text-sm text-red-100">{run.errorMessage}</div>}
                                    <div className="mt-4 grid gap-3 md:grid-cols-4">
                                        <div className="rounded-xl border border-white/5 bg-black/20 p-3"><p className="text-[10px] uppercase tracking-[0.22em] text-zinc-500">Return</p><p className="mt-2 text-sm font-bold text-emerald-300">{fmtPercent(run.summary?.returnPercent)}</p></div>
                                        <div className="rounded-xl border border-white/5 bg-black/20 p-3"><p className="text-[10px] uppercase tracking-[0.22em] text-zinc-500">PnL</p><p className="mt-2 text-sm font-bold text-white">{fmtCurrency(run.summary?.netPnl)}</p></div>
                                        <div className="rounded-xl border border-white/5 bg-black/20 p-3"><p className="text-[10px] uppercase tracking-[0.22em] text-zinc-500">Trades</p><p className="mt-2 text-sm font-bold text-white">{run.summary?.tradeCount ?? 0}</p></div>
                                        <div className="rounded-xl border border-white/5 bg-black/20 p-3"><p className="text-[10px] uppercase tracking-[0.22em] text-zinc-500">Drawdown</p><p className="mt-2 text-sm font-bold text-amber-300">{fmtPercent(run.summary?.maxDrawdownPercent)}</p></div>
                                    </div>
                                    {selectedRunId === run.id && (
                                        <div className="mt-4 grid gap-4 xl:grid-cols-[0.8fr_1.2fr]">
                                            <div className="space-y-4">
                                                <div className="rounded-2xl border border-white/5 bg-black/20 p-4">
                                                    <p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Compiler</p>
                                                    <p className="mt-3 text-sm text-zinc-300">Ready: {selectedRun?.summary?.executionEngineReady ? 'Yes' : 'No'}</p>
                                                    <p className="mt-2 text-sm text-zinc-300">Features: {(selectedRun?.summary?.supportedFeatures ?? []).join(', ') || 'None'}</p>
                                                    <p className="mt-2 text-sm text-zinc-300">Unsupported: {(selectedRun?.summary?.unsupportedRules ?? []).join(', ') || 'None'}</p>
                                                    <p className="mt-2 text-sm text-zinc-300">Warnings: {(selectedRun?.summary?.warnings ?? []).join(' | ') || 'None'}</p>
                                                    {selectedRun?.runMode === 'FORWARD_TEST' && (
                                                        <>
                                                            <p className="mt-2 text-sm text-zinc-300">Last Evaluated: {selectedRun?.summary?.lastEvaluatedOpenTime ? fmtEpoch(selectedRun.summary.lastEvaluatedOpenTime) : 'N/A'}</p>
                                                            <p className="mt-2 text-sm text-zinc-300">Live Position: {selectedRun?.summary?.positionOpen ? `Open @ ${fmtCurrency(selectedRun?.summary?.openEntryPrice)} / ${selectedRun?.summary?.openQuantity?.toFixed(4)}` : 'Flat'}</p>
                                                        </>
                                                    )}
                                                </div>
                                                <div className="rounded-2xl border border-white/5 bg-black/20 p-4">
                                                    <p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Run Detail Tabs</p>
                                                    <p className="mt-3 text-sm text-zinc-300">Heavy journals now load only when their tab is opened.</p>
                                                    <div className="mt-4 flex flex-wrap gap-2">
                                                        {([
                                                            ['SUMMARY', 'Summary'],
                                                            ['FILLS', 'Fills'],
                                                            ['EVENTS', 'Events'],
                                                            ['EQUITY', 'Equity'],
                                                            ['RECONCILIATION', 'Reconciliation'],
                                                            ['RAW', 'Raw'],
                                                        ] as Array<[RunDetailTab, string]>).map(([detailTab, label]) => (
                                                            <button
                                                                key={detailTab}
                                                                type="button"
                                                                onClick={() => setRunDetailTab(detailTab)}
                                                                className={`rounded-full border px-3 py-1.5 text-[11px] font-semibold uppercase tracking-[0.18em] ${
                                                                    runDetailTab === detailTab
                                                                        ? 'border-cyan-500/30 bg-cyan-500/15 text-cyan-100'
                                                                        : 'border-white/10 bg-white/5 text-zinc-400'
                                                                }`}
                                                            >
                                                                {label}
                                                            </button>
                                                        ))}
                                                    </div>
                                                </div>
                                                {runDetailTab === 'SUMMARY' && (
                                                    <div className="rounded-2xl border border-white/5 bg-black/20 p-4">
                                                        <p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Run Summary</p>
                                                        <p className="mt-3 text-sm text-zinc-300">Use the tabs above to open persisted fills, audited decisions, equity points, reconciliation state, or the raw export preview only when needed.</p>
                                                        <div className="mt-4 grid gap-3 sm:grid-cols-2">
                                                            <div className="rounded-xl border border-white/5 bg-black/25 p-3 text-xs text-zinc-300">
                                                                <p className="text-zinc-500">Audited Decisions</p>
                                                                <p className="mt-1 font-bold text-white">{selectedRun?.summary?.eventCount ?? 0}</p>
                                                            </div>
                                                            <div className="rounded-xl border border-white/5 bg-black/25 p-3 text-xs text-zinc-300">
                                                                <p className="text-zinc-500">Persisted Fills</p>
                                                                <p className="mt-1 font-bold text-white">{selectedRun?.summary?.tradeCount ?? 0}</p>
                                                            </div>
                                                            <div className="rounded-xl border border-white/5 bg-black/25 p-3 text-xs text-zinc-300">
                                                                <p className="text-zinc-500">Linked Portfolio</p>
                                                                <p className="mt-1 font-bold text-white">{selectedRun?.summary?.linkedPortfolioName ?? 'None'}</p>
                                                            </div>
                                                            <div className="rounded-xl border border-white/5 bg-black/25 p-3 text-xs text-zinc-300">
                                                                <p className="text-zinc-500">Execution Ready</p>
                                                                <p className="mt-1 font-bold text-white">{selectedRun?.summary?.executionEngineReady ? 'Yes' : 'No'}</p>
                                                            </div>
                                                        </div>
                                                    </div>
                                                )}
                                                {runDetailTab === 'FILLS' && (
                                                    <div className="rounded-2xl border border-white/5 bg-black/20 p-4">
                                                    <p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Persisted Fills</p>
                                                    {outputLoadingState.fills && !selectedRunFillsLoaded ? (
                                                        <div className="mt-3 h-24 animate-pulse rounded-xl bg-white/5" />
                                                    ) : selectedRunFills.length === 0 ? (
                                                        <p className="mt-3 text-sm text-zinc-500">No persisted fills yet for this run.</p>
                                                    ) : (
                                                        <>
                                                            <div className="mt-3 flex items-center justify-between gap-3 text-xs text-zinc-500">
                                                                <span>{selectedRunFillsPageMeta.totalElements || selectedRun?.summary?.tradeCount || selectedRunFills.length} persisted fills</span>
                                                                <span>Loaded {selectedRunFills.length}</span>
                                                            </div>
                                                            <div className="mt-3 space-y-2">
                                                                {selectedRunFills.map((fill) => (
                                                                    <div key={fill.id} className="rounded-xl border border-white/5 bg-black/25 px-3 py-2 text-xs text-zinc-300">
                                                                        <div className="flex items-center justify-between gap-3">
                                                                            <span className={`font-bold ${fill.side === 'ENTRY' ? 'text-cyan-200' : 'text-emerald-200'}`}>{fill.side}</span>
                                                                            <span>{fmtEpoch(fill.openTime)}</span>
                                                                        </div>
                                                                        <div className="mt-2 flex flex-wrap gap-3 text-zinc-400">
                                                                            <span>Price {fmtCurrency(fill.price)}</span>
                                                                            <span>Qty {fill.quantity.toFixed(4)}</span>
                                                                            <span>PnL {fmtCurrency(fill.realizedPnl)}</span>
                                                                        </div>
                                                                    </div>
                                                                ))}
                                                            </div>
                                                            {hasMoreRunFills && selectedRun ? (
                                                                <div className="mt-3 flex justify-end">
                                                                    <button
                                                                        type="button"
                                                                        onClick={() => void loadRunFills(selectedBotId, selectedRun.id, selectedRunFillsPageMeta.pageNumber + 1, true)}
                                                                        disabled={outputLoadingState.fills}
                                                                        className="rounded-full border border-white/10 bg-white/5 px-3 py-2 text-[11px] font-semibold uppercase tracking-[0.18em] text-zinc-200 disabled:cursor-not-allowed disabled:opacity-50"
                                                                    >
                                                                        {outputLoadingState.fills ? 'Loading...' : 'Load More Fills'}
                                                                    </button>
                                                                </div>
                                                            ) : null}
                                                        </>
                                                    )}
                                                </div>
                                                )}
                                                {runDetailTab === 'EVENTS' && (
                                                    <div className="rounded-2xl border border-white/5 bg-black/20 p-4">
                                                    <p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Decision Journal</p>
                                                    {outputLoadingState.events && !selectedRunEventsLoaded ? (
                                                        <div className="mt-3 h-28 animate-pulse rounded-xl bg-white/5" />
                                                    ) : selectedRunEvents.length === 0 ? (
                                                        <p className="mt-3 text-sm text-zinc-500">No persisted decision events yet for this run.</p>
                                                    ) : (
                                                        <>
                                                            <div className="mt-3 flex items-center justify-between gap-3 text-xs text-zinc-500">
                                                                <span>{selectedRunEventsPageMeta.totalElements || selectedRun?.summary?.eventCount || selectedRunEvents.length} audited decisions</span>
                                                                <span>Loaded {selectedRunEvents.length}</span>
                                                            </div>
                                                            <div className="mt-3 space-y-2">
                                                                {selectedRunEvents.map((event) => (
                                                                    <div key={event.id} className="rounded-xl border border-white/5 bg-black/25 px-3 py-3 text-xs text-zinc-300">
                                                                        <div className="flex flex-wrap items-center justify-between gap-3">
                                                                            <div className="flex flex-wrap items-center gap-2">
                                                                                <span className="rounded-full border border-white/10 bg-white/5 px-2 py-1 text-[10px] font-bold tracking-[0.18em] text-zinc-200">{event.phase}</span>
                                                                                <span className="font-semibold text-cyan-100">{event.action}</span>
                                                                            </div>
                                                                            <span>{fmtEpoch(event.openTime)}</span>
                                                                        </div>
                                                                        <div className="mt-2 flex flex-wrap gap-3 text-zinc-400">
                                                                            <span>Close {fmtCurrency(event.closePrice)}</span>
                                                                            <span>Cash {fmtCurrency(event.cashBalance)}</span>
                                                                            <span>Qty {event.positionQuantity.toFixed(4)}</span>
                                                                            <span>Equity {fmtCurrency(event.equity)}</span>
                                                                        </div>
                                                                        {event.matchedRules.length > 0 && (
                                                                            <div className="mt-2 flex flex-wrap gap-2">
                                                                                {event.matchedRules.map((rule) => (
                                                                                    <span key={`${event.id}-${rule}`} className="rounded-full border border-cyan-500/20 bg-cyan-500/10 px-2 py-1 text-[11px] text-cyan-100">{rule}</span>
                                                                                ))}
                                                                            </div>
                                                                        )}
                                                                        {event.details && Object.keys(event.details).length > 0 && (
                                                                            <div className="mt-2 flex flex-wrap gap-2 text-[11px] text-zinc-500">
                                                                                {Object.entries(event.details).slice(0, 4).map(([key, value]) => (
                                                                                    <span key={`${event.id}-${key}`} className="rounded-full border border-white/5 bg-black/20 px-2 py-1">{key}: {String(value)}</span>
                                                                                ))}
                                                                            </div>
                                                                        )}
                                                                    </div>
                                                                ))}
                                                            </div>
                                                            {hasMoreRunEvents && selectedRun ? (
                                                                <div className="mt-3 flex justify-end">
                                                                    <button
                                                                        type="button"
                                                                        onClick={() => void loadRunEvents(selectedBotId, selectedRun.id, selectedRunEventsPageMeta.pageNumber + 1, true)}
                                                                        disabled={outputLoadingState.events}
                                                                        className="rounded-full border border-white/10 bg-white/5 px-3 py-2 text-[11px] font-semibold uppercase tracking-[0.18em] text-zinc-200 disabled:cursor-not-allowed disabled:opacity-50"
                                                                    >
                                                                        {outputLoadingState.events ? 'Loading...' : 'Load More Events'}
                                                                    </button>
                                                                </div>
                                                            ) : null}
                                                        </>
                                                    )}
                                                </div>
                                                )}
                                                <div className="rounded-2xl border border-white/5 bg-black/20 p-4">
                                                    <p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Outcome Quality</p>
                                                    <div className="mt-3 grid gap-3 sm:grid-cols-2">
                                                        <div className="rounded-xl border border-white/5 bg-black/25 p-3 text-xs text-zinc-300">
                                                            <p className="text-zinc-500">Avg Win</p>
                                                            <p className="mt-1 font-bold text-emerald-200">{fmtCurrency(selectedRun?.summary?.avgWinPnl)}</p>
                                                        </div>
                                                        <div className="rounded-xl border border-white/5 bg-black/25 p-3 text-xs text-zinc-300">
                                                            <p className="text-zinc-500">Avg Loss</p>
                                                            <p className="mt-1 font-bold text-red-200">{fmtCurrency(selectedRun?.summary?.avgLossPnl)}</p>
                                                        </div>
                                                        <div className="rounded-xl border border-white/5 bg-black/25 p-3 text-xs text-zinc-300">
                                                            <p className="text-zinc-500">Profit Factor</p>
                                                            <p className="mt-1 font-bold text-white">{selectedRun?.summary?.profitFactor?.toFixed(2) ?? 'N/A'}</p>
                                                        </div>
                                                        <div className="rounded-xl border border-white/5 bg-black/25 p-3 text-xs text-zinc-300">
                                                            <p className="text-zinc-500">Expectancy / Trade</p>
                                                            <p className="mt-1 font-bold text-white">{fmtCurrency(selectedRun?.summary?.expectancyPerTrade)}</p>
                                                        </div>
                                                        <div className="rounded-xl border border-white/5 bg-black/25 p-3 text-xs text-zinc-300">
                                                            <p className="text-zinc-500">Best Trade</p>
                                                            <p className="mt-1 font-bold text-emerald-200">{fmtCurrency(selectedRun?.summary?.bestTradePnl)}</p>
                                                        </div>
                                                        <div className="rounded-xl border border-white/5 bg-black/25 p-3 text-xs text-zinc-300">
                                                            <p className="text-zinc-500">Worst Trade</p>
                                                            <p className="mt-1 font-bold text-red-200">{fmtCurrency(selectedRun?.summary?.worstTradePnl)}</p>
                                                        </div>
                                                    </div>
                                                </div>
                                                <div className="rounded-2xl border border-white/5 bg-black/20 p-4">
                                                    <p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Execution Attribution</p>
                                                    <div className="mt-3 grid gap-3 sm:grid-cols-2">
                                                        <div className="rounded-xl border border-white/5 bg-black/25 p-3 text-xs text-zinc-300">
                                                            <p className="text-zinc-500">Avg Hold</p>
                                                            <p className="mt-1 font-bold text-white">{selectedRun?.summary?.avgHoldHours != null ? `${selectedRun.summary.avgHoldHours.toFixed(2)}h` : 'N/A'}</p>
                                                        </div>
                                                        <div className="rounded-xl border border-white/5 bg-black/25 p-3 text-xs text-zinc-300">
                                                            <p className="text-zinc-500">Max Hold</p>
                                                            <p className="mt-1 font-bold text-white">{selectedRun?.summary?.maxHoldHours != null ? `${selectedRun.summary.maxHoldHours.toFixed(2)}h` : 'N/A'}</p>
                                                        </div>
                                                        <div className="rounded-xl border border-white/5 bg-black/25 p-3 text-xs text-zinc-300">
                                                            <p className="text-zinc-500">Time In Market</p>
                                                            <p className="mt-1 font-bold text-cyan-100">{fmtPercent(selectedRun?.summary?.timeInMarketPercent)}</p>
                                                        </div>
                                                        <div className="rounded-xl border border-white/5 bg-black/25 p-3 text-xs text-zinc-300">
                                                            <p className="text-zinc-500">Avg Exposure</p>
                                                            <p className="mt-1 font-bold text-cyan-100">{fmtPercent(selectedRun?.summary?.avgExposurePercent)}</p>
                                                        </div>
                                                    </div>
                                                    <div className="mt-4 grid gap-3 sm:grid-cols-2">
                                                        <div className="rounded-xl border border-white/5 bg-black/25 p-3 text-xs text-zinc-300">
                                                            <p className="text-zinc-500">Entry Drivers</p>
                                                            <div className="mt-2 flex flex-wrap gap-2">
                                                                {Object.entries(selectedRun?.summary?.entryReasonCounts ?? {}).length === 0 ? (
                                                                    <span className="text-zinc-500">None</span>
                                                                ) : Object.entries(selectedRun?.summary?.entryReasonCounts ?? {}).map(([rule, count]) => (
                                                                    <span key={rule} className="rounded-full border border-cyan-500/20 bg-cyan-500/10 px-2 py-1 text-[11px] text-cyan-100">{rule} x{count}</span>
                                                                ))}
                                                            </div>
                                                        </div>
                                                        <div className="rounded-xl border border-white/5 bg-black/25 p-3 text-xs text-zinc-300">
                                                            <p className="text-zinc-500">Exit Drivers</p>
                                                            <div className="mt-2 flex flex-wrap gap-2">
                                                                {Object.entries(selectedRun?.summary?.exitReasonCounts ?? {}).length === 0 ? (
                                                                    <span className="text-zinc-500">None</span>
                                                                ) : Object.entries(selectedRun?.summary?.exitReasonCounts ?? {}).map(([rule, count]) => (
                                                                    <span key={rule} className="rounded-full border border-emerald-500/20 bg-emerald-500/10 px-2 py-1 text-[11px] text-emerald-100">{rule} x{count}</span>
                                                                ))}
                                                            </div>
                                                        </div>
                                                    </div>
                                                </div>
                                                {runDetailTab === 'RECONCILIATION' && (
                                                    <div className="rounded-2xl border border-white/5 bg-black/20 p-4">
                                                    <p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Portfolio Reconciliation</p>
                                                    {!selectedRunReconciliationLoaded ? (
                                                        <div className="mt-3 h-32 animate-pulse rounded-xl bg-white/5" />
                                                    ) : (
                                                        <>
                                                    <div className="mt-3 grid gap-3 sm:grid-cols-2">
                                                        <div className="rounded-xl border border-white/5 bg-black/25 p-3 text-xs text-zinc-300">
                                                            <p className="text-zinc-500">Linked Portfolio</p>
                                                            <p className="mt-1 font-bold text-white">{selectedRunReconciliation?.linkedPortfolioName ?? selectedRun?.summary?.linkedPortfolioName ?? 'None'}</p>
                                                            <p className="mt-1 text-zinc-500">{selectedRun?.summary?.linkedPortfolioReconciliationBaseline ?? 'N/A'}</p>
                                                        </div>
                                                        <div className="rounded-xl border border-white/5 bg-black/25 p-3 text-xs text-zinc-300">
                                                            <p className="text-zinc-500">Alignment</p>
                                                            <p className={`mt-1 font-bold ${(selectedRunReconciliation?.portfolioAligned ?? selectedRun?.summary?.linkedPortfolioAligned) ? 'text-emerald-200' : 'text-amber-200'}`}>
                                                                {(selectedRunReconciliation?.portfolioAligned ?? selectedRun?.summary?.linkedPortfolioAligned) == null ? 'N/A' : (selectedRunReconciliation?.portfolioAligned ?? selectedRun?.summary?.linkedPortfolioAligned) ? 'Aligned' : 'Drifted'}
                                                            </p>
                                                        </div>
                                                        <div className="rounded-xl border border-white/5 bg-black/25 p-3 text-xs text-zinc-300">
                                                            <p className="text-zinc-500">Portfolio Balance</p>
                                                            <p className="mt-1 font-bold text-white">{fmtCurrency(selectedRunReconciliation?.currentCashBalance ?? selectedRun?.summary?.linkedPortfolioBalance)}</p>
                                                        </div>
                                                        <div className="rounded-xl border border-white/5 bg-black/25 p-3 text-xs text-zinc-300">
                                                            <p className="text-zinc-500">Reference Equity</p>
                                                            <p className="mt-1 font-bold text-white">{fmtCurrency(selectedRunReconciliation?.targetEquity ?? selectedRun?.summary?.linkedPortfolioReferenceEquity)}</p>
                                                        </div>
                                                        <div className="rounded-xl border border-white/5 bg-black/25 p-3 text-xs text-zinc-300">
                                                            <p className="text-zinc-500">Drift</p>
                                                            <p className={`mt-1 font-bold ${(selectedRunReconciliation?.cashDelta ?? selectedRun?.summary?.linkedPortfolioDrift ?? 0) >= 0 ? 'text-emerald-200' : 'text-red-200'}`}>{fmtCurrency(selectedRunReconciliation?.cashDelta ?? selectedRun?.summary?.linkedPortfolioDrift)}</p>
                                                        </div>
                                                        <div className="rounded-xl border border-white/5 bg-black/25 p-3 text-xs text-zinc-300">
                                                            <p className="text-zinc-500">Drift %</p>
                                                            <p className={`mt-1 font-bold ${(selectedRun?.summary?.linkedPortfolioDriftPercent ?? 0) >= 0 ? 'text-emerald-200' : 'text-red-200'}`}>{fmtPercent(selectedRun?.summary?.linkedPortfolioDriftPercent)}</p>
                                                        </div>
                                                    </div>
                                                    {selectedRunReconciliation && (
                                                        <div className="mt-4 grid gap-3 sm:grid-cols-2">
                                                            <div className="rounded-xl border border-white/5 bg-black/25 p-3 text-xs text-zinc-300">
                                                                <p className="text-zinc-500">Target Position</p>
                                                                <p className="mt-1 font-bold text-white">{selectedRunReconciliation.targetPositionOpen ? `${selectedRunReconciliation.targetQuantity.toFixed(4)} @ ${fmtCurrency(selectedRunReconciliation.targetAveragePrice)}` : 'Flat'}</p>
                                                            </div>
                                                            <div className="rounded-xl border border-white/5 bg-black/25 p-3 text-xs text-zinc-300">
                                                                <p className="text-zinc-500">Current Position</p>
                                                                <p className="mt-1 font-bold text-white">{selectedRunReconciliation.currentQuantity > 0 ? `${selectedRunReconciliation.currentQuantity.toFixed(4)} @ ${fmtCurrency(selectedRunReconciliation.currentAveragePrice)}` : 'Flat'}</p>
                                                            </div>
                                                        </div>
                                                    )}
                                                    {selectedRunReconciliation ? (
                                                        <div className="mt-4 flex flex-wrap items-center gap-3">
                                                            <button
                                                                type="button"
                                                                onClick={() => void applyReconciliation(selectedRun.id)}
                                                                disabled={applyingRunId === selectedRun.id || selectedRunReconciliation.portfolioAligned || selectedRunReconciliation.warnings.length > 0}
                                                                className="rounded-xl border border-cyan-500/25 bg-cyan-500/10 px-3 py-2 text-xs font-semibold text-cyan-100 disabled:cursor-not-allowed disabled:opacity-50"
                                                            >
                                                                {applyingRunId === selectedRun.id ? 'Applying...' : selectedRunReconciliation.portfolioAligned ? 'Portfolio Synced' : 'Apply Snapshot To Portfolio'}
                                                            </button>
                                                            <span className="text-[11px] text-zinc-500">
                                                                {selectedRunReconciliation.warnings.length > 0
                                                                    ? 'Manual cleanup required before sync'
                                                                    : 'State sync is audited and updates linked cash/position only'}
                                                            </span>
                                                        </div>
                                                    ) : null}
                                                    {selectedRunReconciliation?.warnings?.length ? (
                                                        <div className="mt-4 rounded-xl border border-amber-500/20 bg-amber-500/10 px-3 py-3 text-xs text-amber-100">
                                                            {selectedRunReconciliation.warnings.join(' | ')}
                                                        </div>
                                                    ) : null}
                                                        </>
                                                    )}
                                                </div>
                                                )}
                                            </div>
                                            <div className="space-y-4">
                                                {runDetailTab === 'EQUITY' && (
                                                    <div className="rounded-2xl border border-white/5 bg-black/20 p-4">
                                                    <p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Persisted Equity Curve</p>
                                                    {outputLoadingState.equity && !selectedRunEquityCurveLoaded ? (
                                                        <div className="mt-3 h-32 animate-pulse rounded-xl bg-white/5" />
                                                    ) : selectedRunEquityCurve.length < 2 ? (
                                                        <p className="mt-3 text-sm text-zinc-500">No persisted equity points yet for this run.</p>
                                                    ) : (
                                                        <>
                                                            <div className="mt-3 overflow-hidden rounded-xl border border-white/5 bg-black/25 p-3">
                                                                <svg viewBox="0 0 320 96" className="h-32 w-full">
                                                                    <defs>
                                                                        <linearGradient id="bot-equity-fill" x1="0" x2="0" y1="0" y2="1">
                                                                            <stop offset="0%" stopColor="rgba(34,211,238,0.35)" />
                                                                            <stop offset="100%" stopColor="rgba(34,211,238,0.02)" />
                                                                        </linearGradient>
                                                                    </defs>
                                                                    <polyline
                                                                        fill="none"
                                                                        stroke="#67e8f9"
                                                                        strokeWidth="2.5"
                                                                        points={buildSparkline(selectedRunEquityCurve)}
                                                                    />
                                                                </svg>
                                                            </div>
                                                            <div className="mt-3 grid gap-3 md:grid-cols-3">
                                                                <div className="rounded-xl border border-white/5 bg-black/25 p-3 text-xs text-zinc-300">
                                                                    <p className="text-zinc-500">Start</p>
                                                                    <p className="mt-1 font-bold text-white">{fmtCurrency(selectedRunEquityCurve[0]?.equity)}</p>
                                                                </div>
                                                                <div className="rounded-xl border border-white/5 bg-black/25 p-3 text-xs text-zinc-300">
                                                                    <p className="text-zinc-500">End</p>
                                                                    <p className="mt-1 font-bold text-white">{fmtCurrency(selectedRunEquityCurve[selectedRunEquityCurve.length - 1]?.equity)}</p>
                                                                </div>
                                                                <div className="rounded-xl border border-white/5 bg-black/25 p-3 text-xs text-zinc-300">
                                                                    <p className="text-zinc-500">Points</p>
                                                                    <p className="mt-1 font-bold text-white">{selectedRunEquityCurve.length} / {selectedRunEquityCurvePageMeta.totalElements || selectedRunEquityCurve.length}</p>
                                                                </div>
                                                            </div>
                                                            {hasMoreRunEquityCurve && selectedRun ? (
                                                                <div className="mt-3 flex justify-end">
                                                                    <button
                                                                        type="button"
                                                                        onClick={() => void loadRunEquityCurve(selectedBotId, selectedRun.id, selectedRunEquityCurvePageMeta.pageNumber + 1, true)}
                                                                        disabled={outputLoadingState.equity}
                                                                        className="rounded-full border border-white/10 bg-white/5 px-3 py-2 text-[11px] font-semibold uppercase tracking-[0.18em] text-zinc-200 disabled:cursor-not-allowed disabled:opacity-50"
                                                                    >
                                                                        {outputLoadingState.equity ? 'Loading...' : 'Load More Points'}
                                                                    </button>
                                                                </div>
                                                            ) : null}
                                                        </>
                                                    )}
                                                </div>
                                                )}
                                                {runDetailTab === 'RAW' && (
                                                    <div className="rounded-2xl border border-white/5 bg-black/20 p-4">
                                                    <div className="flex flex-wrap items-center justify-between gap-3">
                                                        <p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Raw Outputs</p>
                                                        <div className="flex flex-wrap gap-2">
                                                            <button
                                                                type="button"
                                                                onClick={() => void exportRun('csv')}
                                                                disabled={exportingRunFormat !== null}
                                                                className="rounded-full border border-cyan-500/25 bg-cyan-500/10 px-3 py-1.5 text-[11px] font-semibold uppercase tracking-[0.18em] text-cyan-100 disabled:cursor-not-allowed disabled:opacity-50"
                                                            >
                                                                {exportingRunFormat === 'csv' ? 'Exporting CSV...' : 'Export Run CSV'}
                                                            </button>
                                                            <button
                                                                type="button"
                                                                onClick={() => void exportRun('json')}
                                                                disabled={exportingRunFormat !== null}
                                                                className="rounded-full border border-white/10 bg-white/5 px-3 py-1.5 text-[11px] font-semibold uppercase tracking-[0.18em] text-zinc-100 disabled:cursor-not-allowed disabled:opacity-50"
                                                            >
                                                                {exportingRunFormat === 'json' ? 'Exporting JSON...' : 'Export Run JSON'}
                                                            </button>
                                                        </div>
                                                    </div>
                                                    {!selectedRunEventsLoaded || !selectedRunFillsLoaded || !selectedRunEquityCurveLoaded || !selectedRunReconciliationLoaded ? (
                                                        <div className="mt-3 h-40 animate-pulse rounded-xl bg-white/5" />
                                                    ) : (
                                                        <div className="mt-3 max-h-56 overflow-auto rounded-xl border border-white/5 bg-black/25 p-3">
                                                            <pre className="whitespace-pre-wrap text-xs leading-6 text-zinc-300">{pretty({ events: selectedRunEvents, fills: selectedRunFills, equityCurve: selectedRunEquityCurve, reconciliationPlan: selectedRunReconciliation })}</pre>
                                                        </div>
                                                    )}
                                                </div>
                                                )}
                                            </div>
                                        </div>
                                    )}
                                </div>
                            ))}
                        </div>
                    </div>
                </section>
            )}
        </div>
    );
}
