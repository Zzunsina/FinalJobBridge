import { useId, useState } from 'react';
import { Link, Navigate } from 'react-router-dom';
import { useAuth } from '../../stores/auth';
import client, { message } from '../../api/client';
import { AddressInput, ConfirmedDateTime, AlphanumericInput } from './InputTools';
import BookmarkButton from './BookmarkButton';
import EmailInput from './EmailInput';

export const labels = {
  ACTIVE: '활성',
  SUSPENDED: '정지',
  WITHDRAWN: '탈퇴',
  JOB_SEEKER: '구직자',
  COMPANY: '기업',
  ADMIN: '관리자',
  PENDING: '심사 중',
  UNREGISTERED: '기업 정보 미등록',
  APPROVED: '승인',
  REJECTED: '반려·불합격',
  DRAFT: '초안',
  PUBLISHED: '채용 중',
  CLOSED: '마감·종료',
  APPLIED: '지원 완료',
  UNDER_REVIEW: '서류 검토',
  DOCUMENT_PASSED: '서류 합격',
  INTERVIEW: '면접',
  FINAL_PASSED: '최종 합격',
  CANCELED: '취소',
  REQUESTED: '신청 접수',
  COUNSELING: '상담 중',
  MATCHING: '매칭 중',
  EMPLOYED: '취업 완료',
  OPEN: '답변 대기',
  ANSWERED: '답변 완료',
  SCHEDULED: '예정',
  COMPLETED: '완료',
  ONLINE: '온라인',
  OFFLINE: '오프라인',
};

export function Badge({ value }) {
  return <span className={`badge badge-${value}`}>{labels[value] || value}</span>;
}
export function Title({ eyebrow, children, description, action }) {
  return (
    <div className="page-title">
      <div>
        {eyebrow && <p className="eyebrow">{eyebrow}</p>}
        <h1>{children}</h1>
        {description && <p>{description}</p>}
      </div>
      {action}
    </div>
  );
}
export function Resource({ resource, children }) {
  if (resource.loading)
    return (
      <div className="empty" role="status">
        정보를 불러오는 중입니다…
      </div>
    );
  if (resource.error)
    return (
      <div className="empty error" role="alert">
        <p>{resource.error}</p>
        <button onClick={resource.reload}>다시 시도</button>
      </div>
    );
  return children(resource.data);
}
export function Pagination({ page, totalPages, total, onChange }) {
  if (total < 10) return null;
  const start = Math.max(0, Math.min(page - 2, totalPages - 5));
  return (
    <nav className="pagination" aria-label="페이지 이동">
      <button
        type="button"
        className="secondary"
        disabled={page <= 0}
        onClick={() => onChange(page - 1)}
      >
        이전
      </button>
      {Array.from({ length: Math.min(5, totalPages) }, (_, i) => start + i).map((p) => (
        <button
          type="button"
          key={p}
          className={p === page ? '' : 'secondary'}
          aria-current={p === page ? 'page' : undefined}
          onClick={() => onChange(p)}
        >
          {p + 1}
        </button>
      ))}
      <span>
        {page + 1} / {Math.max(totalPages, 1)}
      </span>
      <button
        type="button"
        className="secondary"
        disabled={page >= totalPages - 1}
        onClick={() => onChange(page + 1)}
      >
        다음
      </button>
    </nav>
  );
}
export function PagedRows({ rows, children, focusId, idKey }) {
  const focusIndex =
    focusId && idKey ? rows.findIndex((r) => String(r[idKey]) === String(focusId)) : -1;
  const [page, setPage] = useState(focusIndex >= 0 ? Math.floor(focusIndex / 10) : 0);
  const lastPage = Math.max(0, Math.ceil(rows.length / 10) - 1);
  const current = Math.min(page, lastPage);
  return (
    <>
      {children(rows.slice(current * 10, current * 10 + 10))}
      <Pagination page={current} totalPages={lastPage + 1} total={rows.length} onChange={setPage} />
    </>
  );
}
export function PagedResource({ resource, children, ...rest }) {
  return (
    <Resource resource={resource}>
      {(rows) => (
        <PagedRows rows={rows} {...rest}>
          {children}
        </PagedRows>
      )}
    </Resource>
  );
}
export function Empty({ children = '아직 등록된 내역이 없습니다.', to, action }) {
  return (
    <div className="empty">
      <span className="empty-icon">＋</span>
      <h3>{children}</h3>
      <p>새로운 기회를 위한 첫걸음을 시작해 보세요.</p>
      {to && (
        <Link className="button" to={to}>
          {action}
        </Link>
      )}
    </div>
  );
}
export function Guard({ role, children }) {
  const user = useAuth((s) => s.user);
  if (!user) return <Navigate to="/login" replace />;
  if (role && user.role !== role) return <Empty>접근 권한이 없는 화면입니다.</Empty>;
  return children;
}
export function Action({
  url,
  method = 'post',
  body = {},
  children,
  onDone,
  confirm,
  className = 'secondary',
}) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  async function run() {
    if (confirm && !window.confirm(confirm)) return;
    setBusy(true);
    setError('');
    try {
      await client.request({ url, method, data: body });
      await onDone?.();
    } catch (error) {
      setError(message(error));
    } finally {
      setBusy(false);
    }
  }
  return (
    <span className="action">
      <button className={className} disabled={busy} onClick={run}>
        {busy ? '처리 중…' : children}
      </button>
      {error && (
        <span className="error" role="alert">
          {error}
        </span>
      )}
    </span>
  );
}

export function Form({
  fields,
  initial = {},
  onSubmit,
  submit = '저장하기',
  children,
  resetOnSuccess = false,
  successMessage = '저장되었습니다.',
}) {
  const formId = useId();
  const [values, setValues] = useState(initial);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  async function save(event) {
    event.preventDefault();
    setBusy(true);
    setError('');
    setSuccess('');
    try {
      await onSubmit(values);
      if (resetOnSuccess) setValues({});
      setSuccess(successMessage);
    } catch (error) {
      setError(message(error));
    } finally {
      setBusy(false);
    }
  }
  return (
    <form className="form" onSubmit={save}>
      {fields.map(
        ({
          name,
          label,
          type = 'text',
          options,
          required = false,
          maxLength = 10000,
          sanitize,
          visibleWhen,
          ...rest
        }) =>
          (!visibleWhen || visibleWhen(values)) && (
            <div key={name} className={type === 'checkbox' ? 'checkbox' : 'form-field'}>
              <label htmlFor={`${formId}-${name}`}>
                {label}
                {required && <small> *</small>}
              </label>
              {type === 'address' ? (
                <AddressInput
                  id={`${formId}-${name}`}
                  name={name}
                  value={values[name] || ''}
                  required={required}
                  maxLength={maxLength}
                  onChange={(value) => setValues((old) => ({ ...old, [name]: value }))}
                />
              ) : type === 'confirmed-datetime' ? (
                <ConfirmedDateTime
                  id={`${formId}-${name}`}
                  name={name}
                  value={values[name] || ''}
                  required={required}
                  onChange={(value) => setValues((old) => ({ ...old, [name]: value }))}
                />
              ) : type === 'email-select' ? (
                <EmailInput id={formId + '-' + name} name={name} value={values[name] || ''} required={required} onChange={(value) => setValues((old) => ({ ...old, [name]: value }))} />
              ) : sanitize === 'alphanumeric' ? (
                <AlphanumericInput
                  {...rest}
                  id={formId + '-' + name}
                  name={name}
                  type={type}
                  value={values[name] ?? ''}
                  required={required}
                  maxLength={maxLength}
                  onChange={(value) => setValues((old) => ({ ...old, [name]: value }))}
                />
              ) : type === 'textarea' ? (
                <textarea
                  id={`${formId}-${name}`}
                  name={name}
                  value={values[name] || ''}
                  onChange={(e) => setValues({ ...values, [name]: e.target.value })}
                  required={required}
                  maxLength={maxLength}
                  {...rest}
                />
              ) : options ? (
                <select
                  id={`${formId}-${name}`}
                  name={name}
                  value={values[name] || ''}
                  onChange={(e) => setValues({ ...values, [name]: e.target.value })}
                  required={required}
                >
                  <option value="">선택해 주세요</option>
                  {options.map((o) => (
                    <option key={o.value ?? o} value={o.value ?? o}>
                      {o.label ?? labels[o] ?? o}
                    </option>
                  ))}
                </select>
              ) : (
                <input
                  id={`${formId}-${name}`}
                  name={name}
                  type={type}
                  value={type === 'checkbox' ? undefined : (values[name] ?? '')}
                  checked={type === 'checkbox' ? Boolean(values[name]) : undefined}
                  onChange={(e) =>
                    setValues({
                      ...values,
                      [name]:
                        type === 'checkbox'
                          ? e.target.checked
                          : sanitize === 'alphanumeric'
                            ? e.target.value.replace(/[^A-Za-z0-9]/g, '')
                            : sanitize === 'digits'
                              ? e.target.value.replace(/[^0-9]/g, '')
                              : e.target.value,
                    })
                  }
                  required={required}
                  maxLength={maxLength}
                  {...rest}
                />
              )}
            </div>
          ),
      )}
      {children}
      {success && (
        <p className="form-success" role="status">
          {success}
        </p>
      )}
      {error && (
        <div className="error" role="alert">
          {error}
        </div>
      )}
      <div className="form-actions">
        <button type="submit" disabled={busy}>
          {busy ? '처리 중…' : submit}
        </button>
      </div>
    </form>
  );
}

export function JobCard({ job, onBookmarkChange }) {
  const days = Math.ceil((Date.parse(job.endDate + 'T23:59:59+09:00') - Date.now()) / 86400000);
  return (
    <article className="job-card">
      <div className="card-top">
        <span className="company-symbol">{job.companyName?.slice(0, 1)}</span>
        <div className="card-top-actions">
          <span className="deadline">
            {days < 0 ? '마감' : days === 0 ? '오늘 마감' : `D-${days}`}
          </span>
          <BookmarkButton jobId={job.jobId} compact onChanged={onBookmarkChange} />
        </div>
      </div>
      <p className="company-name">{job.companyName}</p>
      <h3>
        <Link to={`/jobs/${job.jobId}`}>{job.title}</Link>
      </h3>
      <p className="muted">
        {job.workLocation} <span>·</span> {job.careerType}
      </p>
      <div className="tags">
        <span>{job.jobType}</span>
        <span>{job.education || '학력 무관'}</span>
      </div>
      <div className="card-bottom">
        <span>{job.endDate} 마감</span>
        <Link to={`/jobs/${job.jobId}`} aria-label={`${job.title} 자세히 보기`}>
          자세히 보기 ↗
        </Link>
      </div>
    </article>
  );
}
