import HeaderSearch from './components/common/HeaderSearch';
import SidePanels from './components/common/SidePanels';
import { useEffect, useState } from 'react';
import { Link, NavLink, Navigate, Route, Routes, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from './stores/auth';
import { Empty, Guard } from './components/common/UI';
import { Home, Jobs, JobDetail, Companies, CompanyDetail, Notices } from './pages/Public';
// 💡 FindId와 ResetPassword를 추가로 임포트합니다.
import { Login, Signup, FindId, FindPassword, ResetPassword } from './pages/Auth';
import {
  Resumes,
  ResumeForm,
  Applications,
  ApplicationDetail,
  Bookmarks,
  Employment,
  MyPage,
  Notifications,
} from './pages/Seeker';
import {
  CompanyDashboard,
  JobManage,
  JobForm,
  Applicants,
  ApplicantDetail,
  CompanyProfile,
} from './pages/Company';
import { AdminDashboard, AdminManage } from './pages/Admin';
import NotificationLink from './components/common/NotificationLink';
import CustomerCenter from './pages/CustomerCenter';
import { BookmarkNotice } from './components/common/BookmarkButton';

const companyLinks = [
  ['/company', '대시보드'],
  ['/company/jobs', '채용공고 관리'],
  ['/company/profile', '기업 정보'],
  ['/mypage', '내 정보'],
];
const adminLinks = [
  ['/admin', '대시보드'],
  ['/admin/members', '회원 관리'],
  ['/admin/companies', '기업 심사'],
  ['/admin/jobs', '공고 심사'],
  ['/admin/employment', '취업연계 관리'],
  ['/admin/notices', '공지 관리'],
  ['/admin/inquiries', '문의 관리'],
];
const seekerLinks = [
  ['/resumes', '이력서 관리'],
  ['/applications', '지원 내역'],
  ['/bookmarks', '관심 공고'],
  ['/employment', '취업연계'],
  ['/mypage', '내 정보'],
];
const protectedPage = (role, element) => <Guard role={role}>{element}</Guard>;

export default function App() {
  const { user, initialized, error, initialize, logout } = useAuth();
  const [menu, setMenu] = useState(false);
  const [megaDismissed, setMegaDismissed] = useState(false);
  const location = useLocation();
  const navigate = useNavigate();
  useEffect(() => {
    initialize();
  }, [initialize]);
  useEffect(() => {
    window.scrollTo(0, 0);
  }, [location.pathname]);
  const workspace =
    location.pathname !== '/admin/login' &&
    (location.pathname.startsWith('/admin') || location.pathname.startsWith('/company'));
  const links =
    user?.role === 'ADMIN' ? adminLinks : user?.role === 'COMPANY' ? companyLinks : seekerLinks;
  if (!initialized) return <div className="empty">서비스를 준비하는 중입니다…</div>;
  return (
    <>
      <a className="skip-link" href="#main">
        본문으로 바로가기
      </a>
      <BookmarkNotice />
      <div className="utility">
        <div>
          가능성과 기회를 잇는 취업 파트너
          <span>JOBBRIDGE · YOUR NEXT CHAPTER</span>
        </div>
      </div>
      <header id="header">
        <div className="header-inner">
          <Link className="logo" to="/">
            <span className="brand-image">
              <img src="/images/jobbridge-logo.jpg" alt="" />
            </span>
            잡브릿지<span className="logo-en">JOBBRIDGE</span>
          </Link>
          

          
<nav className={menu ? 'main-nav open' : 'main-nav'} aria-label="주 메뉴">
  <div
    className={megaDismissed ? 'nav-mega is-dismissed' : 'nav-mega'}
    onMouseEnter={() => setMegaDismissed(false)}
    onMouseLeave={() => setMegaDismissed(false)}
    onClickCapture={(event) => { if (event.target.closest('a')) setMegaDismissed(true); }}
    onKeyDown={(event) => {
      if (event.key === 'Escape') {
        setMegaDismissed(true);
        event.currentTarget.querySelector('.nav-mega-trigger')?.focus();
      }
    }}
  >
    <NavLink
      className="nav-mega-trigger"
      to="/jobs"
      onClick={() => setMenu(false)}
      aria-haspopup="true"
    >
      채용정보
    </NavLink>

    <div className="mega-panel">
      <div className="mega-heading">
        <div>
          <span className="mega-eyebrow">FIND YOUR NEXT CHAPTER</span>
          <h2>어떤 일로 성장하고 싶으세요?</h2>
          <p>관심 있는 직무를 선택하고 새로운 기회를 만나보세요.</p>
        </div>
        <Link
          className="mega-all"
          to="/jobs"
          onClick={() => setMenu(false)}
        >
          전체 채용공고 보기 <span>↗</span>
        </Link>
      </div>

      <div className="mega-content">
        <div className="mega-categories">
          {[
            {
              icon: '01',
              title: '개발 · 데이터',
              jobs: ['백엔드 개발자', '프론트엔드 개발자', 'AI · 머신러닝', '데이터 분석', '정보보안'],
            },
            {
              icon: '02',
              title: '디자인 · 기획',
              jobs: ['UI · UX 디자이너', '프로덕트 디자이너', '서비스 기획', 'PM · PO', '콘텐츠 기획'],
            },
            {
              icon: '03',
              title: '마케팅 · 광고',
              jobs: ['콘텐츠 마케팅', '퍼포먼스 마케팅', '브랜드 마케팅', '광고 · 홍보', 'SNS 운영'],
            },
            {
              icon: '04',
              title: '경영 · 전문직',
              jobs: ['인사 · HR', '회계 · 재무', '영업 · 영업관리', '법무 · 노무', '고객상담 · CS'],
            },
            {
              icon: '05',
              title: '생산 · 물류',
              jobs: ['생산관리', '품질관리', '물류 · 유통', '구매 · 자재', '기술 · 설비'],
            },
            {
              icon: '06',
              title: '교육 · 서비스',
              jobs: ['교육 · 강사', '의료 · 보건', '서비스 · 판매', '공공 · 복지', '고객지원'],
            },
          ].map((category) => (
            <section className="mega-category" key={category.icon}>
              <div className="mega-category-title">
                <span>{category.icon}</span>
                <h3>{category.title}</h3>
              </div>

              <div className="mega-job-list">
                {category.jobs.map((job) => (
                  <Link
                    key={job}
                    to={`/jobs?q=${encodeURIComponent(job)}`}
                    onClick={() => setMenu(false)}
                  >
                    {job}
                    <span className="mega-job-arrow">↗</span>
                  </Link>
                ))}
              </div>
            </section>
          ))}
        </div>

        <aside className="mega-feature">
          <span className="mega-feature-label">JOBBRIDGE GUIDE</span>
          <div className="mega-feature-icon">✳</div>
          <h3>나에게 맞는<br />커리어를 찾아보세요.</h3>
          <p>
            첫 취업부터 새로운 도전까지,
            나의 가능성을 넓혀줄 채용 기회를 탐색하세요.
          </p>
          <Link to="/jobs" onClick={() => setMenu(false)}>
            채용공고 탐색하기 <span>→</span>
          </Link>
        </aside>
      </div>
    </div>
  </div>

  {[
    ['/companies', '기업정보'],
    ['/employment', '취업연계'],
    ['/notices', '공지사항'],
    ['/inquiries', '고객센터'],
  ].map(([to, label]) => (
    <NavLink key={to} to={to} onClick={() => setMenu(false)}>
      {label}
    </NavLink>
  ))}
</nav>
    

          <HeaderSearch onSubmitted={() => setMenu(false)} />
          <div className="account">
            {user ? (
              <>
                <Link to={links[0][0]}>{user.name}님</Link>
                <NotificationLink key={user.userId} />
                <button
                  className="plain"
                  onClick={async () => {
                    try {
                      await logout();
                      navigate('/');
                    } catch {
                      window.alert('로그아웃을 완료하지 못했습니다. 다시 시도해 주세요.');
                    }
                  }}
                >
                  로그아웃
                </button>
              </>
            ) : (
              <>
                <Link to="/login">로그인</Link>
                <Link to="/signup">회원가입</Link>
              </>
            )}
          </div>
          <button
            className="mobile-menu secondary"
            aria-expanded={menu}
            aria-label="메뉴 열기"
            onClick={() => setMenu(!menu)}
          >
            ☰
          </button>
        </div>
      </header>
      {user && (
        <div className="member-nav">
          <nav aria-label="회원 메뉴">
            {links.map(([to, label]) => (
              <NavLink key={to} to={to} end={to === '/company' || to === '/admin'}>
                {label}
              </NavLink>
            ))}
            {user.role !== 'ADMIN' && <NavLink to="/inquiries">고객센터</NavLink>}
          </nav>
        </div>
      )}
      <div className={workspace && user ? 'workspace container' : 'container'}>
        {workspace && user && (
          <aside className="sidebar">
            <p className="eyebrow">{user.role === 'ADMIN' ? 'ADMIN CENTER' : 'COMPANY CENTER'}</p>
            {links.map(([to, label]) => (
              <NavLink key={to} to={to} end={to === '/company' || to === '/admin'}>
                {label}
              </NavLink>
            ))}
          </aside>
        )}
        <main id="main">
          {error && (
            <div className="connection-alert" role="alert">
              {error}
              <button className="secondary" onClick={initialize}>
                다시 연결
              </button>
            </div>
          )}
          <Routes>
            <Route path="/" element={<Home />} />
            <Route path="/jobs" element={<Jobs />} />
            <Route path="/jobs/:id" element={<JobDetail />} />
            <Route path="/companies" element={<Companies />} />
            <Route path="/companies/:id" element={<CompanyDetail />} />
            <Route path="/notices" element={<Notices />} />
            <Route path="/notices/:id" element={<Notices />} />
            <Route path="/login" element={<Login />} />
            <Route path="/admin/login" element={<Login key="admin-login" adminOnly />} />
            <Route path="/signup" element={<Signup />} />
            
            {/* 💡 아이디 찾기, 비밀번호 찾기 및 재설정 라우트 추가 */}
            <Route path="/find-id" element={<FindId />} />
            <Route path="/find-password" element={<FindPassword />} />
            <Route path="/reset-password" element={<ResetPassword />} />

            <Route path="/resumes" element={protectedPage('JOB_SEEKER', <Resumes />)} />
            <Route path="/resumes/new" element={protectedPage('JOB_SEEKER', <ResumeForm />)} />
            <Route path="/resumes/:id/edit" element={protectedPage('JOB_SEEKER', <ResumeForm />)} />
            <Route path="/applications" element={protectedPage('JOB_SEEKER', <Applications />)} />
            <Route
              path="/applications/:id"
              element={protectedPage('JOB_SEEKER', <ApplicationDetail />)}
            />
            <Route path="/bookmarks" element={protectedPage('JOB_SEEKER', <Bookmarks />)} />
            <Route path="/employment" element={user?.role === 'ADMIN' ? protectedPage('ADMIN', <Navigate to="/admin/employment" replace />) : protectedPage('JOB_SEEKER', <Employment />)} />
            <Route path="/mypage" element={protectedPage(null, <MyPage />)} />
            <Route path="/notifications" element={protectedPage(null, <Notifications />)} />
            <Route path="/inquiries" element={<CustomerCenter />} />
            <Route path="/company" element={protectedPage('COMPANY', <CompanyDashboard />)} />
            <Route path="/company/jobs" element={protectedPage('COMPANY', <JobManage />)} />
            <Route path="/company/jobs/new" element={protectedPage('COMPANY', <JobForm />)} />
            <Route path="/company/jobs/:id/edit" element={protectedPage('COMPANY', <JobForm />)} />
            <Route
              path="/company/jobs/:id/applicants"
              element={protectedPage('COMPANY', <Applicants />)}
            />
            <Route
              path="/company/applications/:id"
              element={protectedPage('COMPANY', <ApplicantDetail />)}
            />
            <Route path="/company/profile" element={protectedPage('COMPANY', <CompanyProfile />)} />
            <Route path="/admin" element={protectedPage('ADMIN', <AdminDashboard />)} />
            {[
              ['members', 'users'],
              ['companies', 'companies'],
              ['jobs', 'jobs'],
              ['employment', 'employment-links'],
              ['notices', 'notices'],
              ['inquiries', 'inquiries'],
            ].map(([path, resource]) => (
              <Route
                key={path}
                path={`/admin/${path}`}
                element={protectedPage(
                  'ADMIN',
                  <AdminManage key={resource} resourceName={resource} />,
                )}
              />
            ))}
            <Route
              path="*"
              element={
                <Empty to="/" action="홈으로 이동">
                  페이지를 찾을 수 없습니다.
                </Empty>
              }
            />
          </Routes>
        </main>
      </div>
      <SidePanels />
      <footer>
        <div className="container footer-inner">
          <div>
            <Link className="footer-logo" to="/">
              <span className="brand-image">
                <img src="/images/jobbridge-logo.jpg" alt="" />
              </span>{' '}
              잡브릿지
            </Link>
            <p>
              가능성과 기회를 잇다.
              <br />
              당신의 다음 시작을 함께합니다.
            </p>
          </div>
          <div>
            <Link to="/notices">공지사항</Link>
            {user?.role !== 'ADMIN' && <Link to="/inquiries">고객센터</Link>}
            <Link to="/admin/login">관리자 로그인</Link>
            <span>학습·시연용 취업연계 플랫폼</span>
          </div>
          <p className="copyright">© 2026 JOBBRIDGE. All rights reserved.</p>
        </div>
      </footer>
    </>
  );
}
