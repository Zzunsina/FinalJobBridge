import { Link, useParams } from 'react-router-dom';
import { useResource } from '../hooks/useResource';
import { Badge, Resource, Title } from '../components/common/UI';
import '../assets/styles/employment-detail.css';

const stages = [
  ['REQUESTED', '신청 접수'], ['COUNSELING', '맞춤 상담'],
  ['MATCHING', '기업 매칭'], ['INTERVIEW', '면접 진행'], ['EMPLOYED', '취업 완료'],
];
const guidance = {
  REQUESTED: '신청이 접수되었습니다. 이력서를 최신 정보로 정리하고 담당자의 안내를 기다려 주세요.',
  COUNSELING: '맞춤 상담 단계입니다. 희망 직무와 근무 조건을 정리하고 담당자의 상담 안내를 확인해 주세요.',
  MATCHING: '기업 매칭 단계입니다. 연결된 기업과 공고를 확인하고 담당자의 다음 안내를 기다려 주세요.',
  INTERVIEW: '면접 진행 단계입니다. 담당자의 안내에서 면접 일시와 장소를 확인해 주세요. 일반 채용 지원의 면접 일정은 지원 내역에서 별도로 확인할 수 있습니다.',
  EMPLOYED: '취업연계가 완료되었습니다. 담당자가 남긴 최종 안내를 확인해 주세요.',
  CLOSED: '취업연계가 종료되었습니다. 안내 내용을 확인하고 추가 상담이 필요하면 고객센터로 문의해 주세요.',
};
const date = (value) => value ? new Date(value).toLocaleString('ko-KR', { timeZone: 'Asia/Seoul' }) : '—';

export default function EmploymentDetail() {
  const { id } = useParams();
  const resource = useResource(`/employment-links/${id}`, { refreshInterval: 30000 });
  return <div className="employment-detail">
    <Link className="text-link" to="/employment">← 나의 취업연계 목록</Link>
    <Resource resource={resource}>{(record) => {
      const current = stages.findIndex(([status]) => status === record.status);
      return <>
        <Title description="담당자의 안내와 연결된 채용 정보를 확인하세요." action={<Badge value={record.status === 'CLOSED' ? '종료' : record.status} />}>취업연계({record.applicantName || '이름 미등록'})</Title>
        {record.status !== 'CLOSED' && <ol className="employment-stages" aria-label="현재 취업연계 진행 단계">{stages.map(([status, label], index) => <li key={status} className={index === current ? 'current' : ''} aria-current={index === current ? 'step' : undefined}><span>{index + 1}</span>{label}{index === current && <small>현재 단계</small>}</li>)}</ol>}
        <section className="panel"><h2>{record.status === 'CLOSED' ? '종료 안내' : '지금 확인할 내용'}</h2><p className="prose">{guidance[record.status] || '담당자의 안내를 확인해 주세요.'}</p></section>
        <div className="employment-detail-grid">
          <section className="panel"><h2>신청 및 담당자 정보</h2><dl className="employment-facts"><dt>신청자</dt><dd>{record.applicantName || '이름 미등록'}</dd><dt>신청일</dt><dd>{date(record.requestedAt)}</dd><dt>담당자</dt><dd>{record.managerName || '담당자 배정 대기 중'}</dd>{record.completedAt && <><dt>{record.status === 'EMPLOYED' ? '완료일' : '종료일'}</dt><dd>{date(record.completedAt)}</dd></>}</dl></section>
          <section className="panel"><h2>연결된 기업과 채용공고</h2><dl className="employment-facts"><dt>기업</dt><dd>{record.companyName || '기업 매칭 대기 중'}</dd><dt>채용공고</dt><dd>{record.jobTitle || '연결된 공고가 없습니다.'}</dd></dl><div className="actions">{record.companyId && <Link className="button secondary" to={`/companies/${record.companyId}`}>기업 소개 보기</Link>}{record.jobId && <Link className="button secondary" to={`/jobs/${record.jobId}`}>채용공고 보기</Link>}</div>{record.jobTitle && !record.jobId && <p className="muted">현재 공개되지 않은 공고입니다. 자세한 내용은 담당자의 안내를 확인해 주세요.</p>}</section>
        </div>
        <section className="panel"><h2>담당자 안내</h2><p className="prose">{record.publicMessage || '아직 등록된 안내가 없습니다. 안내가 등록되면 알림으로 알려드립니다.'}</p></section>
        <div className="actions"><Link className="button secondary" to="/resumes">내 이력서 관리</Link><Link className="button secondary" to="/inquiries?view=inquiry">고객센터 문의</Link></div>
      </>;
    }}</Resource>
  </div>;
}
