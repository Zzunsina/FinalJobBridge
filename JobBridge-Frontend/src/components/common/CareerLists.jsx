import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Action, Badge, Empty, PagedRows } from './UI';
import '../../assets/styles/career-lists.css';

function FileIcon() {
  return <svg viewBox="0 0 40 48" fill="none" aria-hidden="true"><path d="M8 2h17l9 9v33H6V2h2Z" stroke="currentColor" strokeWidth="2" strokeLinejoin="round"/><path d="M24 2v11h10M13 22h14M13 29h14M13 36h9" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/></svg>;
}
function Summary({ items }) {
  return <div className="career-summary">{items.map(([label, value, note]) => <div key={label}><span>{label}</span><strong>{value}<small>건</small></strong><p>{note}</p></div>)}</div>;
}
export function ResumeCollection({ rows, reload }) {
  return <div className="career-list-page">
    <Summary items={[
      ['보관 중인 이력서', rows.length, '나의 경험을 담은 커리어 기록'],
      ['기본 이력서', rows.filter((r) => r.isDefault).length, '지원할 때 우선 선택되는 이력서'],
      ['공개 이력서', rows.filter((r) => r.isPublic).length, '공개 설정한 이력서'],
    ]} />
    <div className="career-list-heading"><h2>나의 이력서 <span>{rows.length}</span></h2><p>지원 직무에 맞춰 이력서를 관리해 보세요.</p></div>
    {rows.length ? <PagedRows rows={rows}>{(pageRows) => <div className="resume-card-list">{pageRows.map((resume) => {
      const skills = (resume.skills || '').split(/[,，\n]/).map((s) => s.trim()).filter(Boolean);
      return <article key={resume.resumeId} className={'career-resume-card' + (resume.isDefault ? ' is-default' : '')}>
        <div className="career-document-icon"><FileIcon /><span>RESUME</span></div>
        <div className="career-resume-main"><div className="career-card-eyebrow">MY CAREER STORY</div><h3><Link to={'/resumes/' + resume.resumeId + '/edit'}>{resume.title}</Link></h3><p className="career-card-date">최종 수정 <time>{resume.updatedAt?.slice(0, 10).replaceAll('-', '.') || '—'}</time></p><div className="career-skill-list">{skills.length ? <>{skills.slice(0, 5).map((skill, index) => <span key={index}>{skill}</span>)}{skills.length > 5 && <span>+{skills.length - 5}</span>}</> : <p>보유 기술을 추가해 나의 강점을 보여주세요.</p>}</div></div>
        <div className="career-resume-side"><div className="career-card-badges">{Boolean(resume.isDefault) && <span className="career-default-badge">기본 이력서</span>}<span className={'career-visibility ' + (resume.isPublic ? 'public' : '')}>{resume.isPublic ? '공개' : '비공개'}</span></div><div className="career-card-actions"><Link className="button" to={'/resumes/' + resume.resumeId + '/edit'}>이력서 수정 ↗</Link>{!resume.isDefault && <Action url={'/resumes/' + resume.resumeId + '/default'} method="put" onDone={reload}>기본 지정</Action>}<Action method="delete" url={'/resumes/' + resume.resumeId} onDone={reload} confirm="이 이력서를 삭제할까요?" className="plain career-delete">삭제</Action></div></div>
      </article>;
    })}</div>}</PagedRows> : <Empty to="/resumes/new" action="첫 이력서 작성">나만의 이력서를 만들어 보세요.</Empty>}
    <div className="career-tip"><span aria-hidden="true">✦</span><div><strong>제출 전, 미리보기로 한 번 더 확인하세요.</strong><p>이름과 연락처, 경력 기간과 오탈자를 확인하면 더 완성도 있는 이력서가 됩니다.</p></div></div>
  </div>;
}

const active = ['APPLIED', 'UNDER_REVIEW', 'DOCUMENT_PASSED', 'INTERVIEW'];
const finished = ['FINAL_PASSED', 'REJECTED', 'CANCELED'];
const stages = { APPLIED: 0, UNDER_REVIEW: 1, DOCUMENT_PASSED: 1, INTERVIEW: 2, FINAL_PASSED: 3, REJECTED: 3 };
export function ApplicationCollection({ rows }) {
  const [filter, setFilter] = useState('all');
  const visible = rows.filter((r) => filter === 'all' || (filter === 'active' ? active.includes(r.status) : filter === 'interview' ? r.status === 'INTERVIEW' : finished.includes(r.status)));
  return <div className="career-list-page">
    <Summary items={[
      ['전체 지원', rows.length, '새로운 기회를 향한 나의 발걸음'],
      ['진행 중', rows.filter((r) => active.includes(r.status)).length, '결과와 다음 단계 안내를 확인하세요'],
      ['예정된 면접', rows.filter((r) => r.nextInterviewAt).length, '지원 상세에서 시간·장소를 확인하세요'],
    ]} />
    <div className="career-application-toolbar"><div className="career-filter" role="group" aria-label="지원 상태 필터">{[['all','전체'],['active','진행 중'],['interview','면접 단계'],['finished','전형 종료']].map(([key, label]) => <button type="button" key={key} className={filter === key ? 'active' : ''} aria-pressed={filter === key} onClick={() => setFilter(key)}>{label}</button>)}</div><span>총 <strong>{visible.length}</strong>건</span></div>
    {visible.length ? <PagedRows key={filter} rows={visible}>{(pageRows) => <div className="application-card-list">{pageRows.map((application) => <article className={'career-application-card status-' + application.status} key={application.applicationId}>
      <div className="career-application-top"><span className="career-company-mark" aria-hidden="true">{application.companyName?.slice(0, 1) || '기'}</span><div className="career-application-title"><p>{application.companyName}</p><h3><Link to={'/applications/' + application.applicationId}>{application.title}</Link></h3></div><Badge value={application.status} /></div>
      <div className="career-application-bottom"><div className="career-application-meta"><span>지원일 <time>{application.appliedAt?.slice(0, 10).replaceAll('-', '.') || '—'}</time></span>{application.nextInterviewAt && <span className="career-interview-time">면접 예정 · {new Date(application.nextInterviewAt).toLocaleString('ko-KR', { timeZone: 'Asia/Seoul' })} (한국 시간)</span>}</div>
      {!['CANCELED', 'REJECTED'].includes(application.status) && stages[application.status] !== undefined && <ol className="career-progress" aria-label="채용 진행 단계">{['지원 접수','서류 전형','면접','최종 결과'].map((label, index) => <li key={label} className={(index < stages[application.status] ? 'passed ' : '') + (index === stages[application.status] ? 'current' : '')} aria-current={index === stages[application.status] ? 'step' : undefined}><span aria-hidden="true">{index < stages[application.status] ? '✓' : index + 1}</span>{label}</li>)}</ol>}
      <Link className="career-detail-link" to={'/applications/' + application.applicationId}>지원 상세·면접 일정 확인 <span aria-hidden="true">→</span></Link></div>
    </article>)}</div>}</PagedRows> : <Empty to={rows.length ? undefined : '/jobs'} action={rows.length ? undefined : '채용공고 둘러보기'}>{rows.length ? '해당 상태의 지원 내역이 없습니다.' : '아직 지원한 공고가 없습니다.'}</Empty>}
  </div>;
}
