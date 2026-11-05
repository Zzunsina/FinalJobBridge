import { formatDateTime } from "../../utils/dateTime";
import { useId, useState } from 'react';
import { useResource } from '../../hooks/useResource';
import client, { message } from '../../api/client';
import { Action, labels, Resource } from './UI';
import { employmentReviewLabels } from '../../utils/employmentReview';

const moves = {
  REQUESTED: ['MATCHING', 'CLOSED'],
  COUNSELING: ['MATCHING', 'CLOSED'],
  MATCHING: ['MATCHING', 'CLOSED'],
  INTERVIEW: ['INTERVIEW', 'MATCHING', 'CLOSED'],
};

export default function EmploymentLinkForm({ record, onDone }) {
  const resource = useResource(
    `/admin/employment-link-options?employmentLinkId=${record.employmentLinkId}`
  );
  const history = useResource(`/admin/employment-links/${record.employmentLinkId}/referrals`, { refreshInterval: 30000 });
  return <Resource resource={resource}>{(options) => <Resource resource={history}>{(rows) => <SelectionForm key={record.employmentLinkId + ':' + record.status + ':' + (rows[0]?.referralId || '') + ':' + (rows[0]?.reviewStatus || '')} record={record} referral={rows.find((row) => row.referralId)} options={options} onDone={async (values) => { await onDone(values); await history.reload(); }} />}</Resource>}</Resource>;
}

function SelectionForm({ record, referral, options, onDone }) {
  const id = useId();
  const [values, setValues] = useState({ ...record, status: record.status === 'REQUESTED' ? '' : record.status === 'COUNSELING' ? 'MATCHING' : record.status });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const active = referral && !['DECLINED', 'CANCELED'].includes(referral.reviewStatus);
  const canInterview = record.status === 'INTERVIEW';
  const jobs = options.jobs.filter((j) => String(j.companyId) === String(values.companyId));
  const needsMatch = ['MATCHING', 'INTERVIEW', 'EMPLOYED'].includes(values.status);
  function field(name, value) { setValues((old) => ({ ...old, [name]: value })); setSuccess(''); }
  async function save(event) {
    event.preventDefault();
    if (busy) return;
    setBusy(true); setError(''); setSuccess('');
    try {
      await client.patch('/admin/employment-links/' + record.employmentLinkId, { ...values, ...(values.interviewAt ? { interviewAt: values.interviewAt + ':00+09:00' } : {}) });
      setSuccess('취업연계 정보를 저장했습니다.');
      await onDone(values);
    } catch (ex) { setError(message(ex)); }
    finally { setBusy(false); }
  }
  function unavailable(rows, key, value) {
    return value && !rows.some((row) => String(row[key]) === String(value));
  }
  return (
    <form className="form" onSubmit={save}>
      <p className="callout">기업·공고를 선택하고 먼저 저장해 주세요. ‘추천 전달’을 눌러야 기업이 추천과 전달 당시 이력서를 확인할 수 있습니다.</p>
      <p>기업 응답: <strong>{referral ? employmentReviewLabels[referral.reviewStatus] : '추천 전달 전'}</strong></p>
      {referral?.proposalId && <div className="callout"><strong>기업 면접 제안 · {referral.proposalResponse === 'ACCEPTED' ? '사용자 수락 / 면접 확정' : referral.proposalResponse === 'CHANGE_REQUESTED' ? '일정 변경 요청' : referral.proposalResponse === 'DECLINED' ? '참여 어려움' : '사용자 응답 대기'}</strong><p>{formatDateTime(referral.proposedInterviewAt)} · {referral.proposedInterviewType === 'ONLINE' ? '온라인' : '오프라인'}</p><p className="prose">{referral.proposedLocation || referral.proposedMeetingUrl}</p><p className="prose">기업 안내: {referral.applicantGuidance || '없음'}</p><p className="prose">사용자 요청: {referral.proposalRequests || '없음'}</p><p>기업이 일정을 제안하고 사용자가 수락하면 자동 확정됩니다.</p></div>}
      <div className="form-field">
        <label htmlFor={id + '-status'}>진행 상태 *</label>
        <select id={id + '-status'} value={values.status} required disabled={busy} onChange={(e) => field('status', e.target.value)}>
          <option value="">진행 상태 선택</option>
          {(moves[record.status] || [record.status]).filter((status) => (status !== 'INTERVIEW' || canInterview || record.status === 'INTERVIEW') && (status !== 'EMPLOYED' || referral?.reviewStatus === 'HIRE_REQUESTED') && !(record.status === 'INTERVIEW' && status === 'MATCHING')).map((status) => <option key={status} value={status}>{status === 'INTERVIEW' ? '면접 일정 확정' : labels[status] || status}</option>)}
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
        <select id={id + '-company'} value={values.companyId || ''} required={needsMatch} disabled={busy || active} onChange={(e) => { setValues((old) => ({ ...old, companyId: e.target.value, jobId: '' })); setSuccess(''); }}>
          <option value="">연계 기업 선택</option>
          {unavailable(options.companies, 'companyId', values.companyId) && <option value={values.companyId} disabled>기존 기업 · 사용 불가 (다시 선택해 주세요)</option>}
          {options.companies.map((c) => <option key={c.companyId} value={c.companyId}>{c.companyName} ({c.businessNumber})</option>)}
        </select>
      </div>
      <div className="form-field">
        <label htmlFor={id + '-job'}>
          연계 공고 (선택 사항)
        </label>
        <select id={id + '-job'} value={values.jobId || ''} disabled={busy || active || !values.companyId} onChange={(e) => field('jobId', e.target.value)}>
          <option value="">
            {values.companyId
              ? '채용공고 없이 기업만 추천'
              : '기업을 먼저 선택해 주세요'}
          </option>
          {unavailable(jobs, 'jobId', values.jobId) && <option value={values.jobId} disabled>기존 공고 · 사용 불가 (다시 선택해 주세요)</option>}
          {jobs.map((j) => <option key={j.jobId} value={j.jobId}>{j.title} · {labels[j.status] || j.status}</option>)}
        </select>
      </div>
      {values.status === 'EMPLOYED' && <label><input type="checkbox" required checked={!!values.employmentConfirmed} onChange={(e) => field('employmentConfirmed', e.target.checked)} />사용자와 기업의 실제 입사를 확인했습니다.</label>}
      <div className="form-field">
        <label htmlFor={id + '-public'}>관리자 취업연계 안내 (추천·재매칭 안내)</label>
        <textarea id={id + '-public'} maxLength={10000} value={values.publicMessage || ''} disabled={busy} onChange={(e) => field('publicMessage', e.target.value)} />
      </div>
      <div className="form-field">
        <label htmlFor={id + '-memo'}>관리자 내부 메모 (비공개)</label>
        <textarea id={id + '-memo'} maxLength={10000} value={values.memo || ''} disabled={busy} onChange={(e) => field('memo', e.target.value)} />
      </div>
      {error && <p className="error" role="alert">{error}</p>}
      {success && <p className="form-success" role="status">{success}</p>}
      <div className="form-actions"><button type="submit" disabled={busy}>{busy ? '저장 중…' : '정보 저장'}</button></div>
      <div className="actions">
        {!active &&
          record.status === 'MATCHING' &&
          record.companyId &&
          record.managerUserId && (
            <Action
              url={`/admin/employment-links/${record.employmentLinkId}/referrals`}
              confirm="저장된 기업에 현재 공개 이력서를 추천 전달할까요? 채용공고를 선택하지 않은 경우에도 기업에 이력서를 전달합니다."
              onDone={() =>
                onDone({
                  ...record,
                  status: 'MATCHING',
                  completionMessage: '기업에 추천을 전달했습니다. 기업 검토를 기다려 주세요.',
                })
              }
            >
              저장된 기업에 추천 전달
            </Action>
          )}
        {(active || record.status === 'INTERVIEW') && <Action url={`/admin/employment-links/${record.employmentLinkId}/rematch`} confirm="현재 추천을 종료하고 다른 기업과 재매칭할까요? 이전 추천 이력은 보관됩니다." onDone={() => onDone({ ...record, status: 'MATCHING', completionMessage: '이전 추천을 종료했습니다. 새로운 기업과 공고를 선택해 저장한 뒤 추천을 전달해 주세요.' })}>현재 추천 종료 · 재매칭 시작</Action>}
      </div>
    </form>
  );
}
