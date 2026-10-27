import { create } from 'zustand';

const key = (owner) => `jobbridge:recent-jobs:${owner}`;
function read(owner) {
  try {
    const rows = JSON.parse(sessionStorage.getItem(key(owner)) || '[]');
    return Array.isArray(rows) ? rows.filter((row) => Number.isSafeInteger(row.jobId) && row.jobId > 0 && typeof row.title === 'string' && typeof row.companyName === 'string').slice(0, 3) : [];
  } catch { return []; }
}
function write(owner, rows) {
  try { sessionStorage.setItem(key(owner), JSON.stringify(rows)); } catch { /* Browsing still works when storage is unavailable. */ }
}
export const useRecentJobs = create((set, get) => ({
  byOwner: {},
  load(owner) {
    if (get().byOwner[owner]) return;
    set((state) => ({ byOwner: { ...state.byOwner, [owner]: read(owner) } }));
  },
  remember(owner, job) {
    const row = { jobId: Number(job.jobId), title: job.title, companyName: job.companyName, industry: job.industry || '', endDate: job.endDate || '' };
    if (!Number.isSafeInteger(row.jobId) || row.jobId < 1) return;
    const previous = get().byOwner[owner] || read(owner);
    const rows = [row, ...previous.filter((item) => item.jobId !== row.jobId)].slice(0, 3);
    write(owner, rows);
    set((state) => ({ byOwner: { ...state.byOwner, [owner]: rows } }));
  },
  clear(owner) {
    write(owner, []);
    set((state) => ({ byOwner: { ...state.byOwner, [owner]: [] } }));
  },
}));
