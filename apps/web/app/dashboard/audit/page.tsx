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

export default function AuditPage() {
    const [data, setData] = useState<AuditResponse | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [exporting, setExporting] = useState(false);
    const [copiedLink, setCopiedLink] = useState(false);
    const [page, setPage] = useState(0);
    const [filters, setFilters] = useState({
        limit: '20',
        days: '',
        requestId: '',
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
            actorId: params.get('actorId') || '',
            actionType: params.get('actionType') || '',
            resourceType: params.get('resourceType') || '',
        }));
        setPage(Math.max(0, Number.parseInt(params.get('page') || '0', 10) || 0));
    }, []);

    const fetchAudit = useCallback(async () => {
        setLoading(true);
        setError('');
        try {
            const params = new URLSearchParams();
            if (filters.limit) params.set('limit', filters.limit);
            params.set('page', page.toString());
            if (filters.days) params.set('days', filters.days);
            if (filters.requestId.trim()) params.set('requestId', filters.requestId.trim());
            if (filters.actorId.trim()) params.set('actorId', filters.actorId.trim());
            if (filters.actionType) params.set('actionType', filters.actionType);
            if (filters.resourceType) params.set('resourceType', filters.resourceType);

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
    }, [filters, page]);

    useEffect(() => {
        void fetchAudit();
    }, [fetchAudit]);

    useEffect(() => {
        setPage(0);
    }, [filters.limit, filters.days, filters.requestId, filters.actorId, filters.actionType, filters.resourceType]);

    const filteredCountLabel = useMemo(() => {
        if (!data) {
            return '0';
        }
        return data.count.toString();
    }, [data]);

    const activeFilterCount = useMemo(() => {
        return [filters.days, filters.requestId, filters.actorId, filters.actionType, filters.resourceType].filter(Boolean).length;
    }, [filters]);

    const activeWindowLabel = useMemo(() => {
        if (!filters.days) {
            return 'ALL';
        }
        return `${filters.days}D`;
    }, [filters]);

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
            const params = new URLSearchParams();
            if (filters.limit) params.set('limit', filters.limit);
            if (filters.days) params.set('days', filters.days);
            if (filters.requestId.trim()) params.set('requestId', filters.requestId.trim());
            if (filters.actorId.trim()) params.set('actorId', filters.actorId.trim());
            if (filters.actionType) params.set('actionType', filters.actionType);
            if (filters.resourceType) params.set('resourceType', filters.resourceType);

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
        const params = new URLSearchParams();
        if (filters.limit) params.set('limit', filters.limit);
        if (filters.days) params.set('days', filters.days);
        if (filters.requestId.trim()) params.set('requestId', filters.requestId.trim());
        if (filters.actorId.trim()) params.set('actorId', filters.actorId.trim());
        if (filters.actionType) params.set('actionType', filters.actionType);
        if (filters.resourceType) params.set('resourceType', filters.resourceType);
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
            const payload = {
                filters: {
                    ...filters,
                    page,
                },
                snapshot: data,
            };
            const blob = new Blob([JSON.stringify(payload, null, 2)], { type: 'application/json' });
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

            <section className="mt-6 grid gap-6 lg:grid-cols-[0.85fr_1.15fr]">
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
                                <article key={entry.id} className="rounded-2xl border border-white/10 bg-zinc-950/50 p-4">
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
                                            </div>
                                        </div>
                                        <div className="rounded-xl border border-zinc-800 bg-black/50 p-3">
                                            <p className="text-[10px] uppercase tracking-[0.24em] text-zinc-600">Actor Id</p>
                                            <p className="mt-2 break-all text-xs text-zinc-300">{entry.actorId ?? 'N/A'}</p>
                                        </div>
                                    </div>

                                    {entry.details != null && (
                                        <div className="mt-4 rounded-xl border border-zinc-800 bg-black/50 p-3">
                                            <p className="text-[10px] uppercase tracking-[0.24em] text-zinc-600">Details</p>
                                            <pre className="mt-2 overflow-x-auto whitespace-pre-wrap break-words text-xs leading-6 text-zinc-300">
                                                {JSON.stringify(entry.details, null, 2)}
                                            </pre>
                                        </div>
                                    )}
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
            </section>
        </div>
    );
}
