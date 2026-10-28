import { Link, useSearchParams } from 'react-router-dom';
import { useCompanyOverview } from '../hooks/companyOverview';
import { useResource } from '../hooks/useResource';
import CompanyCover from '../components/common/CompanyCover';
import { Badge, Empty, PagedResource, Resource, Title } from '../components/common/UI';
import '../assets/styles/company-overview.css';

const number = (value) => Number(value || 0).toLocaleString('ko-KR');
const date = (value) => new Intl.DateTimeFormat('ko-KR', { timeZone: 'Asia/Seoul', month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit', hour12: false }).format(new Date(value));
function Icon({ type }) {
  return <svg viewBox="0 0 24 24" fill="none" aria-hidden="true" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round">{type === 'people' ? <><circle cx="9" cy="8" r="3" /><path d="M3 20v-2a6 6 0 0 1 12 0v2M16 5a3 3 0 0 1 0 6M21 20v-2a6 6 0 0 0-4-5" /></> : type === 'calendar' ? <><rect x="3" y="5" width="18" height="16" rx="2" /><path d="M7 3v4M17 3v4M3 11h18M8 15h3M8 18h6" /></> : <><rect x="5" y="3" width="14" height="18" rx="2" /><path d="M9 8h6M9 12h6M9 16h4" /></>}</svg>;
}

export default function CompanyOverview() {
  const resource = useCompanyOverview();
  return <div className="company-overview">
    <Title description="우리 기업의 채용 현황을 한눈에 확인하세요." action={<button className="secondary" onClick={resource.reload} disabled={resource.loading}>새로고침</button>}>기업 채용 대시보드</Title>
    <Resource resource={resource}>{(data) => data.profile.profileMissing ? <div className="callout"><h2>기업 정보를 등록해 주세요</h2><p>기업명과 사업자번호를 등록하면 관리자 심사가 시작됩니다.</p><Link className="button" to="/company/profile">기업 정보 등록 →</Link></div> : <>
      <section className="company-welcome"><span className="company-welcome-icon"><Icon /></span><div><h2>{data.profile.companyName}님, 좋은 인재와의 만남을 응원합니다.</h2><p>{data.profile.reviewReason || (data.profile.approvalStatus === 'APPROVED' ? '지원자를 확인하고 다음 채용 단계를 준비해 보세요.' : '기업 승인 상태를 확인하고 채용을 준비해 보세요.')}</p></div><Badge value={data.profile.approvalStatus} /></section>
      <div className="company-summary-grid">{[
        ['게시 중인 공고', data.publishedJobs, 'document', 'green', '/company/jobs?status=PUBLISHED'],
        ['누적 지원자', data.applications, 'people', 'purple', '/company/applicants'],
        ['예정된 면접', data.interviewCount, 'calendar', 'orange', '/company/interviews'],
      ].map(([label, value, icon, tone, to]) => <Link to={to} className="company-summary-card" key={label}><span className={`company-metric-icon ${tone}`}><Icon type={icon} /></span><span><small>{label}</small><strong>{number(value)}</strong></span><span className="company-card-arrow">↗</span></Link>)}</div>
      <div className="company-section-heading"><h2>진행 중인 채용공고 <span>({number(data.publishedJobs)})</span></h2><Link to="/company/jobs">전체 공고 보기 →</Link></div>
      {data.activeJobs.length ? <div className="company-overview-job-list">{data.activeJobs.map((job) => <article className="company-overview-job" key={job.jobId}>
        <CompanyCover companyName={data.profile.companyName} industry={data.profile.industry} bannerImageUrl={job.bannerImageUrl} className="company-overview-job-image" />
        <div className="company-overview-job-copy"><div className="company-overview-job-title"><h3>{job.title}</h3><Badge value={job.status} /></div><p>{job.jobType} · {job.workLocation}</p><div className="company-overview-job-meta"><span>지원자 {number(job.applicationCount)}명</span><span>{job.endDate} 마감</span></div></div>
        <Link className="company-applicant-link" to={`/company/jobs/${job.jobId}/applicants`}>지원자 관리 →</Link>
      </article>)}</div> : <Empty to="/company/jobs/new" action="새 채용공고 작성">게시 중인 공고가 없습니다. 새로운 동료를 만날 공고를 작성해 보세요.</Empty>}
      <div className="company-dashboard-actions"><Link className="button" to="/company/jobs/new">＋ 채용공고 작성</Link><Link className="button secondary" to="/company/profile">기업 정보 관리</Link></div>
    </>}</Resource>
  </div>;
}

export function CompanyActivityPanels() {
  const resource = useCompanyOverview();
  const data = resource.data;
  const count = (key) => data ? number(data[key]) : '—';
  return <>
    <aside className="page-side-panel page-side-left company-side-panel" aria-label="기업 채용공고와 기업 정보">
      <section className="side-panel-card"><div className="side-panel-heading"><h2>내 채용공고</h2><Icon /></div>
        <div className="company-side-status">{[['게시 중', 'publishedJobs', 'green', '/company/jobs?status=PUBLISHED'], ['심사 중', 'pendingJobs', 'amber', '/company/jobs?status=PENDING'], ['마감 예정', 'closingJobs', 'blue', '/company/jobs?closing=true']].map(([label,key,tone,to]) => <Link key={key} to={to}><span><i className={tone} />{label}</span><strong>{count(key)}</strong><span>›</span></Link>)}</div>
        <Link className="side-primary-link" to="/company/jobs/new">＋ 채용공고 작성</Link>
      </section>
      <section className="side-panel-card"><div className="side-panel-heading"><h2>기업 정보</h2><Icon /></div><p className="company-profile-completion">기업 정보 완성도 <strong>{data ? `${data.profileCompletion}%` : '—'}</strong></p><div className="company-profile-progress" role="progressbar" aria-label="기업 정보 완성도" aria-valuemin={0} aria-valuemax={100} aria-valuenow={data?.profileCompletion || 0}><span style={{width: `${data?.profileCompletion || 0}%`}} /></div><p className="side-panel-description">기업 소개와 이미지를 채워 우리 회사를 알려보세요.</p><Link className="company-side-outline" to="/company/profile">기업 정보 수정 →</Link></section>
    </aside>
    <aside className="page-side-panel page-side-right company-side-panel" aria-label="기업 지원자 현황과 면접 일정">
      <section className="side-panel-card"><div className="side-panel-heading"><h2>지원자 현황</h2><Icon type="people" /></div><Link className="company-side-applicants" to="/company/applicants?view=new"><span>신규 지원자<small>최근 7일 기준</small></span><strong>{count('newApplicants')}<small>명</small></strong></Link><Link className="company-side-applicants pending" to="/company/applicants?view=unreviewed"><span>미검토 지원자<small>서류 검토 시작 전</small></span><strong>{count('unreviewedApplicants')}<small>명</small></strong></Link><Link className="company-side-outline" to="/company/applicants">지원자 확인 →</Link></section>
      <section className="side-panel-card"><div className="side-panel-heading"><h2>다가오는 면접</h2><Icon type="calendar" /></div>{resource.error ? <p className="side-state-message">현황을 불러오지 못했습니다.</p> : !data ? <p className="side-state-message">일정을 확인하고 있어요.</p> : data.upcomingInterviews.length ? <div className="side-interview-list">{data.upcomingInterviews.map((interview) => <Link to={`/company/applications/${interview.applicationId}`} key={interview.interviewId}><time dateTime={interview.interviewAt}>{date(interview.interviewAt)}</time><strong>{interview.name}</strong><small>{interview.interviewType === 'ONLINE' ? '온라인 면접' : '오프라인 면접'}</small></Link>)}</div> : <p className="side-state-message">예정된 면접이 없습니다.</p>}<Link className="company-side-outline" to="/company/interviews">전체 일정 보기 →</Link></section>
      {resource.error && <button className="side-clear-button" onClick={resource.reload}>현황 다시 불러오기</button>}
    </aside>
  </>;
}

export function CompanyApplicants() {
  const [params] = useSearchParams();
  const view = ['new','unreviewed'].includes(params.get('view')) ? params.get('view') : '';
  const resource = useResource(`/company/applicants?view=${view}`, { refreshInterval: 30000 });
  return <><Title description={view === 'new' ? '최근 7일 이내 접수한 지원서입니다.' : view === 'unreviewed' ? '지원 완료 상태로 서류 검토를 시작하지 않은 지원서입니다.' : '우리 기업의 모든 채용공고에 접수한 지원서를 확인하세요.'} action={view ? <Link to="/company/applicants">전체 지원자 보기 →</Link> : null}>{view === 'new' ? '신규 지원자' : view === 'unreviewed' ? '미검토 지원자' : '지원자 관리'}</Title><PagedResource key={view} resource={resource}>{(rows) => rows.length ? <div className="stack">{rows.map((row) => <Link className="panel row company-applicant-row" to={`/company/applications/${row.applicationId}`} key={row.applicationId}><div><h2>{row.name}</h2><p>{row.title}</p><small>{date(row.appliedAt)} 지원</small></div><Badge value={row.status} /></Link>)}</div> : <Empty>해당하는 지원자가 없습니다.</Empty>}</PagedResource></>;
}

export function CompanyInterviewSchedule() {
  const resource = useResource('/company/interviews', { refreshInterval: 30000 });
  return <><Title description="앞으로 예정된 면접을 가까운 일정부터 확인하세요.">면접 일정</Title><PagedResource resource={resource}>{(rows) => rows.length ? <div className="stack">{rows.map((row) => <Link className="panel row company-applicant-row" to={`/company/applications/${row.applicationId}`} key={row.interviewId}><div><strong className="company-interview-time">{date(row.interviewAt)}</strong><h2>{row.name}</h2><p>{row.title}</p></div><Badge value={row.interviewType} /></Link>)}</div> : <Empty>예정된 면접이 없습니다.</Empty>}</PagedResource></>;
}
