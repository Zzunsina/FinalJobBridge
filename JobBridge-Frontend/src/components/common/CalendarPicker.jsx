import { useEffect, useId, useRef, useState } from 'react';
import { koreanToday } from '../../utils/memberInputs';

const pad = (n) => String(n).padStart(2, '0');
export default function CalendarPicker({ id, name, value = '', onChange, required, disabled, type = 'date', min, max, label }) {
  const generatedId = useId();
  const fieldId = id || generatedId;
  const dialog = useRef(null);
  const trigger = useRef(null);
  const [open, setOpen] = useState(false);
  const [year, setYear] = useState(Number(koreanToday().slice(0, 4)));
  const [month, setMonth] = useState(Number(koreanToday().slice(5, 7)));
  const [day, setDay] = useState('');
  const [hour, setHour] = useState('09');
  const [minute, setMinute] = useState('00');
  const datetime = type === 'datetime-local';
  const monthOnly = type === 'month';
  const title = label || (datetime ? '날짜·시간 선택' : monthOnly ? '연월 선택' : '날짜 선택');
  const yearMonth = year + '-' + pad(month);
  const candidate = monthOnly ? yearMonth : day + (datetime ? 'T' + hour + ':' + minute : '');
  const canConfirm = (monthOnly || Boolean(day)) && (!min || candidate >= min) && (!max || candidate <= max);
  const base = new Date(Date.UTC(year, month - 1, 1));
  const offset = base.getUTCDay();
  const length = new Date(Date.UTC(year, month, 0)).getUTCDate();
  const currentYear = Number(koreanToday().slice(0, 4));
  const firstYear = min ? Number(min.slice(0, 4)) : Math.min(currentYear - 100, year);
  const lastYear = max ? Number(max.slice(0, 4)) : Math.max(currentYear + 20, year);
  function begin() {
    if (disabled) return;
    const initial = value || koreanToday();
    setYear(Number(initial.slice(0, 4))); setMonth(Number(initial.slice(5, 7)));
    setDay(value && !monthOnly ? value.slice(0, 10) : '');
    setHour(value?.slice(11, 13) || '09'); setMinute(value?.slice(14, 16) || '00');
    setOpen(true);
  }
  useEffect(() => {
    if (!open) return;
    const modal = dialog.current;
    modal.showModal();
    return () => { modal.close(); };
  }, [open]);
  function close() { setOpen(false); trigger.current?.focus(); }
  const display = value ? value.replace('T', ' ').replaceAll('-', '.') + (datetime ? ' (한국 시간)' : '') : '';
  return (
    <div className="calendar-field">
      <input ref={trigger} id={fieldId} name={name} value={display} readOnly disabled={disabled} placeholder={title} aria-label={label} aria-haspopup="dialog" aria-expanded={open} onClick={begin} onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); begin(); } }} />
      <input className="calendar-validation" tabIndex={-1} aria-label={title + ' 필수값'} required={required} disabled={disabled} value={value} onChange={() => {}} onInvalid={(e) => { e.preventDefault(); begin(); }} />
      <dialog ref={dialog} className="calendar-dialog" aria-labelledby={fieldId + '-title'} onCancel={(e) => { e.preventDefault(); close(); }}>
        <h2 id={fieldId + '-title'}>{title}</h2>
        <div className="calendar-month-controls">
          <select aria-label="연도 선택" value={year} onChange={(e) => { setYear(Number(e.target.value)); setDay(''); }}>
            {Array.from({ length: lastYear - firstYear + 1 }, (_, i) => firstYear + i).map((y) => <option key={y} value={y}>{y}년</option>)}
          </select>
          <select aria-label="월 선택" value={month} onChange={(e) => { setMonth(Number(e.target.value)); setDay(''); }}>
            {Array.from({ length: 12 }, (_, i) => i + 1).map((m) => <option key={m} value={m}>{m}월</option>)}
          </select>
        </div>
        {!monthOnly && <>
          <div className="calendar-weekdays" aria-hidden="true">{['일', '월', '화', '수', '목', '금', '토'].map((d) => <span key={d}>{d}</span>)}</div>
          <div className="calendar-days" role="group" aria-label="날짜 선택">
            {Array.from({ length: offset }, (_, i) => <span key={'blank' + i} />)}
            {Array.from({ length }, (_, i) => i + 1).map((d) => {
              const date = yearMonth + '-' + pad(d);
              return <button key={d} type="button" className={day === date ? 'calendar-day selected' : 'calendar-day'} aria-label={year + '년 ' + month + '월 ' + d + '일'} aria-pressed={day === date} disabled={(min && date < min.slice(0, 10)) || (max && date > max.slice(0, 10))} onClick={() => setDay(date)}>{d}</button>;
            })}
          </div>
        </>}
        {datetime && <div className="calendar-time"><span>시간 (한국 시간)</span><select aria-label="시 선택" value={hour} onChange={(e) => setHour(e.target.value)}>{Array.from({ length: 24 }, (_, i) => pad(i)).map((h) => <option key={h} value={h}>{h}시</option>)}</select><select aria-label="분 선택" value={minute} onChange={(e) => setMinute(e.target.value)}>{Array.from({ length: 60 }, (_, i) => pad(i)).map((m) => <option key={m} value={m}>{m}분</option>)}</select></div>}
        <p className="calendar-selection" role="status">{monthOnly || day ? candidate.replace('T', ' ').replaceAll('-', '.') : '날짜를 선택해 주세요.'}</p>
        <div className="calendar-footer">
          <button type="button" className="secondary" onClick={close}>취소</button>
          <button type="button" disabled={!canConfirm} onClick={() => { onChange(candidate); close(); }}>선택 완료</button>
        </div>
      </dialog>
    </div>
  );
}
