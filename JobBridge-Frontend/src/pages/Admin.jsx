import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useResource } from '../hooks/useResource';
import client from '../api/client';
import EmploymentLinkForm from '../components/common/EmploymentLinkForm';
import { Action, Badge, Empty, Form, PagedResource, Title } from '../components/common/UI';

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
  const resource = useResource(`/admin/${resourceName}`);
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
    <>
      <Title>{titles[resourceName]}</Title>
      {resourceName === 'notices' && (
        <div className="panel narrow">
          <h2>공지 작성</h2>
          <NoticeForm onDone={resource.reload} />
        </div>
      )}
      <PagedResource resource={resource} focusId={focus} idKey={key} key={resourceName}>
        {(rows) =>
          rows.length ? (
            <div className={`stack ${resourceName === 'users' ? 'admin-member-list' : ''}`}>
              {rows.map((r, i) => (
                <article
                  className={`panel ${resourceName === 'users' ? 'admin-member-card' : ''} ${String(r[key]) === focus ? 'admin-focused-record' : ''}`}
                  id={`admin-record-${r[key]}`}
                  key={r[key] ?? i}
                >
                  <AdminRow name={resourceName} row={r} reload={resource.reload} />
                </article>
              ))}
            </div>
          ) : (
            <Empty>처리할 내역이 없습니다.</Empty>
          )
        }
      </PagedResource>
    </>
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
          <h2>{company ? r.companyName : r.title}</h2>
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
        { name: 'isPublished', label: '공개 게시', type: 'checkbox' },
      ]}
      onSubmit={async (body) => {
        if (initial) await client.put(`/admin/notices/${initial.noticeId}`, body);
        else await client.post('/admin/notices', body);
        await onDone();
      }}
    />
  );
}
