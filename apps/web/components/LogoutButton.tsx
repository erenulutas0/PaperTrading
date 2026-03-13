'use client';

import { useRouter } from 'next/navigation';
import { useState } from 'react';
import { clearAuthSession } from '../lib/auth-storage';
import { apiFetch } from '../lib/api-client';

interface LogoutButtonProps {
    className?: string;
    label?: string;
}

export default function LogoutButton({ className, label = 'Exit' }: LogoutButtonProps) {
    const router = useRouter();
    const [submitting, setSubmitting] = useState(false);

    const handleLogout = async () => {
        if (submitting) {
            return;
        }

        setSubmitting(true);
        try {
            const refreshToken = typeof window !== 'undefined'
                ? window.localStorage.getItem('refreshToken')
                : null;

            await apiFetch('/api/v1/auth/logout', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({
                    refreshToken,
                    allSessions: true,
                }),
            });
        } catch (error) {
            console.error(error);
        } finally {
            clearAuthSession();
            router.replace('/');
            router.refresh();
            setSubmitting(false);
        }
    };

    return (
        <button
            type="button"
            onClick={handleLogout}
            disabled={submitting}
            className={className}
        >
            {submitting ? 'Exiting...' : label}
        </button>
    );
}
