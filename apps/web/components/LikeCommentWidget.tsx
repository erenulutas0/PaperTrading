'use client';

import { useCallback, useEffect, useState } from 'react';
import { apiFetch } from '../lib/api-client';

interface LikeCommentWidgetProps {
    targetId: string;
    targetType: 'PORTFOLIO' | 'ANALYSIS_POST';
}

interface CommentItem {
    id: string;
    actorId: string;
    actorUsername: string;
    actorDisplayName: string;
    actorAvatarUrl?: string | null;
    content: string;
    createdAt: string;
    likeCount: number;
    hasLiked: boolean;
    replyCount: number;
}

interface InteractionCreateResponse {
    id: string;
    actorId: string;
    content: string;
    createdAt: string;
}

interface InteractionSummary {
    likeCount: number;
    hasLiked: boolean;
    commentCount: number;
}

function DiscussionEmptyState({ targetType }: { targetType: 'PORTFOLIO' | 'ANALYSIS_POST' }) {
    const noun = targetType === 'ANALYSIS_POST' ? 'thesis' : 'portfolio';

    return (
        <div className="rounded-2xl border border-dashed border-zinc-800 bg-zinc-950/40 px-4 py-8 text-center">
            <p className="text-[10px] uppercase tracking-[0.3em] text-zinc-600">Discussion Empty</p>
            <p className="mt-3 text-sm font-semibold text-zinc-200">No responses on this {noun} yet.</p>
            <p className="mt-2 text-xs leading-6 text-zinc-500">
                The first comment sets the tone for public review, disagreement, or follow-up evidence.
            </p>
        </div>
    );
}

function formatRelativeTime(createdAt: string): string {
    const ts = new Date(createdAt).getTime();
    if (!Number.isFinite(ts)) return '';
    const diffMs = Date.now() - ts;
    const minutes = Math.floor(diffMs / 60000);
    if (minutes < 1) return 'just now';
    if (minutes < 60) return `${minutes}m ago`;
    const hours = Math.floor(minutes / 60);
    if (hours < 24) return `${hours}h ago`;
    const days = Math.floor(hours / 24);
    return `${days}d ago`;
}

function CommentThread({
    comment,
    onRefresh,
    depth = 0,
}: {
    comment: CommentItem;
    onRefresh: () => Promise<void>;
    depth?: number;
}) {
    const [replies, setReplies] = useState<CommentItem[]>([]);
    const [showReplies, setShowReplies] = useState(false);
    const [replyDraft, setReplyDraft] = useState('');
    const [loadingReplies, setLoadingReplies] = useState(false);
    const [submittingReply, setSubmittingReply] = useState(false);
    const [commentState, setCommentState] = useState(comment);

    useEffect(() => {
        setCommentState(comment);
    }, [comment]);

    const fetchReplies = async () => {
        setLoadingReplies(true);
        try {
            const res = await apiFetch(`/api/v1/interactions/${comment.id}/comments?type=COMMENT`, { cache: 'no-store' });
            if (!res.ok) return;
            const data = await res.json();
            setReplies(data.content ?? []);
        } catch (error) {
            console.error(error);
        } finally {
            setLoadingReplies(false);
        }
    };

    const handleToggleReplies = async () => {
        const next = !showReplies;
        setShowReplies(next);
        if (next) {
            await fetchReplies();
        }
    };

    const toggleCommentLike = async () => {
        const previous = commentState;
        const nextHasLiked = !commentState.hasLiked;
        setCommentState({
            ...commentState,
            hasLiked: nextHasLiked,
            likeCount: commentState.likeCount + (nextHasLiked ? 1 : -1),
        });

        try {
            const res = await apiFetch(`/api/v1/interactions/${comment.id}/like`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ targetType: 'COMMENT' }),
            });
            if (!res.ok) {
                throw new Error('Comment like failed');
            }
        } catch (error) {
            console.error(error);
            setCommentState(previous);
        }
    };

    const postReply = async (event: React.FormEvent) => {
        event.preventDefault();
        const trimmed = replyDraft.trim();
        if (!trimmed) return;

        setSubmittingReply(true);
        try {
            const res = await apiFetch(`/api/v1/interactions/${comment.id}/comments`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ targetType: 'COMMENT', content: trimmed }),
            });
            if (!res.ok) {
                throw new Error('Reply failed');
            }

            const created = await res.json() as InteractionCreateResponse;
            const currentUserId = localStorage.getItem('userId') ?? '';
            const currentUsername = localStorage.getItem('username') ?? 'you';
            const optimisticReply: CommentItem = {
                id: created.id,
                actorId: created.actorId ?? currentUserId,
                actorUsername: currentUsername,
                actorDisplayName: currentUsername,
                actorAvatarUrl: null,
                content: created.content ?? trimmed,
                createdAt: created.createdAt ?? new Date().toISOString(),
                likeCount: 0,
                hasLiked: false,
                replyCount: 0,
            };

            setReplyDraft('');
            setShowReplies(true);
            setReplies(prev => [optimisticReply, ...prev.filter(reply => reply.id !== optimisticReply.id)]);
            setCommentState(prev => ({ ...prev, replyCount: prev.replyCount + 1 }));
            await fetchReplies();
            await onRefresh();
        } catch (error) {
            console.error(error);
        } finally {
            setSubmittingReply(false);
        }
    };

    return (
        <div className="rounded-xl border border-zinc-800/70 bg-zinc-950/60 p-4 space-y-3">
            <div className="flex items-start gap-3">
                <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full border border-zinc-800 bg-zinc-900 text-[10px] font-bold uppercase text-zinc-300">
                    {(commentState.actorDisplayName || commentState.actorUsername || '?').slice(0, 2)}
                </div>
                <div className="min-w-0 flex-1">
                    <div className="flex flex-wrap items-center gap-2 text-[11px]">
                        <span className="font-semibold text-white">{commentState.actorDisplayName || commentState.actorUsername}</span>
                        <span className="text-zinc-500">@{commentState.actorUsername}</span>
                        <span className="text-zinc-600">{formatRelativeTime(commentState.createdAt)}</span>
                    </div>
                    <p className="mt-2 text-sm leading-relaxed text-zinc-300">{commentState.content}</p>
                    <div className="mt-3 flex flex-wrap items-center gap-2">
                        <button
                            type="button"
                            onClick={toggleCommentLike}
                            className={`rounded-full border px-3 py-1 text-[11px] font-semibold transition ${commentState.hasLiked
                                ? 'border-red-500/30 bg-red-500/10 text-red-400'
                                : 'border-zinc-800 bg-zinc-900 text-zinc-400 hover:text-white'
                                }`}
                        >
                            Like {commentState.likeCount > 0 ? `(${commentState.likeCount})` : ''}
                        </button>
                        <button
                            type="button"
                            onClick={handleToggleReplies}
                            className="rounded-full border border-zinc-800 bg-zinc-900 px-3 py-1 text-[11px] font-semibold text-zinc-400 transition hover:text-white"
                        >
                            {showReplies ? 'Hide replies' : `Replies${commentState.replyCount > 0 ? ` (${commentState.replyCount})` : ''}`}
                        </button>
                    </div>
                </div>
            </div>

            {showReplies && (
                <div className={`${depth > 0 ? 'ml-6' : 'ml-11'} space-y-3 border-l border-zinc-800/70 pl-4`}>
                    <form onSubmit={postReply} className="flex gap-2">
                        <input
                            type="text"
                            value={replyDraft}
                            onChange={(e) => setReplyDraft(e.target.value)}
                            placeholder="Write a reply..."
                            className="flex-1 rounded-lg border border-zinc-800 bg-black p-2 text-xs text-white placeholder:text-zinc-600 focus:border-blue-500 focus:outline-none"
                        />
                        <button
                            type="submit"
                            disabled={!replyDraft.trim() || submittingReply}
                            className="rounded-lg bg-blue-600 px-4 text-[10px] font-bold uppercase tracking-wider text-white transition hover:bg-blue-500 disabled:cursor-not-allowed disabled:opacity-50"
                        >
                            {submittingReply ? 'Replying...' : 'Reply'}
                        </button>
                    </form>

                    {loadingReplies ? (
                        <div className="space-y-2">
                            <div className="h-10 rounded-lg bg-white/5 animate-pulse" />
                            <div className="h-10 rounded-lg bg-white/5 animate-pulse" />
                        </div>
                    ) : replies.length === 0 ? (
                        <p className="rounded-lg border border-dashed border-zinc-800 px-3 py-3 text-[11px] italic text-zinc-600">
                            No replies yet. Use this thread to challenge the thesis or add supporting context.
                        </p>
                    ) : (
                        replies.map((reply) => (
                            <CommentThread
                                key={reply.id}
                                comment={reply}
                                onRefresh={onRefresh}
                                depth={depth + 1}
                            />
                        ))
                    )}
                </div>
            )}
        </div>
    );
}

export default function LikeCommentWidget({ targetId, targetType }: LikeCommentWidgetProps) {
    const SUMMARY_SYNC_INTERVAL_MS = 12000;
    const [likes, setLikes] = useState(0);
    const [hasLiked, setHasLiked] = useState(false);
    const [comments, setComments] = useState<CommentItem[]>([]);
    const [commentCount, setCommentCount] = useState(0);
    const [newComment, setNewComment] = useState('');
    const [showComments, setShowComments] = useState(false);
    const [loading, setLoading] = useState(true);
    const [commentsLoading, setCommentsLoading] = useState(false);

    const fetchLegacySummary = useCallback(async (includeCommentCount: boolean) => {
        try {
            const [likeRes, commentMetaRes] = await Promise.all([
                apiFetch(`/api/v1/interactions/${targetId}/likes/count?type=${targetType}`, { cache: 'no-store' }),
                includeCommentCount
                    ? apiFetch(`/api/v1/interactions/${targetId}/comments?type=${targetType}&page=0&size=1`, { cache: 'no-store' })
                    : Promise.resolve(null),
            ]);

            if (likeRes.ok) {
                const likeData = await likeRes.json();
                setLikes(likeData.count ?? 0);
                setHasLiked(Boolean(likeData.hasLiked));
            }

            if (commentMetaRes && commentMetaRes.ok) {
                const commentData = await commentMetaRes.json();
                setCommentCount(commentData.page?.totalElements ?? commentData.content?.length ?? 0);
            }
        } catch (error) {
            console.error(error);
        }
    }, [targetId, targetType]);

    const fetchSummary = useCallback(async () => {
        try {
            const res = await apiFetch(`/api/v1/interactions/${targetId}/summary?type=${targetType}`, { cache: 'no-store' });
            if (res.ok) {
                const summary = await res.json() as InteractionSummary;
                setLikes(summary.likeCount ?? 0);
                setHasLiked(Boolean(summary.hasLiked));
                setCommentCount(summary.commentCount ?? 0);
                return;
            }
            await fetchLegacySummary(true);
        } catch (error) {
            console.error(error);
            await fetchLegacySummary(true);
        } finally {
            setLoading(false);
        }
    }, [fetchLegacySummary, targetId, targetType]);

    const fetchComments = useCallback(async () => {
        setCommentsLoading(true);
        try {
            const res = await apiFetch(`/api/v1/interactions/${targetId}/comments?type=${targetType}`, { cache: 'no-store' });
            if (!res.ok) {
                await fetchLegacySummary(true);
                return;
            }
            const commentData = await res.json();
            setComments(commentData.content ?? []);
            setCommentCount(commentData.page?.totalElements ?? commentData.content?.length ?? 0);
        } catch (error) {
            console.error(error);
            await fetchLegacySummary(true);
        } finally {
            setCommentsLoading(false);
        }
    }, [fetchLegacySummary, targetId, targetType]);

    useEffect(() => {
        void fetchSummary();
    }, [fetchSummary]);

    useEffect(() => {
        if (!showComments) {
            return;
        }

        const syncNow = () => {
            void fetchSummary();
        };

        void fetchComments();
        const handleFocus = () => syncNow();
        const handleVisibilityChange = () => {
            if (document.visibilityState === 'visible') {
                syncNow();
                void fetchComments();
            }
        };

        window.addEventListener('focus', handleFocus);
        document.addEventListener('visibilitychange', handleVisibilityChange);

        return () => {
            window.removeEventListener('focus', handleFocus);
            document.removeEventListener('visibilitychange', handleVisibilityChange);
        };
    }, [fetchComments, fetchSummary, showComments]);

    useEffect(() => {
        const syncSummary = () => {
            void fetchSummary();
        };

        const intervalId = window.setInterval(syncSummary, SUMMARY_SYNC_INTERVAL_MS);
        const handleFocus = () => syncSummary();
        const handleVisibilityChange = () => {
            if (document.visibilityState === 'visible') {
                syncSummary();
            }
        };

        window.addEventListener('focus', handleFocus);
        document.addEventListener('visibilitychange', handleVisibilityChange);

        return () => {
            window.clearInterval(intervalId);
            window.removeEventListener('focus', handleFocus);
            document.removeEventListener('visibilitychange', handleVisibilityChange);
        };
    }, [fetchSummary]);

    const toggleLike = async () => {
        const previousState = hasLiked;
        const previousLikes = likes;

        setHasLiked(!hasLiked);
        setLikes(hasLiked ? likes - 1 : likes + 1);

        try {
            const res = await apiFetch(`/api/v1/interactions/${targetId}/like`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({ targetType })
            });
            if (!res.ok) throw new Error();
        } catch {
            setHasLiked(previousState);
            setLikes(previousLikes);
            return;
        }
        void fetchSummary();
    };

    const postComment = async (e: React.FormEvent) => {
        e.preventDefault();
        const trimmed = newComment.trim();
        if (!trimmed) return;

        try {
            const res = await apiFetch(`/api/v1/interactions/${targetId}/comments`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({ targetType, content: trimmed })
            });

            if (res.ok) {
                const created = await res.json() as InteractionCreateResponse;
                const currentUserId = localStorage.getItem('userId') ?? '';
                const currentUsername = localStorage.getItem('username') ?? 'you';
                const optimisticComment: CommentItem = {
                    id: created.id,
                    actorId: created.actorId ?? currentUserId,
                    actorUsername: currentUsername,
                    actorDisplayName: currentUsername,
                    actorAvatarUrl: null,
                    content: created.content ?? trimmed,
                    createdAt: created.createdAt ?? new Date().toISOString(),
                    likeCount: 0,
                    hasLiked: false,
                    replyCount: 0,
                };

                setNewComment('');
                setShowComments(true);
                setCommentCount(prev => prev + 1);
                setComments(prev => [optimisticComment, ...prev.filter(comment => comment.id !== optimisticComment.id)]);
                await fetchComments();
                await fetchSummary();
            }
        } catch (error) {
            console.error(error);
        }
    };

    if (loading) {
        return (
            <div className="mt-4 rounded-2xl border border-zinc-800/80 bg-zinc-950/50 p-4">
                <div className="animate-pulse space-y-4">
                    <div className="flex gap-3">
                        <div className="h-8 w-16 rounded-full bg-zinc-800" />
                        <div className="h-8 w-16 rounded-full bg-zinc-800" />
                    </div>
                    <div className="h-16 rounded-xl bg-zinc-900" />
                </div>
            </div>
        );
    }

    const discussionTitle = targetType === 'ANALYSIS_POST' ? 'Thesis Discussion' : 'Portfolio Discussion';
    const discussionHint = targetType === 'ANALYSIS_POST'
        ? 'Use comments to challenge assumptions, confirm catalysts, or pressure-test the target and stop.'
        : 'Use comments to inspect portfolio intent, risk posture, and execution quality.';

    return (
        <div className="mt-4 flex w-full flex-col border-t border-zinc-800 pt-4">
            <div className="rounded-2xl border border-zinc-800/80 bg-zinc-950/50 p-4">
                <div className="flex flex-wrap items-start justify-between gap-4">
                    <div>
                        <p className="text-[11px] uppercase tracking-[0.32em] text-zinc-500">Public Review</p>
                        <h3 className="mt-2 text-lg font-bold text-white">{discussionTitle}</h3>
                        <p className="mt-2 max-w-2xl text-sm leading-6 text-zinc-400">{discussionHint}</p>
                    </div>
                    <div className="flex items-center gap-3">
                        <button
                            onClick={toggleLike}
                            className={`flex items-center gap-2 rounded-full border px-3 py-1.5 text-xs font-bold transition-all ${hasLiked
                                ? 'border-red-500/20 bg-red-500/10 text-red-500 shadow-[0_0_10px_rgba(239,68,68,0.2)]'
                                : 'border-zinc-800 bg-zinc-900 text-zinc-400 hover:bg-zinc-800 hover:text-white'
                                }`}
                        >
                            <svg className={`h-4 w-4 ${hasLiked ? 'fill-current' : 'fill-none stroke-current'}`} viewBox="0 0 24 24" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                                <path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"></path>
                            </svg>
                            {likes}
                        </button>

                        <button
                            onClick={() => {
                                const next = !showComments;
                                setShowComments(next);
                                if (next) {
                                    void fetchComments();
                                }
                            }}
                            className="flex items-center gap-2 rounded-full border border-zinc-800 bg-zinc-900 px-3 py-1.5 text-xs font-bold text-zinc-400 transition-all hover:bg-zinc-800 hover:text-white"
                        >
                            <svg className="h-4 w-4 fill-none stroke-current" viewBox="0 0 24 24" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                                <path d="M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.48 8.48 0 0 1 8 8v.5z"></path>
                            </svg>
                            {commentCount}
                        </button>
                    </div>
                </div>
            </div>

            {showComments && (
                <div className="mt-4 space-y-4 animate-in fade-in slide-in-from-top-2 duration-200">
                    <div className="rounded-2xl border border-zinc-800/80 bg-zinc-950/50 p-4">
                        <form onSubmit={postComment} className="flex gap-2">
                            <input
                                type="text"
                                className="flex-1 rounded-lg border border-zinc-800 bg-black p-2 text-xs text-white placeholder:text-zinc-600 focus:border-green-500 focus:outline-none"
                                placeholder={targetType === 'ANALYSIS_POST' ? 'Add evidence, risk challenge, or follow-up thesis...' : 'Add execution or risk commentary...'}
                                value={newComment}
                                onChange={(e) => setNewComment(e.target.value)}
                            />
                            <button
                                type="submit"
                                disabled={!newComment.trim()}
                                className="rounded-lg bg-green-600 px-4 text-[10px] font-bold uppercase tracking-wider text-white transition-colors hover:bg-green-500 disabled:cursor-not-allowed disabled:opacity-50"
                            >
                                Post
                            </button>
                        </form>
                    </div>

                    <div className="max-h-[28rem] space-y-3 overflow-y-auto pr-2 custom-scrollbar">
                        {commentsLoading ? (
                            <div className="space-y-3">
                                <div className="h-24 rounded-xl bg-zinc-900 animate-pulse" />
                                <div className="h-24 rounded-xl bg-zinc-900 animate-pulse" />
                            </div>
                        ) : comments.length === 0 ? (
                            <DiscussionEmptyState targetType={targetType} />
                        ) : (
                            comments.map((comment) => (
                                <CommentThread
                                    key={comment.id}
                                    comment={comment}
                                    onRefresh={async () => {
                                        await fetchComments();
                                        await fetchSummary();
                                    }}
                                />
                            ))
                        )}
                    </div>
                </div>
            )}
        </div>
    );
}
