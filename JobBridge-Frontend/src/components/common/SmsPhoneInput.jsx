import { useEffect, useState } from 'react';
import client, { message } from '../../api/client';
import { MobileInput } from './MemberInputs';

export default function SmsPhoneInput({ id, value, onChange, onVerified, serverError }) {
  const [challenge, setChallenge] = useState(null);
  const [code, setCode] = useState('');
  const [verified, setVerified] = useState(null);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState('');
  const [now, setNow] = useState(Date.now);
  const remaining = Math.max(0, Math.ceil(((challenge?.expiresAt || 0) - now) / 1000));
  const cooldown = Math.max(0, Math.ceil(((challenge?.resendAt || 0) - now) / 1000));
  const complete = verified?.phone === value && verified.expiresAt > now;
  useEffect(() => {
    if (!challenge && !verified) return;
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, [challenge, verified]);
  async function send() {
    setBusy(true); setNotice('');
    try {
      const { data } = await client.post('/auth/sms/send', { phone: value });
      const time = Date.now(); setNow(time);
      setChallenge({ id: data.challengeId, expiresAt: time + data.expiresIn * 1000, resendAt: time + data.resendAfter * 1000 });
      setVerified(null); onVerified(''); setCode(''); setNotice(challenge ? '인증번호를 재전송했습니다.' : '인증번호를 발송했습니다.');
    } catch (error) { setNotice(message(error)); }
    finally { setBusy(false); }
  }
  async function verify() {
    setBusy(true); setNotice('');
    try {
      const { data } = await client.post('/auth/sms/verify', { phone: value, code, challengeId: challenge.id });
      const time = Date.now(); setNow(time); setVerified({ phone: value, expiresAt: time + data.expiresIn * 1000 });
      onVerified(data.verificationToken); setNotice('휴대폰 인증이 완료되었습니다.');
    } catch (error) { setNotice(message(error)); }
    finally { setBusy(false); }
  }
  function changePhone(phone) {
    onChange(phone); setChallenge(null); setVerified(null); setCode(''); setNotice('');
  }
  return <div className="sms-auth-section">
    <div className="sms-input-row"><MobileInput id={id} name="phone" value={value} onChange={changePhone} required /><button type="button" className="secondary" disabled={busy || complete || cooldown > 0 || !/^01[016789][0-9]{7,8}$/.test(value)} onClick={send}>{busy ? '처리 중…' : complete ? '인증 완료' : cooldown > 0 ? cooldown + '초 후 재전송' : challenge ? '인증번호 재전송' : '인증번호 전송'}</button></div>
    {challenge && !complete && <div className="sms-code-row"><label className="sms-code-label" htmlFor={id + '-code'}>인증번호</label><div className="sms-code-input"><input id={id + '-code'} inputMode="numeric" autoComplete="one-time-code" maxLength={6} placeholder="인증번호 6자리" value={code} onChange={(event) => setCode(event.target.value.replace(/[^0-9]/g, ''))} /><span>{Math.floor(remaining / 60)}:{String(remaining % 60).padStart(2, '0')}</span></div><button type="button" disabled={busy || remaining === 0 || code.length !== 6} onClick={verify}>확인</button></div>}
    {(serverError || (notice && (!verified || complete))) && <p className={'sms-feedback' + (complete ? ' verified' : '')} role="status">{serverError || notice}{!serverError && challenge && !complete && remaining > 0 && <span className="sms-notice-countdown"> · 유효시간 {Math.floor(remaining / 60)}:{String(remaining % 60).padStart(2, '0')}{cooldown > 0 && ` · 재전송 ${cooldown}초 후`}</span>}</p>}
    {challenge && !complete && remaining === 0 && <p className="sms-feedback" role="status">인증 시간이 만료되었습니다. 인증번호를 다시 요청해 주세요.</p>}
    {verified && !complete && <p className="sms-feedback" role="status">인증 유효시간이 지났습니다. 다시 인증해 주세요.</p>}
  </div>;
}
