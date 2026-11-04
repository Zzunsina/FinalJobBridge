import { Form } from './UI';

const fields = [
  { name: 'interviewAt', label: '면접 일시 (한국 시간)', type: 'confirmed-datetime', required: true },
  { name: 'interviewType', label: '면접 방식', options: ['ONLINE', 'OFFLINE'], required: true },
  { name: 'location', label: '면접 장소', type: 'address', maxLength: 255, required: true,
    visibleWhen: (values) => values.interviewType === 'OFFLINE' },
  { name: 'meetingUrl', label: '온라인 면접 주소', type: 'url', maxLength: 500, required: true,
    visibleWhen: (values) => values.interviewType === 'ONLINE' },
  { name: 'applicantGuidance', label: '면접 안내사항 (지원자에게 공개)', type: 'textarea', maxLength: 2000,
    placeholder: '예: 면접 10분 전 도착해 주세요. 신분증을 지참해 주세요.' },
  { name: 'memo', label: '기업 내부 메모 (구직자에게 비공개)', type: 'textarea', maxLength: 10000 },
];

// The API returns instants; the picker always edits Korean wall-clock time.
function koreanDateTime(value) {
  if (!value) return '';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '';
  return new Date(date.getTime() + 9 * 60 * 60 * 1000).toISOString().slice(0, 16);
}

export default function CompanyInterviewForm({ interview, onSubmit, onCancel }) {
  return (
    <section aria-label={interview ? '면접 일정 수정' : '면접 등록'}>
      <h3>{interview ? '면접 일정 수정' : '면접 등록'}</h3>
      {interview && <p>변경한 일정과 안내사항은 지원자에게 전달됩니다. 내부 메모는 기업에서만 확인할 수 있습니다.</p>}
      <Form
        initial={interview ? { ...interview, interviewAt: koreanDateTime(interview.interviewAt) } : {}}
        fields={fields}
        onSubmit={(body) => onSubmit({
          ...body,
          location: body.interviewType === 'OFFLINE' ? body.location : '',
          meetingUrl: body.interviewType === 'ONLINE' ? body.meetingUrl : '',
          interviewAt: body.interviewAt + ':00+09:00',
        })}
        submit={interview ? '면접 변경사항 저장' : '면접 일정 등록'}
        resetOnSuccess={!interview}
        successMessage={interview ? '면접 일정을 수정했습니다.' : '면접 일정이 등록되었습니다.'}
      />
      {onCancel && <button type="button" className="secondary" onClick={onCancel}>수정 취소</button>}
    </section>
  );
}
