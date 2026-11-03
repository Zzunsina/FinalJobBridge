import { formatDateTime } from "../utils/dateTime";
import CompanyOnboarding from '../components/common/CompanyOnboarding';
import { Link, useParams } from 'react-router-dom';
import { useResource } from '../hooks/useResource';
import client from '../api/client';
import { Badge, Form, Resource, Title } from '../components/common/UI';
import ResumeDocument from '../components/resume/ResumeDocument';
import { employmentReviewLabels } from '../utils/employmentReview';
import '../assets/styles/employment-detail.css';

export default function CompanyEmploymentDetail() {
  const { id } = useParams();
  const resource = useResource(`/company/employment-referrals/${id}`, { refreshInterval: 30000 });
  return <div className="employment-detail"><Link className="text-link" to="/company/employment">← 기업 취업연계 목록</Link>
    <Resource resource={resource}>{(record) => {
      const proposing = record.reviewStatus === 'PENDING' || record.reviewStatus === 'INTERVIEW_REQUESTED' && (!record.proposalId || record.proposalResponse === 'CHANGE_REQUESTED');
      return <>
      <Title description="관리자 추천 · 취업연계" action={<Badge value={record.reviewStatus === 'HIRE_REQUESTED' ? record.joinedAt ? '취업 완료' : '최종 합격·입사 대기' : record.status === 'CLOSED' ? '종료' : record.status} />}>취업연계({record.applicantName || '이름 미등록'})</Title>
      <section className="panel"><h2>연계 정보</h2><dl className="employment-facts"><dt>신청자</dt><dd>{record.applicantName}</dd><dt>연결된 공고</dt><dd>{record.jobTitle || '공고 미지정'}</dd><dt>담당 관리자</dt><dd>{record.managerName || '배정 대기 중'}</dd><dt>기업 검토</dt><dd>{employmentReviewLabels[record.reviewStatus] || '미검토'}</dd></dl></section>
      <section className="panel"><h2>추천 당시 이력서</h2><p className="muted">관리자가 추천을 전달한 시점의 공개 이력서를 보관한 내용입니다.</p>{record.resume ? record.resume.details ? <ResumeDocument resume={record.resume} contact={{ name: record.applicantName }} /> : <><h3>{record.resume.title}</h3><p>학력: {record.resume.education || '미입력'}</p><h3>경력</h3><p className="prose">{record.resume.careerDescription || '미입력'}</p><h3>보유 기술</h3><p className="prose">{record.resume.skills || '미입력'}</p></> : <p className="callout">공개된 이력서가 없습니다. 담당 관리자에게 이력서 공개 안내를 요청해 주세요.</p>}</section>
      {record.reviewStatus === 'HIRE_REQUESTED' && <CompanyOnboarding record={record} reload={resource.reload} />}
      <section className="panel"><h2>기업 면접 제안 및 결과</h2>
        {record.proposalId && <div className="callout"><strong>{record.proposalResponse === 'ACCEPTED' ? '사용자 수락 · 면접 확정' : record.proposalResponse === 'CHANGE_REQUESTED' ? '사용자가 일정 변경을 요청했습니다.' : record.proposalResponse === 'DECLINED' ? '사용자가 참여 어려움을 전달했습니다.' : '사용자 응답 대기 중'}</strong><p>{formatDateTime(record.proposedInterviewAt)} · {record.proposedInterviewType === 'ONLINE' ? '온라인' : '오프라인'}</p><p className="prose">{record.proposedLocation || record.proposedMeetingUrl}</p><p className="prose">구직자 면접 안내: {record.applicantGuidance || '별도 안내 없음'}</p>{record.proposalRequests && <p className="prose">사용자 요청: {record.proposalRequests}</p>}</div>}
        {!['CLOSED','EMPLOYED'].includes(record.status) && (proposing || record.reviewStatus === 'INTERVIEW_REQUESTED' && record.status === 'INTERVIEW' && record.proposalResponse === 'ACCEPTED') ? <Form key={id + ':' + record.reviewStatus + ':' + (record.proposalId || '') + ':' + record.proposalResponse} initial={{ reviewStatus: '', opinion: '' }} fields={[
          { name: 'reviewStatus', label: proposing ? '면접 제안 여부' : '면접 결과', required: true, options: (proposing ? ['INTERVIEW_REQUESTED','DECLINED'] : ['HIRE_REQUESTED','DECLINED']).map((value) => ({ value, label: value === 'INTERVIEW_REQUESTED' ? '면접 일정 제안' : employmentReviewLabels[value] })) },
          { name: 'interviewAt', label: '제안할 면접 일시 (한국 시간)', type: 'confirmed-datetime', required: true, visibleWhen: (values) => values.reviewStatus === 'INTERVIEW_REQUESTED' },
          { name: 'interviewType', label: '면접 방식', required: true, options: ['ONLINE','OFFLINE'], visibleWhen: (values) => values.reviewStatus === 'INTERVIEW_REQUESTED' },
          { name: 'location', label: '면접 장소', type: 'address', maxLength: 500, required: true, visibleWhen: (values) => values.reviewStatus === 'INTERVIEW_REQUESTED' && values.interviewType === 'OFFLINE' },
          { name: 'meetingUrl', label: '온라인 면접 접속 주소', type: 'url', maxLength: 500, required: true, visibleWhen: (values) => values.reviewStatus === 'INTERVIEW_REQUESTED' && values.interviewType === 'ONLINE' },
          { name: 'applicantGuidance', label: '구직자 면접 안내 (준비물·참석 방법 등)', type: 'textarea', maxLength: 10000, visibleWhen: (values) => values.reviewStatus === 'INTERVIEW_REQUESTED' },
          { name: 'opinion', label: '담당 관리자에게 전달할 결과·사유 (진행 어려움 선택 시 필수)', type: 'textarea', maxLength: 10000, visibleWhen: (values) => values.reviewStatus !== 'INTERVIEW_REQUESTED', validate: (value, values) => values.reviewStatus === 'DECLINED' && !value?.trim() ? '진행이 어려운 사유를 입력해 주세요.' : '' },
        ]} onSubmit={async (body) => {
          if (body.reviewStatus === 'INTERVIEW_REQUESTED') await client.post(`/company/employment-referrals/${id}/proposals`, { ...body, interviewAt: body.interviewAt + ':00+09:00' });
          else await client.patch(`/company/employment-referrals/${id}/response`, body);
          await resource.reload();
        }} submit={proposing ? '면접 제안·응답 전달' : '면접 결과 전달'} successMessage="전달했습니다." /> : <p className="muted">{record.reviewStatus === 'INTERVIEW_REQUESTED' ? '사용자가 제안을 수락하면 면접이 확정됩니다. 일정 변경 요청이 오면 새 일정을 제안해 주세요.' : '해당 추천은 종료되었거나 결과를 전달한 상태입니다.'}</p>}
      </section>
    </>; }}</Resource>
  </div>;
}
