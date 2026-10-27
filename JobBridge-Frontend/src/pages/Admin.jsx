import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useResource } from '../hooks/useResource';
import client from '../api/client';
import EmploymentLinkForm from '../components/common/EmploymentLinkForm';
import CompanyImage from '../components/common/CompanyImage';
import { Action, Badge, Form, PagedResource, Title, labels } from '../components/common/UI';
import { filterAdminRows, recordStatus, searchHints } from '../utils/adminManagement';

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
  const resource = useResource(`/admin/${resourceName}`);
  const [query, setQuery] = useState('');
  const [status, setStatus] = useState('');
  const allRows = resource.data || [];
  const filteredRows = filterAdminRows(allRows, resourceName, query, status);
  const statuses = [...new Set(allRows.map((row) => recordStatus(resourceName, row)))].filter(
    Boolean,
  );
  const filtering = Boolean(query.trim() || status);
  const [params] = useSearchParams();
  const focus = params.get('focus');
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
            {filtering ? '검색 결과' : '전체'}{' '}
            <strong>{filteredRows.length.toLocaleString('ko-KR')}</strong>건
            {filtering && ` / 전체 ${allRows.length.toLocaleString('ko-KR')}건`}
          </span>
          <span>항목을 누르면 상세 내용이 펼쳐집니다</span>
        </div>
      )}
      <PagedResource
        resource={{ ...resource, data: resource.data == null ? null : filteredRows }}
        focusId={focus}
        idKey={key}
        key={`${resourceName}:${query}:${status}`}
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
                        {resourceName === 'jobs'
                          ? r.title
                          : r.name || r.companyName || r.title || `취업연계 #${r.employmentLinkId}`}
                      </strong>
                      <small>
                        {r.email ||
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
        {r.role !== 'ADMIN' && r.status !== 'WITHDRAWN' && (
          <Action
            method="patch"
            url={`/admin/users/${r.userId}/status`}
            body={{ status: r.status === 'ACTIVE' ? 'SUSPENDED' : 'ACTIVE' }}
            onDone={reload}
            confirm="회원 상태를 변경할까요?"
          >
            {r.status === 'ACTIVE' ? '회원 정지' : '정지 해제'}
          </Action>
        )}
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
              <h2>{company ? r.companyName : r.title}</h2>
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
        {status === 'PENDING' && (
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
          <Action
            method="delete"
            url={`/admin/jobs/${r.jobId}`}
            onDone={reload}
            confirm="이 공고를 삭제할까요? 지원·연계 이력이 있는 공고는 삭제할 수 없습니다."
          >
            공고 삭제
          </Action>
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
        <h2>취업연계 #{r.employmentLinkId}</h2>
        <Badge value={r.status} />
      </div>
      <p>
        구직자 #{r.jobSeekerId} · 신청 {r.requestedAt?.slice(0, 10)}
      </p>
      {!['EMPLOYED', 'CLOSED'].includes(r.status) && (
        <EmploymentLinkForm record={r} onDone={reload} />
      )}
    </>
  );
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
