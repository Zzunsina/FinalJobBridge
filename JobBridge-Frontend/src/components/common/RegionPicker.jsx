import { useEffect, useId, useRef, useState } from 'react';
import { MAX_WORK_REGIONS, workRegions } from '../../data/workRegions';

export default function RegionPicker({ value = '', onChange }) {
  const selected = value ? value.split('|').filter(Boolean) : [];
  const [open, setOpen] = useState(false);
  const [province, setProvince] = useState('서울');
  const [draft, setDraft] = useState([]);
  const [error, setError] = useState('');
  const container = useRef(null);
  const trigger = useRef(null);
  const panelId = useId();
  const current = workRegions.find((r) => r.name === province) || workRegions[0];

  useEffect(() => {
    if (!open) return;
    function outside(event) {
      if (!container.current?.contains(event.target)) setOpen(false);
    }
    function escape(event) {
      if (event.key === 'Escape') {
        setOpen(false);
        trigger.current?.focus();
      }
    }
    document.addEventListener('pointerdown', outside);
    document.addEventListener('keydown', escape);
    return () => {
      document.removeEventListener('pointerdown', outside);
      document.removeEventListener('keydown', escape);
    };
  }, [open]);

  function toggle(location) {
    setError('');
    if (draft.includes(location)) {
      setDraft(draft.filter((r) => r !== location));
      return;
    }
    const wholeProvince = location === province;
    const next = draft.filter((r) =>
      wholeProvince ? !r.startsWith(`${province} `) : r !== province,
    );
    if (next.length >= MAX_WORK_REGIONS) {
      setError(`지역은 최대 ${MAX_WORK_REGIONS}개까지 선택할 수 있습니다.`);
      return;
    }
    setDraft([...next, location]);
  }
  const summary = selected.length
    ? `${selected[0]}${selected[0].includes(' ') ? '' : ' 전체'}${selected.length > 1 ? ` 외 ${selected.length - 1}곳` : ''}`
    : '근무지역 전체';
  return (
    <div className="region-picker" ref={container}>
      <button
        ref={trigger}
        type="button"
        className={`region-trigger secondary ${selected.length ? 'has-selection' : ''}`}
        aria-label={`근무지역: ${summary}`}
        aria-expanded={open}
        aria-haspopup="dialog"
        aria-controls={panelId}
        onClick={() => {
          if (!open) {
            setDraft(selected);
            setError('');
            const firstProvince = selected[0]?.split(' ')[0];
            setProvince(workRegions.some((r) => r.name === firstProvince) ? firstProvince : '서울');
          }
          setOpen(!open);
        }}
      >
        <svg aria-hidden="true" viewBox="0 0 24 24">
          <path d="M12 22s8-7 8-14A8 8 0 0 0 4 8c0 7 8 14 8 14Z" />
          <circle cx="12" cy="8" r="2.5" />
        </svg>
        <span>{summary}</span>
        <span aria-hidden="true">{open ? '⌃' : '⌄'}</span>
      </button>
      {open && (
        <section id={panelId} className="region-panel" role="dialog" aria-label="근무지역 선택">
          <div className="region-panel-heading">
            <strong>근무지역 선택</strong>
            <button
              type="button"
              className="plain"
              aria-label="지역 선택 닫기"
              onClick={() => {
                setOpen(false);
                trigger.current?.focus();
              }}
            >
              ×
            </button>
          </div>
          <div className="region-columns">
            <div
              className="province-list"
              role="tablist"
              aria-label="시·도"
              aria-orientation="vertical"
            >
              {workRegions.map((r) => {
                const count = draft.filter(
                  (v) => v === r.name || v.startsWith(`${r.name} `),
                ).length;
                return (
                  <button
                    type="button"
                    role="tab"
                    id={`${panelId}-${r.name}`}
                    aria-selected={province === r.name}
                    aria-controls={`${panelId}-districts`}
                    key={r.name}
                    onClick={() => setProvince(r.name)}
                    onKeyDown={(event) => {
                      if (!['ArrowUp', 'ArrowDown', 'Home', 'End'].includes(event.key)) return;
                      event.preventDefault();
                      const index = workRegions.indexOf(r);
                      const next =
                        event.key === 'Home'
                          ? 0
                          : event.key === 'End'
                            ? workRegions.length - 1
                            : (index + (event.key === 'ArrowDown' ? 1 : -1) + workRegions.length) %
                              workRegions.length;
                      setProvince(workRegions[next].name);
                      const tabs =
                        event.currentTarget.parentElement.querySelectorAll('[role="tab"]');
                      tabs[next]?.focus();
                    }}
                  >
                    <span>{r.name}</span>
                    {count > 0 && <small>{count}</small>}
                  </button>
                );
              })}
            </div>
            <div
              key={province}
              id={`${panelId}-districts`}
              className="district-list"
              role="tabpanel"
              aria-labelledby={`${panelId}-${current.name}`}
            >
              {[
                { value: province, label: `${province} 전체` },
                ...current.districts.map((district) => ({
                  value: `${province} ${district}`,
                  label: district,
                })),
              ].map((item) => (
                <label key={item.value} className={draft.includes(item.value) ? 'selected' : ''}>
                  <input
                    type="checkbox"
                    checked={draft.includes(item.value)}
                    onChange={() => toggle(item.value)}
                  />
                  <span className="region-check" aria-hidden="true">
                    ✓
                  </span>
                  <span>{item.label}</span>
                </label>
              ))}
              {!current.districts.length && (
                <p className="region-help">세종시는 ‘세종 전체’로 검색합니다.</p>
              )}
            </div>
          </div>
          <div className="region-selection-summary" aria-live="polite">
            {draft.length
              ? `${draft.length}개 지역 선택 · ${draft.join(', ')}`
              : '전체 지역에서 검색합니다.'}
          </div>
          {error && (
            <p className="region-error" role="alert">
              {error}
            </p>
          )}
          <footer className="region-panel-actions">
            <button
              type="button"
              className="plain region-reset"
              onClick={() => {
                setDraft([]);
                setError('');
              }}
            >
              ↻ 초기화
            </button>
            <button
              type="button"
              onClick={() => {
                onChange(draft.join('|'));
                setOpen(false);
                trigger.current?.focus();
              }}
            >
              적용하기
            </button>
          </footer>
        </section>
      )}
    </div>
  );
}
