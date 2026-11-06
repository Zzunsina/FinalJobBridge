import { useEffect } from 'react';
import client from '../api/client';

export function useSessionMonitor(user) {
  useEffect(() => {
    if (!user) return;
    let pending = false;
    const check = async () => {
      if (pending || document.visibilityState === 'hidden') return;
      pending = true;
      try { await client.get('/me'); } catch { /* The client announces expired/replaced sessions. */ }
      finally { pending = false; }
    };
    const interval = window.setInterval(check, 30000);
    window.addEventListener('focus', check);
    document.addEventListener('visibilitychange', check);
    return () => {
      window.clearInterval(interval);
      window.removeEventListener('focus', check);
      document.removeEventListener('visibilitychange', check);
    };
  }, [user]);
}
