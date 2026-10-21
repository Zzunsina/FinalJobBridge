import { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../stores/auth';
import { useBookmarks } from '../../stores/bookmarks';
import '../../assets/styles/bookmarks.css';

export default function BookmarkButton({ jobId, compact = false, onChanged }) {
  const user = useAuth((state) => state.user);
  const bookmarks = useBookmarks();
  const load = bookmarks.load;
  const navigate = useNavigate();
  useEffect(() => {
    if (user?.role === 'JOB_SEEKER') load(user.userId);
  }, [user?.userId, user?.role, load]);
  if (user && user.role !== 'JOB_SEEKER') return null;
  const own = bookmarks.owner === user?.userId;
  const active = own && bookmarks.ids.includes(String(jobId));
  const busy =
    Boolean(user) && (!own || bookmarks.loading || bookmarks.pending.includes(String(jobId)));
  return (
    <button
      type="button"
      className={`bookmark-button secondary ${compact ? 'compact' : ''} ${active ? 'is-saved' : ''}`}
      aria-pressed={active}
      aria-label={active ? '관심공고 해제' : '관심공고 등록'}
      title={active ? '관심공고 해제' : '관심공고 등록'}
      disabled={busy}
      onClick={async () => {
        if (!user) {
          bookmarks.notify('개인회원으로 로그인하면 관심공고를 저장할 수 있습니다.');
          navigate('/login');
          return;
        }
        if (!own || !bookmarks.loaded) {
          await load(user.userId, true);
          return;
        }
        if (await bookmarks.toggle(user.userId, jobId)) await onChanged?.();
      }}
    >
      <svg viewBox="0 0 24 24" aria-hidden="true">
        <path d="m12 2.8 2.8 5.7 6.3.9-4.5 4.4 1.1 6.2-5.7-3-5.7 3 1.1-6.2L3.2 9.4l6.3-.9Z" />
      </svg>
      {!compact && <span>{busy ? '처리 중…' : active ? '관심공고 해제' : '관심공고 등록'}</span>}
    </button>
  );
}

export function BookmarkNotice() {
  const notice = useBookmarks((state) => state.notice);
  const dismiss = useBookmarks((state) => state.dismiss);
  useEffect(() => {
    if (!notice) return;
    const timer = window.setTimeout(dismiss, 5000);
    return () => window.clearTimeout(timer);
  }, [notice, dismiss]);
  return (
    notice && (
      <div
        className={`bookmark-notice ${notice.error ? 'is-error' : ''}`}
        role={notice.error ? 'alert' : 'status'}
      >
        <span aria-hidden="true">{notice.error ? '!' : '✓'}</span>
        <p>{notice.text}</p>
        <button type="button" className="plain" aria-label="관심공고 안내 닫기" onClick={dismiss}>
          ×
        </button>
      </div>
    )
  );
}
