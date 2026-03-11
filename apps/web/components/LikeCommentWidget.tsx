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
            const res = await apiFetch(`/api/v1/interactions/${comment.id}/comments?type=COMMENT`);
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

            setReplyDraft('');
            setShowReplies(true);
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
                        <p className="text-[11px] text-zinc-500">Loading replies...</p>
                    ) : replies.length === 0 ? (
                        <p className="text-[11px] italic text-zinc-600">No replies yet.</p>
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
    const COMMENT_SYNC_INTERVAL_MS = 10000;
    const [likes, setLikes] = useState(0);
    const [hasLiked, setHasLiked] = useState(false);
    const [comments, setComments] = useState<CommentItem[]>([]);
    const [newComment, setNewComment] = useState('');
    const [showComments, setShowComments] = useState(false);
    const [loading, setLoading] = useState(true);

    const fetchInteractionData = useCallback(async () => {
        try {
            const likeRes = await apiFetch(`/api/v1/interactions/${targetId}/likes/count?type=${targetType}`);
            if (likeRes.ok) {
                const likeData = await likeRes.json();
                setLikes(likeData.count);
                setHasLiked(likeData.hasLiked);
            }

            const commentRes = await apiFetch(`/api/v1/interactions/${targetId}/comments?type=${targetType}`);
            if (commentRes.ok) {
                const commentData = await commentRes.json();
                setComments(commentData.content ?? []);
            }
        } catch (error) {
            console.error(error);
        } finally {
            setLoading(false);
        }
    }, [targetId, targetType]);

    useEffect(() => {
        void fetchInteractionData();
    }, [fetchInteractionData]);

    useEffect(() => {
        if (!showComments) {
            return;
        }

        const syncNow = () => {
            void fetchInteractionData();
        };

        const intervalId = window.setInterval(syncNow, COMMENT_SYNC_INTERVAL_MS);
        const handleFocus = () => syncNow();
        const handleVisibilityChange = () => {
            if (document.visibilityState === 'visible') {
                syncNow();
            }
        };

        window.addEventListener('focus', handleFocus);
        document.addEventListener('visibilitychange', handleVisibilityChange);

        return () => {
            window.clearInterval(intervalId);
            window.removeEventListener('focus', handleFocus);
            document.removeEventListener('visibilitychange', handleVisibilityChange);
        };
    }, [fetchInteractionData, showComments]);

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
        }
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
                setNewComment('');
                setShowComments(true);
                await fetchInteractionData();
            }
        } catch (error) {
            console.error(error);
        }
    };

    if (loading) return <div className="animate-pulse flex gap-2"><div className="w-16 h-8 bg-zinc-800 rounded"></div></div>;

    return (
        <div className="mt-4 flex w-full flex-col border-t border-zinc-800 pt-4">
            <div className="flex items-center gap-4">
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
                    onClick={() => setShowComments(!showComments)}
                    className="flex items-center gap-2 rounded-full border border-zinc-800 bg-zinc-900 px-3 py-1.5 text-xs font-bold text-zinc-400 transition-all hover:bg-zinc-800 hover:text-white"
                >
                    <svg className="h-4 w-4 fill-none stroke-current" viewBox="0 0 24 24" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                        <path d="M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.48 8.48 0 0 1 8 8v.5z"></path>
                    </svg>
                    {comments.length}
                </button>
            </div>

            {showComments && (
                <div className="mt-4 space-y-4 animate-in fade-in slide-in-from-top-2 duration-200">
                    <form onSubmit={postComment} className="flex gap-2">
                        <input
                            type="text"
                            className="flex-1 rounded-lg border border-zinc-800 bg-black p-2 text-xs text-white placeholder:text-zinc-600 focus:border-green-500 focus:outline-none"
                            placeholder="Add a comment..."
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

                    <div className="max-h-[28rem] space-y-3 overflow-y-auto pr-2 custom-scrollbar">
                        {comments.length === 0 ? (
                            <p className="py-2 text-center text-[10px] italic text-zinc-600">No comments yet. Be the first!</p>
                        ) : (
                            comments.map((comment) => (
                                <CommentThread
                                    key={comment.id}
                                    comment={comment}
                                    onRefresh={fetchInteractionData}
                                />
                            ))
                        )}
                    </div>
                </div>
            )}
        </div>
    );
}
