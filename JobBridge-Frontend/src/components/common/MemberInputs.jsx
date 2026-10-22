import { useEffect, useLayoutEffect, useRef, useState } from 'react';
import { birthDateError, formatMobile } from '../../utils/memberInputs';

export function BirthDateInput({ id, value, onChange, required }) {
  const input = useRef(null);
  const [touched, setTouched] = useState(false);
  const error = birthDateError(value);
  const showError = Boolean(error && (touched || value.length === 8));
  useEffect(() => { input.current?.setCustomValidity(error); }, [error]);
  return (
    <>
      <input ref={input} id={id} name="birthDate" inputMode="numeric" autoComplete="bday" required={required} maxLength={8} placeholder="예: 20000123" value={value} onBlur={() => setTouched(true)} onChange={(e) => onChange(e.target.value.replace(/[^0-9]/g, ''))} aria-describedby={showError ? id + '-hint' : undefined} aria-invalid={showError} />
      {showError && <p id={id + '-hint'} className="input-guidance" role="status">{error}</p>}
    </>
  );
}

export function MobileInput({ id, name, value, onChange, required }) {
  const input = useRef(null);
  const caret = useRef(null);
  const formatted = formatMobile(value);
  useLayoutEffect(() => {
    if (caret.current !== null && input.current === document.activeElement) {
      let position = 0, count = 0;
      while (position < formatted.length && count < caret.current) {
        if (/[0-9]/.test(formatted[position])) count++;
        position++;
      }
      input.current.setSelectionRange(position, position);
      caret.current = null;
    }
  });
  return <input ref={input} id={id} name={name} type="tel" inputMode="numeric" autoComplete="tel-national" required={required} maxLength={13} value={formatted}
    onChange={(e) => {
      caret.current = e.target.value.slice(0, e.target.selectionStart).replace(/[^0-9]/g, '').length;
      onChange(e.target.value.replace(/[^0-9]/g, '').slice(0, 11));
    }}
    onKeyDown={(e) => {
      const start = e.currentTarget.selectionStart;
      if (e.key === 'Backspace' && start === e.currentTarget.selectionEnd && formatted[start - 1] === '-') {
        e.preventDefault();
        const before = formatted.slice(0, start).replace(/[^0-9]/g, '').length;
        const digits = formatted.replace(/[^0-9]/g, '');
        caret.current = before - 1;
        onChange(digits.slice(0, before - 1) + digits.slice(before));
      }
    }} />;
}
