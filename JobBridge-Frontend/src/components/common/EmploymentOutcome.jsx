import { formatDateTime } from "../../utils/dateTime";
import { useState } from 'react';
import { Link } from 'react-router-dom';
import client, { message } from '../../api/client';
import { Form } from './UI';

const date = (value) => value ? formatDateTime(value) : '기업 안내 대기';
export default function EmploymentOutcome({ record, reload }) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  async function action(kind) {
    if (busy) return;
    if (kind === 'JOINED' && !window.confirm('실제로 출근하여 입사한 것이 맞습니까? 확인하면 취업 완료로 처리됩니다.')) return;
    setBusy(true); setError('');
    try { await client.post(`/employment-referrals/${record.referralId}/onboarding-actions`, { action: kind }); await reload(); }
    catch (ex) { setError(message(ex)); } finally { setBusy(false); }
  }
  const confirmed = !!record.joinedAt;
  const email = record.contact?.match(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i)?.[0];
  const phone = record.contact?.match(/0\d{1,2}[- ]?\d{3,4}[- ]?\d{4}/)?.[0];
  const contactLink = email ? 'mailto:' + email : phone ? 'tel:' + phone.replaceAll(/[- ]/g, '') : '#entry-contact';
  return <>
    <section className="panel employment-outcome"><p className="eyebrow">{confirmed ? '입사 확인 완료' : record.notJoinedAt ? '재매칭 안내' : '최종 합격'}</p><h2>{confirmed ? '취업을 축하합니다!' : record.notJoinedAt ? '입사하지 않음을 전달했습니다.' : '최종 합격을 축하합니다!'}</h2><p>{confirmed ? '사용자가 실제 입사를 확인하여 취업연계가 완료되었습니다.' : record.notJoinedAt ? '담당자가 다른 기업과 매칭을 도와드립니다.' : '기업이 채용 의사를 전달했습니다. 입사 안내를 확인해 주세요. 실제 입사 전에는 취업 완료로 처리되지 않습니다.'}</p>
      <dl className="employment-facts"><dt>기업</dt><dd>{record.outcomeCompanyName || record.companyName || '이전 매칭 기업'}</dd><dt>채용 직무</dt><dd>{record.outcomeJobTitle || record.jobTitle || '연결된 채용공고 확인'}</dd><dt>최종 합격일</dt><dd>{date(record.hiredAt)}</dd><dt>입사 예정일</dt><dd>{date(record.startAt)}</dd>{confirmed && <><dt>입사 확인일</dt><dd>{date(record.joinedAt)}</dd></>}</dl>
    </section>
    <section className="panel" id="entry-guidance"><h2>기업의 입사 안내</h2>{record.startAt ? <><dl className="employment-facts"><dt>첫 출근 일시</dt><dd>{date(record.startAt)} (한국 시간)</dd><dt>출근 장소</dt><dd className="prose">{record.firstDayLocation}</dd><dt>준비물</dt><dd className="prose">{record.preparations || '별도 준비물 안내 없음'}</dd><dt>담당자 연락처</dt><dd id="entry-contact" className="prose">{record.contact}</dd></dl><p className="prose">{record.instructions || '추가 안내 없음'}</p></> : <p className="callout">기업의 입사 안내 등록을 기다리고 있습니다. 입사 일정이나 조건이 궁금하면 기업 또는 담당 관리자에게 문의해 주세요.</p>}
      {record.acknowledgedAt && <p className="form-success" role="status">입사 안내 확인 완료 · {date(record.acknowledgedAt)}</p>}
      {error && <p className="error" role="alert">{error}</p>}
      {!record.notJoinedAt && record.onboardingReady && <div className="actions"><button disabled={busy || !record.startAt || !!record.acknowledgedAt} onClick={() => action('ACKNOWLEDGE')}>{record.acknowledgedAt ? '입사 안내 확인 완료' : '입사 안내 확인'}</button>{record.contact ? <a className="button secondary" href={contactLink}>기업에 문의</a> : <Link className="button secondary" to="/inquiries?view=inquiry">입사 안내 문의</Link>}{!confirmed && <button disabled={busy || !record.startAt || !record.acknowledgedAt || new Date(record.startAt) > new Date()} onClick={() => action('JOINED')}>실제 입사 확인</button>}</div>}
      {!confirmed && !record.notJoinedAt && <p className="muted">입사 안내를 확인한 뒤, 입사 예정 일시 이후 실제 출근한 경우에만 입사를 확인해 주세요.</p>}
      {record.onboardingReady && !record.notJoinedAt && <details className="employment-help"><summary>입사하지 않음 · 도움 요청</summary><p>도움 요청은 관리자 문의로 접수됩니다. 입사하지 않음을 선택하면 현재 추천을 종료하고 재매칭을 시작합니다.</p><Form fields={[
        { name: 'action', label: '요청 종류', required: true, options: [{ value: 'HELP', label: '입사 관련 도움 요청' }, ...(!confirmed ? [{ value: 'NOT_JOINED', label: '입사하지 않음 · 재매칭 요청' }] : [])] },
        { name: 'message', label: '사유 또는 도움이 필요한 내용', type: 'textarea', required: true, maxLength: 2000 },
      ]} onSubmit={async (body) => { await client.post(`/employment-referrals/${record.referralId}/onboarding-actions`, body); await reload(); }} submit="요청 전달" successMessage="요청을 전달했습니다." /></details>}
      {record.userMessage && <p className="callout prose">전달한 요청: {record.userMessage}</p>}
      <Link className="text-link" to="/inquiries?view=history">나의 문의 확인 →</Link>
    </section>
  </>;
}
