import { create } from 'zustand';
import client, { loadCsrf, refresh, message } from '../api/client';
import { useBookmarks } from './bookmarks';

export const useAuth = create((set) => ({
  user: null,
  initialized: false,
  error: '',
  sessionNotice: '',
  dismissSessionNotice() { set({ sessionNotice: '' }); },
  async initialize() {
    set({ initialized: false, error: '' });
    try {
      await loadCsrf();
      try {
        const { data } = await refresh();
        set({ user: data, sessionNotice: '' });
      } catch (error) {
        if (error.response?.status !== 401) throw error;
        set({ user: null });
      }
    } catch (error) {
      set({ error: message(error) });
    } finally {
      set({ initialized: true });
    }
  },
  async login(body, adminOnly = false) {
    await loadCsrf();
    const { data } = await client.post(adminOnly ? '/auth/admin/login' : '/auth/login', body);
    useBookmarks.getState().reset();
    set({ user: data, sessionNotice: '' });
    return data;
  },
  async logout() {
    await client.post('/auth/logout');
    useBookmarks.getState().reset();
    set({ user: null });
  },
  setUser(user) {
    set({ user });
  },
}));

if (typeof window !== 'undefined') window.addEventListener('jobbridge:session-ended', (event) => {
  const state = useAuth.getState();
  if (!state.user && !event.detail.replaced) return;
  useBookmarks.getState().reset();
  useAuth.setState({ user: null, sessionNotice: event.detail.message });
});
