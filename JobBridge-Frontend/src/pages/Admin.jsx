import { formatDateTime, formatTime } from "../utils/dateTime";
import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useResource } from '../hooks/useResource';
import client from '../api/client';
import EmploymentLinkForm from '../components/common/EmploymentLinkForm';
import CompanyImage from '../components/common/CompanyImage';
import { Action, Badge, Form, PagedResource, Title, labels } from '../components/common/UI';
import { filterAdminRows, recordStatus, searchHints } from '../utils/adminManagement';
import { employmentReviewLabels } from '../utils/employmentReview';
import { Resource } from '../components/common/UI';

export { default as AdminDashboard } from './AdminDashboard';

const titles = {
  users: '회원 관리',
  companies: '기업 심사',
  jobs: '공고 심사',
  'employment-links': '취업연계 관리',
  notices: '공지 관리',
  inquiries: '문의 관리',
};
export function AdminManage({ resourceName }) {
  return <AdminManageContent key={resourceName} resourceName={resourceName} />;
}

function AdminManageContent({ resourceName }) {
  const resource = useResource(`/admin/${resourceName}`, { refreshInterval: 10000 });
  const [query, setQuery] = useState('');
  const [status, setStatus] = useState('');
  const [memberRole, setMemberRole] = useState(null);
  const allRows = resource.data || [];
  const [params] = useSearchParams();
  const focus = params.get('focus');
  const focusedRole = allRows.find((row) => String(row.userId) === focus)?.role;
  const selectedRole = resourceName === 'users' ? (memberRole ?? focusedRole ?? 'JOB_SEEKER') : '';
  const typeRows = resourceName === 'users' && selectedRole ? allRows.filter((row) => row.role === selectedRole) : allRows;
  const filteredRows = filterAdminRows(allRows, resourceName, query, status, selectedRole);
  const statuses = [...new Set(typeRows.map((row) => recordStatus(resourceName, row)))].filter(
    Boolean,
  );
  const filtering = Boolean(query.trim() || status);
  const key = {
    users: 'userId',
    companies: 'companyId',
    jobs: 'jobId',
    'employment-links': 'employmentLinkId',
    notices: 'noticeId',
    inquiries: 'inquiryId',
  }[resourceName];
  useEffect(() => {
    if (!resource.loading && resource.data && focus)
      document
        .getElementById(`admin-record-${focus}`)
        ?.scrollIntoView({ behavior: 'smooth', block: 'center' });
  }, [resource.loading, resource.data, focus]);
  return (
    <div className="admin-management">
      <Title
        eyebrow="ADMIN CENTER"
        description="목록에서 항목을 선택해 상세 내용을 확인하고 처리하세요."
      >
        {titles[resourceName]}
      </Title>
      {resourceName === 'notices' && (
        <details className="panel admin-notice-compose">
          <summary>＋ 새 공지 작성</summary>
          <NoticeForm onDone={resource.reload} />
        </details>
      )}
      {resourceName === 'jobs' && <p className="callout">심사 중 → 초안 → 반려 → 게시 중 → 마감 순으로 표시합니다. 같은 상태에서는 최근 등록 공고가 먼저 표시됩니다.</p>}
      {resourceName === 'users' && (
        <div className="admin-member-tabs" role="group" aria-label="회원 유형">
          {[
            ['JOB_SEEKER', '개인회원'], ['COMPANY', '기업회원'], ['', '전체'],
            ...(allRows.some((row) => row.role === 'ADMIN') ? [['ADMIN', '관리자']] : []),
          ].map(([role, label]) => (
            <button key={role} type="button" className={selectedRole === role ? 'selected' : ''}
              aria-pressed={selectedRole === role}
              onClick={() => { setMemberRole(role); setStatus(''); }}>
              {label} <span>{(role ? allRows.filter((row) => row.role === role) : allRows).length}</span>
            </button>
          ))}
        </div>
      )}
      <div className="admin-list-toolbar">
        <div className="admin-search-field">
          <label htmlFor="admin-search">목록 검색</label>
          <div className="admin-search-input">
            <svg
              aria-hidden="true"
              width="18"
              height="18"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="1.7"
            >
              <circle cx="10.5" cy="10.5" r="6.5" />
              <path d="m16 16 5 5" />
            </svg>
            <input
              id="admin-search"
              type="search"
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              placeholder={searchHints[resourceName]}
            />
          </div>
        </div>
        <div className="admin-status-field">
          <label htmlFor="admin-status">상태</label>
          <select
            id="admin-status"
            value={status}
            onChange={(event) => setStatus(event.target.value)}
          >
            <option value="">전체 상태</option>
            {statuses.map((value) => (
              <option key={value} value={value}>
                {labels[value] || value}
              </option>
            ))}
          </select>
        </div>
        <button
          className="secondary admin-filter-reset"
          disabled={!query && !status}
          onClick={() => {
            setQuery('');
            setStatus('');
          }}
        >
          초기화
        </button>
      </div>
      {!resource.loading && !resource.error && (
        <div className="admin-list-caption" role="status" aria-live="polite">
          <span>
            {filtering ? '검색 결과' : (resourceName === 'users' ? ({ JOB_SEEKER: '개인회원', COMPANY: '기업회원', ADMIN: '관리자' }[selectedRole] || '전체') : '전체')}{' '}
            <strong>{filteredRows.length.toLocaleString('ko-KR')}</strong>건
            {filtering && ` / 전체 ${typeRows.length.toLocaleString('ko-KR')}건`}
          </span>
          <span>항목을 누르면 상세 내용이 펼쳐집니다</span>
        </div>
      )}
      <PagedResource
        resource={{ ...resource, data: resource.data == null ? null : filteredRows }}
        focusId={focus}
        idKey={key}
        key={`${resourceName}:${selectedRole}:${query}:${status}`}
      >
        {(rows) =>
          rows.length ? (
            <div className="admin-compact-list">
              {rows.map((r, i) => (
                <details
                  className={
                    String(r[key]) === focus
                      ? 'admin-compact-record admin-focused-record'
                      : 'admin-compact-record'
                  }
                  id={`admin-record-${r[key]}`}
                  key={r[key] ?? i}
                  open={String(r[key]) === focus || undefined}
                >
                  <summary>
                    <span className="admin-record-heading">
                      <strong>
                        {resourceName === 'employment-links'
                          ? `취업연계(${r.jobSeekerName || '이름 미등록'})`
                          : resourceName === 'jobs'
                          ? r.title
                          : r.name || r.companyName || r.title || `취업연계 #${r.employmentLinkId}`}
                      </strong>
                      <small>
                        {resourceName === 'inquiries' ? `작성자: ${r.authorName || '이름 미등록'} · ${r.createdAt?.slice(0, 10) || ''}` : r.email ||
                          (resourceName === 'jobs' ? r.companyName : r.industry) ||
                          r.createdAt?.slice(0, 10) ||
                          r.requestedAt?.slice(0, 10) ||
                          '상세 정보 확인'}
                      </small>
                    </span>
                    <Badge value={recordStatus(resourceName, r)} />
                    <span className="admin-record-toggle" aria-hidden="true" />
                  </summary>
                  <div className="admin-record-body">
                    <AdminRow name={resourceName} row={r} reload={resource.reload} />
                  </div>
                </details>
              ))}
            </div>
          ) : (
            <div className="admin-list-empty">
              <strong>{filtering ? '검색 결과가 없습니다.' : '등록된 내역이 없습니다.'}</strong>
              <p>
                {filtering
                  ? '검색어 또는 상태 조건을 변경해 주세요.'
                  : '새로운 내역이 등록되면 이곳에 표시됩니다.'}
              </p>
              {filtering && (
                <button
                  className="secondary"
                  onClick={() => {
                    setQuery('');
                    setStatus('');
                  }}
                >
                  전체 목록 보기
                </button>
              )}
            </div>
          )
        }
      </PagedResource>
    </div>
  );
}

function AdminRow({ name, row: r, reload }) {
  const [editing, setEditing] = useState(false);
  const [employmentSuccess, setEmploymentSuccess] = useState('');
  if (name === 'users')
    return (
      <>
        <div className="row">
          <div>
            <h2>{r.name}</h2>
            <p>
              {r.email} · {r.role}
            </p>
          </div>
          <Badge value={r.status} />
        </div>
        {r.status === 'SUSPENDED' && <p className="callout" style={{ whiteSpace:'pre-line' }}>정지 사유: {r.suspensionReason || '기존 정지 건으로 사유가 등록되지 않았습니다.'}</p>}
        {r.role !== 'ADMIN' && r.status === 'ACTIVE' && <details className="admin-suspend-form"><summary>회원 정지</summary><p className="muted">입력한 사유는 해당 회원의 로그인 화면에 표시됩니다.</p><Form fields={[{ name:'reason', label:'정지 사유 (회원에게 표시됩니다)', type:'textarea', required:true, maxLength:1000 }]} submit="사유 저장 후 회원 정지" onSubmit={async(body)=>{ await client.patch('/admin/users/'+r.userId+'/status',{ status:'SUSPENDED', reason:body.reason }); await reload(); }} /></details>}
        {r.role !== 'ADMIN' && r.status === 'SUSPENDED' && <Action method="patch" url={'/admin/users/'+r.userId+'/status'} body={{ status:'ACTIVE' }} onDone={reload} confirm="회원 정지를 해제할까요?">정지 해제</Action>}
      </>
    );
  if (name === 'companies' || name === 'jobs') {
    const company = name === 'companies';
    const status = company ? r.approvalStatus : r.status;
    const url = company
      ? `/admin/companies/${r.companyId}/approval`
      : `/admin/jobs/${r.jobId}/status`;
    return (
      <>
        <div className="row">
          <div className="admin-review-company">
            <CompanyImage
              companyId={r.companyId}
              companyName={r.companyName}
              admin
              className="company-symbol admin-review-image"
            />
            <div>
              <p className="muted">기업 대표 이미지</p>
              <h2>
                {company ? (
                  <Link className="text-link" to={`/companies/${r.companyId}`}>
                    {r.companyName}
                  </Link>
                ) : r.title}
              </h2>
              {!company && (
                <Link className="text-link" to={`/companies/${r.companyId}`}>
                  {r.companyName}
                </Link>
              )}
            </div>
          </div>
          <Badge value={status} />
        </div>
        <p className="prose">{r.description || '소개 미입력'}</p>
        {company ? (
          <p>
            사업자번호: {r.businessNumber} · 업종: {r.industry}
          </p>
        ) : (
          <p>
            {r.workLocation} · {r.startDate} ~ {r.endDate}
          </p>
        )}
        {!company && status === 'DRAFT' && (
          <p className="callout" role="status">기업이 저장한 초안으로, 아직 심사 요청을 하지 않은 공고입니다. 기업 계정의 ‘채용공고 관리’에서 ‘심사 요청’을 누르면 관리자가 게시 승인 또는 반려할 수 있습니다.</p>
        )}
        {!company && status === 'REJECTED' && (
          <p className="callout">반려된 공고입니다. 기업이 내용을 수정하고 다시 심사를 요청해야 합니다.{r.reviewReason && <> 반려 사유: {r.reviewReason}</>}</p>
        )}
        {!company && status === 'PENDING' && (
          <div className="stack">
            <p className="callout">기업의 심사 요청이 접수되었습니다. 내용을 확인한 후 게시 승인하거나, 사유를 입력해 반려해 주세요.</p>
            <div className="admin-job-approval-bar"><p>승인하면 채용공고가 바로 게시됩니다.</p><Action className="admin-job-approve-button" method="patch" url={url} body={{ status: 'PUBLISHED' }} onDone={reload} confirm="이 공고를 게시 승인할까요? 승인하면 채용공고에 공개됩니다."><span aria-hidden="true">✓</span> 승인하고 게시하기</Action></div>
            <Form
              fields={[{ name: 'reason', label: '반려 사유', type: 'textarea', required: true }]}
              onSubmit={async (body) => { await client.patch(url, { ...body, status: 'REJECTED' }); await reload(); }}
              submit="심사 반려"
            />
          </div>
        )}
        {company && status === 'PENDING' && (
          <Form
            fields={[
              {
                name: 'status',
                label: '심사 결과',
                required: true,
                options: company ? ['APPROVED', 'REJECTED'] : ['PUBLISHED', 'REJECTED'],
              },
              {
                name: 'reason',
                label: '심사 사유 (반려 시 필수)',
                type: 'textarea',
              },
            ]}
            onSubmit={async (body) => {
              await client.patch(url, body);
              await reload();
            }}
            submit="심사 결과 저장"
          />
        )}
        {!company && status === 'PUBLISHED' && (
          <Action method="patch" url={url} body={{ status: 'CLOSED' }} onDone={reload}>
            공고 마감
          </Action>
        )}
        {!company && (
          <details className="admin-job-delete">
            <summary>공고 삭제</summary>
            <p>삭제 사유가 기업에 알림으로 전달됩니다. 지원·취업연계 이력이 있는 공고는 삭제할 수 없습니다.</p>
            <Form
              fields={[{name:'reason',label:'삭제 사유',type:'textarea',required:true,maxLength:1000}]}
              onSubmit={async body=>{await client.delete(`/admin/jobs/${r.jobId}`,{data:body});await reload();}}
              submit="사유 전달 후 공고 삭제"
              successMessage="공고를 삭제하고 기업에 사유를 알렸습니다."
            />
          </details>
        )}
      </>
    );
  }
  if (name === 'inquiries')
    return (
      <>
        <div className="row">
          <h2>{r.title}</h2>
          <Badge value={r.status} />
        </div>
        <p className="muted">작성자: {r.authorName || '이름 미등록'} · 작성일: {r.createdAt?.slice(0, 10)}</p>
        <p className="prose">{r.content}</p>
        {r.answer && !editing ? (
          <>
            <div className="callout">
              <strong>운영자 답변</strong>
              <p className="prose">{r.answer}</p>
            </div>
            <button className="secondary" onClick={() => setEditing(true)}>
              답변 수정
            </button>
          </>
        ) : (
          <Form
            initial={r}
            fields={[
              {
                name: 'answer',
                label: '운영자 답변',
                type: 'textarea',
                required: true,
              },
            ]}
            onSubmit={async (body) => {
              await client.put(`/admin/inquiries/${r.inquiryId}/answer`, body);
              setEditing(false);
              await reload();
            }}
            submit="답변 등록"
          />
        )}
      </>
    );
  if (name === 'notices')
    return (
      <>
        <h2>{r.title}</h2>
        <Badge value={r.isPublished ? '게시 중' : '미게시'} />
        <p className="prose">{r.content}</p>
        <div className="actions">
          <button className="secondary" onClick={() => setEditing(!editing)}>
            {editing ? '수정 취소' : '수정'}
          </button>
          <Action
            method="delete"
            url={`/admin/notices/${r.noticeId}`}
            onDone={reload}
            confirm="이 공지를 삭제할까요?"
          >
            삭제
          </Action>
        </div>
        {editing && (
          <NoticeForm
            initial={r}
            onDone={async () => {
              setEditing(false);
              await reload();
            }}
          />
        )}
      </>
    );
  return (
    <>
      <div className="row">
        <h2>취업연계({r.jobSeekerName || '이름 미등록'})</h2>
        <Badge value={recordStatus('employment-links', r)} />
      </div>
      <p>
        구직자: {r.jobSeekerName || '이름 미등록'} · 신청 {r.requestedAt?.slice(0, 10)}
      </p>
      <p>희망 업종: {r.preferredIndustry || '기존 신청 · 미입력'} · 선호 직업: {r.preferredOccupation || '기존 신청 · 미입력'}</p>
      {employmentSuccess && <div className="form-success" role="status">{employmentSuccess}</div>}
      {!['EMPLOYED', 'CLOSED'].includes(r.status) && (
        <EmploymentLinkForm record={r} onDone={async (values) => {
          const statusNames = { REQUESTED: '신청 접수', COUNSELING: '기업 매칭', MATCHING: '기업 매칭', INTERVIEW: '면접 진행', EMPLOYED: '취업 완료', CLOSED: '종료' };
          const time = formatTime();
          setEmploymentSuccess(values.completionMessage || (r.status === values.status
            ? `취업연계 정보를 저장했습니다. 현재 상태: ${statusNames[values.status] || values.status} · ${time}`
            : `진행 상태가 변경되었습니다: ${statusNames[r.status] || r.status} → ${statusNames[values.status] || values.status} · ${time}`));
          await reload();
        }} />
      )}
      {!['EMPLOYED', 'CLOSED'].includes(r.status) && <details className="admin-employment-cancel"><summary><span aria-hidden="true">⊗</span> 취업연계 신청 취소 <span className="admin-cancel-arrow" aria-hidden="true">⌄</span></summary><p>전체 취업연계를 종료합니다. 입력한 사유는 사용자에게 공개되며, 진행 중인 기업 추천도 종료됩니다.</p><Form fields={[{ name: 'reason', label: '취소 사유 (사용자에게 공개)', type: 'textarea', required: true, maxLength: 1000 }]} submit="취소 확정" onSubmit={async (body) => {
        await client.post(`/admin/employment-links/${r.employmentLinkId}/cancel`, body);
        setEmploymentSuccess('취업연계를 취소했습니다. 사용자에게 취소 사유를 안내했습니다.');
        await reload();
      }} /></details>}
      <CompanyReview id={r.employmentLinkId} />
    </>
  );
}

function CompanyReview({ id }) {
  const resource = useResource(`/admin/employment-links/${id}/company-review`, { refreshInterval: 30000 });
  return <section className="callout"><h3>추천 전달 및 기업 응답 이력</h3><Resource resource={resource}>{(rows) => rows.length ? rows.map((row) => <div key={row.referralId || `legacy-${row.companyId}`}><strong>{row.companyName} · {employmentReviewLabels[row.reviewStatus] || row.reviewStatus}{!row.referralId && ' (이전 방식의 검토 의견)'}</strong><p className="prose">{row.opinion || '별도 의견 없음'}</p>{row.proposalId && <div><strong>기업 면접 제안 · {row.proposalResponse === 'ACCEPTED' ? '사용자 수락 / 면접 확정' : row.proposalResponse === 'CHANGE_REQUESTED' ? '일정 변경 요청' : row.proposalResponse === 'DECLINED' ? '참여 어려움' : '응답 대기'}</strong><p>{formatDateTime(row.proposedInterviewAt)} · {row.proposedLocation || row.proposedMeetingUrl}</p><p className="prose">기업 안내: {row.applicantGuidance || '없음'}</p><p className="prose">사용자 요청: {row.proposalRequests || '없음'}</p></div>}{row.startAt && <div><strong>기업의 입사 안내</strong><p>{formatDateTime(row.startAt)} · {row.firstDayLocation}</p><p className="prose">준비물: {row.preparations || '없음'} · 연락처: {row.contact}</p><p>{row.joinedAt ? '사용자 실제 입사 확인 완료' : '사용자 입사 확인 대기'}</p></div>}{row.userMessage && <p className="callout prose">입사 관련 사용자 요청: {row.userMessage}</p>}<p>{row.jobTitle || '공고 미지정'}{row.interviewAt && <> · 면접 {formatDateTime(row.interviewAt)} · {row.interviewLocation}</>}</p><small>{formatDateTime(row.respondedAt || row.sentAt)}</small></div>) : <p>아직 추천을 전달하지 않았습니다.</p>}</Resource></section>;
}

function NoticeForm({ initial, onDone }) {
  return (
    <Form
      initial={initial || {}}
      resetOnSuccess={!initial}
      successMessage="공지를 저장했습니다."
      fields={[
        { name: 'title', label: '공지 제목', required: true, maxLength: 200 },
        {
          name: 'content',
          label: '공지 본문',
          type: 'textarea',
          required: true,
        },
        {
          name: 'image',
          label: '안내 이미지 (선택)',
          type: 'notice-image',
          previewUrl: initial?.hasImage ? '/api/admin/notices/' + initial.noticeId + '/image' : '',
        },
        { name: 'isPinned', label: '중요 공지 · 목록 상단 고정', type: 'checkbox' },
        { name: 'isPublished', label: '공개 게시', type: 'checkbox' },
      ]}
      onSubmit={async (body) => {
        if (initial) await client.put(`/admin/notices/${initial.noticeId}`, body);
        else await client.post('/admin/notices', body);
        window.alert('공지를 저장했습니다.');
        await onDone();
      }}
    />
  );
}
