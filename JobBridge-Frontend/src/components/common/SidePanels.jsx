import { useEffect, useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { useAuth } from '../../stores/auth';
import { useBookmarks } from '../../stores/bookmarks';
import { useRecentJobs } from '../../stores/recentJobs';
import client from '../../api/client';
import CompanyCover from './CompanyCover';
import '../../assets/styles/side-panels.css';

const wideQuery = '(min-width: 1680px)';
const emptyRows = [];
export default function SidePanels() {
  const { pathname } = useLocation();
  const [wide, setWide] = useState(() => window.matchMedia(wideQuery).matches);
  useEffect(() => {
    const media = window.matchMedia(wideQuery);
    const update = () => setWide(media.matches);
    media.addEventListener('change', update);
    return () => media.removeEventListener('change', update);
  }, []);
  const allowed = pathname === '/' || ['/jobs', '/companies', '/notices', '/bookmarks', '/applications', '/resumes', '/employment'].some((path) => pathname === path || pathname.startsWith(path + '/'));
  return wide && allowed ? <WidePanels /> : null;
}

function WidePanels() {
  const user = useAuth((state) => state.user);
  const { pathname } = useLocation();
  const owner = user?.userId || 'guest';
  const recent = useRecentJobs((state) => state.byOwner[owner] || emptyRows);
  const loadRecent = useRecentJobs((state) => state.load);
  const clearRecent = useRecentJobs((state) => state.clear);
  const bookmarks = useBookmarks();
  const loadBookmarks = bookmarks.load;
  const seeker = user?.role === 'JOB_SEEKER';
  const [summary, setSummary] = useState({ owner: null, count: null, error: false });
  const [showTop, setShowTop] = useState(window.scrollY > 300);
  useEffect(() => { loadRecent(owner); }, [owner, loadRecent]);
  useEffect(() => {
    if (seeker) loadBookmarks(user.userId);
  }, [seeker, user?.userId, loadBookmarks]);
  useEffect(() => {
    if (!seeker) return;
    let active = true;
    async function load() {
      try {
        const { data } = await client.get('/applications');
        if (active) setSummary({ owner, count: data.length, error: false });
      } catch {
        if (active) setSummary({ owner, count: null, error: true });
      }
    }
    load();
    window.addEventListener('focus', load);
    return () => { active = false; window.removeEventListener('focus', load); };
  }, [owner, seeker, pathname]);
  useEffect(() => {
    const update = () => setShowTop(window.scrollY > 300);
    window.addEventListener('scroll', update, { passive: true });
    return () => window.removeEventListener('scroll', update);
  }, []);
  const saved = bookmarks.owner === user?.userId && bookmarks.loaded ? bookmarks.ids.length : '—';
  const applications = summary.owner === owner && !summary.error ? summary.count ?? '—' : '—';
  return (
    <>
      <aside className="page-side-panel page-side-left" aria-label="최근 본 채용공고">
        <section className="side-panel-card">
          <div className="side-panel-heading"><h2>최근 본 공고</h2><span>{recent.length}/3</span></div>
          {recent.length ? <div className="side-recent-list">{recent.map((job) => (
            <Link to={`/jobs/${job.jobId}`} key={job.jobId} className="side-recent-job">
              <CompanyCover companyName={job.companyName} industry={job.industry} className="side-recent-cover" />
              <span><small>{job.companyName}</small><strong>{job.title}</strong></span>
            </Link>
          ))}</div> : <div className="side-panel-empty"><span aria-hidden="true">◷</span><p>아직 둘러본 공고가 없어요.</p><small>공고를 열면 이곳에 모아드려요.</small><Link to="/jobs">채용공고 둘러보기 →</Link></div>}
          {!!recent.length && <button className="side-clear-button" type="button" onClick={() => clearRecent(owner)}>기록 비우기</button>}
        </section>
        <p className="side-panel-caption">좋은 기회, 놓치지 마세요.</p>
      </aside>
      <aside className="page-side-panel page-side-right" aria-label="나의 취업 활동 바로가기">
        <section className="side-panel-card side-career-panel">
          <span className="side-panel-eyebrow">MY NEXT CHAPTER</span>
          <h2>{seeker ? '나의 취업 활동' : user ? '나의 워크스페이스' : '다음 기회를 준비해요'}</h2>
          {seeker ? <>
            <div className="side-activity-stats"><Link to="/bookmarks"><span>관심 공고</span><strong>{saved}<small>개</small></strong></Link><Link to="/applications"><span>지원 내역</span><strong>{applications}<small>건</small></strong></Link></div>
            <Link className="side-primary-link" to="/resumes/new">이력서 작성 <span aria-hidden="true">↗</span></Link>
            <Link className="side-secondary-link" to="/resumes">내 이력서 관리 →</Link>
          </> : user ? <>
            <p className="side-panel-description">등록한 공고와 운영 현황을 확인하세요.</p>
            <Link className="side-primary-link" to={user.role === 'ADMIN' ? '/admin' : '/company'}>대시보드로 이동 <span aria-hidden="true">↗</span></Link>
          </> : <>
            <p className="side-panel-description">관심 공고와 지원 현황을<br />한눈에 확인하세요.</p>
            <Link className="side-primary-link" to="/login">로그인하기 <span aria-hidden="true">↗</span></Link>
            <Link className="side-secondary-link" to="/signup">처음이신가요? 회원가입 →</Link>
          </>}
        </section>
        <Link className="side-help-link" to="/inquiries">도움이 필요하세요? <span>고객센터 ↗</span></Link>
      </aside>
      {showTop && <button className="page-back-to-top" type="button" onClick={() => window.scrollTo({ top: 0, behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth' })} aria-label="맨 위로 이동"><svg viewBox="0 0 20 20" fill="none" aria-hidden="true"><path d="m5 10 5-5 5 5M10 5v11" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" /></svg><span>TOP</span></button>}
    </>
  );
}
