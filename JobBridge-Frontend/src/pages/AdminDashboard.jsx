import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../stores/auth';
import { useResource } from '../hooks/useResource';
import { Badge, Resource, labels } from '../components/common/UI';
import '../assets/styles/admin-dashboard.css';

const number = (value) => Number(value || 0).toLocaleString('ko-KR');
const dateLabel = (value) =>
  new Date(value).toLocaleDateString('ko-KR', {
    timeZone: 'Asia/Seoul',
    month: '2-digit',
    day: '2-digit',
  });
const focusLink = (path, id) => `${path}?focus=${id}`;

function Icon({ name }) {
  const paths = {
    people: (
      <>
        <circle cx="9" cy="8" r="3" />
        <path d="M3 20v-2a6 6 0 0 1 12 0v2M16 5a3 3 0 0 1 0 6M21 20v-2a6 6 0 0 0-4-5" />
      </>
    ),
    jobs: (
      <>
        <rect x="3" y="7" width="18" height="14" rx="2" />
        <path d="M8 7V4h8v3M3 12a24 24 0 0 0 18 0M12 11v4" />
      </>
    ),
    send: (
      <>
        <path d="m22 2-7 20-4-9-9-4 20-7ZM11 13 22 2" />
      </>
    ),
    check: (
      <>
        <circle cx="12" cy="12" r="9" />
        <path d="m8 12 3 3 5-6" />
      </>
    ),
    clock: (
      <>
        <circle cx="12" cy="12" r="9" />
        <path d="M12 7v5l3 2" />
      </>
    ),
    message: (
      <>
        <path d="M21 11a8 8 0 0 1-8 8H6l-4 3V11a9 9 0 0 1 19 0Z" />
        <path d="M7 10h10M7 14h6" />
      </>
    ),
    refresh: (
      <>
        <path d="M20 7a9 9 0 1 0 1 8M20 2v5h-5" />
      </>
    ),
    arrow: (
      <>
        <path d="M5 12h14m-5-5 5 5-5 5" />
      </>
    ),
  };
  return (
    <svg
      width="22"
      height="22"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.6"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      {paths[name] || paths.jobs}
    </svg>
  );
}

export default function AdminDashboard() {
  const resource = useResource('/admin/dashboard');
  const user = useAuth((state) => state.user);
  return (
    <div className="admin-overview">
      <div className="admin-page-heading">
        <div>
          <p className="eyebrow">JOBBRIDGE ADMIN CENTER</p>
          <h1>관리자 대시보드</h1>
          <p>안녕하세요, {user.name}님. 오늘의 운영 현황을 확인하세요.</p>
        </div>
        <button
          className="secondary admin-refresh"
          disabled={resource.loading}
          onClick={resource.reload}
        >
          <Icon name="refresh" />
          새로고침
        </button>
      </div>
      <nav className="admin-quick-access" aria-label="관리 기능 바로가기">
        {[
          ['/admin/members', '회원 관리'], ['/admin/companies', '기업 심사'],
          ['/admin/jobs', '공고 심사'], ['/admin/employment', '취업연계 관리'],
          ['/admin/notices', '공지 관리'], ['/admin/inquiries', '문의 관리'],
        ].map(([to, title]) => <Link key={to} to={to}>{title}<span aria-hidden="true">↗</span></Link>)}
      </nav>
      <Resource resource={resource}>{(data) => <DashboardContent data={data} />}</Resource>
    </div>
  );
}

function DashboardContent({ data }) {
  const metrics = [
    {
      label: '전체 회원',
      value: data.totalMembers,
      icon: 'people',
      tone: 'blue',
      path: '/admin/members',
      detail: `개인 ${number(data.jobSeekers)} · 기업 ${number(data.companyMembers)}`,
      chip: `오늘 +${number(data.todayMembers)}`,
    },
    {
      label: '게시 중인 공고',
      value: data.publishedJobs,
      icon: 'jobs',
      tone: 'violet',
      path: '/admin/jobs',
      detail: '승인된 활성 기업의 접수 중·예정 공고',
      chip: `심사 대기 ${number(data.jobs)}`,
    },
    {
      label: '누적 입사 지원',
      value: data.totalApplications,
      icon: 'send',
      tone: 'cyan',
      path: '/admin/jobs',
      detail: '전체 공고의 누적 지원 건수',
      chip: `오늘 +${number(data.todayApplications)}`,
    },
    {
      label: '취업 완료 연계',
      value: data.employedLinks,
      icon: 'check',
      tone: 'green',
      path: '/admin/employment',
      detail: `진행 중인 취업연계 ${number(data.employmentLinks)}건`,
      chip: '관리자 확인 기준',
    },
  ];
  const pending = [
    ['companies', '기업 승인', '/admin/companies', 'jobs'],
    ['jobs', '공고 심사', '/admin/jobs', 'clock'],
    ['inquiries', '문의 답변', '/admin/inquiries', 'message'],
    ['newEmploymentLinks', '신규 연계', '/admin/employment', 'people'],
  ];
  return (
    <>
      <section className="admin-summary-banner">
        <div>
          <span className="admin-live-dot" />
          <strong>
            {data.pendingTotal > 0
              ? `확인이 필요한 업무가 ${number(data.pendingTotal)}건 있습니다.`
              : '새로 접수된 처리 대기 업무가 없습니다.'}
          </strong>
          <p>기업 승인, 공고 심사, 문의와 신규 연계 신청을 확인해 주세요.</p>
        </div>
        <div className="admin-date">
          <strong>
            {new Date(`${data.date}T12:00:00+09:00`).toLocaleDateString('ko-KR', {
              year: 'numeric',
              month: 'long',
              day: 'numeric',
              weekday: 'short',
              timeZone: 'Asia/Seoul',
            })}
          </strong>
          <small>
            {new Date(data.asOf).toLocaleTimeString('ko-KR', {
              hour: '2-digit',
              minute: '2-digit',
              timeZone: 'Asia/Seoul',
            })}{' '}
            조회 · 한국 시간
          </small>
        </div>
      </section>
      <section className="admin-metrics" aria-label="주요 운영 지표">
        {metrics.map((m) => (
          <Link key={m.label} to={m.path} className={`admin-metric tone-${m.tone}`}>
            <div className="admin-metric-top">
              <span>{m.label}</span>
              <span className="admin-icon">
                <Icon name={m.icon} />
              </span>
            </div>
            <strong className="admin-metric-value">
              {number(m.value)}
              <small>{m.label === '전체 회원' ? '명' : '건'}</small>
            </strong>
            <p>{m.detail}</p>
            <span className="admin-metric-chip">{m.chip}</span>
          </Link>
        ))}
      </section>
      <section className="admin-pending-strip" aria-label="처리 대기 업무">
        {pending.map(([key, label, to, icon]) => (
          <Link key={key} to={to}>
            <span className="admin-mini-icon">
              <Icon name={icon} />
            </span>
            <div>
              <span>{label}</span>
              <strong>
                {number(data[key])}
                <small>건 대기</small>
              </strong>
            </div>
            <Icon name="arrow" />
          </Link>
        ))}
      </section>
      <div className="admin-chart-grid">
        <ActivityChart rows={data.trend} />
        <ApplicationPipeline rows={data.applicationStatuses} total={data.totalApplications} />
      </div>
      <div className="admin-main-grid">
        <ReviewQueue data={data} />
        <section className="admin-box">
          <BoxHeading title="답변을 기다리는 문의" to="/admin/inquiries" />
          <div className="admin-list">
            {data.openInquiries.length ? (
              data.openInquiries.map((r) => (
                <Link
                  key={r.inquiryId}
                  to={focusLink('/admin/inquiries', r.inquiryId)}
                  className="admin-inquiry"
                >
                  <span className="admin-inquiry-icon">
                    <Icon name="message" />
                  </span>
                  <div>
                    <strong>{r.title}</strong>
                    <small>{dateLabel(r.createdAt)} 접수 · 답변 대기</small>
                  </div>
                  <span>↗</span>
                </Link>
              ))
            ) : (
              <DashboardEmpty>답변을 기다리는 문의가 없습니다.</DashboardEmpty>
            )}
          </div>
          <div className="admin-box-bottom">
            <Link to="/admin/inquiries">
              고객 문의 관리하기 <Icon name="arrow" />
            </Link>
          </div>
        </section>
      </div>
      <div className="admin-main-grid">
        <RecentMembers rows={data.recentMembers} />
        <section className="admin-box">
          <BoxHeading title="진행 중인 취업연계" to="/admin/employment" />
          <div className="admin-list">
            {data.recentLinks.length ? (
              data.recentLinks.map((r) => (
                <Link
                  key={r.employmentLinkId}
                  className="admin-link-row"
                  to={focusLink('/admin/employment', r.employmentLinkId)}
                >
                  <div>
                    <strong>취업연계 #{r.employmentLinkId}</strong>
                    <small>{dateLabel(r.requestedAt)} 신청</small>
                  </div>
                  <Badge value={r.status} />
                </Link>
              ))
            ) : (
              <DashboardEmpty>진행 중인 취업연계가 없습니다.</DashboardEmpty>
            )}
          </div>
          <div className="admin-box-bottom">
            <Link to="/admin/employment">
              상담·매칭 현황 확인하기 <Icon name="arrow" />
            </Link>
          </div>
        </section>
      </div>
      <p className="admin-data-note">
        지표는 조회 시점의 실제 데이터입니다. 오늘·최근 7일은 한국 시간을 기준으로 집계합니다.
      </p>
    </>
  );
}

function BoxHeading({ title, to, children }) {
  return (
    <div className="admin-box-heading">
      <h2>{title}</h2>
      {children || (to && <Link to={to}>전체 보기 ↗</Link>)}
    </div>
  );
}
function DashboardEmpty({ children }) {
  return (
    <div className="admin-empty">
      <Icon name="check" />
      <p>{children}</p>
    </div>
  );
}

function ActivityChart({ rows }) {
  const [series, setSeries] = useState('members');
  const total = rows.reduce((sum, r) => sum + Number(r[series]), 0);
  const max = Math.max(1, ...rows.map((r) => Number(r[series])));
  return (
    <section className="admin-box admin-activity">
      <BoxHeading title="최근 7일 서비스 활동">
        <div className="admin-chart-tabs" role="group" aria-label="활동 지표 선택">
          <button
            aria-pressed={series === 'members'}
            className={series === 'members' ? 'selected' : ''}
            onClick={() => setSeries('members')}
          >
            신규 회원
          </button>
          <button
            aria-pressed={series === 'applications'}
            className={series === 'applications' ? 'selected' : ''}
            onClick={() => setSeries('applications')}
          >
            입사 지원
          </button>
        </div>
      </BoxHeading>
      <div className="admin-chart-summary">
        <strong>
          {number(total)}
          <small>{series === 'members' ? '명' : '건'}</small>
        </strong>
        <span>최근 7일 {series === 'members' ? '신규 가입' : '지원 접수'} 합계</span>
      </div>
      <div
        className={`admin-bar-chart chart-${series}`}
        role="img"
        aria-label={rows
          .map(
            (r) => `${dateLabel(r.date)} ${number(r[series])}${series === 'members' ? '명' : '건'}`,
          )
          .join(', ')}
      >
        <div className="admin-chart-gridlines" aria-hidden="true">
          <span />
          <span />
          <span />
        </div>
        {rows.map((r, i) => (
          <div className="admin-chart-column" key={r.date}>
            <span className="admin-chart-number">{number(r[series])}</span>
            <div className="admin-bar-track">
              <div
                className={`admin-bar ${i === rows.length - 1 ? 'today' : ''}`}
                style={{ height: `${(Number(r[series]) / max) * 100}%` }}
              />
            </div>
            <span className="admin-chart-date">
              {r.date.slice(5).replace('-', '.')}
              {i === rows.length - 1 && <small>오늘</small>}
            </span>
          </div>
        ))}
      </div>
      <p className="admin-chart-caption">
        {rows[0].date} — {rows[rows.length - 1].date}
        {total === 0 ? ' · 해당 기간에 등록된 활동이 없습니다.' : ''}
      </p>
    </section>
  );
}

function ApplicationPipeline({ rows, total }) {
  const states = [
    'APPLIED',
    'UNDER_REVIEW',
    'DOCUMENT_PASSED',
    'INTERVIEW',
    'FINAL_PASSED',
    'REJECTED',
    'CANCELED',
  ];
  return (
    <section className="admin-box">
      <BoxHeading title="지원 진행 현황" />
      <p className="admin-box-description">전체 지원 {number(total)}건의 현재 상태</p>
      <div className="admin-pipeline">
        {states.map((status) => {
          const count = Number(rows.find((r) => r.status === status)?.total || 0);
          return (
            <div key={status}>
              <div>
                <span>
                  <i className={`pipeline-dot dot-${status}`} />
                  {labels[status]}
                </span>
                <strong>
                  {number(count)}
                  <small>건</small>
                </strong>
              </div>
              <div className="pipeline-track">
                <span
                  className={`pipeline-fill fill-${status}`}
                  style={{ width: `${total ? (count / total) * 100 : 0}%` }}
                />
              </div>
            </div>
          );
        })}
      </div>
    </section>
  );
}

function ReviewQueue({ data }) {
  const [tab, setTab] = useState('companies');
  const companies = tab === 'companies';
  const rows = companies ? data.pendingCompanies : data.pendingJobs;
  return (
    <section className="admin-box">
      <BoxHeading title="승인·심사 대기" to={companies ? '/admin/companies' : '/admin/jobs'} />
      <div className="admin-queue-tabs" role="group" aria-label="심사 목록 선택">
        <button
          aria-pressed={companies}
          className={companies ? 'selected' : ''}
          onClick={() => setTab('companies')}
        >
          기업 승인 <span>{number(data.companies)}</span>
        </button>
        <button
          aria-pressed={!companies}
          className={!companies ? 'selected' : ''}
          onClick={() => setTab('jobs')}
        >
          공고 심사 <span>{number(data.jobs)}</span>
        </button>
      </div>
      {rows.length ? (
        <div className="admin-table-wrap">
          <table className="admin-table">
            <thead>
              <tr>
                <th>{companies ? '기업명' : '공고명'}</th>
                <th>{companies ? '업종' : '기업명'}</th>
                <th>상태</th>
                <th>
                  <span className="sr-only">작업</span>
                </th>
              </tr>
            </thead>
            <tbody>
              {rows.map((r) => (
                <tr key={companies ? r.companyId : r.jobId}>
                  <td>
                    <strong>{companies ? r.companyName : r.title}</strong>
                  </td>
                  <td>{companies ? r.industry || '미등록' : r.companyName}</td>
                  <td>
                    <Badge value="PENDING" />
                  </td>
                  <td>
                    <Link
                      className="admin-table-action"
                      to={focusLink(
                        companies ? '/admin/companies' : '/admin/jobs',
                        companies ? r.companyId : r.jobId,
                      )}
                    >
                      검토 →
                    </Link>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        <DashboardEmpty>
          {companies ? '승인을 기다리는 기업이 없습니다.' : '심사를 기다리는 공고가 없습니다.'}
        </DashboardEmpty>
      )}
      <p className="admin-table-caption">최근 접수 순으로 최대 5건을 표시합니다.</p>
    </section>
  );
}

function RecentMembers({ rows }) {
  return (
    <section className="admin-box">
      <BoxHeading title="최근 가입한 회원" to="/admin/members" />
      {rows.length ? (
        <div className="admin-table-wrap">
          <table className="admin-table">
            <thead>
              <tr>
                <th>회원</th>
                <th>유형</th>
                <th>가입일</th>
                <th>상태</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((r) => (
                <tr key={r.userId}>
                  <td>
                    <Link className="admin-member-name" to={focusLink('/admin/members', r.userId)}>
                      <span className="admin-member-avatar">{r.name.slice(0, 1)}</span>
                      <strong>{r.name}</strong>
                    </Link>
                  </td>
                  <td>{r.role === 'COMPANY' ? '기업' : '개인'}</td>
                  <td>{dateLabel(r.createdAt)}</td>
                  <td>
                    <Badge value={r.status} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        <DashboardEmpty>아직 가입한 회원이 없습니다.</DashboardEmpty>
      )}
      <p className="admin-table-caption">최근 가입 순으로 최대 5명을 표시합니다.</p>
    </section>
  );
}
