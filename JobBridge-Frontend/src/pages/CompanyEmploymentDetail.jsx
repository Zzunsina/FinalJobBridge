import { Link, useParams } from 'react-router-dom';
import { useResource } from '../hooks/useResource';
import client from '../api/client';
import { Badge, Form, Resource, Title } from '../components/common/UI';
import ResumeDocument from '../components/resume/ResumeDocument';
import { employmentReviewLabels } from '../utils/employmentReview';
import '../assets/styles/employment-detail.css';

export default function CompanyEmploymentDetail() {
  const { id } = useParams();
  const resource = useResource(`/company/employment-links/${id}`);
  return <div className="employment-detail"><Link className="text-link" to="/company/employment">← 기업 취업연계 목록</Link>
    <Resource resource={resource}>{(record) => <>
      <Title description="관리자 추천 · 취업연계" action={<Badge value={record.status === 'CLOSED' ? '종료' : record.status} />}>취업연계({record.applicantName || '이름 미등록'})</Title>
      <section className="panel"><h2>연계 정보</h2><dl className="employment-facts"><dt>신청자</dt><dd>{record.applicantName}</dd><dt>연결된 공고</dt><dd>{record.jobTitle || '공고 미지정'}</dd><dt>담당 관리자</dt><dd>{record.managerName || '배정 대기 중'}</dd><dt>기업 검토</dt><dd>{employmentReviewLabels[record.reviewStatus] || '미검토'}</dd></dl></section>
      <section className="panel"><h2>공개 이력서</h2><p className="muted">신청자가 현재 공개한 이력서 중 기본 이력서를 우선 표시합니다. 공개 설정 변경 시 표시 내용이 달라질 수 있습니다.</p>{record.resume ? record.resume.details ? <ResumeDocument resume={record.resume} contact={{ name: record.applicantName }} /> : <><h3>{record.resume.title}</h3><p>학력: {record.resume.education || '미입력'}</p><h3>경력</h3><p className="prose">{record.resume.careerDescription || '미입력'}</p><h3>보유 기술</h3><p className="prose">{record.resume.skills || '미입력'}</p></> : <p className="callout">공개된 이력서가 없습니다. 담당 관리자에게 이력서 공개 안내를 요청해 주세요.</p>}</section>
      <section className="panel"><h2>기업 검토 의견</h2><p className="muted">검토 의견은 담당 관리자에게 전달됩니다. 취업연계의 최종 진행 상태는 관리자가 변경합니다.</p>
        {['CLOSED', 'EMPLOYED'].includes(record.status) ? <><p>{employmentReviewLabels[record.reviewStatus] || '등록된 검토 상태가 없습니다.'}</p><p className="prose">{record.opinion || '등록된 의견이 없습니다.'}</p><p className="muted">종료된 취업연계는 의견을 수정할 수 없습니다.</p></> : <Form key={id + ':' + (record.reviewUpdatedAt || '')} initial={{ reviewStatus: record.reviewStatus || 'REVIEWING', opinion: record.opinion || '' }} fields={[
          { name: 'reviewStatus', label: '검토 상태', required: true, options: Object.entries(employmentReviewLabels).map(([value, label]) => ({ value, label })) },
          { name: 'opinion', label: '담당 관리자에게 전달할 의견 (진행 어려움 선택 시 필수)', type: 'textarea', maxLength: 10000, validate: (value, values) => values.reviewStatus === 'DECLINED' && !value?.trim() ? '진행이 어려운 사유를 입력해 주세요.' : '' },
        ]} onSubmit={async (body) => { await client.patch(`/company/employment-links/${id}/review`, body); await resource.reload(); }} submit="검토 의견 전달" successMessage="검토 의견을 전달했습니다." />}
      </section>
    </>}</Resource>
  </div>;
}
