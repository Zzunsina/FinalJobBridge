import { Link } from 'react-router-dom';
import './SiteFooter.css';
export default function SiteFooter() {
  return <footer className="jb-site-footer">
    <div className="jb-footer-guides"><nav className="container" aria-label="회원 이용안내">
      <Link to="/guide/personal"><span className="jb-guide-icon" aria-hidden="true">♙</span><span><strong>개인회원 이용안내</strong><small>이력서부터 입사지원까지</small></span></Link>
      <Link to="/guide/company"><span className="jb-guide-icon" aria-hidden="true">▦</span><span><strong>기업회원 이용안내</strong><small>공고 등록부터 지원자 관리까지</small></span></Link>
      <Link to="/inquiries?view=inquiry&kind=report"><span className="jb-guide-icon" aria-hidden="true">⚑</span><span><strong>채용공고 신고</strong><small>안전한 채용 문화를 함께 만들어요</small></span></Link>
    </nav></div>
    <div className="container">
      <div className="jb-footer-body"><section className="jb-footer-brand" aria-label="잡브릿지 운영 정보">
        <Link to="/" className="jb-footer-logo"><img src="/images/jobbridge-logo.jpg" alt="" />잡브릿지</Link>
        <p className="jb-footer-slogan">가능성과 기회를 잇습니다.</p>
        <p>잡브릿지 프로젝트팀 · 취업연계 플랫폼</p>
        <p>문의는 고객센터의 1:1 온라인 문의를 이용해 주세요.</p>
        <p className="jb-footer-demo">학습·시연용 서비스 / 운영 정보는 가상입니다.</p>
      </section>
      <nav aria-label="서비스 안내"><h2>서비스 안내</h2><Link to="/about">서비스 소개</Link><Link to="/inquiries?view=inquiry&kind=partnership">제휴·광고 문의</Link><Link to="/notices">공지사항</Link></nav>
      <nav aria-label="고객지원"><h2>고객지원</h2><Link to="/inquiries">자주 묻는 질문</Link><Link to="/inquiries?view=inquiry">1:1 문의하기</Link><Link to="/inquiries?view=history">내 문의 내역</Link></nav></div>
      <div className="jb-footer-bottom"><nav aria-label="서비스 정책"><Link to="/policies/terms">이용약관</Link><Link className="jb-privacy-link" to="/policies/privacy">개인정보처리방침</Link><Link to="/policies/email">이메일 무단수집 거부</Link><Link to="/admin/login">관리자 로그인</Link></nav></div>
      <p className="jb-footer-copyright">© 2026 JOBBRIDGE. All rights reserved.</p>
    </div>
  </footer>;
}
