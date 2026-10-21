import { useId, useState } from 'react';
import { useResource } from '../../hooks/useResource';
import client, { message } from '../../api/client';
import { labels, Resource } from './UI';

const moves = {
  REQUESTED: ['REQUESTED', 'COUNSELING', 'CLOSED'],
  COUNSELING: ['COUNSELING', 'MATCHING', 'CLOSED'],
  MATCHING: ['MATCHING', 'INTERVIEW', 'CLOSED'],
  INTERVIEW: ['INTERVIEW', 'MATCHING', 'EMPLOYED', 'CLOSED'],
};

export default function EmploymentLinkForm({ record, onDone }) {
  const resource = useResource('/admin/employment-link-options');
  return <Resource resource={resource}>{(options) => <SelectionForm key={record.employmentLinkId + ':' + record.status} record={record} options={options} onDone={onDone} />}</Resource>;
}

function SelectionForm({ record, options, onDone }) {
  const id = useId();
  const [values, setValues] = useState(record);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const jobs = options.jobs.filter((j) => String(j.companyId) === String(values.companyId));
  const needsMatch = ['MATCHING', 'INTERVIEW', 'EMPLOYED'].includes(values.status);
  function field(name, value) { setValues((old) => ({ ...old, [name]: value })); setSuccess(''); }
  async function save(event) {
    event.preventDefault();
    if (busy) return;
    setBusy(true); setError(''); setSuccess('');
    try {
      await client.patch('/admin/employment-links/' + record.employmentLinkId, values);
      setSuccess('취업연계 정보를 저장했습니다.');
      window.alert('취업연계 정보 저장이 완료되었습니다.');
      await onDone();
    } catch (ex) { setError(message(ex)); }
    finally { setBusy(false); }
  }
  function unavailable(rows, key, value) {
    return value && !rows.some((row) => String(row[key]) === String(value));
  }
  return (
    <form className="form" onSubmit={save}>
      <p className="callout">담당자와 기업을 이름으로 선택해 주세요. 기업을 선택하면 해당 기업의 공고를 선택할 수 있습니다.</p>
      <div className="form-field">
        <label htmlFor={id + '-status'}>진행 상태 *</label>
        <select id={id + '-status'} value={values.status} required disabled={busy} onChange={(e) => field('status', e.target.value)}>
          {(moves[record.status] || [record.status]).map((status) => <option key={status} value={status}>{labels[status] || status}</option>)}
        </select>
      </div>
      <div className="form-field">
        <label htmlFor={id + '-manager'}>담당 관리자{needsMatch ? ' *' : ''}</label>
        <select id={id + '-manager'} value={values.managerUserId || ''} required={needsMatch} disabled={busy} onChange={(e) => field('managerUserId', e.target.value)}>
          <option value="">담당 관리자 선택</option>
          {unavailable(options.managers, 'userId', values.managerUserId) && <option value={values.managerUserId} disabled>기존 담당자 · 사용 불가 (다시 선택해 주세요)</option>}
          {options.managers.map((m) => <option key={m.userId} value={m.userId}>{m.name} ({m.loginId})</option>)}
        </select>
      </div>
      <div className="form-field">
        <label htmlFor={id + '-company'}>연계 기업{needsMatch ? ' *' : ''}</label>
        <select id={id + '-company'} value={values.companyId || ''} required={needsMatch} disabled={busy} onChange={(e) => { setValues((old) => ({ ...old, companyId: e.target.value, jobId: '' })); setSuccess(''); }}>
          <option value="">연계 기업 선택</option>
          {unavailable(options.companies, 'companyId', values.companyId) && <option value={values.companyId} disabled>기존 기업 · 사용 불가 (다시 선택해 주세요)</option>}
          {options.companies.map((c) => <option key={c.companyId} value={c.companyId}>{c.companyName} ({c.businessNumber})</option>)}
        </select>
      </div>
      <div className="form-field">
        <label htmlFor={id + '-job'}>연계 공고 (선택)</label>
        <select id={id + '-job'} value={values.jobId || ''} disabled={busy || !values.companyId} onChange={(e) => field('jobId', e.target.value)}>
          <option value="">{!values.companyId ? '기업을 먼저 선택해 주세요' : jobs.length ? '공고 선택 안 함' : '해당 기업의 등록된 공고가 없습니다'}</option>
          {unavailable(jobs, 'jobId', values.jobId) && <option value={values.jobId} disabled>기존 공고 · 사용 불가 (다시 선택해 주세요)</option>}
          {jobs.map((j) => <option key={j.jobId} value={j.jobId}>{j.title} · {labels[j.status] || j.status}</option>)}
        </select>
      </div>
      <div className="form-field">
        <label htmlFor={id + '-public'}>구직자 공개 안내</label>
        <textarea id={id + '-public'} maxLength={10000} value={values.publicMessage || ''} disabled={busy} onChange={(e) => field('publicMessage', e.target.value)} />
      </div>
      <div className="form-field">
        <label htmlFor={id + '-memo'}>관리자 내부 메모 (비공개)</label>
        <textarea id={id + '-memo'} maxLength={10000} value={values.memo || ''} disabled={busy} onChange={(e) => field('memo', e.target.value)} />
      </div>
      {error && <p className="error" role="alert">{error}</p>}
      {success && <p className="form-success" role="status">{success}</p>}
      <div className="form-actions"><button type="submit" disabled={busy}>{busy ? '저장 중…' : '저장하기'}</button></div>
    </form>
  );
}
