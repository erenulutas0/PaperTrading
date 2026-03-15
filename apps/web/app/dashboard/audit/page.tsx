'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { apiFetch } from '../../../lib/api-client';

interface AuditEntry {
    id: string;
    actorId: string | null;
    actionType: string;
    resourceType: string;
    resourceId: string | null;
    requestId: string | null;
    ipAddress: string | null;
    requestMethod: string | null;
    requestPath: string | null;
    details: unknown;
    createdAt: string | null;
}

interface AuditResponse {
    checkedAt: string;
    limit: number;
    page: number;
    days: number | null;
    requestId: string | null;
    requestPath: string | null;
    actorId: string | null;
    actionType: string | null;
    resourceType: string | null;
    count: number;
    totalCount: number;
    hasMore: boolean;
    facets?: {
        actions?: { value: string; count: number }[];
        resources?: { value: string; count: number }[];
        actors?: { value: string; count: number }[];
    };
    entries: AuditEntry[];
}

const ACTION_OPTIONS = [
    'PORTFOLIO_CREATED',
    'PORTFOLIO_VISIBILITY_CHANGED',
    'PORTFOLIO_DEPOSITED',
    'PORTFOLIO_DELETED',
    'TRADE_BUY_EXECUTED',
    'TRADE_SELL_EXECUTED',
    'USER_FOLLOWED',
    'USER_UNFOLLOWED',
    'ANALYSIS_POST_CREATED',
    'ANALYSIS_POST_DELETED',
    'INTERACTION_LIKED',
    'INTERACTION_UNLIKED',
    'INTERACTION_COMMENTED',
] as const;

const RESOURCE_OPTIONS = [
    'PORTFOLIO',
    'TRADE',
    'USER',
    'ANALYSIS_POST',
    'COMMENT',
] as const;

const AUDIT_PRESETS = [
    {
        label: 'Portfolio Writes',
        description: 'Portfolio creation, funding, visibility, and deletion flow.',
        actionType: '',
        resourceType: 'PORTFOLIO',
    },
    {
        label: 'Trade Flow',
        description: 'Buy and sell executions on trade resources.',
        actionType: '',
        resourceType: 'TRADE',
    },
    {
        label: 'Follow Graph',
        description: 'User follow and unfollow actions.',
        actionType: 'USER_FOLLOWED',
        resourceType: 'USER',
    },
    {
        label: 'Analysis Lifecycle',
        description: 'Immutable post creation and tombstone activity.',
        actionType: 'ANALYSIS_POST_CREATED',
        resourceType: 'ANALYSIS_POST',
    },
    {
        label: 'Interactions',
        description: 'Like and comment events.',
        actionType: 'INTERACTION_COMMENTED',
        resourceType: 'COMMENT',
    },
] as const;

function formatTimestamp(value: string | null) {
    if (!value) {
        return 'N/A';
    }
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) {
        return value;
    }
    return date.toLocaleString();
}

function AuditSummaryCard({
    label,
    value,
}: {
    label: string;
    value: string;
}) {
    return (
        <div className="rounded-2xl border border-white/10 bg-black/30 p-4">
            <p className="text-[10px] uppercase tracking-[0.28em] text-zinc-500">{label}</p>
            <p className="mt-3 text-2xl font-black text-white">{value}</p>
        </div>
    );
}

function FacetChips({
    title,
    items,
    onSelect,
}: {
    title: string;
    items: { label: string; count: number; value: string }[];
    onSelect: (value: string) => void;
}) {
    if (items.length === 0) {
        return null;
    }

    return (
        <div className="rounded-2xl border border-white/10 bg-black/30 p-4">
            <p className="text-[10px] uppercase tracking-[0.28em] text-zinc-500">{title}</p>
            <div className="mt-4 flex flex-wrap gap-2">
                {items.map((item) => (
                    <button
                        key={`${title}-${item.value}`}
                        type="button"
                        onClick={() => onSelect(item.value)}
                        className="rounded-full border border-zinc-800 bg-black px-3 py-1.5 text-xs font-medium text-zinc-300 transition hover:border-primary/35 hover:text-white"
                    >
                        {item.label} <span className="text-zinc-500">({item.count})</span>
                    </button>
                ))}
            </div>
        </div>
    );
}

function ActiveFilterPill({
    label,
    value,
    onClear,
}: {
    label: string;
    value: string;
    onClear: () => void;
}) {
    return (
        <button
            type="button"
            onClick={onClear}
            className="rounded-full border border-primary/25 bg-primary/10 px-3 py-1.5 text-xs font-medium text-primary transition hover:bg-primary/20"
        >
            {label}: {value} <span className="ml-1 text-primary/70">x</span>
        </button>
    );
}

export default function AuditPage() {
    const [data, setData] = useState<AuditResponse | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [exporting, setExporting] = useState(false);
    const [copiedLink, setCopiedLink] = useState(false);
    const [copiedDetailField, setCopiedDetailField] = useState('');
    const [page, setPage] = useState(0);
    const [selectedEntryId, setSelectedEntryId] = useState<string | null>(null);
    const [filters, setFilters] = useState({
        limit: '20',
        days: '',
        requestId: '',
        requestPath: '',
        actorId: '',
        actionType: '',
        resourceType: '',
    });

    useEffect(() => {
        if (typeof window === 'undefined') {
            return;
        }

        const params = new URLSearchParams(window.location.search);
        setFilters((current) => ({
            ...current,
            limit: params.get('limit') || current.limit,
            days: params.get('days') || '',
            requestId: params.get('requestId') || '',
            requestPath: params.get('requestPath') || '',
            actorId: params.get('actorId') || '',
            actionType: params.get('actionType') || '',
            resourceType: params.get('resourceType') || '',
        }));
        setPage(Math.max(0, Number.parseInt(params.get('page') || '0', 10) || 0));
    }, []);

    const buildAuditQuery = useCallback((includePage: boolean) => {
        const params = new URLSearchParams();
        if (filters.limit) params.set('limit', filters.limit);
        if (includePage) params.set('page', page.toString());
        if (filters.days) params.set('days', filters.days);
        if (filters.requestId.trim()) params.set('requestId', filters.requestId.trim());
        if (filters.requestPath.trim()) params.set('requestPath', filters.requestPath.trim());
        if (filters.actorId.trim()) params.set('actorId', filters.actorId.trim());
        if (filters.actionType) params.set('actionType', filters.actionType);
        if (filters.resourceType) params.set('resourceType', filters.resourceType);
        return params;
    }, [filters, page]);

    const fetchAudit = useCallback(async () => {
        setLoading(true);
        setError('');
        try {
            const params = buildAuditQuery(true);
            const response = await apiFetch(`/api/v1/ops/auditlog?${params.toString()}`, {
                cache: 'no-store',
            });
            if (!response.ok) {
                const message = await response.text();
                throw new Error(message || `Audit fetch failed (${response.status})`);
            }

            const payload = await response.json() as AuditResponse;
            setData(payload);
        } catch (fetchError) {
            setError(fetchError instanceof Error ? fetchError.message : 'Failed to load audit log');
            setData(null);
        } finally {
            setLoading(false);
        }
    }, [buildAuditQuery]);

    useEffect(() => {
        void fetchAudit();
    }, [fetchAudit]);

    useEffect(() => {
        setPage(0);
    }, [filters.limit, filters.days, filters.requestId, filters.requestPath, filters.actorId, filters.actionType, filters.resourceType]);

    const filteredCountLabel = useMemo(() => {
        if (!data) {
            return '0';
        }
        return data.count.toString();
    }, [data]);

    const activeFilterCount = useMemo(() => {
        return [filters.days, filters.requestId, filters.requestPath, filters.actorId, filters.actionType, filters.resourceType].filter(Boolean).length;
    }, [filters]);

    const activeWindowLabel = useMemo(() => {
        if (!filters.days) {
            return 'ALL';
        }
        return `${filters.days}D`;
    }, [filters]);

    const activePreset = useMemo(() => {
        return AUDIT_PRESETS.find((preset) =>
            preset.actionType === filters.actionType &&
            preset.resourceType === filters.resourceType
        ) ?? null;
    }, [filters.actionType, filters.resourceType]);

    const activeFilterPills = useMemo(() => {
        const pills: { key: string; label: string; value: string; onClear: () => void }[] = [];
        if (filters.days) {
            pills.push({
                key: 'days',
                label: 'Window',
                value: `${filters.days}D`,
                onClear: () => setFilters((current) => ({ ...current, days: '' })),
            });
        }
        if (filters.requestId.trim()) {
            pills.push({
                key: 'requestId',
                label: 'Request',
                value: filters.requestId.trim(),
                onClear: () => setFilters((current) => ({ ...current, requestId: '' })),
            });
        }
        if (filters.requestPath.trim()) {
            pills.push({
                key: 'requestPath',
                label: 'Path',
                value: filters.requestPath.trim(),
                onClear: () => setFilters((current) => ({ ...current, requestPath: '' })),
            });
        }
        if (filters.actorId.trim()) {
            pills.push({
                key: 'actorId',
                label: 'Actor',
                value: filters.actorId.trim(),
                onClear: () => setFilters((current) => ({ ...current, actorId: '' })),
            });
        }
        if (filters.actionType) {
            pills.push({
                key: 'actionType',
                label: 'Action',
                value: filters.actionType,
                onClear: () => setFilters((current) => ({ ...current, actionType: '' })),
            });
        }
        if (filters.resourceType) {
            pills.push({
                key: 'resourceType',
                label: 'Resource',
                value: filters.resourceType,
                onClear: () => setFilters((current) => ({ ...current, resourceType: '' })),
            });
        }
        return pills;
    }, [filters]);

    const focusedRequestLead = useMemo(() => {
        if (!filters.requestId.trim() || !data?.entries.length) {
            return null;
        }
        return data.entries[0];
    }, [data, filters.requestId]);

    const selectedEntry = useMemo(() => {
        if (!data?.entries.length) {
            return null;
        }
        if (!selectedEntryId) {
            return data.entries[0];
        }
        return data.entries.find((entry) => entry.id === selectedEntryId) ?? data.entries[0];
    }, [data, selectedEntryId]);

    const topActionFacets = useMemo(() => {
        if (!data) {
            return [];
        }
        if (data.facets?.actions?.length) {
            return data.facets.actions.map((item) => ({ value: item.value, count: item.count, label: item.value }));
        }
        const counts = new Map<string, number>();
        data.entries.forEach((entry) => {
            counts.set(entry.actionType, (counts.get(entry.actionType) ?? 0) + 1);
        });
        return [...counts.entries()]
            .sort((a, b) => b[1] - a[1])
            .slice(0, 6)
            .map(([value, count]) => ({ value, count, label: value }));
    }, [data]);

    const topResourceFacets = useMemo(() => {
        if (!data) {
            return [];
        }
        if (data.facets?.resources?.length) {
            return data.facets.resources.map((item) => ({ value: item.value, count: item.count, label: item.value }));
        }
        const counts = new Map<string, number>();
        data.entries.forEach((entry) => {
            counts.set(entry.resourceType, (counts.get(entry.resourceType) ?? 0) + 1);
        });
        return [...counts.entries()]
            .sort((a, b) => b[1] - a[1])
            .slice(0, 6)
            .map(([value, count]) => ({ value, count, label: value }));
    }, [data]);

    const topActorFacets = useMemo(() => {
        if (!data) {
            return [];
        }
        if (data.facets?.actors?.length) {
            return data.facets.actors.map((item) => ({
                value: item.value,
                count: item.count,
                label: item.value.length > 12 ? `${item.value.slice(0, 8)}...` : item.value,
            }));
        }
        const counts = new Map<string, number>();
        data.entries.forEach((entry) => {
            if (!entry.actorId) {
                return;
            }
            counts.set(entry.actorId, (counts.get(entry.actorId) ?? 0) + 1);
        });
        return [...counts.entries()]
            .sort((a, b) => b[1] - a[1])
            .slice(0, 6)
            .map(([value, count]) => ({
                value,
                count,
                label: value.length > 12 ? `${value.slice(0, 8)}...` : value,
            }));
    }, [data]);

    const exportCsv = async () => {
        setExporting(true);
        try {
            const params = buildAuditQuery(false);
            const response = await apiFetch(`/api/v1/ops/auditlog/export?${params.toString()}`, {
                cache: 'no-store',
            });
            if (!response.ok) {
                const message = await response.text();
                throw new Error(message || `Audit export failed (${response.status})`);
            }

            const blob = await response.blob();
            const url = window.URL.createObjectURL(blob);
            const anchor = document.createElement('a');
            anchor.href = url;
            anchor.download = 'audit-log-export.csv';
            anchor.click();
            window.URL.revokeObjectURL(url);
        } catch (exportError) {
            setError(exportError instanceof Error ? exportError.message : 'Failed to export audit log');
        } finally {
            setExporting(false);
        }
    };

    const buildViewQuery = () => {
        const params = buildAuditQuery(false);
        params.set('page', page.toString());
        return params;
    };

    const copyViewLink = async () => {
        try {
            const params = buildViewQuery();
            const url = `${window.location.origin}/dashboard/audit?${params.toString()}`;
            await navigator.clipboard.writeText(url);
            setCopiedLink(true);
            window.setTimeout(() => setCopiedLink(false), 1600);
        } catch (copyError) {
            setError(copyError instanceof Error ? copyError.message : 'Failed to copy audit view link');
        }
    };

    const exportJson = async () => {
        try {
            const params = buildAuditQuery(true);
            const response = await apiFetch(`/api/v1/ops/auditlog/export/json?${params.toString()}`, {
                cache: 'no-store',
            });
            if (!response.ok) {
                const message = await response.text();
                throw new Error(message || `Audit JSON export failed (${response.status})`);
            }
            const blob = await response.blob();
            const url = window.URL.createObjectURL(blob);
            const anchor = document.createElement('a');
            anchor.href = url;
            anchor.download = 'audit-log-view.json';
            anchor.click();
            window.URL.revokeObjectURL(url);
        } catch (jsonError) {
            setError(jsonError instanceof Error ? jsonError.message : 'Failed to export audit JSON');
        }
    };

    const focusRequestId = (requestId: string | null) => {
        if (!requestId) {
            return;
        }
        setPage(0);
        setFilters((current) => ({
            ...current,
            requestId,
        }));
    };

    const copyDetailValue = async (label: string, value: string | null) => {
        if (!value) {
            return;
        }
        try {
            await navigator.clipboard.writeText(value);
            setCopiedDetailField(label);
            window.setTimeout(() => setCopiedDetailField(''), 1600);
        } catch (copyError) {
            setError(copyError instanceof Error ? copyError.message : `Failed to copy ${label}`);
        }
    };

    const copyFocusedViewLink = async (entry: AuditEntry) => {
        try {
            const params = new URLSearchParams();
            params.set('limit', filters.limit);
            params.set('page', '0');
            if (filters.days) params.set('days', filters.days);
            if (entry.requestId) params.set('requestId', entry.requestId);
            const url = `${window.location.origin}/dashboard/audit?${params.toString()}`;
            await navigator.clipboard.writeText(url);
            setCopiedDetailField('focusLink');
            window.setTimeout(() => setCopiedDetailField(''), 1600);
        } catch (copyError) {
            setError(copyError instanceof Error ? copyError.message : 'Failed to copy focused audit link');
        }
    };

    const copySliceLink = async (label: string, overrides: { actorId?: string; requestPath?: string; resourceType?: string; actionType?: string }) => {
        try {
            const params = new URLSearchParams();
            params.set('limit', filters.limit);
            params.set('page', '0');
            if (filters.days) params.set('days', filters.days);
            if (overrides.actorId) params.set('actorId', overrides.actorId);
            if (overrides.requestPath) params.set('requestPath', overrides.requestPath);
            if (overrides.resourceType) params.set('resourceType', overrides.resourceType);
            if (overrides.actionType) params.set('actionType', overrides.actionType);
            const url = `${window.location.origin}/dashboard/audit?${params.toString()}`;
            await navigator.clipboard.writeText(url);
            setCopiedDetailField(label);
            window.setTimeout(() => setCopiedDetailField(''), 1600);
        } catch (copyError) {
            setError(copyError instanceof Error ? copyError.message : `Failed to copy ${label}`);
        }
    };

    const openFocusedView = (entry: AuditEntry) => {
        if (!entry.requestId) {
            return;
        }
        focusRequestId(entry.requestId);
    };

    const openActorSlice = (entry: AuditEntry) => {
        if (!entry.actorId) {
            return;
        }
        setPage(0);
        setFilters((current) => ({ ...current, actorId: entry.actorId ?? '' }));
    };

    const openResourceSlice = (entry: AuditEntry) => {
        setPage(0);
        setFilters((current) => ({ ...current, resourceType: entry.resourceType }));
    };

    const openPathSlice = (entry: AuditEntry) => {
        if (!entry.requestPath) {
            return;
        }
        setPage(0);
        setFilters((current) => ({ ...current, requestPath: entry.requestPath ?? '' }));
    };

    useEffect(() => {
        if (!data?.entries.length) {
            setSelectedEntryId(null);
            return;
        }
        setSelectedEntryId((current) => {
            if (!current) {
                return data.entries[0].id;
            }
            return data.entries.some((entry) => entry.id === current) ? current : data.entries[0].id;
        });
    }, [data]);

    return (
        <div className="mx-auto max-w-[1400px] px-8 py-8 pb-20">
            <header className="rounded-3xl border border-white/10 bg-black/35 p-6 backdrop-blur-xl">
                <p className="text-[11px] uppercase tracking-[0.35em] text-zinc-500">Ops Audit</p>
                <h1 className="mt-3 text-4xl font-black text-white">Audit Log Inbox</h1>
                <p className="mt-3 max-w-3xl text-sm leading-7 text-zinc-400">
                    Filter write-side audit rows by actor, action, resource, and request correlation, then export the current slice without dropping to SQL.
                </p>
            </header>

            <section className="mt-6 grid gap-4 md:grid-cols-4">
                <AuditSummaryCard label="Loaded Rows" value={loading ? '...' : filteredCountLabel} />
                <AuditSummaryCard label="Active Filters" value={activeFilterCount.toString()} />
                <AuditSummaryCard label="Window" value={activeWindowLabel} />
                <AuditSummaryCard label="Checked At" value={data?.checkedAt ? new Date(data.checkedAt).toLocaleTimeString() : '...'} />
            </section>

            {activeFilterPills.length > 0 && (
                <section className="mt-6 rounded-3xl border border-white/10 bg-black/30 p-5">
                    <div className="flex flex-wrap items-center justify-between gap-3">
                        <div>
                            <p className="text-[10px] uppercase tracking-[0.28em] text-zinc-500">Active Filters</p>
                            <p className="mt-2 text-sm text-zinc-400">Click any chip to clear that slice without resetting the full audit view.</p>
                        </div>
                        <button
                            type="button"
                            onClick={() => setFilters({
                                limit: filters.limit,
                                days: '',
                                requestId: '',
                                requestPath: '',
                                actorId: '',
                                actionType: '',
                                resourceType: '',
                            })}
                            className="rounded-xl border border-zinc-800 bg-black px-4 py-2 text-xs font-semibold text-zinc-300 transition hover:border-zinc-700 hover:text-white"
                        >
                            Clear Active Filters
                        </button>
                    </div>
                    <div className="mt-4 flex flex-wrap gap-2">
                        {activeFilterPills.map((pill) => (
                            <ActiveFilterPill key={pill.key} label={pill.label} value={pill.value} onClear={pill.onClear} />
                        ))}
                    </div>
                </section>
            )}

            {focusedRequestLead && (
                <section className="mt-6 rounded-3xl border border-primary/20 bg-primary/5 p-5">
                    <div className="flex flex-wrap items-start justify-between gap-4">
                        <div>
                            <p className="text-[10px] uppercase tracking-[0.28em] text-primary/80">Request Focus</p>
                            <h2 className="mt-2 text-lg font-bold text-white">{filters.requestId.trim()}</h2>
                            <p className="mt-2 text-sm text-zinc-300">
                                Loaded {data?.count ?? 0} row{data?.count === 1 ? '' : 's'} for this request slice. Latest visible event is {focusedRequestLead.actionType} on {focusedRequestLead.resourceType}.
                            </p>
                        </div>
                        <button
                            type="button"
                            onClick={() => setFilters((current) => ({ ...current, requestId: '' }))}
                            className="rounded-xl border border-primary/30 bg-primary/10 px-4 py-2 text-xs font-semibold text-primary transition hover:bg-primary/20"
                        >
                            Clear Request Focus
                        </button>
                    </div>
                    <div className="mt-4 grid gap-3 md:grid-cols-3">
                        <div className="rounded-2xl border border-white/10 bg-black/30 p-3">
                            <p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Latest Method</p>
                            <p className="mt-2 text-sm font-semibold text-white">{focusedRequestLead.requestMethod ?? 'N/A'}</p>
                        </div>
                        <div className="rounded-2xl border border-white/10 bg-black/30 p-3">
                            <p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Latest Path</p>
                            <p className="mt-2 break-all text-sm font-semibold text-white">{focusedRequestLead.requestPath ?? 'N/A'}</p>
                        </div>
                        <div className="rounded-2xl border border-white/10 bg-black/30 p-3">
                            <p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Latest Event Time</p>
                            <p className="mt-2 text-sm font-semibold text-white">{formatTimestamp(focusedRequestLead.createdAt)}</p>
                        </div>
                    </div>
                </section>
            )}

            {!loading && data && data.entries.length > 0 && (
                <section className="mt-6 grid gap-4 xl:grid-cols-3">
                    <FacetChips
                        title="Top Actions"
                        items={topActionFacets}
                        onSelect={(value) => {
                            setPage(0);
                            setFilters((current) => ({ ...current, actionType: value }));
                        }}
                    />
                    <FacetChips
                        title="Top Resources"
                        items={topResourceFacets}
                        onSelect={(value) => {
                            setPage(0);
                            setFilters((current) => ({ ...current, resourceType: value }));
                        }}
                    />
                    <FacetChips
                        title="Top Actors"
                        items={topActorFacets}
                        onSelect={(value) => {
                            setPage(0);
                            setFilters((current) => ({ ...current, actorId: value }));
                        }}
                    />
                </section>
            )}

            <section className="mt-6 grid gap-6 xl:grid-cols-[0.72fr_1.08fr_0.8fr]">
                <div className="rounded-3xl border border-white/10 bg-black/30 p-6">
                    <h2 className="text-lg font-bold text-white">Filters</h2>
                    <div className="mt-5">
                        <label className="mb-2 block text-xs uppercase tracking-[0.24em] text-zinc-500">Date Window</label>
                        <div className="flex flex-wrap gap-2">
                            {[
                                { label: '24H', value: '1' },
                                { label: '7D', value: '7' },
                                { label: '30D', value: '30' },
                                { label: 'ALL', value: '' },
                            ].map((windowOption) => (
                                <button
                                    key={windowOption.label}
                                    type="button"
                                    onClick={() => setFilters((current) => ({ ...current, days: windowOption.value }))}
                                    className={`rounded-full border px-3 py-1.5 text-xs font-medium transition ${filters.days === windowOption.value
                                        ? 'border-primary/40 bg-primary/15 text-primary'
                                        : 'border-zinc-800 bg-black text-zinc-400 hover:text-white'
                                        }`}
                                >
                                    {windowOption.label}
                                </button>
                            ))}
                        </div>
                    </div>

                    <div className="mt-5">
                        <div className="mb-2 flex items-center justify-between gap-3">
                            <label className="block text-xs uppercase tracking-[0.24em] text-zinc-500">Quick Presets</label>
                            {activePreset && (
                                <button
                                    type="button"
                                    onClick={() => {
                                        setPage(0);
                                        setFilters((current) => ({
                                            ...current,
                                            actionType: '',
                                            resourceType: '',
                                        }));
                                    }}
                                    className="text-[10px] font-semibold uppercase tracking-wide text-primary transition hover:text-white"
                                >
                                    Clear Preset
                                </button>
                            )}
                        </div>
                        <div className="grid gap-3">
                            {AUDIT_PRESETS.map((preset) => (
                                <button
                                    key={preset.label}
                                    type="button"
                                    onClick={() => {
                                        setPage(0);
                                        setFilters((current) => ({
                                            ...current,
                                            actionType: preset.actionType,
                                            resourceType: preset.resourceType,
                                        }));
                                    }}
                                    className={`rounded-2xl border p-3 text-left transition ${activePreset?.label === preset.label
                                        ? 'border-primary/35 bg-primary/10'
                                        : 'border-zinc-800 bg-black/50 hover:border-primary/30 hover:bg-primary/5'
                                        }`}
                                >
                                    <div className="flex items-start justify-between gap-3">
                                        <div>
                                            <p className="text-sm font-semibold text-white">{preset.label}</p>
                                            <p className="mt-1 text-xs leading-5 text-zinc-500">{preset.description}</p>
                                        </div>
                                        {activePreset?.label === preset.label && (
                                            <span className="rounded-full border border-primary/30 bg-primary/10 px-2 py-1 text-[10px] font-semibold uppercase tracking-wide text-primary">
                                                Active
                                            </span>
                                        )}
                                    </div>
                                </button>
                            ))}
                        </div>
                        {activePreset && (
                            <p className="mt-3 text-xs leading-5 text-zinc-500">
                                Current preset: <span className="font-semibold text-zinc-300">{activePreset.label}</span>. You can still narrow further with manual filters or facet chips.
                            </p>
                        )}
                    </div>

                    <div className="mt-5 grid gap-4">
                        <div>
                            <label className="mb-2 block text-xs uppercase tracking-[0.24em] text-zinc-500">Limit</label>
                            <select
                                value={filters.limit}
                                onChange={(event) => setFilters((current) => ({ ...current, limit: event.target.value }))}
                                className="w-full rounded-xl border border-zinc-800 bg-black px-3 py-3 text-sm text-white outline-none transition focus:border-primary"
                            >
                                <option value="10">10</option>
                                <option value="20">20</option>
                                <option value="50">50</option>
                                <option value="100">100</option>
                            </select>
                        </div>

                        <div>
                            <label className="mb-2 block text-xs uppercase tracking-[0.24em] text-zinc-500">Request Id</label>
                            <input
                                type="text"
                                value={filters.requestId}
                                onChange={(event) => setFilters((current) => ({ ...current, requestId: event.target.value }))}
                                className="w-full rounded-xl border border-zinc-800 bg-black px-3 py-3 text-sm text-white outline-none transition focus:border-primary"
                                placeholder="req-ops-audit"
                            />
                        </div>

                        <div>
                            <label className="mb-2 block text-xs uppercase tracking-[0.24em] text-zinc-500">Request Path</label>
                            <input
                                type="text"
                                value={filters.requestPath}
                                onChange={(event) => setFilters((current) => ({ ...current, requestPath: event.target.value }))}
                                className="w-full rounded-xl border border-zinc-800 bg-black px-3 py-3 text-sm text-white outline-none transition focus:border-primary"
                                placeholder="/api/v1/portfolios"
                            />
                        </div>

                        <div>
                            <label className="mb-2 block text-xs uppercase tracking-[0.24em] text-zinc-500">Actor Id</label>
                            <input
                                type="text"
                                value={filters.actorId}
                                onChange={(event) => setFilters((current) => ({ ...current, actorId: event.target.value }))}
                                className="w-full rounded-xl border border-zinc-800 bg-black px-3 py-3 text-sm text-white outline-none transition focus:border-primary"
                                placeholder="uuid"
                            />
                        </div>

                        <div>
                            <label className="mb-2 block text-xs uppercase tracking-[0.24em] text-zinc-500">Action Type</label>
                            <select
                                value={filters.actionType}
                                onChange={(event) => setFilters((current) => ({ ...current, actionType: event.target.value }))}
                                className="w-full rounded-xl border border-zinc-800 bg-black px-3 py-3 text-sm text-white outline-none transition focus:border-primary"
                            >
                                <option value="">All actions</option>
                                {ACTION_OPTIONS.map((option) => (
                                    <option key={option} value={option}>{option}</option>
                                ))}
                            </select>
                        </div>

                        <div>
                            <label className="mb-2 block text-xs uppercase tracking-[0.24em] text-zinc-500">Resource Type</label>
                            <select
                                value={filters.resourceType}
                                onChange={(event) => setFilters((current) => ({ ...current, resourceType: event.target.value }))}
                                className="w-full rounded-xl border border-zinc-800 bg-black px-3 py-3 text-sm text-white outline-none transition focus:border-primary"
                            >
                                <option value="">All resources</option>
                                {RESOURCE_OPTIONS.map((option) => (
                                    <option key={option} value={option}>{option}</option>
                                ))}
                            </select>
                        </div>
                    </div>

                    <div className="mt-6 flex flex-wrap gap-3">
                        <button
                            type="button"
                            onClick={() => void fetchAudit()}
                            className="rounded-xl border border-primary/35 bg-primary/10 px-4 py-3 text-sm font-semibold text-primary transition hover:bg-primary/20"
                        >
                            Apply Filters
                        </button>
                        <button
                            type="button"
                            onClick={() => setFilters({
                                limit: '20',
                                days: '',
                                requestId: '',
                                requestPath: '',
                                actorId: '',
                                actionType: '',
                                resourceType: '',
                            })}
                            className="rounded-xl border border-zinc-800 bg-black px-4 py-3 text-sm font-semibold text-zinc-300 transition hover:border-zinc-700 hover:text-white"
                        >
                            Reset
                        </button>
                        <button
                            type="button"
                            onClick={() => void exportCsv()}
                            disabled={exporting}
                            className="rounded-xl border border-emerald-500/35 bg-emerald-500/10 px-4 py-3 text-sm font-semibold text-emerald-300 transition hover:bg-emerald-500/20 disabled:opacity-60"
                        >
                            {exporting ? 'Exporting...' : 'Export CSV'}
                        </button>
                        <button
                            type="button"
                            onClick={() => exportJson()}
                            className="rounded-xl border border-sky-500/35 bg-sky-500/10 px-4 py-3 text-sm font-semibold text-sky-300 transition hover:bg-sky-500/20"
                        >
                            Export JSON
                        </button>
                        <button
                            type="button"
                            onClick={() => void copyViewLink()}
                            className="rounded-xl border border-zinc-700 bg-zinc-900 px-4 py-3 text-sm font-semibold text-zinc-200 transition hover:border-zinc-600 hover:text-white"
                        >
                            {copiedLink ? 'Link Copied' : 'Copy View Link'}
                        </button>
                    </div>

                    <p className="mt-4 text-xs leading-6 text-zinc-500">
                        Filters are optional. Date window and export always match the current slice exactly.
                    </p>
                </div>

                <div className="rounded-3xl border border-white/10 bg-black/30 p-6">
                    <div className="flex flex-wrap items-center justify-between gap-3">
                        <h2 className="text-lg font-bold text-white">Recent Rows</h2>
                        <div className="rounded-full border border-zinc-800 bg-black px-3 py-1.5 text-[11px] uppercase tracking-[0.24em] text-zinc-400">
                            Page {data?.page != null ? data.page + 1 : page + 1}
                        </div>
                    </div>
                    {error && (
                        <div className="mt-4 rounded-xl border border-red-500/25 bg-red-500/10 p-4 text-sm text-red-300">
                            {error}
                        </div>
                    )}

                    {loading ? (
                        <div className="mt-5 space-y-3">
                            {[1, 2, 3].map((item) => (
                                <div key={item} className="h-28 rounded-2xl bg-zinc-900 animate-pulse" />
                            ))}
                        </div>
                    ) : !data || data.entries.length === 0 ? (
                        <div className="mt-5 rounded-2xl border border-dashed border-zinc-800 bg-zinc-950/40 px-5 py-10 text-center">
                            <p className="text-[11px] uppercase tracking-[0.32em] text-zinc-600">No Rows</p>
                            <p className="mt-3 text-sm font-semibold text-zinc-200">No audit entries match this filter set.</p>
                            <p className="mt-2 text-xs leading-6 text-zinc-500">Broaden the slice or remove request and actor filters.</p>
                        </div>
                    ) : (
                        <div className="mt-5 space-y-3">
                            {data.entries.map((entry) => (
                                <article
                                    key={entry.id}
                                    className={`rounded-2xl border p-4 transition ${selectedEntry?.id === entry.id
                                        ? 'border-primary/35 bg-primary/5'
                                        : 'border-white/10 bg-zinc-950/50'
                                        }`}
                                >
                                    <div className="flex flex-wrap items-start justify-between gap-3">
                                        <div>
                                            <div className="flex flex-wrap items-center gap-2">
                                                <span className="rounded-full border border-primary/30 bg-primary/10 px-2 py-1 text-[11px] font-semibold text-primary">
                                                    {entry.actionType}
                                                </span>
                                                <span className="rounded-full border border-zinc-800 bg-black px-2 py-1 text-[11px] font-semibold text-zinc-300">
                                                    {entry.resourceType}
                                                </span>
                                            </div>
                                            <p className="mt-3 text-sm text-zinc-300">
                                                {entry.requestMethod ?? 'METHOD'} {entry.requestPath ?? 'path unavailable'}
                                            </p>
                                        </div>
                                        <p className="text-xs text-zinc-500">{formatTimestamp(entry.createdAt)}</p>
                                    </div>

                                    <div className="mt-4 grid gap-3 md:grid-cols-2">
                                        <div className="rounded-xl border border-zinc-800 bg-black/50 p-3">
                                            <p className="text-[10px] uppercase tracking-[0.24em] text-zinc-600">Request Id</p>
                                            <div className="mt-2 flex flex-wrap items-center gap-2">
                                                <p className="break-all text-xs text-zinc-300">{entry.requestId ?? 'N/A'}</p>
                                                {entry.requestId && (
                                                    <button
                                                        type="button"
                                                        onClick={() => focusRequestId(entry.requestId)}
                                                        className="rounded-md border border-primary/30 bg-primary/10 px-2 py-1 text-[10px] font-semibold uppercase tracking-wide text-primary transition hover:bg-primary/20"
                                                    >
                                                        Focus
                                                    </button>
                                                )}
                                                <button
                                                    type="button"
                                                    onClick={() => setSelectedEntryId(entry.id)}
                                                    className={`rounded-md border px-2 py-1 text-[10px] font-semibold uppercase tracking-wide transition ${selectedEntry?.id === entry.id
                                                        ? 'border-primary/30 bg-primary/10 text-primary'
                                                        : 'border-zinc-700 bg-black text-zinc-300 hover:border-zinc-600 hover:text-white'
                                                        }`}
                                                >
                                                    Inspect
                                                </button>
                                            </div>
                                        </div>
                                        <div className="rounded-xl border border-zinc-800 bg-black/50 p-3">
                                            <p className="text-[10px] uppercase tracking-[0.24em] text-zinc-600">Actor Id</p>
                                            <p className="mt-2 break-all text-xs text-zinc-300">{entry.actorId ?? 'N/A'}</p>
                                        </div>
                                    </div>
                                </article>
                            ))}
                            <div className="flex flex-wrap items-center justify-between gap-3 rounded-2xl border border-zinc-800 bg-black/40 px-4 py-3">
                                <div className="text-xs text-zinc-500">
                                    Showing {data.count} of {data.totalCount} filtered row{data.totalCount === 1 ? '' : 's'}
                                </div>
                                <div className="flex gap-2">
                                    <button
                                        type="button"
                                        onClick={() => setPage((current) => Math.max(0, current - 1))}
                                        disabled={page === 0}
                                        className="rounded-lg border border-zinc-800 bg-black px-3 py-2 text-xs font-semibold text-zinc-300 transition hover:border-zinc-700 hover:text-white disabled:opacity-40"
                                    >
                                        Prev
                                    </button>
                                    <button
                                        type="button"
                                        onClick={() => setPage((current) => current + 1)}
                                        disabled={!data.hasMore}
                                        className="rounded-lg border border-primary/35 bg-primary/10 px-3 py-2 text-xs font-semibold text-primary transition hover:bg-primary/20 disabled:opacity-40"
                                    >
                                        Next
                                    </button>
                                </div>
                            </div>
                        </div>
                    )}
                </div>

                <div className="rounded-3xl border border-white/10 bg-black/30 p-6">
                    <div className="flex flex-wrap items-center justify-between gap-3">
                        <div>
                            <p className="text-[10px] uppercase tracking-[0.28em] text-zinc-500">Row Detail</p>
                            <h2 className="mt-2 text-lg font-bold text-white">Selected Audit Entry</h2>
                        </div>
                        {selectedEntry && (
                            <div className="flex flex-wrap gap-2">
                                <div className="rounded-xl border border-zinc-800 bg-black px-3 py-2 text-xs font-semibold text-zinc-300">
                                    Inspecting {selectedEntry.actionType}
                                </div>
                                <button
                                    type="button"
                                    onClick={() => {
                                        setPage(0);
                                        setFilters((current) => ({ ...current, actionType: selectedEntry.actionType }));
                                    }}
                                    className="rounded-xl border border-zinc-800 bg-black px-3 py-2 text-xs font-semibold text-zinc-300 transition hover:border-zinc-700 hover:text-white"
                                >
                                    Filter Action
                                </button>
                                <button
                                    type="button"
                                    onClick={() => {
                                        openResourceSlice(selectedEntry);
                                    }}
                                    className="rounded-xl border border-zinc-800 bg-black px-3 py-2 text-xs font-semibold text-zinc-300 transition hover:border-zinc-700 hover:text-white"
                                >
                                    Filter Resource
                                </button>
                                {selectedEntry.actorId && (
                                    <button
                                        type="button"
                                        onClick={() => {
                                            openActorSlice(selectedEntry);
                                        }}
                                        className="rounded-xl border border-zinc-800 bg-black px-3 py-2 text-xs font-semibold text-zinc-300 transition hover:border-zinc-700 hover:text-white"
                                    >
                                        Filter Actor
                                    </button>
                                )}
                                <button
                                    type="button"
                                    onClick={() => void copySliceLink('resourceSlice', { resourceType: selectedEntry.resourceType })}
                                    className="rounded-xl border border-zinc-800 bg-black px-3 py-2 text-xs font-semibold text-zinc-300 transition hover:border-zinc-700 hover:text-white"
                                >
                                    {copiedDetailField === 'resourceSlice' ? 'Resource Link Copied' : 'Copy Resource Slice'}
                                </button>
                                {selectedEntry.requestPath && (
                                    <>
                                        <button
                                            type="button"
                                            onClick={() => openPathSlice(selectedEntry)}
                                            className="rounded-xl border border-zinc-800 bg-black px-3 py-2 text-xs font-semibold text-zinc-300 transition hover:border-zinc-700 hover:text-white"
                                        >
                                            Open Path Slice
                                        </button>
                                        <button
                                            type="button"
                                            onClick={() => void copySliceLink('pathSlice', { requestPath: selectedEntry.requestPath ?? '' })}
                                            className="rounded-xl border border-zinc-800 bg-black px-3 py-2 text-xs font-semibold text-zinc-300 transition hover:border-zinc-700 hover:text-white"
                                        >
                                            {copiedDetailField === 'pathSlice' ? 'Path Link Copied' : 'Copy Path Slice'}
                                        </button>
                                    </>
                                )}
                                {selectedEntry.actorId && (
                                    <button
                                        type="button"
                                        onClick={() => void copySliceLink('actorSlice', { actorId: selectedEntry.actorId ?? '' })}
                                        className="rounded-xl border border-zinc-800 bg-black px-3 py-2 text-xs font-semibold text-zinc-300 transition hover:border-zinc-700 hover:text-white"
                                    >
                                        {copiedDetailField === 'actorSlice' ? 'Actor Link Copied' : 'Copy Actor Slice'}
                                    </button>
                                )}
                                {selectedEntry.requestId && (
                                    <>
                                        <button
                                            type="button"
                                            onClick={() => openFocusedView(selectedEntry)}
                                            className="rounded-xl border border-primary/30 bg-primary/10 px-3 py-2 text-xs font-semibold text-primary transition hover:bg-primary/20"
                                        >
                                            Open Focused View
                                        </button>
                                        <button
                                            type="button"
                                            onClick={() => void copyFocusedViewLink(selectedEntry)}
                                            className="rounded-xl border border-zinc-800 bg-black px-3 py-2 text-xs font-semibold text-zinc-300 transition hover:border-zinc-700 hover:text-white"
                                        >
                                            {copiedDetailField === 'focusLink' ? 'Focus Link Copied' : 'Copy Focus Link'}
                                        </button>
                                    </>
                                )}
                                <button
                                    type="button"
                                    onClick={() => void copyDetailValue('json', JSON.stringify(selectedEntry, null, 2))}
                                    className="rounded-xl border border-zinc-800 bg-black px-3 py-2 text-xs font-semibold text-zinc-300 transition hover:border-zinc-700 hover:text-white"
                                >
                                    {copiedDetailField === 'json' ? 'JSON Copied' : 'Copy JSON'}
                                </button>
                            </div>
                        )}
                    </div>

                    {!selectedEntry ? (
                        <div className="mt-5 rounded-2xl border border-dashed border-zinc-800 bg-zinc-950/40 px-5 py-10 text-center">
                            <p className="text-[11px] uppercase tracking-[0.32em] text-zinc-600">No Selection</p>
                            <p className="mt-3 text-sm font-semibold text-zinc-200">Pick an audit row to inspect full metadata here.</p>
                        </div>
                    ) : (
                        <div className="mt-5 space-y-4">
                            <div className="rounded-2xl border border-white/10 bg-zinc-950/50 p-4">
                                <div className="flex flex-wrap items-center gap-2">
                                    <span className="rounded-full border border-primary/30 bg-primary/10 px-2 py-1 text-[11px] font-semibold text-primary">
                                        {selectedEntry.actionType}
                                    </span>
                                    <span className="rounded-full border border-zinc-800 bg-black px-2 py-1 text-[11px] font-semibold text-zinc-300">
                                        {selectedEntry.resourceType}
                                    </span>
                                </div>
                                <div className="mt-3 flex items-start justify-between gap-3">
                                    <p className="text-sm text-zinc-300">
                                        {selectedEntry.requestMethod ?? 'METHOD'} {selectedEntry.requestPath ?? 'path unavailable'}
                                    </p>
                                    {selectedEntry.requestPath && (
                                        <button
                                            type="button"
                                            onClick={() => void copyDetailValue('requestPath', selectedEntry.requestPath)}
                                            className="text-[10px] font-semibold uppercase tracking-wide text-primary transition hover:text-white"
                                        >
                                            {copiedDetailField === 'requestPath' ? 'Copied' : 'Copy Path'}
                                        </button>
                                    )}
                                </div>
                            </div>

                            <div className="grid gap-3">
                                <div className="rounded-2xl border border-zinc-800 bg-black/40 p-4">
                                    <div className="flex items-center justify-between gap-3">
                                        <p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Request Id</p>
                                        {selectedEntry.requestId && (
                                            <button
                                                type="button"
                                                onClick={() => void copyDetailValue('requestId', selectedEntry.requestId)}
                                                className="text-[10px] font-semibold uppercase tracking-wide text-primary transition hover:text-white"
                                            >
                                                {copiedDetailField === 'requestId' ? 'Copied' : 'Copy'}
                                            </button>
                                        )}
                                    </div>
                                    <p className="mt-2 break-all text-sm text-white">{selectedEntry.requestId ?? 'N/A'}</p>
                                </div>
                                <div className="rounded-2xl border border-zinc-800 bg-black/40 p-4">
                                    <div className="flex items-center justify-between gap-3">
                                        <p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Actor Id</p>
                                        {selectedEntry.actorId && (
                                            <button
                                                type="button"
                                                onClick={() => void copyDetailValue('actorId', selectedEntry.actorId)}
                                                className="text-[10px] font-semibold uppercase tracking-wide text-primary transition hover:text-white"
                                            >
                                                {copiedDetailField === 'actorId' ? 'Copied' : 'Copy'}
                                            </button>
                                        )}
                                    </div>
                                    <p className="mt-2 break-all text-sm text-white">{selectedEntry.actorId ?? 'N/A'}</p>
                                </div>
                                <div className="rounded-2xl border border-zinc-800 bg-black/40 p-4">
                                    <div className="flex items-center justify-between gap-3">
                                        <p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Resource Id</p>
                                        {selectedEntry.resourceId && (
                                            <button
                                                type="button"
                                                onClick={() => void copyDetailValue('resourceId', selectedEntry.resourceId)}
                                                className="text-[10px] font-semibold uppercase tracking-wide text-primary transition hover:text-white"
                                            >
                                                {copiedDetailField === 'resourceId' ? 'Copied' : 'Copy'}
                                            </button>
                                        )}
                                    </div>
                                    <p className="mt-2 break-all text-sm text-white">{selectedEntry.resourceId ?? 'N/A'}</p>
                                </div>
                                <div className="rounded-2xl border border-zinc-800 bg-black/40 p-4">
                                    <p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Occurred At</p>
                                    <p className="mt-2 text-sm text-white">{formatTimestamp(selectedEntry.createdAt)}</p>
                                </div>
                                {selectedEntry.details != null && (
                                    <div className="rounded-2xl border border-zinc-800 bg-black/40 p-4">
                                        <div className="flex items-center justify-between gap-3">
                                            <p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Details</p>
                                            <button
                                                type="button"
                                                onClick={() => void copyDetailValue('details', JSON.stringify(selectedEntry.details, null, 2))}
                                                className="text-[10px] font-semibold uppercase tracking-wide text-primary transition hover:text-white"
                                            >
                                                {copiedDetailField === 'details' ? 'Copied' : 'Copy Details'}
                                            </button>
                                        </div>
                                        <pre className="mt-3 overflow-x-auto whitespace-pre-wrap break-words text-xs leading-6 text-zinc-300">
                                            {JSON.stringify(selectedEntry.details, null, 2)}
                                        </pre>
                                    </div>
                                )}
                            </div>
                        </div>
                    )}
                </div>
            </section>
        </div>
    );
}
