'use client';

import { useState, useEffect } from 'react';
import { apiFetch } from '../lib/api-client';

interface LikeCommentWidgetProps {
    targetId: string;
    targetType: 'PORTFOLIO' | 'ANALYSIS_POST';
}

interface Comment {
    id: string;
    actorId: string;
    content: string;
    createdAt: string;
}

export default function LikeCommentWidget({ targetId, targetType }: LikeCommentWidgetProps) {
    const [likes, setLikes] = useState(0);
    const [hasLiked, setHasLiked] = useState(false);
    const [comments, setComments] = useState<Comment[]>([]);
    const [newComment, setNewComment] = useState('');
    const [showComments, setShowComments] = useState(false);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        fetchInteractionData();
    }, [targetId]);

    const fetchInteractionData = async () => {
        try {
            // Fetch Likes
            const likeRes = await apiFetch(`/api/v1/interactions/${targetId}/likes/count?type=${targetType}`);
            if (likeRes.ok) {
                const likeData = await likeRes.json();
                setLikes(likeData.count);
                setHasLiked(likeData.hasLiked);
            }

            // Fetch Comments
            const commentRes = await apiFetch(`/api/v1/interactions/${targetId}/comments?type=${targetType}`);
            if (commentRes.ok) {
                const commentData = await commentRes.json();
                setComments(commentData.content);
            }
        } catch (error) {
            console.error(error);
        } finally {
            setLoading(false);
        }
    };

    const toggleLike = async () => {
        const userId = localStorage.getItem('userId');
        if (!userId) {
            alert("Please sign in to like this.");
            return;
        }

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
            // Revert on error
            setHasLiked(previousState);
            setLikes(previousLikes);
        }
    };

    const postComment = async (e: React.FormEvent) => {
        e.preventDefault();
        const userId = localStorage.getItem('userId');
        if (!userId) {
            alert("Please sign in to comment.");
            return;
        }
        if (!newComment.trim()) return;

        try {
            const res = await apiFetch(`/api/v1/interactions/${targetId}/comments`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({ targetType, content: newComment })
            });

            if (res.ok) {
                setNewComment('');
                fetchInteractionData(); // Refresh comments list
            }
        } catch (error) {
            console.error(error);
        }
    };

    if (loading) return <div className="animate-pulse flex gap-2"><div className="w-16 h-8 bg-zinc-800 rounded"></div></div>;

    return (
        <div className="flex flex-col w-full mt-4 border-t border-zinc-800 pt-4">
            <div className="flex items-center gap-4">
                <button
                    onClick={toggleLike}
                    className={`flex items-center gap-2 text-xs font-bold px-3 py-1.5 rounded-full transition-all border ${hasLiked
                            ? 'bg-red-500/10 text-red-500 border-red-500/20 shadow-[0_0_10px_rgba(239,68,68,0.2)]'
                            : 'bg-zinc-900 border-zinc-800 text-zinc-400 hover:text-white hover:bg-zinc-800'
                        }`}
                >
                    <svg className={`w-4 h-4 ${hasLiked ? 'fill-current' : 'fill-none stroke-current'}`} viewBox="0 0 24 24" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                        <path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"></path>
                    </svg>
                    {likes}
                </button>

                <button
                    onClick={() => setShowComments(!showComments)}
                    className="flex items-center gap-2 text-xs font-bold text-zinc-400 px-3 py-1.5 rounded-full border border-zinc-800 bg-zinc-900 hover:text-white hover:bg-zinc-800 transition-all"
                >
                    <svg className="w-4 h-4 fill-none stroke-current" viewBox="0 0 24 24" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
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
                            className="flex-1 bg-black border border-zinc-800 rounded-lg p-2 text-xs text-white placeholder-zinc-600 focus:outline-none focus:border-green-500 transition-colors"
                            placeholder="Add a comment..."
                            value={newComment}
                            onChange={(e) => setNewComment(e.target.value)}
                        />
                        <button
                            type="submit"
                            disabled={!newComment.trim()}
                            className="bg-green-600 text-white text-[10px] font-bold px-4 rounded-lg uppercase tracking-wider hover:bg-green-500 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
                        >
                            Post
                        </button>
                    </form>

                    <div className="space-y-3 max-h-48 overflow-y-auto custom-scrollbar pr-2">
                        {comments.length === 0 ? (
                            <p className="text-zinc-600 text-[10px] italic text-center py-2">No comments yet. Be the first!</p>
                        ) : (
                            comments.map(c => (
                                <div key={c.id} className="bg-zinc-900/50 border border-zinc-800/50 p-3 rounded-lg flex items-start gap-3">
                                    <div className="w-6 h-6 rounded-full bg-zinc-800 shrink-0 border border-zinc-700"></div>
                                    <div>
                                        <p className="text-[10px] text-zinc-500 font-mono mb-1">{new Date(c.createdAt).toLocaleDateString()}</p>
                                        <p className="text-xs text-zinc-300 leading-snug">{c.content}</p>
                                    </div>
                                </div>
                            ))
                        )}
                    </div>
                </div>
            )}
        </div>
    );
}
