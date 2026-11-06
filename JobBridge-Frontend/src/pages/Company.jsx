import { ReportForm } from './ReviewFeatures';

import { useState } from 'react';
import CompanyInterviewForm from '../components/common/CompanyInterviewForm';

import NoticeImageInput from '../components/common/NoticeImageInput';
import SubmittedAssessment from '../components/common/SubmittedAssessment';

import CalendarPicker from '../components/common/CalendarPicker';
import { koreanToday } from '../utils/memberInputs';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { useResource } from '../hooks/useResource';
import client from '../api/client';
import {
  Action,
  Badge,
  Empty,
  Form,
  PagedResource,
  Resource,
  Title,
} from '../components/common/UI';
import { AccountSettings, Interviews, Snapshot } from './Seeker';
import CompanyImageUpload from '../components/common/CompanyImageUpload';
import AssessmentRanking from '../components/common/AssessmentRanking';
import CompanyCover from '../components/common/CompanyCover';
import CompanyIndustryInput from '../components/common/CompanyIndustryInput';
import { companySizes } from '../data/companySizes';

export { default as CompanyDashboard } from './CompanyOverview';

export function JobManage() {
  const all = useResource('/company/jobs');
  const profile = useResource('/company/profile');
  const [params] = useSearchParams();
  const status = ['PUBLISHED','PENDING','DRAFT','CLOSED','REJECTED'].includes(params.get('status')) ? params.get('status') : '';
  const closing = params.get('closing') === 'true';
  const resource = { ...all, data: all.data?.filter((job) => {
    const days = Math.round((Date.parse(job.endDate) - Date.parse(koreanToday())) / 86400000);
    return (!status || job.status === status) && (status !== 'PUBLISHED' || days >= 0) && (!closing || (job.status === 'PUBLISHED' && days >= 0 && days <= 7));
  }) };
  return (
    <>
      <Title
        action={
          <Link className="button" to="/company/jobs/new">
            ＋ 공고 작성
          </Link>
        }
      >
        채용공고 관리
      </Title>
      {(status || closing) && <p className="callout">{closing ? '7일 이내 마감 예정 공고만 표시합니다.' : ({ PUBLISHED: '게시 중', PENDING: '심사 중', DRAFT: '작성 중', CLOSED: '마감', REJECTED: '반려' }[status] + ' 공고만 표시합니다.')} <Link to="/company/jobs">전체 공고 보기 →</Link></p>}
      <PagedResource resource={resource}>
        {(rows) =>
          rows.length ? (
            <div className="stack">
              {rows.map((j) => (
                <article className="panel company-managed-job" key={j.jobId}>
                  <CompanyCover
                    companyId={j.companyId}
                    profileImageUrl="/api/company/image"
                    companyName={profile.data?.companyName}
                    industry={profile.data?.industry}
                    bannerImageUrl={j.bannerImageUrl}
                    className="company-managed-job-cover"
                  />
                  <div className="company-managed-job-content">
                  <div className="row">
                    <h2>{j.title}</h2>
                    <Badge value={j.status} />
                  </div>
                  <p className="muted">
                    {j.workLocation} · {j.startDate} ~ {j.endDate}
                  </p>
                  {j.reviewReason && <p className="callout">심사 의견: {j.reviewReason}</p>}
                  <div className="actions">
                    {!['CLOSED', 'PENDING'].includes(j.status) && (
                      <Link className="button secondary" to={`/company/jobs/${j.jobId}/edit`}>
                        수정하기
                      </Link>
                    )}
                    {j.status === 'DRAFT' && (
                      <Action
                        url={`/company/jobs/${j.jobId}/review-request`}
                        onDone={resource.reload}
                      >
                        심사 요청
                      </Action>
                    )}
                    {j.status === 'PUBLISHED' && (
                      <Action
                        url={`/company/jobs/${j.jobId}/close`}
                        onDone={resource.reload}
                        confirm="이 공고를 마감할까요?"
                      >
                        공고 마감
                      </Action>
                    )}
                    <Link className="button secondary" to={`/company/jobs/${j.jobId}/applicants`}>
                      지원자 관리 →
                    </Link>
                    <Action
                      method="delete"
                      url={`/company/jobs/${j.jobId}`}
                      onDone={resource.reload}
                      confirm="이 공고를 삭제할까요? 지원·연계 이력이 있는 공고는 삭제할 수 없으며 마감으로 관리해 주세요."
                    >
                      삭제
                    </Action>
                  </div>
                  </div>
                </article>
              ))}
            </div>
          ) : (
            <Empty to="/company/jobs/new" action="공고 작성">
              아직 등록한 공고가 없습니다.
            </Empty>
          )
        }
      </PagedResource>
    </>
  );
}

const jobFields = [
  { name: 'title', label: '공고 제목', required: true, maxLength: 200 },
  {
    name: 'jobType',
    label: '고용형태',
    required: true,
    options: ['정규직', '계약직', '인턴'],
  },
  {
    name: 'careerType',
    label: '경력',
    required: true,
    options: ['신입', '경력', '신입·경력'],
  },
  {
    name: 'education',
    label: '학력',
    required: true,
    options: ['학력무관', '고졸 이상', '전문대졸(2·3년) 이상', '대졸(4년) 이상', '석사 이상', '박사'],
  },
  { name: 'salary', label: '급여', maxLength: 100 },
  {
    name: 'workLocation',
    label: '근무지역',
    type: 'address',
    required: true,
    maxLength: 255,
  },
  {
    name: 'headcount',
    label: '모집인원',
    type: 'number',
    min: 1,
    max: 10000,
    required: true,
  },
  { name: 'startDate', label: '접수 시작일', type: 'date', required: true },
  { name: 'endDate', label: '접수 마감일', type: 'date', required: true },
 { name: 'description', label: '상세 설명', type: 'textarea', required: true },
 { name: 'detailImageUrl', label: '상세 소개 이미지', render: ({ id, values, setValues }) => <NoticeImageInput id={id} value={values.detailImageUrl || ''} onChange={(value) => setValues(old => ({ ...old, detailImageUrl: value }))} maxFileSizeMB={4} acceptedTypes={['image/jpeg', 'image/png']} helpText="채용 안내 포스터 1장 · JPG·PNG 최대 4MB · 공고 저장 시 함께 반영됩니다." previewAlt="상세 소개 이미지 미리보기" removeLabel="상세 소개 이미지 삭제" /> },

{
  name: 'bannerImageUrl',
  label: '기업 홍보 배너 이미지',
  type: 'notice-image',
  previewUrl: '',
},

{ name: 'requirements', label: '자격요건', type: 'textarea' },
  { name: 'preferredQualifications', label: '우대사항', type: 'textarea' },
];
export function JobForm() {
  const { id } = useParams();
  const navigate = useNavigate();
  const resource = useResource(id ? `/company/jobs/${id}` : '/company/profile');
  return (
    <>
      <Title description="저장한 공고는 초안 상태입니다. 심사 요청 후 관리자 승인을 받으면 공개됩니다.">
        {id ? '공고 수정' : '공고 작성'}
      </Title>
      <p className="callout">게시 중인 공고를 수정하면 공개가 중단되고 재심사가 필요합니다.</p>
      <Resource resource={resource}>
        {(data) => (
          <div className="panel narrow">
            <Form
              key={`${id || "new"}:${data.jobId || ""}:${data.updatedAt || ""}`}
              initial={id ? { ...data, education: data.education || '학력무관' } : { headcount: 1, jobType: '정규직', careerType: '신입·경력', education: '학력무관' }}
              fields={jobFields.map((field) => {
                if (field.name === 'education' && id && data.education && !field.options.includes(data.education)) {
                  return { ...field, options: [...field.options, { value: data.education, label: `${data.education} (기존 입력값)` }] };
                }
                if (!['startDate', 'endDate'].includes(field.name)) return field;
                const originalStart = id ? data.startDate : '';
                const startField = field.name === 'startDate';
                return {
                  ...field,
                  validate: (value, values) => {
                    const today = koreanToday();
                    if (!value) return '날짜를 선택해 주세요.';
                    if (startField && value < today && value !== originalStart) return '시작일은 오늘 이후로 선택해 주세요.';
                    if (!startField && value < today) return '마감일은 오늘 이후로 선택해 주세요.';
                    if (!startField && values.startDate && value < values.startDate) return '마감일은 시작일보다 빠를 수 없습니다.';
                    return '';
                  },
                  render: ({ id: fieldId, values, setValues, error, clearError }) => {
                    const today = koreanToday();
                    const min = startField
                      ? (originalStart && originalStart < today ? originalStart : today)
                      : (values.startDate && values.startDate > today ? values.startDate : today);
                    return <><CalendarPicker id={fieldId} name={field.name} label={field.label} required value={values[field.name] || ''} min={min}
                      isDateDisabled={startField ? (date) => date < today && date !== originalStart : undefined}
                      onChange={(value) => { clearError(); setValues((old) => ({ ...old, [field.name]: value, ...(startField && old.endDate && old.endDate < value ? { endDate: '' } : {}) })); }} />
                      {error && <p className="input-guidance" role="alert">{error}</p>}</>;
                  },
                };
              })}
              onSubmit={async (body) => {
                if (id) await client.put(`/company/jobs/${id}`, body);
                else await client.post('/company/jobs', body);
                navigate('/company/jobs');
              }}
              submit="초안 저장"
            />

            <CompanyImageUpload />
          </div>
        )}
      </Resource>
    </>
  );
}

export function Applicants() {
  const { id } = useParams();
  const resource = useResource(`/company/jobs/${id}/applications`, { refreshInterval:10000 });
  return (
    <>
      <Title action={<button type="button" className="secondary" onClick={resource.reload}>새로고침</button>}>지원자 관리</Title>
      <Resource resource={resource}>{(rows) => <AssessmentRanking rows={rows} />}</Resource>
      <h2>전체 지원자</h2>
      <PagedResource resource={resource}>
        {(rows) =>
          rows.length ? (
            <div className="stack">
              {rows.map((a) => (
                <Link
                  className="panel row"
                  key={a.applicationId}
                  to={`/company/applications/${a.applicationId}`}
                >
                  <div>
                    <h2>{a.name}</h2>
                    <p>{a.appliedAt?.slice(0, 10)} 지원{a.score != null && <> · 지원 당시 적성 점수 {a.score}점</>}</p>
                  </div>
                  <Badge value={a.status} />
                </Link>
              ))}
            </div>
          ) : (
            <Empty>아직 지원자가 없습니다.</Empty>
          )
        }
      </PagedResource>
    </>
  );
}

export function ApplicantDetail() {
  const { id } = useParams();

  return <ApplicantDetailContent key={id} id={id} />;
}

function ApplicantDetailContent({ id }) {
  const [editingInterview, setEditingInterview] = useState(null);
  const [interviewMessage, setInterviewMessage] = useState('');

  const resource = useResource(`/company/applications/${id}`, { refreshInterval:10000 });
  const transitions = {
    APPLIED: ['UNDER_REVIEW', 'REJECTED'],
    UNDER_REVIEW: ['DOCUMENT_PASSED', 'REJECTED'],
    DOCUMENT_PASSED: ['INTERVIEW', 'REJECTED'],
    INTERVIEW: ['FINAL_PASSED', 'REJECTED'],
  };
  return (
    <Resource resource={resource}>
      {(a) => (
        <>
          <Title description={a.title} action={<button type="button" className="secondary" onClick={resource.reload}>새로고침</button>}>지원자 상세</Title>
          <div className="panel">
            <Badge value={a.status} />
            <Snapshot data={a.snapshot} />
            <SubmittedAssessment data={a.snapshot?.careerAssessment} />
            <ReportForm targetType="APPLICANT" targetId={Number(id)} />
            {a.snapshot?.resume?.attachment && <a className="button secondary" href={`/api/applications/${id}/attachment`}>지원 당시 첨부파일 다운로드</a>}
            <h3>자기소개서</h3>
            <p className="prose">{a.coverLetter || '미입력'}</p>
            <div className="actions applicant-status-actions">
              {(transitions[a.status] || []).map((status) => (
                <Action
                  key={status}
                  method="patch"
                  url={`/company/applications/${id}/status`}
                  body={{ status }}
                  onDone={resource.reload}
                  confirm="지원 상태를 변경할까요?"
                >
                  {status === 'DOCUMENT_PASSED'
                    ? '서류 합격'
                    : status === 'UNDER_REVIEW'
                      ? '서류 검토 시작'
                      : status === 'INTERVIEW'
                        ? '면접 단계로 이동'
                        : status === 'FINAL_PASSED'
                          ? '최종 합격'
                          : '불합격 처리'}
                </Action>
              ))}
            </div>
            <h2 className="applicant-interview-heading">면접 일정</h2>
            {interviewMessage && <p className="form-success" role="status">{interviewMessage}</p>}
            <Interviews rows={a.interviews} onDelete={resource.reload} showInternalMemo
              onEdit={a.status === 'INTERVIEW' ? (interview) => {
                // 💡 당일 면접 수정 불가 로직
                const interviewDate = interview.interviewAt?.slice(0, 10);
                const today = koreanToday();

                if (interviewDate === today) {
                  window.alert('당일 면접 일정은 수정할 수 없습니다. 담당자에게 직접 연락해 주세요.');
                  return;
                }

                setInterviewMessage('');
                setEditingInterview(interview);
              } : undefined} />
            {editingInterview && a.status === 'INTERVIEW' && (
              <CompanyInterviewForm key={editingInterview.interviewId} interview={editingInterview}
                onCancel={() => setEditingInterview(null)}
                onSubmit={async (body) => {
                  await client.put('/company/interviews/' + editingInterview.interviewId, body);
                  setEditingInterview(null);
                  setInterviewMessage('면접 일정을 수정했습니다.');
                  await resource.reload();
                }} />
            )}
            {!editingInterview && a.status === 'INTERVIEW' && !a.interviews.some((interview) => interview.status === 'SCHEDULED') && (
              <CompanyInterviewForm onSubmit={async (body) => {
                await client.post('/company/applications/' + id + '/interviews', body,
                  { headers: { 'Idempotency-Key': crypto.randomUUID() } });
                await resource.reload();
              }} />
            )}
          </div>
        </>
      )}
    </Resource>
  );
}

export function CompanyProfile() {
  const resource = useResource('/company/profile');
  return (
    <>
      <Title>기업·계정 정보</Title>
      <Resource resource={resource}>
        {(data) => (
          <div className="panel narrow">

            <CompanyImageUpload />
            <Badge value={data.approvalStatus} />
            <p className="callout">
              {data.profileMissing
                ? '기업 정보를 등록하면 관리자 심사가 시작됩니다. 승인 후 채용공고를 이용할 수 있습니다.'
                : '사업자번호를 변경하면 재심사가 필요합니다.'}
            </p>
            {data.reviewReason && <p>심사 의견: {data.reviewReason}</p>}
            <Form
              initial={data}
              submit={data.profileMissing ? '기업 정보 등록 및 심사 요청' : '저장하기'}
              fields={[
                {
                  name: 'companyName',
                  label: '기업명',
                  required: true,
                  maxLength: 200,
                },
                {
                  name: 'businessNumber',
                  label: '사업자번호',
                  required: true,
                  maxLength: 10,
                },
                { name: 'ceoName', label: '대표자명', maxLength: 100 },
                {
                  name: 'industry', label: '업종', maxLength: 100,
                  render: ({ id, values, setValues }) => <CompanyIndustryInput id={id} value={values.industry || ''}
                    onChange={(industry) => setValues((old) => ({ ...old, industry }))} />,
                },
                {
                  name: 'companySize', label: '기업 규모 (직원 수)',
                  options: data.companySize && !companySizes.includes(data.companySize)
                    ? [...companySizes, { value: data.companySize, label: `${data.companySize} (기존 입력값)` }]
                    : companySizes,
                },
                {
                  name: 'address',
                  label: '주소',
                  type: 'address',
                  maxLength: 255,
                },
                {
                  name: 'website',
                  label: '홈페이지',
                  type: 'url',
                  maxLength: 500,
                },
                { name: 'description', label: '기업 소개', type: 'textarea' },
              ]}
              onSubmit={async (body) => {
                await client.put('/company/profile', body);
                window.alert('기업 정보를 저장했습니다.');
                await resource.reload();
              }}
            />
            {data.approvalStatus === 'REJECTED' && (
              <Action url="/company/profile/review-request" onDone={resource.reload}>
                재심사 요청
              </Action>
            )}
          </div>
        )}
      </Resource>
      <div className="panel narrow"><AccountSettings company /></div>
    </>
  );
}