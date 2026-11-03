import { useState } from 'react';
import { jobCategories } from '../../data/jobCategories';

const options = new Set(jobCategories.flatMap((category) => [category.title, ...category.jobs]));

export default function CompanyIndustryInput({ id, value, onChange }) {
  const [selection, setSelection] = useState(() =>
    value && !options.has(value) ? 'other' : value || '',
  );
  const [custom, setCustom] = useState(() => (value && !options.has(value) ? value : ''));
  return (
    <div className="stack">
      <select
        id={id}
        name="industryCategory"
        value={selection}
        aria-describedby={id + '-hint'}
        onChange={(event) => {
          const next = event.target.value;
          setSelection(next);
          onChange(next === 'other' ? custom : next);
        }}
      >
        <option value="">업종을 선택해 주세요</option>
        {jobCategories.map((category) => (
          <optgroup key={category.icon} label={category.title}>
            <option value={category.title}>{category.title} (전체)</option>
            {category.jobs.map((job) => (
              <option key={job} value={job}>
                {job}
              </option>
            ))}
          </optgroup>
        ))}
        <option value="other">기타 · 직접 입력</option>
      </select>
      {selection === 'other' && (
        <div>
          <label htmlFor={id + '-custom'}>업종 직접 입력</label>
          <input
            id={id + '-custom'}
            name="industry"
            value={custom}
            maxLength={100}
            required
            pattern=".*\S.*"
            placeholder="업종을 입력해 주세요"
            onChange={(event) => {
              setCustom(event.target.value);
              onChange(event.target.value);
            }}
          />
        </div>
      )}
      <p id={id + '-hint'} className="muted">
        채용정보 메뉴의 분류에서 선택하세요. 해당하는 항목이 없으면 기타를 선택해 직접 입력할 수
        있습니다.
      </p>
    </div>
  );
}
