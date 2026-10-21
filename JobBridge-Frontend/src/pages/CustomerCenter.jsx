import { useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useAuth } from '../stores/auth';
import { useResource } from '../hooks/useResource';
import { Empty, PagedRows, Resource, Title } from '../components/common/UI';
import { Inquiries, InquiryWrite } from './Seeker';

const categories = { ALL: '전체', ACCOUNT: '회원가입·정보', RESUME: '이력서 관리', APPLICATION: '입사지원', JOBS: '채용정보', OTHER: '기타' };
export default function CustomerCenter() {
  const user = useAuth((s) => s.user);
  const [params, setParams] = useSearchParams();
  const view = ['inquiry', 'history'].includes(params.get('view')) ? params.get('view') : 'faq';
  return <div className="customer-center">
    <Title description="자주 묻는 질문에서 도움을 찾고, 궁금한 점은 1:1 문의로 남겨 주세요.">고객센터</Title>
    <div className="customer-layout">
      <aside className="customer-sidebar"><h2>고객센터</h2><nav aria-label="고객센터 메뉴">
        <button type="button" className={view === 'faq' ? 'active' : ''} aria-current={view === 'faq' ? 'page' : undefined} onClick={() => setParams({})}>자주 묻는 질문</button>
        <button type="button" className={view === 'inquiry' ? 'active' : ''} aria-current={view === 'inquiry' ? 'page' : undefined} onClick={() => setParams({ view: 'inquiry' })}>1:1 문의하기</button>
        <button type="button" className={view === 'history' ? 'active' : ''} aria-current={view === 'history' ? 'page' : undefined} onClick={() => setParams({ view: 'history' })}>나의 문의</button>
        <Link to="/notices">공지사항</Link>
      </nav><p>개인정보가 포함된 문의는 1:1 문의를 이용해 주세요.</p></aside>
      <section className="customer-content">{view === 'faq' ? <Faqs /> : user ? (view === 'inquiry' ? <InquiryWrite onSubmitted={() => setParams({ view: 'history' })} /> : <Inquiries />) : <div className="customer-login"><h2>{view === 'history' ? '나의 문의' : '1:1 문의하기'}</h2><p>문의 작성과 답변 확인은 로그인 후 이용할 수 있습니다.<br />문의 내용은 작성자와 관리자에게만 공개됩니다.</p><Link className="button" to="/login" state={{ customerCenter: true, customerCenterView: view }}>{view === 'history' ? '로그인하고 내 문의 확인' : '로그인하고 문의하기'}</Link><Link className="text-link" to="/signup">아직 계정이 없으신가요? 회원가입 →</Link></div>}</section>
    </div>
  </div>;
}

function Faqs() {
  const resource = useResource('/faqs');
  const [category, setCategory] = useState('ALL');
  const [audience, setAudience] = useState('ALL');
  const [query, setQuery] = useState('');
  return <>
    <div className="faq-heading"><h2>자주 묻는 질문 <span>FAQ</span></h2><label><span className="notice-search-label">키워드 검색</span><input type="search" placeholder="궁금한 내용을 검색해 보세요" value={query} onChange={(e) => setQuery(e.target.value)} /></label></div>
    <div className="faq-audiences" role="group" aria-label="회원 유형">{[['ALL','전체'],['JOB_SEEKER','개인회원'],['COMPANY','기업회원']].map(([key,title]) => <button type="button" key={key} className={audience===key?'active':''} aria-pressed={audience===key} onClick={() => setAudience(key)}>{title}</button>)}</div>
    <div className="faq-categories" role="group" aria-label="질문 분류">{Object.entries(categories).map(([key,title]) => <button type="button" key={key} className={category===key?'active':''} aria-pressed={category===key} onClick={() => setCategory(key)}>{title}</button>)}</div>
    <Resource resource={resource}>{(data) => {
      const term=query.trim().toLocaleLowerCase();
      const rows=data.filter((row) => (category==='ALL'||row.category===category) && (audience==='ALL'||row.audience==='ALL'||row.audience===audience) && (!term||(row.question+' '+row.answer).toLocaleLowerCase().includes(term)));
      return <><p className="faq-count">전체 <strong>{rows.length}</strong>건</p>{rows.length ? <PagedRows key={category+audience+query} rows={rows}>{(pageRows) => <div className="faq-list">{pageRows.map((row) => <details className="faq-item" key={row.faqId}><summary><span className="faq-question-mark">Q</span><span><span className="faq-category-label">[{categories[row.category]}]</span> {row.question}</span><span className="faq-chevron" aria-hidden="true">⌄</span></summary><div className="faq-answer"><span>A</span><p>{row.answer}</p></div></details>)}</div>}</PagedRows> : <Empty>조건에 맞는 질문이 없습니다. 다른 키워드를 입력해 주세요.</Empty>}<div className="faq-help"><div><strong>원하는 답변을 찾지 못하셨나요?</strong><p>1:1 문의를 남기면 관리자가 확인 후 답변합니다.</p></div><Link className="button secondary" to="/inquiries?view=inquiry">1:1 문의하기 →</Link></div></>;
    }}</Resource>
  </>;
}
