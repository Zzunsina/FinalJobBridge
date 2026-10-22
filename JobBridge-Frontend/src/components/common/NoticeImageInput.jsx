import { useRef, useState } from 'react';

export default function NoticeImageInput({ id, value, previewUrl, onChange }) {
  const input = useRef(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const source = value === undefined ? previewUrl : value;
  async function select(event) {
    const file = event.target.files?.[0]; event.target.value = '';
    if (!file) return;
    setError(''); setBusy(true);
    try {
      if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type) || file.size > 10 * 1024 * 1024) throw new Error('10MB 이하의 JPG, PNG, WEBP 이미지를 선택해 주세요.');
      const bitmap = await createImageBitmap(file);
      try {
        const scale = Math.min(1, 1600 / bitmap.width, 8000 / bitmap.height);
        const canvas = document.createElement('canvas'); canvas.width = Math.max(1, Math.round(bitmap.width * scale)); canvas.height = Math.max(1, Math.round(bitmap.height * scale));
        const context = canvas.getContext('2d'); context.fillStyle = '#fff'; context.fillRect(0, 0, canvas.width, canvas.height); context.drawImage(bitmap, 0, 0, canvas.width, canvas.height);
        const data = canvas.toDataURL('image/jpeg', 0.9);
        if (data.length > 5600000) throw new Error('저장할 이미지가 너무 큽니다. 크기를 줄여 다시 선택해 주세요.');
        onChange(data);
      } finally { bitmap.close(); }
    } catch (ex) { setError(ex.message || '이미지를 읽을 수 없습니다.'); }
    finally { setBusy(false); }
  }
  return <div className="notice-image-input">
    <input id={id} ref={input} type="file" accept="image/jpeg,image/png,image/webp" onChange={select} disabled={busy} />
    <p className="muted">안내 이미지 1장 · 최대 10MB · 비율을 유지하며 저장합니다. 공지 저장 시 함께 반영됩니다.</p>
    {source && <><img src={source} alt="공지 안내 이미지 미리보기" /><button type="button" className="secondary" disabled={busy} onClick={() => onChange('')}>이미지 삭제</button></>}
    {busy && <p role="status">이미지를 처리하는 중입니다…</p>}
    {error && <p className="error" role="alert">{error}</p>}
  </div>;
}
