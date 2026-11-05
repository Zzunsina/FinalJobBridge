import { formatDateTime } from "../../utils/dateTime";
import client from '../../api/client';
import { Form } from './UI';

function localDate(value) {
  if (!value) return '';
  return new Date(new Date(value).getTime() + 9 * 60 * 60 * 1000).toISOString().slice(0, 16);
}
export default function CompanyOnboarding({ record, reload }) {
  return <section className="panel"><h2>최종 합격 · 입사 안내</h2><p>최종 합격 결과가 전달되었습니다. 첫 출근 안내를 등록해 주세요. 사용자가 실제 입사를 확인하면 취업 완료가 됩니다.</p>
    {record.joinedAt ? <p className="form-success">실제 입사 확인 완료 · {formatDateTime(record.joinedAt)}</p> : <Form detectChanges={Boolean(record.onboardingUpdatedAt)} key={record.onboardingUpdatedAt || 'new'} initial={{ startAt: localDate(record.startAt), firstDayLocation: record.firstDayLocation || '', preparations: record.preparations || '', contact: record.contact || '', instructions: record.instructions || '' }} fields={[
      { name: 'startAt', label: '첫 출근 일시 (한국 시간)', type: 'confirmed-datetime', required: true },
      { name: 'firstDayLocation', label: '첫 출근 장소 또는 원격 근무 접속 안내', required: true, maxLength: 500 },
      { name: 'preparations', label: '준비물·제출서류', type: 'textarea', maxLength: 10000 },
      { name: 'contact', label: '입사 담당자 연락처 (이메일·전화번호)', required: true, maxLength: 300 },
      { name: 'instructions', label: '추가 입사 안내', type: 'textarea', maxLength: 10000 },
    ]} onSubmit={async (body) => { await client.put(`/company/employment-referrals/${record.referralId}/onboarding`, { ...body, startAt: body.startAt + ':00+09:00' }); window.alert('입사 안내를 전달했습니다. 사용자가 입사 안내를 확인할 수 있습니다.'); await reload(); }} submit="입사 안내 전달" successMessage="입사 안내를 전달했습니다." />}
    {record.acknowledgedAt && <p>사용자가 입사 안내를 확인했습니다.</p>}{record.userMessage && <p className="callout prose">사용자 요청: {record.userMessage}</p>}
  </section>;
}
