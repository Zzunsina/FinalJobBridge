import { useRef, useState } from 'react';

const MAX_IMAGES = 10;
const MAX_TOTAL_LENGTH = 5600000; // 기존 단일 이미지 한도(문자열 길이) 기준. 백엔드 한도에 맞춰 조정하세요.

async function fileToDataUrl(file) {
  if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type) || file.size > 10 * 1024 * 1024) {
    throw new Error(`${file.name}: 10MB 이하의 JPG, PNG, WEBP 이미지를 선택해 주세요.`);
  }
  const bitmap = await createImageBitmap(file);
  try {
    const scale = Math.min(1, 1600 / bitmap.width, 8000 / bitmap.height);
    const canvas = document.createElement('canvas');
    canvas.width = Math.max(1, Math.round(bitmap.width * scale));
    canvas.height = Math.max(1, Math.round(bitmap.height * scale));
    const context = canvas.getContext('2d');
    context.fillStyle = '#fff';
    context.fillRect(0, 0, canvas.width, canvas.height);
    context.drawImage(bitmap, 0, 0, canvas.width, canvas.height);
    return canvas.toDataURL('image/jpeg', 0.9);
  } finally {
    bitmap.close();
  }
}

export default function DetailImagesInput({ id, value = [], onChange, onBusyChange, helpText, previewAlt, removeLabel }) {
  const input = useRef(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  async function select(event) {
    const files = Array.from(event.target.files || []);
    event.target.value = '';
    if (!files.length) return;
    if (value.length + files.length > MAX_IMAGES) {
      setError(`이미지는 최대 ${MAX_IMAGES}장까지 등록할 수 있습니다.`);
      return;
    }
    setError('');
    setBusy(true);
    onBusyChange?.(true);
    try {
      const added = [];
      for (const file of files) added.push(await fileToDataUrl(file)); // 순서 보장을 위해 순차 처리
      const next = [...value, ...added];
      if (next.reduce((sum, v) => sum + v.length, 0) > MAX_TOTAL_LENGTH) {
        throw new Error('저장할 이미지의 전체 용량이 너무 큽니다. 일부 이미지를 줄이거나 삭제해 주세요.');
      }
      onChange(next);
    } catch (ex) {
      setError(ex.message || '이미지를 읽을 수 없습니다.');
    } finally {
      setBusy(false);
      onBusyChange?.(false);
    }
  }

  const remove = (index) => onChange(value.filter((_, i) => i !== index));
  const move = (index, dir) => {
    const target = index + dir;
    if (target < 0 || target >= value.length) return;
    const next = [...value];
    [next[index], next[target]] = [next[target], next[index]];
    onChange(next);
  };

  return (
    <div className="notice-image-input detail-images-input">
      <input id={id} ref={input} type="file" accept="image/jpeg,image/png,image/webp" multiple hidden onChange={select} disabled={busy} />
      <button
        type="button"
        className="secondary"
        disabled={busy || value.length >= MAX_IMAGES}
        onClick={() => input.current?.click()}
      >
        {busy ? '처리 중…' : '이미지 선택'}
      </button>
      <p className="muted">
        {value.length}/{MAX_IMAGES}장 · {helpText || 'JPG, PNG, WEBP 최대 10MB · 여러 장 선택 가능 · 공고 저장 시 함께 반영됩니다.'}
      </p>

      {value.length > 0 && (
        <ul className="detail-images-list">
          {value.map((src, i) => (
            <li key={i} className="detail-images-item">
              <img src={src} alt={`${previewAlt || '상세 소개 이미지 미리보기'} ${i + 1}`} />
              <div className="actions">
                <button type="button" className="secondary" disabled={busy || i === 0} onClick={() => move(i, -1)} aria-label="앞으로 이동">←</button>
                <button type="button" className="secondary" disabled={busy || i === value.length - 1} onClick={() => move(i, 1)} aria-label="뒤로 이동">→</button>
                <button type="button" className="secondary" disabled={busy} onClick={() => remove(i)}>
                  {removeLabel || '이미지 삭제'}
                </button>
              </div>
            </li>
          ))}
        </ul>
      )}

      {busy && <p role="status">이미지를 처리하는 중입니다…</p>}
      {error && <p className="error" role="alert">{error}</p>}
    </div>
  );
}