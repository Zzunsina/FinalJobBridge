import { useEffect, useRef, useState } from 'react';
import { useAuth } from '../../stores/auth';
import client, { message } from '../../api/client';
import '../../assets/styles/profile-photo.css';

async function preparePhoto(file) {
  if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type)) {
    throw new Error('JPG, PNG, WEBP 사진을 선택해 주세요.');
  }
  if (file.size > 10 * 1024 * 1024) throw new Error('10MB 이하의 사진을 선택해 주세요.');
  const bitmap = await createImageBitmap(file);
  try {
    const canvas = document.createElement('canvas');
    canvas.width = canvas.height = 512;
    const context = canvas.getContext('2d');
    context.fillStyle = '#ffffff';
    context.fillRect(0, 0, 512, 512);
    const scale = 512 / Math.max(bitmap.width, bitmap.height);
    const width = bitmap.width * scale;
    const height = bitmap.height * scale;
    context.drawImage(bitmap, (512 - width) / 2, (512 - height) / 2, width, height);
    return canvas.toDataURL('image/jpeg', 0.9);
  } finally {
    bitmap.close();
  }
}

export default function CompanyImageUpload() {
  const user = useAuth((s) => s.user);
  const [saved, setSaved] = useState('');
  const [draft, setDraft] = useState('');
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const input = useRef(null);

  useEffect(() => {
    let active = true;
    let url;
    client
      .get('/company/image', { responseType: 'blob' })
      .then(({ data, status }) => {
        if (active && status === 200) {
          url = URL.createObjectURL(data);
          setSaved(url);
        }
      })
      .catch(() => {
        if (active) setError('사진을 불러오지 못했습니다. 페이지를 새로고침해 주세요.');
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
      if (url) URL.revokeObjectURL(url);
    };
  }, [user.userId]);

  async function select(event) {
    const file = event.target.files?.[0];
    event.target.value = '';
    if (!file) return;
    setError('');
    setNotice('');
    setBusy(true);
    try {
      setDraft(await preparePhoto(file));
    } catch (ex) {
      setError(
        ex.message === 'The source image could not be decoded.'
          ? '사진을 읽을 수 없습니다. 다른 사진을 선택해 주세요.'
          : ex.message,
      );
    } finally {
      setBusy(false);
    }
  }

  async function save() {
    setBusy(true);
    setError('');
    setNotice('');
    try {
      await client.put('/company/image', { image: draft });
      setSaved(draft);
      setDraft('');
      setNotice('기업 이미지을 저장했습니다.');
    } catch (ex) {
      setError(message(ex));
    } finally {
      setBusy(false);
    }
  }

  async function remove() {
    setBusy(true);
    setError('');
    setNotice('');
    try {
      await client.delete('/company/image');
      setSaved('');
      setDraft('');
      setNotice('기업 이미지을 삭제했습니다.');
    } catch (ex) {
      setError(message(ex));
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="profile-photo-section" aria-labelledby="company-image-heading">
      <div className="profile-photo-preview">
        {draft || saved ? (
          <img src={draft || saved} alt={`${user.name}님의 기업 이미지`} />
        ) : (
          <span aria-hidden="true">{user.name?.slice(0, 1) || '나'}</span>
        )}
      </div>
      <div className="profile-photo-content">
        <h2 id="company-image-heading">기업 이미지</h2>
        <p>공고 목록·상세와 공개 기업 정보에 표시되는 대표 이미지입니다. 이미지 저장을 눌러 등록해 주세요.</p>
        <input
          ref={input}
          type="file"
          accept="image/jpeg,image/png,image/webp"
          aria-label="기업 이미지 파일 선택"
          onChange={select}
          hidden
        />
        <div className="profile-photo-actions">
          <button
            type="button"
            className="button small"
            disabled={loading || busy}
            onClick={() => input.current.click()}
          >
            {saved || draft ? '사진 변경' : '사진 선택'}
          </button>
          {draft && (
            <>
              <button type="button" className="button small" disabled={busy} onClick={save}>
                사진 저장
              </button>
              <button
                type="button"
                className="button outline small"
                disabled={busy}
                onClick={() => {
                  setDraft('');
                  setError('');
                }}
              >
                선택 취소
              </button>
            </>
          )}
          {saved && !draft && (
            <button
              type="button"
              className="button outline small"
              disabled={busy || loading}
              onClick={remove}
            >
              사진 삭제
            </button>
          )}
        </div>
        <small>JPG, PNG, WEBP · 최대 10MB · 비율을 유지해 정사각형 안에 저장됩니다.</small>
        {draft && (
          <p className="profile-photo-hint">미리보기입니다. 사진 저장을 눌러 등록해 주세요.</p>
        )}
        {loading && <p role="status">사진을 불러오는 중입니다.</p>}
        {busy && <p role="status">사진을 처리하고 있습니다.</p>}
        {notice && (
          <p className="profile-photo-success" role="status">
            {notice}
          </p>
        )}
        {error && (
          <p className="profile-photo-error" role="alert">
            {error}
          </p>
        )}
      </div>
    </section>
  );
}
