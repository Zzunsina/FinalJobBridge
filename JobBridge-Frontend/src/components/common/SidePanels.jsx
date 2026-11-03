import { CompanyActivityPanels } from '../../pages/CompanyOverview';
import { useEffect, useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { useAuth } from '../../stores/auth';
import { useRecentJobs } from '../../stores/recentJobs';
import { koreanToday } from '../../utils/memberInputs';
import client from '../../api/client';
import '../../assets/styles/side-panels.css';

const wideQuery = '(min-width: 1680px)';
const emptyRows = [];
const daysLeft = (date) => Math.round((Date.parse(date) - Date.parse(koreanToday())) / 86400000);
const deadline = (date) => { const days = daysLeft(date); return days < 0 ? '마감' : days === 0 ? '오늘 마감' : `D-${days}`; };

export default function SidePanels() {
  const role = useAuth((state) => state.user?.role);
  const { pathname } = useLocation();
  const [wide, setWide] = useState(() => window.matchMedia(wideQuery).matches);
  
  // 스크롤 감지 상태를 최상위로 올려서 어떤 화면이든 TOP 버튼이 동작하게 합니다.
  const [showTop, setShowTop] = useState(false);

  useEffect(() => {
    const media = window.matchMedia(wideQuery);
    const update = () => setWide(media.matches);
    media.addEventListener('change', update);
    return () => media.removeEventListener('change', update);
  }, []);

  useEffect(() => {
    const updateScroll = () => setShowTop(window.scrollY > 300);
    window.addEventListener('scroll', updateScroll, { passive: true });
    return () => window.removeEventListener('scroll', updateScroll);
  }, []);

  const allowed = pathname === '/' || ['/jobs', '/companies', '/bookmarks', '/applications', '/resumes', '/employment'].some((path) => pathname === path || pathname.startsWith(path + '/'));
  const personalView = !role || role === 'JOB_SEEKER';
  const companyView = role === 'COMPANY' && (pathname === '/' || pathname === '/company' || pathname === '/jobs' || pathname.startsWith('/jobs/') || pathname === '/companies' || pathname.startsWith('/companies/'));

  return (
    <>
      {wide && companyView ? (
        <CompanyActivityPanels />
      ) : wide && allowed && personalView ? (
        <WidePanels />
      ) : null}

      {/* 권한 및 해상도와 무관하게 스크롤이 내려가면 모든 페이지(관리자, 기업, 유저)에 TOP 버튼 노출 */}
      {showTop && (
        <button
          className="page-back-to-top"
          type="button"
          onClick={() =>
            window.scrollTo({
              top: 0,
              behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth',
            })
          }
          aria-label="맨 위로 이동"
        >
          <svg viewBox="0 0 20 20" fill="none" aria-hidden="true">
            <path d="m5 10 5-5 5 5M10 5v11" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" />
          </svg>
          <span>TOP</span>
        </button>
      )}
    </>
  );
}

function WidePanels() {
  const owner = useAuth(state => state.user?.userId || 'guest');
  const recent = useRecentJobs(state => state.byOwner[owner] || emptyRows);
  const loadRecent = useRecentJobs(state => state.load);
  const clearRecent = useRecentJobs(state => state.clear);
  const [closing, setClosing] = useState({ rows: [], loading: true, error: false });
  useEffect(() => { loadRecent(owner); }, [owner, loadRecent]);
  useEffect(() => {
    let active = true;
    async function load() {
      try {
        const { data } = await client.get('/jobs?sort=deadline&size=3');
        if (active) setClosing({ rows: (data.content || []).filter(job => daysLeft(job.endDate) >= 0 && daysLeft(job.endDate) <= 7).slice(0, 2), loading: false, error: false });
      } catch { if (active) setClosing({ rows: [], loading: false, error: true }); }
    }
    load();
    window.addEventListener('focus', load);
    const timer = window.setInterval(() => { if (!document.hidden) load(); }, 60000);
    return () => { active = false; window.removeEventListener('focus', load); window.clearInterval(timer); };
  }, []);
  const visibleRecent = recent.slice(0, 2);
  return <aside className="page-side-panel page-side-right side-compact-rail" aria-label="최근 본 공고와 마감 임박 채용">
    <section className="side-compact-section" aria-labelledby="side-recent-title">
      <div className="side-compact-heading"><h2 id="side-recent-title">최근 본 공고</h2><span>{visibleRecent.length}</span></div>
      {visibleRecent.length ? <><div className="side-compact-list">{visibleRecent.map(job => <Link key={job.jobId} to={'/jobs/' + job.jobId}><strong>{job.companyName}</strong><span>{job.title}</span></Link>)}</div><button className="side-compact-clear" type="button" onClick={() => clearRecent(owner)}>기록 비우기</button></> : <div className="side-compact-empty"><p>최근 본 공고가 없습니다.</p><Link to="/jobs">채용공고 둘러보기</Link></div>}
    </section>
    <section className="side-compact-section" aria-labelledby="side-closing-title">
      <div className="side-compact-heading"><h2 id="side-closing-title">마감 임박 채용</h2></div>
      {closing.loading ? <p className="side-compact-empty" role="status">공고를 불러오는 중입니다.</p> : closing.error ? <p className="side-compact-empty">공고를 불러오지 못했습니다.</p> : closing.rows.length ? <div className="side-compact-list closing">{closing.rows.map(job => <Link key={job.jobId} to={'/jobs/' + job.jobId}><div><strong>{job.companyName}</strong><em>{deadline(job.endDate)}</em></div><span>{job.title}</span></Link>)}</div> : <p className="side-compact-empty">7일 이내 마감 공고가 없습니다.</p>}
      <Link className="side-compact-all" to="/jobs?sort=deadline">마감순 공고 보기</Link>
    </section>
  </aside>;
}
