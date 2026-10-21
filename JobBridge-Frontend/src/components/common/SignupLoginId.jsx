import { useEffect, useState } from 'react';
import client from '../../api/client';
import { AlphanumericInput } from './InputTools';

export default function SignupLoginId({ id, value, onChange, serverError, ...props }) {
  const [result, setResult] = useState(null);
  const [retry, setRetry] = useState(0);
  const valid = /^[A-Za-z0-9]{4,30}$/.test(value);
  const current = result?.value === value ? result : null;
  useEffect(() => {
    if (!/^[A-Za-z0-9]{4,30}$/.test(value)) return;
    let active = true;
    const controller = new AbortController();
    const timer = window.setTimeout(async () => {
      try {
        const { data } = await client.get('/auth/login-id/availability', { params: { loginId: value }, signal: controller.signal });
        if (active) setResult({ value, available: data.available, message: data.message });
      } catch {
        if (active) setResult({ value, failed: true, message: '아이디 확인에 실패했습니다. 다시 확인해 주세요.' });
      }
    }, 450);
    return () => { active = false; controller.abort(); window.clearTimeout(timer); };
  }, [value, retry]);
  const feedback = serverError || (value && !valid ? '아이디는 영문과 숫자로 4~30자를 입력해 주세요.' : current?.message || (valid ? '사용 가능 여부를 확인하고 있습니다…' : ''));
  const invalid = Boolean(serverError || (value && !valid) || current?.available === false);
  return <>
    <div className="signup-id-row"><AlphanumericInput {...props} id={id} name="loginId" value={value} onChange={onChange} aria-invalid={invalid} aria-describedby={feedback ? id + '-availability' : undefined} />{current?.failed && <button type="button" className="secondary" onClick={() => { setResult(null); setRetry((n) => n + 1); }}>다시 확인</button>}</div>
    {feedback && <p id={id + '-availability'} className={'signup-id-feedback ' + (invalid ? 'invalid' : current?.available && !serverError ? 'available' : '')} role="status">{feedback}</p>}
  </>;
}
