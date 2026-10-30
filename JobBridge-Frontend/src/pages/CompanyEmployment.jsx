import { useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useResource } from '../hooks/useResource';
import { Badge, PagedRows, Resource, Title } from '../components/common/UI';
import '../assets/styles/employment-detail.css';

export default function CompanyEmployment() {
  const resource = useResource('/company/employment-links', { refreshInterval: 30000 });
  const [query, setQuery] = useState('');
  const [params] = useSearchParams();
  const focus = params.get('focus');
  return <div className="employment-detail">
    <Title description="관리자가 우리 기업에 연결한 취업연계 신청자를 확인하세요." action={<button className="secondary" onClick={resource.reload} disabled={resource.loading}>새로고침</button>}>취업연계</Title>
    <p className="callout">취업연계는 관리자가 연결한 상담·매칭 내역입니다. 직접 접수한 지원서는 <Link to="/company/applicants">지원자 관리</Link>에서 확인할 수 있습니다. 상담과 면접 진행은 담당 관리자와 확인해 주세요.</p>
    <label className="form-field">신청자·공고 검색<input type="search" value={query} onChange={(e) => setQuery(e.target.value)} placeholder="신청자 이름 또는 채용공고" /></label>
    <Resource resource={resource}>{(rows) => {
      const visible = rows.filter((row) => `${row.applicantName || ''} ${row.jobTitle || ''}`.toLocaleLowerCase().includes(query.trim().toLocaleLowerCase()));
      return <><p className="muted">총 {visible.length}건</p>{visible.length ? <PagedRows key={query + ':' + focus} rows={visible} focusId={focus} idKey="employmentLinkId">{(pageRows) => <div className="stack">{pageRows.map((row) => <article className="panel" key={row.employmentLinkId} style={String(row.employmentLinkId) === focus ? { borderColor: '#3370cc' } : undefined}>
        <div className="row"><h2>취업연계({row.applicantName || '이름 미등록'})</h2><Badge value={row.status === 'CLOSED' ? '종료' : row.status} /></div>
        <dl className="employment-facts"><dt>신청자</dt><dd>{row.applicantName || '이름 미등록'}</dd><dt>담당 관리자</dt><dd>{row.managerName || '담당자 배정 대기 중'}</dd><dt>연결된 공고</dt><dd>{row.jobTitle || '공고 미지정'}</dd><dt>신청일</dt><dd>{new Date(row.requestedAt).toLocaleDateString('ko-KR', { timeZone: 'Asia/Seoul' })}</dd></dl>
        <Link className="button secondary" to={`/company/employment/${row.employmentLinkId}`}>취업연계 신청자 상세 보기 →</Link>
      </article>)}</div>}</PagedRows> : <div className="empty">{rows.length ? '검색 조건에 맞는 취업연계가 없습니다.' : '우리 기업에 연결된 취업연계가 없습니다.'}</div>}</>;
    }}</Resource>
  </div>;
}
