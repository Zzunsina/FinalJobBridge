import { CompanyActivityPanels } from '../../pages/CompanyOverview';
import { useEffect, useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { useAuth } from '../../stores/auth';
import { useBookmarks } from '../../stores/bookmarks';
import { useRecentJobs } from '../../stores/recentJobs';
import { koreanToday } from '../../utils/memberInputs';
import client from '../../api/client';
import CompanyCover from './CompanyCover';
import '../../assets/styles/side-panels.css';

const wideQuery = '(min-width: 1680px)';
const emptyRows = [];
const daysLeft = (date) => Math.round((Date.parse(date) - Date.parse(koreanToday())) / 86400000);
const deadline = (date) => { const days = daysLeft(date); return days < 0 ? '마감' : days === 0 ? '오늘 마감' : `D-${days}`; };
const interviewDate = (date) => new Intl.DateTimeFormat('ko-KR', { timeZone: 'Asia/Seoul', month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit', hour12: false }).format(new Date(date));

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
  const user = useAuth((state) => state.user);
  const { pathname } = useLocation();
  const owner = user?.userId || 'guest';
  const recent = useRecentJobs((state) => state.byOwner[owner] || emptyRows);
  const loadRecent = useRecentJobs((state) => state.load);
  const clearRecent = useRecentJobs((state) => state.clear);
  const bookmarks = useBookmarks();
  const loadBookmarks = bookmarks.load;
  const seeker = user?.role === 'JOB_SEEKER';
  const [summary, setSummary] = useState({ owner: null, data: null, error: false });
  const [closing, setClosing] = useState({ rows: [], loading: true, error: false });

  useEffect(() => { loadRecent(owner); }, [owner, loadRecent]);
  useEffect(() => { if (seeker) loadBookmarks(user.userId); }, [seeker, user?.userId, loadBookmarks]);
  
  useEffect(() => {
    let active = true;
    async function load() {
      try {
        const { data } = await client.get('/jobs?sort=deadline&size=3');
        if (active) setClosing({ rows: (data.content || []).filter((job) => daysLeft(job.endDate) >= 0 && daysLeft(job.endDate) <= 7), loading: false, error: false });
      } catch { if (active) setClosing({ rows: [], loading: false, error: true }); }
    }
    load();
    window.addEventListener('focus', load);
    const timer = window.setInterval(() => { if (!document.hidden) load(); }, 60000);
    return () => { active = false; window.removeEventListener('focus', load); window.clearInterval(timer); };
  }, []);

  useEffect(() => {
    if (!seeker) return;
    let active = true;
    async function load() {
      try {
        const { data } = await client.get('/job-seekers/me/activity');
        if (active) setSummary({ owner, data, error: false });
      } catch { if (active) setSummary({ owner, data: null, error: true }); }
    }
    load();
    window.addEventListener('focus', load);
    const timer = window.setInterval(() => { if (!document.hidden) load(); }, 60000);
    return () => { active = false; window.removeEventListener('focus', load); window.clearInterval(timer); };
  }, [owner, seeker, pathname]);

  const saved = bookmarks.owner === user?.userId && bookmarks.loaded ? bookmarks.ids.length : '—';
  const activity = summary.owner === owner && !summary.error ? summary.data : null;
  const activityError = summary.owner === owner && summary.error;
  const preparation = activity ? (activity.resumeCount > 0 ? 1 : 0) + (activity.assessedResumeCount > 0 ? 1 : 0) : 0;

  return (
    <>
      <aside className="page-side-panel page-side-left" aria-label="최근 본 공고와 마감 임박 채용">
        <section className="side-panel-card">
          <div className="side-panel-heading"><h2>최근 본 공고</h2><span>{recent.length}/3</span></div>
          {recent.length ? <div className="side-recent-list">{recent.map((job) => <Link to={`/jobs/${job.jobId}`} key={job.jobId} className="side-recent-job">
            <CompanyCover companyName={job.companyName} industry={job.industry} className="side-recent-cover" />
            <span><small>{job.companyName}</small><strong>{job.title}</strong>{job.endDate && <em className="side-mini-deadline">{deadline(job.endDate)}</em>}</span>
          </Link>)}</div> : <div className="side-panel-empty"><span aria-hidden="true">◷</span><p>아직 둘러본 공고가 없어요.</p><small>공고를 열면 이곳에 모아드려요.</small><Link to="/jobs">채용공고 둘러보기 →</Link></div>}
          {!!recent.length && <button className="side-clear-button" type="button" onClick={() => clearRecent(owner)}>기록 비우기</button>}
        </section>
        <section className="side-panel-card side-deadline-panel">
          <div className="side-panel-heading"><h2>마감 임박 채용</h2><span className="side-live-dot">7일 이내</span></div>
          {closing.loading ? <p className="side-state-message" role="status">공고를 불러오는 중입니다.</p> : closing.error ? <p className="side-state-message">공고를 불러오지 못했습니다.<Link to="/jobs?sort=deadline">전체 공고에서 확인 →</Link></p> : closing.rows.length ? <div className="side-deadline-list">{closing.rows.map((job) => <Link to={`/jobs/${job.jobId}`} key={job.jobId}>
            <div><small>{job.companyName}</small><span className="side-deadline-badge">{deadline(job.endDate)}</span></div><strong>{job.title}</strong><span className="side-deadline-location">{job.workLocation} · {job.careerType}</span>
          </Link>)}</div> : <p className="side-state-message">7일 이내 마감 공고가 없습니다.</p>}
          <Link className="side-section-link" to="/jobs?sort=deadline">마감순으로 전체 보기 →</Link>
        </section>
        <p className="side-panel-caption">좋은 기회, 놓치지 마세요.</p>
      </aside>
      <aside className="page-side-panel page-side-right" aria-label="나의 취업 활동과 준비 현황">
        <section className="side-panel-card side-career-panel">
          <span className="side-panel-eyebrow">MY NEXT CHAPTER</span><h2>{seeker ? '나의 취업 활동' : user ? '나의 워크스페이스' : '다음 기회를 준비해요'}</h2>
          {seeker ? <>
            <div className="side-activity-stats"><Link to="/bookmarks"><span>관심 공고</span><strong>{saved}<small>개</small></strong></Link><Link to="/applications"><span>지원 내역</span><strong>{activity?.applicationCount ?? '—'}<small>건</small></strong></Link></div>
            {activity && <Link className="side-in-progress" to="/applications">전형 진행 중 <strong>{activity.activeApplications}건</strong></Link>}
            <Link className="side-primary-link" to="/resumes/new">이력서 작성 <span aria-hidden="true">↗</span></Link>
          </> : user ? <><p className="side-panel-description">등록한 공고와 운영 현황을 확인하세요.</p><Link className="side-primary-link" to={user.role === 'ADMIN' ? '/admin' : '/company'}>대시보드로 이동 ↗</Link></> : <><p className="side-panel-description">관심 공고와 지원 현황을<br />한눈에 확인하세요.</p><Link className="side-primary-link" to="/login">로그인하기 ↗</Link><Link className="side-secondary-link" to="/signup">처음이신가요? 회원가입 →</Link></>}
        </section>
        {(!user || seeker) && <>
          <section className="side-panel-card side-interview-panel">
            <div className="side-panel-heading"><h2>다가오는 면접</h2><span aria-hidden="true">◷</span></div>
            {!seeker ? <p className="side-state-message">로그인하면 나의 면접 일정을 확인할 수 있어요.</p> : activityError ? <p className="side-state-message">일정을 불러오지 못했습니다.</p> : !activity ? <p className="side-state-message" role="status">일정을 확인하고 있어요.</p> : activity.upcomingInterviews.length ? <div className="side-interview-list">{activity.upcomingInterviews.map((interview) => <Link key={interview.interviewId} to={`/applications/${interview.applicationId}`}><time dateTime={interview.interviewAt}>{interviewDate(interview.interviewAt)}</time><strong>{interview.companyName}</strong><small>{interview.interviewType === 'ONLINE' ? '온라인 면접' : '오프라인 면접'} · 상세 일정 확인 ↗</small></Link>)}</div> : <p className="side-state-message">예정된 면접이 없습니다.<br />새로운 기회를 둘러보세요.</p>}
            {seeker && <Link className="side-section-link" to="/applications">지원 내역 확인 →</Link>}
          </section>
          <section className="side-panel-card side-preparation-panel">
            <div className="side-panel-heading"><h2>취업 준비 체크</h2><span>{activity ? `${preparation}/2` : 'CHECK'}</span></div>
            {activityError ? <p className="side-state-message">준비 상태를 불러오지 못했습니다.</p> : seeker && !activity ? <p className="side-state-message" role="status">준비 상태를 확인하고 있어요.</p> : <>
              {activity && <div className="side-preparation-progress" role="progressbar" aria-label="취업 준비 완료 항목" aria-valuenow={preparation} aria-valuemin={0} aria-valuemax={2}><span style={{ width: `${preparation * 50}%` }} /></div>}
              <Link className={`side-check-item ${activity?.resumeCount > 0 ? 'is-complete' : ''}`} to="/resumes"><span aria-hidden="true">{activity?.resumeCount > 0 ? '✓' : '1'}</span><span>이력서 작성<small>{activity ? activity.resumeCount > 0 ? `${activity.resumeCount}개 작성 완료` : '나의 경험을 정리해 보세요' : '경험과 강점을 담아보세요'}</small></span></Link>
              <Link className={`side-check-item ${activity?.assessedResumeCount > 0 ? 'is-complete' : ''}`} to="/resumes"><span aria-hidden="true">{activity?.assessedResumeCount > 0 ? '✓' : '2'}</span><span>인적성검사<small>{activity ? activity.assessedResumeCount > 0 ? '검사 완료 이력서 보유' : '이력서에서 검사를 완료하세요' : '이력서 작성 시 필수 검사'}</small></span></Link>
            </>}
            <Link className="side-section-link" to="/notices">취업 준비 가이드 →</Link>
          </section>
        </>}
        <Link className="side-help-link" to="/inquiries">도움이 필요하세요? <span>고객센터 ↗</span></Link>
      </aside>
    </>
  );
}