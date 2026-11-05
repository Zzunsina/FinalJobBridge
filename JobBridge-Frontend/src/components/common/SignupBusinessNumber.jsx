import { useEffect, useState } from 'react';
import client from '../../api/client';

export default function SignupBusinessNumber({ id, value, onChange, serverError, ...props }) {
  const [result, setResult] = useState(null);
  const [retry, setRetry] = useState(0);
  const valid = /^[0-9]{10}$/.test(value);
  const current = result?.value === value ? result : null;
  useEffect(() => {
    if (!/^[0-9]{10}$/.test(value)) return;
    let active = true;
    const controller = new AbortController();
    const timer = window.setTimeout(async () => {
      try {
        const { data } = await client.get('/auth/business-number/availability', { params: { businessNumber: value }, signal: controller.signal });
        if (active) setResult({ value, available: data.available, message: data.message });
      } catch {
        if (active) setResult({ value, failed: true, message: '사업자등록번호 확인에 실패했습니다. 다시 확인해 주세요.' });
      }
    }, 450);
    return () => { active = false; controller.abort(); window.clearTimeout(timer); };
  }, [value, retry]);
  const feedback = serverError || (value && !valid ? '사업자등록번호는 숫자 10자리로 입력해 주세요.' : current?.message || (valid ? '사용 가능 여부를 확인하고 있습니다…' : ''));
  const invalid = Boolean(serverError || (value && !valid) || current?.available === false);
  return <>
    <div className="signup-id-row"><input {...props} id={id} name="businessNumber" type="text" inputMode="numeric" autoComplete="off" minLength={10} maxLength={10} pattern="[0-9]{10}" value={value} onChange={event=>onChange(event.target.value.replace(/[^0-9]/g,'').slice(0,10))} aria-invalid={invalid} aria-describedby={feedback ? id + '-availability' : undefined} />{current?.failed && <button type="button" className="secondary" onClick={() => { setResult(null); setRetry((n) => n + 1); }}>다시 확인</button>}</div>
    {feedback && <p id={id + '-availability'} className={'signup-id-feedback ' + (invalid ? 'invalid' : current?.available && !serverError ? 'available' : '')} role="status">{feedback}</p>}
  </>;
}
