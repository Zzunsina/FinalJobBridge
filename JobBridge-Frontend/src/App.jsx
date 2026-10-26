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
            {[
              ['/jobs', '채용정보'],
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
