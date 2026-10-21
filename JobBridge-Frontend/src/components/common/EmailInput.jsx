import { useEffect, useRef, useState } from 'react';

const domains = ['naver.com', 'gmail.com', 'daum.net', 'hanmail.net', 'nate.com', 'outlook.com', 'icloud.com'];
const localPattern = /^[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\.[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+)*$/;
const domainPattern = /^(?:[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?\.)+[A-Za-z]{2,63}$/;

export default function EmailInput({ id, name, value, onChange, required }) {
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
  useEffect(() => {
    localInput.current?.setCustomValidity(localError || tooLong ? '이메일 아이디 형식을 확인해 주세요.' : '');
    domainInput.current?.setCustomValidity(domainError ? '도메인을 올바르게 입력해 주세요. 예: example.com' : '');
  }, [localError, domainError, tooLong, selection]);
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
    </div>
  );
}
