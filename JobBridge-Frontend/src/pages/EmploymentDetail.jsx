import { formatDateTime } from "../utils/dateTime";
import EmploymentOutcome from '../components/common/EmploymentOutcome';
import { employmentStage } from '../utils/employmentStage';
import client from '../api/client';
import { Link, useParams } from 'react-router-dom';
import { useResource } from '../hooks/useResource';
import { Badge, Form, Resource, Title } from '../components/common/UI';
import '../assets/styles/employment-detail.css';

const stages = [
  ['REQUESTED', '신청 접수'],
  ['MATCHING', '기업 매칭'], ['INTERVIEW', '면접 진행'], ['HIRED', '최종 합격'], ['EMPLOYED', '입사 확인 완료'],
];
const guidance = {
  REQUESTED: '신청이 접수되었습니다. 제출한 희망 업종·직업에 맞는 기업을 검토합니다. 담당자의 안내를 기다려 주세요.',
  COUNSELING: '제출한 희망 조건을 바탕으로 기업을 매칭하고 있습니다.',
  MATCHING: '기업 매칭 단계입니다. 연결된 기업과 공고를 확인하고 담당자의 다음 안내를 기다려 주세요.',
  INTERVIEW: '면접 진행 단계입니다. 담당자의 안내에서 면접 일시와 장소를 확인해 주세요. 일반 채용 지원의 면접 일정은 지원 내역에서 별도로 확인할 수 있습니다.',
  EMPLOYED: '취업연계가 완료되었습니다. 담당자가 남긴 최종 안내를 확인해 주세요.',
  CLOSED: '취업연계가 종료되었습니다. 안내 내용을 확인하고 추가 상담이 필요하면 고객센터로 문의해 주세요.',
};
const date = (value) => value ? formatDateTime(value) : '—';

export default function EmploymentDetail() {
  const { id } = useParams();
  const resource = useResource(`/employment-links/${id}`, { refreshInterval: 30000 });
  return <div className="employment-detail">
    <Link className="text-link" to="/employment">← 나의 취업연계 목록</Link>
    <Resource resource={resource}>{(record) => {
      const hasOutcome = record.referralStatus === 'HIRE_REQUESTED' || !!record.joinedAt || !!record.notJoinedAt;
      const currentStage = record.joinedAt ? 'EMPLOYED' : record.referralStatus === 'HIRE_REQUESTED' ? 'HIRED' : record.status === 'COUNSELING' ? 'MATCHING' : record.status;
      const current = stages.findIndex(([status]) => status === currentStage);
      return <>
        <Title description="담당자의 안내와 연결된 채용 정보를 확인하세요." action={<Badge value={employmentStage(record)} />}>취업연계({record.applicantName || '이름 미등록'})</Title>
        {record.status !== 'CLOSED' && <ol className="employment-stages" aria-label="현재 취업연계 진행 단계">{stages.map(([status, label], index) => <li key={status} className={index === current ? 'current' : ''} aria-current={index === current ? 'step' : undefined}><span>{index + 1}</span>{label}{index === current && <small>현재 단계</small>}</li>)}</ol>}
        {hasOutcome && <EmploymentOutcome record={record} reload={resource.reload} />}
        {!hasOutcome && <section className="panel"><h2>{record.status === 'CLOSED' ? '종료 안내' : '지금 확인할 내용'}</h2><p className="prose">{record.referralStatus === 'HIRE_REQUESTED' && record.status !== 'EMPLOYED' ? '기업이 채용을 희망했습니다. 담당자가 실제 입사 여부를 확인하고 있습니다.' : record.referralStatus === 'PENDING' ? '기업에 추천을 전달했습니다. 기업의 면접 희망 여부를 기다리고 있습니다.' : record.referralStatus === 'INTERVIEW_REQUESTED' && record.status !== 'INTERVIEW' ? (record.proposalResponse === 'CHANGE_REQUESTED' ? '일정 변경을 요청했습니다. 기업의 새 제안을 기다려 주세요.' : record.proposalId ? '기업이 면접을 제안했습니다. 일시·장소·안내를 확인하고 수락 또는 변경 요청을 선택해 주세요.' : '기업의 구체적인 면접 일정 제안을 기다려 주세요.') : ['DECLINED', 'CANCELED'].includes(record.referralStatus) && record.status === 'MATCHING' ? '이전 기업과의 추천이 종료되었습니다. 담당자가 다른 기업과의 매칭을 준비합니다.' : guidance[record.status] || '담당자의 안내를 확인해 주세요.'}</p></section>}
        {record.proposalId && <details className="panel employment-history" open={hasOutcome ? undefined : true}><summary>{hasOutcome ? '전형 이력 보기' : '기업의 면접 제안'}</summary><div>
          <dl className="employment-facts"><dt>면접 일시</dt><dd>{date(record.proposedInterviewAt)} (한국 시간)</dd><dt>면접 방식</dt><dd>{record.proposedInterviewType === 'ONLINE' ? '온라인' : '오프라인'}</dd><dt>{record.proposedInterviewType === 'ONLINE' ? '접속 주소' : '면접 장소'}</dt><dd className="prose">{record.proposedMeetingUrl || record.proposedLocation}</dd></dl>
          <h3>기업 면접 안내</h3><p className="prose">{record.applicantGuidance || '별도 안내가 없습니다.'}</p>
          {record.proposalResponse ? <><div className="form-success" role="status">{record.proposalResponse === 'ACCEPTED' ? (hasOutcome ? '수락한 면접 제안 이력입니다.' : '제안을 수락하여 면접이 확정되었습니다.') : record.proposalResponse === 'CHANGE_REQUESTED' ? '일정 변경을 요청했습니다. 기업의 새 제안을 기다려 주세요.' : '참여 어려움을 전달했습니다. 다른 기업과 매칭을 진행합니다.'}</div><p className="prose">요청사항·사유: {record.proposalRequests || '없음'}</p></> : record.referralStatus === 'INTERVIEW_REQUESTED' && !['CLOSED','EMPLOYED'].includes(record.status) ? <Form key={record.proposalId} fields={[
            { name: 'response', label: '제안된 면접 일정에 응답해 주세요.', required: true, options: [{ value: 'ACCEPTED', label: '일정 수락 · 면접 확정' }, { value: 'CHANGE_REQUESTED', label: '일정 변경 요청' }, { value: 'DECLINED', label: '참여 어려움 · 다른 기업 매칭 희망' }] },
            { name: 'requests', label: '기업에 전달할 요청사항 또는 사유 (변경 요청·참여 어려움 시 필수)', type: 'textarea', maxLength: 2000, validate: (value, values) => ['DECLINED','CHANGE_REQUESTED'].includes(values.response) && !value?.trim() ? '변경 요청 또는 참여가 어려운 사유를 입력해 주세요.' : '' },
          ]} onSubmit={async (body) => { await client.patch(`/employment-interview-proposals/${record.proposalId}/response`, body); await resource.reload(); }} submit="면접 제안 응답 전달" successMessage="응답을 전달했습니다." /> : <p className="muted">종료된 면접 제안입니다.</p>}
        </div></details>}
        <div className="employment-detail-grid">
          <section className="panel"><h2>신청 및 담당자 정보</h2><dl className="employment-facts"><dt>신청자</dt><dd>{record.applicantName || '이름 미등록'}</dd><dt>신청일</dt><dd>{date(record.requestedAt)}</dd><dt>희망 업종</dt><dd>{record.preferredIndustry || '기존 신청 · 미입력'}</dd><dt>선호 직업</dt><dd>{record.preferredOccupation || '기존 신청 · 미입력'}</dd><dt>담당자</dt><dd>{record.managerName || '담당자 배정 대기 중'}</dd>{record.completedAt && <><dt>{record.status === 'EMPLOYED' ? '완료일' : '종료일'}</dt><dd>{date(record.completedAt)}</dd></>}</dl></section>
          <section className="panel"><h2>연결된 기업과 채용공고</h2><dl className="employment-facts"><dt>기업</dt><dd>{record.companyName || '기업 매칭 대기 중'}</dd><dt>채용공고</dt><dd>{record.jobTitle || '연결된 공고가 없습니다.'}</dd></dl><div className="actions">{record.companyId && <Link className="button secondary" to={`/companies/${record.companyId}`}>기업 소개 보기</Link>}{record.jobId && <Link className="button secondary" to={`/jobs/${record.jobId}`}>채용공고 보기</Link>}</div>{record.jobTitle && !record.jobId && <p className="muted">현재 공개되지 않은 공고입니다. 자세한 내용은 담당자의 안내를 확인해 주세요.</p>}</section>
        </div>
        <section className="panel"><h2>관리자 취업연계 안내</h2><p className="prose">{record.publicMessage || '아직 등록된 안내가 없습니다. 안내가 등록되면 알림으로 알려드립니다.'}</p></section>
        <div className="actions"><Link className="button secondary" to="/resumes">내 이력서 관리</Link><Link className="button secondary" to="/inquiries?view=inquiry">고객센터 문의</Link></div>
      </>;
    }}</Resource>
  </div>;
}
