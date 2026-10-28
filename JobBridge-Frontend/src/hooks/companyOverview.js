import { useEffect } from 'react';
import { useLocation } from 'react-router-dom';
import { create } from 'zustand';
import { useAuth } from '../stores/auth';
import client, { message } from '../api/client';

const useOverview = create((set, get) => ({
  owner: null, data: null, loading: false, error: '', loadedAt: 0,
  async load(owner, force = false) {
    const old = get();
    if (old.owner === owner && (old.loading || (!force && Date.now() - old.loadedAt < 15000))) return;
    set({ owner, data: old.owner === owner ? old.data : null, loading: true, error: '' });
    try {
      const { data } = await client.get('/company/dashboard');
      if (!Array.isArray(data.activeJobs) || !Array.isArray(data.upcomingInterviews)) throw new Error('기업 현황을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.');
      if (get().owner === owner) set({ data, loading: false, loadedAt: Date.now() });
    } catch (error) {
      if (get().owner === owner) set({ loading: false, error: message(error), loadedAt: 0 });
    }
  },
}));

export function useCompanyOverview() {
  const user = useAuth((state) => state.user);
  const { pathname } = useLocation();
  const state = useOverview();
  const load = state.load;
  useEffect(() => {
    if (user?.role !== 'COMPANY') return;
    load(user.userId);
    const refresh = () => { if (!document.hidden) load(user.userId, true); };
    const timer = window.setInterval(refresh, 60000);
    window.addEventListener('focus', refresh);
    return () => { window.clearInterval(timer); window.removeEventListener('focus', refresh); };
  }, [user?.role, user?.userId, pathname, load]);
  const own = state.owner === user?.userId;
  return { data: own ? state.data : null, loading: !own || (!state.data && state.loading), error: own ? state.error : '', reload: () => load(user.userId, true) };
}
