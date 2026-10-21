import { create } from 'zustand';
import client, { message } from '../api/client';

export const useBookmarks = create((set, get) => ({
  owner: null,
  ids: [],
  pending: [],
  loading: false,
  loaded: false,
  notice: null,
  reset() {
    set({ owner: null, ids: [], pending: [], loading: false, loaded: false, notice: null });
  },
  notify(text, error = false) {
    set({ notice: { text, error, key: Date.now() } });
  },
  dismiss() {
    set({ notice: null });
  },
  async load(owner, force = false) {
    const state = get();
    if (state.owner === owner && (state.loading || (state.loaded && !force))) return;
    set({
      owner,
      ids: state.owner === owner ? state.ids : [],
      pending: [],
      loaded: false,
      loading: true,
    });
    try {
      const { data } = await client.get('/bookmarks');
      if (get().owner !== owner) return;
      set({ ids: data.map((r) => String(r.jobId)), loading: false, loaded: true });
    } catch (error) {
      if (get().owner === owner) {
        set({ loading: false });
        get().notify(message(error), true);
      }
    }
  },
  async toggle(owner, id) {
    const key = String(id);
    const state = get();
    if (state.owner !== owner || !state.loaded || state.pending.includes(key)) return false;
    const saved = state.ids.includes(key);
    set({ pending: [...state.pending, key] });
    try {
      if (saved) await client.delete(`/bookmarks/${id}`);
      else await client.put(`/bookmarks/${id}`);
      if (get().owner !== owner) return false;
      set((current) => ({
        ids: saved
          ? current.ids.filter((item) => item !== key)
          : [...new Set([...current.ids, key])],
      }));
      get().notify(
        saved
          ? '관심공고에서 해제되었습니다.'
          : '관심공고에 등록되었습니다. 관심 공고 메뉴에서 확인하세요.',
      );
      return true;
    } catch (error) {
      get().notify(message(error), true);
      return false;
    } finally {
      if (get().owner === owner)
        set((current) => ({ pending: current.pending.filter((item) => item !== key) }));
    }
  },
}));
