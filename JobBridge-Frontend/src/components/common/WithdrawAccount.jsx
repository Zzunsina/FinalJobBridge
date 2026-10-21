import { useId, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import client, { message } from '../../api/client';
import { useAuth } from '../../stores/auth';
import { useBookmarks } from '../../stores/bookmarks';

export default function WithdrawAccount() {
  const user = useAuth((s) => s.user);
  const setUser = useAuth((s) => s.setUser);
  const navigate = useNavigate();
  const id = useId();
  const [open, setOpen] = useState(false);
  const [password, setPassword] = useState('');
  const [confirmed, setConfirmed] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  if (!['JOB_SEEKER', 'COMPANY'].includes(user?.role)) return null;
  async function withdraw(event) {
    event.preventDefault();
    if (busy || !confirmed) return;
    setBusy(true);
    setError('');
    try {
      await client.delete('/me', { data: { currentPassword: password } });
      useBookmarks.getState().reset();
      setUser(null);
      window.alert('회원탈퇴가 완료되었습니다.');
      navigate('/', { replace: true });
    } catch (ex) {
      setError(message(ex));
      setBusy(false);
    }
  }
  return (
    <section className="withdraw-account" aria-labelledby={id + '-heading'}>
      <h2 id={id + '-heading'}>회원탈퇴</h2>
      <p>탈퇴하면 모든 기기에서 로그아웃되며 이 계정으로 다시 로그인할 수 없습니다.</p>
      {!open ? (
        <button type="button" className="secondary" onClick={() => setOpen(true)}>회원탈퇴 진행</button>
      ) : (
        <form className="form" onSubmit={withdraw}>
          <p className="callout">
            {user.role === 'COMPANY'
              ? '기업의 채용공고가 마감되고 진행 중인 지원과 예정된 면접이 취소됩니다.'
              : '진행 중인 지원과 예정된 면접이 취소되고 취업연계가 종료됩니다.'}
            {' '}프로필 사진은 삭제됩니다. 계정 정보와 기존 지원·면접 등 활동 기록은 보관되며, 탈퇴 후 직접 조회하거나 수정할 수 없습니다. 같은 아이디·이메일{user.role === 'COMPANY' ? '·사업자번호' : ''}로 재가입할 수 없습니다.
          </p>
          <div className="form-field">
            <label htmlFor={id + '-password'}>현재 비밀번호</label>
            <input id={id + '-password'} type="password" autoComplete="current-password" required maxLength={64} value={password} disabled={busy} onChange={(e) => setPassword(e.target.value)} />
          </div>
          <label className="checkbox">
            <input type="checkbox" checked={confirmed} disabled={busy} onChange={(e) => setConfirmed(e.target.checked)} required />
            안내 내용을 확인했으며 회원탈퇴에 동의합니다.
          </label>
          {error && <p role="alert">{error}</p>}
          <div className="actions">
            <button type="submit" disabled={busy || !confirmed}>{busy ? '탈퇴 처리 중…' : '회원탈퇴 확정'}</button>
            <button type="button" className="secondary" disabled={busy} onClick={() => { setOpen(false); setPassword(''); setConfirmed(false); setError(''); }}>취소</button>
          </div>
        </form>
      )}
    </section>
  );
}
