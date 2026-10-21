import { create } from 'zustand';
import client, { loadCsrf, refresh, message } from '../api/client';
import { useBookmarks } from './bookmarks';

export const useAuth = create((set) => ({
  user: null,
  initialized: false,
  error: '',
  async initialize() {
    set({ initialized: false, error: '' });
    try {
      await loadCsrf();
      try {
        const { data } = await refresh();
        set({ user: data });
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
    set({ user: data });
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
