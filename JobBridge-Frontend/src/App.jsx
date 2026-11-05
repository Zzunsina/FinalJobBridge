import { MyInterviewReviews, CompanyReviewCenter, AdminReviewReports } from './pages/ReviewFeatures';

import CompanyAdvertising, { AdminAdvertising } from './pages/Advertising';


import CareerExploration from './pages/CareerExploration';
import CareerAssessment from './pages/CareerAssessment';
import SiteFooter from './components/common/SiteFooter';
import ServiceInfo from './pages/ServiceInfo';
import { jobCategories } from './data/jobCategories';
import { CompanyApplicants, CompanyInterviewSchedule } from './pages/CompanyOverview';
import HeaderSearch from './components/common/HeaderSearch';
import SidePanels from './components/common/SidePanels';
import { useEffect, useState } from 'react';
import { Link, NavLink, Navigate, Route, Routes, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from './stores/auth';
import { Empty, Guard } from './components/common/UI';
import { Home, Jobs, JobDetail, Companies, CompanyDetail, Notices } from './pages/Public';
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
import AdminHomePromotions from './pages/AdminHomePromotions';
import NotificationLink from './components/common/NotificationLink';
import CustomerCenter from './pages/CustomerCenter';
import EmploymentDetail from './pages/EmploymentDetail';
import CompanyEmployment from './pages/CompanyEmployment';
import CompanyEmploymentDetail from './pages/CompanyEmploymentDetail';
import { BookmarkNotice } from './components/common/BookmarkButton';


const companyLinks = [
  ['/company', '대시보드'],
  ['/company/jobs', '채용공고 관리'],
  ['/company/applicants', '지원자 관리'],
  ['/company/employment', '취업연계'],
  ['/company/interviews', '면접 일정'],
  ['/company/reviews', '면접 후기·신고'],
  ['/company/profile', '기업·계정 정보'],

  ['/company/advertising', '광고·결제'],


  



];
const adminLinks = [
  ['/admin', '대시보드'],
  ['/admin/members', '회원 관리'],
  ['/admin/companies', '기업 심사'],
  ['/admin/jobs', '공고 심사'],
  ['/admin/employment', '취업연계 관리'],
  ['/admin/notices', '공지 관리'],
  ['/admin/home-promotions', '홈 홍보 관리'],
  ['/admin/advertising', '광고·결제 관리'],
  ['/admin/inquiries', '문의 관리'],
  ['/admin/reports', '후기·신고 관리'],
];
const seekerLinks = [
  ['/resumes', '이력서 관리'],
  ['/career-test', '인적성검사'],
  ['/career-exploration', '진로 탐색'],
  ['/applications', '지원 내역'],
  ['/interview-reviews', '면접 후기'],
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

  // 카카오 로그인 성공 시 URL 파라미터(?login=kakao) 감지 로직
  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    if (params.get('login') === 'kakao') {
      localStorage.setItem('loginProvider', 'kakao');
      window.history.replaceState({}, document.title, window.location.pathname);
    }
  }, []);

  const workspace =
    location.pathname !== '/admin/login' &&

    (location.pathname.startsWith('/admin') ||
      location.pathname.startsWith('/company') ||
      (user?.role === 'COMPANY' && location.pathname === '/mypage'));

    (location.pathname.startsWith('/admin') || location.pathname.startsWith('/company') || location.pathname.startsWith('/company/payment'));

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
      <header id="header" className={user?.role === 'ADMIN' ? 'admin-header' : undefined}>
        <div className="header-inner">
          <Link className="logo" to="/">
            <span className="brand-image">
              <img src="/api/site-images/images/jobbridge-logo.jpg" alt="" />
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
                    {jobCategories.map((category) => (
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

          <HeaderSearch key={location.key} onSubmitted={() => setMenu(false)} />
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
                      localStorage.removeItem('loginProvider');
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
        <div className={user.role === 'ADMIN' ? 'member-nav admin-member-nav' : 'member-nav'}>
          <nav aria-label="회원 메뉴">
            {links.map(([to, label]) => (
              <NavLink key={to} to={to} end={to === '/company' || to === '/admin'}>
                {label}
              </NavLink>
            ))}
          </nav>
        </div>
      )}
      <div className={workspace && user ? `workspace container ${user.role === 'ADMIN' ? 'admin-workspace' : ''} ${user.role === 'COMPANY' && location.pathname === '/company' ? 'company-overview-workspace' : ''}` : location.pathname === '/' ? 'container home-content-container' : 'container'}>
        {workspace && user && !(user.role === 'COMPANY' && location.pathname === '/company') && (
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
            <Route path="/career-exploration" element={<CareerExploration />} />
            <Route path="/career-exploration/:id" element={<CareerExploration />} />
            <Route path="/career-test" element={<CareerAssessment />} />
            <Route path="/career-test/:id" element={<CareerAssessment />} />
            <Route path="/about" element={<ServiceInfo page="about" />} />
            <Route path="/guide/:topic" element={<ServiceInfo />} />
            <Route path="/policies/:topic" element={<ServiceInfo />} />
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
            <Route path="/employment/:id" element={protectedPage('JOB_SEEKER', <EmploymentDetail />)} />
            <Route path="/mypage" element={protectedPage(null, user?.role === 'COMPANY' ? <Navigate to="/company/profile" replace /> : <MyPage />)} />
            <Route path="/interview-reviews" element={protectedPage('JOB_SEEKER', <MyInterviewReviews />)} />
            <Route path="/company/reviews" element={protectedPage('COMPANY', <CompanyReviewCenter />)} />
            <Route path="/admin/reports" element={protectedPage('ADMIN', <AdminReviewReports />)} />
            <Route path="/notifications" element={protectedPage(null, <Notifications />)} />
            <Route path="/inquiries" element={<CustomerCenter />} />

            {/* 기업회원 전용 라우트 */}
            <Route path="/company" element={protectedPage('COMPANY', <CompanyDashboard />)} />
            <Route path="/company/applicants" element={protectedPage('COMPANY', <CompanyApplicants />)} />
            <Route path="/company/employment" element={protectedPage('COMPANY', <CompanyEmployment />)} />
            <Route path="/company/employment/referrals/:id" element={protectedPage('COMPANY', <CompanyEmploymentDetail />)} />
            <Route path="/company/interviews" element={protectedPage('COMPANY', <CompanyInterviewSchedule />)} />
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
            <Route path="/company/advertising" element={protectedPage('COMPANY', <CompanyAdvertising />)} />
            <Route path="/admin/advertising" element={protectedPage('ADMIN', <AdminAdvertising />)} />
            <Route path="/company/profile" element={protectedPage('COMPANY', <CompanyProfile />)} />


  


            <Route path="/admin" element={protectedPage('ADMIN', <AdminDashboard />)} />
            <Route path="/admin/home-promotions" element={protectedPage('ADMIN', <AdminHomePromotions />)} />
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
      <SiteFooter />
    </>
  );
}