import { useEffect, useRef, useState } from 'react';
import client from '../../api/client';

const domains = ['naver.com', 'gmail.com', 'daum.net', 'hanmail.net', 'nate.com', 'outlook.com', 'icloud.com'];
const localPattern = /^[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\.[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+)*$/;
const domainPattern = /^(?:[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?\.)+[A-Za-z]{2,63}$/;

export default function EmailInput({ id, name, value, onChange, required, checkAvailability = false, serverError }) {
  const separator = value.indexOf('@');
  const local = separator < 0 ? value : value.slice(0, separator);
  const domain = separator < 0 ? '' : value.slice(separator + 1);
  const [selection, setSelection] = useState(domains.includes(domain) ? domain : 'custom');
  const [touched, setTouched] = useState(false);
  const localInput = useRef(null);
  const domainInput = useRef(null);
  const localError = local && (!localPattern.test(local) || local.length > 64);
  const domainError = domain && !domainPattern.test(domain);
  const tooLong = value.length > 254;
  const error = localError || domainError || tooLong;
  const [result, setResult] = useState(null);
  const [retry, setRetry] = useState(0);
  const valid = Boolean(local && domain && !error);
  const current = result?.value === value ? result : null;
  useEffect(() => {
    if (!checkAvailability || !valid) return;
    let active = true;
    const controller = new AbortController();
    const timer = window.setTimeout(async () => {
      try {
        const { data } = await client.get('/auth/email/availability', {
          params: { email: value }, signal: controller.signal,
        });
        if (active) setResult({ value, available: data.available, message: data.message });
      } catch {
        if (active) setResult({ value, failed: true, message: '이메일 확인에 실패했습니다. 다시 확인해 주세요.' });
      }
    }, 450);
    return () => { active = false; controller.abort(); window.clearTimeout(timer); };
  }, [checkAvailability, valid, value, retry]);
  const feedback = serverError || (checkAvailability && valid
    ? current?.message || '사용 가능 여부를 확인하고 있습니다…' : '');
  const invalid = Boolean(serverError || current?.available === false);
  useEffect(() => {
    localInput.current?.setCustomValidity(localError || tooLong
      ? '이메일 아이디 형식을 확인해 주세요.'
      : checkAvailability && valid && !current?.available ? feedback : serverError || '');
    domainInput.current?.setCustomValidity(domainError ? '도메인을 올바르게 입력해 주세요. 예: example.com' : '');
    localInput.current?.setAttribute('aria-invalid', String(invalid));
    localInput.current?.setAttribute('aria-describedby', id + '-hint' + (feedback ? ' ' + id + '-availability' : ''));
  }, [checkAvailability, localError, domainError, tooLong, valid, current, feedback, serverError, invalid, id, selection]);
  return (
    <div>
      <div className="email-input-row">
        <input ref={localInput} id={id} name={name + 'Local'} aria-label="이메일 아이디" autoComplete="off" autoCapitalize="none" spellCheck={false} required={required} maxLength={64} placeholder="이메일 아이디" value={local} onBlur={() => setTouched(true)} onChange={(e) => onChange(e.target.value + '@' + domain)} aria-describedby={id + '-hint'} />
        <span aria-hidden="true">@</span>
        {selection === 'custom' && <input ref={domainInput} name={name + 'Domain'} aria-label="이메일 도메인 직접 입력" autoCapitalize="none" spellCheck={false} required={required} maxLength={253} placeholder="example.com" value={domain} onBlur={() => setTouched(true)} onChange={(e) => onChange(local + '@' + e.target.value)} aria-describedby={id + '-hint'} />}
        <select aria-label="이메일 도메인 선택" value={selection} onChange={(e) => { const next = e.target.value; setSelection(next); onChange(local + '@' + (next === 'custom' ? '' : next)); }}>
          <option value="custom">직접 입력</option>
          {domains.map((item) => <option key={item} value={item}>{item}</option>)}
        </select>
      </div>
      <p id={id + '-hint'} className={touched && error ? 'input-guidance' : 'email-hint'} role="status">{touched && error ? '이메일 형식을 확인해 주세요. 예: jobbridge@example.com' : '이메일 아이디를 입력하고 도메인을 선택하거나 직접 입력해 주세요.'}</p>
      {feedback && <p id={id + '-availability'} className={'signup-id-feedback ' + (invalid ? 'invalid' : current?.available && !serverError ? 'available' : '')} role="status">{feedback}</p>}
      {checkAvailability && current?.failed && <button type="button" className="secondary" onClick={() => { setResult(null); setRetry((count) => count + 1); }}>다시 확인</button>}
    </div>
  );
}
