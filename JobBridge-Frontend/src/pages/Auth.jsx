import { useState, useEffect } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import client from '../api/client';
import { useAuth } from '../stores/auth';
import { Form } from '../components/common/UI';

export function Login({ adminOnly = false }) {
  const location = useLocation();
  const [memberType, setMemberType] = useState(
    location.state?.memberType === 'COMPANY' ? 'COMPANY' : 'JOB_SEEKER',
  );
  const login = useAuth((s) => s.login);
  const navigate = useNavigate();
  return (
    <div className="auth-shell">
      {!adminOnly && location.state?.signupComplete && (
        <div className="signup-complete" role="status">
          <span className="signup-complete-icon" aria-hidden="true">
            ✓
          </span>
          <div>
            <strong>회원가입이 완료되었습니다.</strong>
            <p>가입한 아이디로 로그인해 주세요.</p>
          </div>
          <button
            type="button"
            className="plain"
            aria-label="회원가입 완료 안내 닫기"
            onClick={() => navigate(location.pathname, { replace: true, state: null })}
          >
            ×
          </button>
        </div>
      )}
      <div className="auth-brand">
        <img src="/images/jobbridge-logo.jpg" alt="잡브릿지 로고" />
      </div>
      <p className="eyebrow">{adminOnly ? 'ADMINISTRATOR LOGIN' : 'JOBBRIDGE LOGIN'}</p>
      <h1>{adminOnly ? '관리자 로그인' : '새로운 기회를 연결하세요.'}</h1>
      <p className="muted">
        {adminOnly
          ? '관리자 계정으로 로그인해 주세요.'
          : '회원 유형을 선택하고 아이디로 로그인해 주세요.'}
      </p>
      {!adminOnly && (
        <div className="tabs" role="group" aria-label="로그인 회원 유형">
          <button
            type="button"
            aria-pressed={memberType === 'JOB_SEEKER'}
            className={memberType === 'JOB_SEEKER' ? '' : 'secondary'}
            onClick={() => setMemberType('JOB_SEEKER')}
          >
            개인
          </button>
          <button
            type="button"
            aria-pressed={memberType === 'COMPANY'}
            className={memberType === 'COMPANY' ? '' : 'secondary'}
            onClick={() => setMemberType('COMPANY')}
          >
            기업
          </button>
        </div>
      )}
      <Form
        key={adminOnly ? 'admin' : memberType}
        fields={[
          {
            name: 'loginId',
            label: '아이디',
            required: true,
            maxLength: 30,
            autoComplete: 'username',
            autoCapitalize: 'none',
          },
          {
            name: 'password',
            label: '비밀번호',
            type: 'password',
            required: true,
            maxLength: 64,
          },
        ]}
        onSubmit={async (body) => {
          const user = await login(adminOnly ? body : { ...body, memberType }, adminOnly);
          navigate(user.role === 'ADMIN' ? '/admin' : user.role === 'COMPANY' ? '/company' : '/');
        }}
        submit={
          adminOnly ? '관리자 로그인' : memberType === 'COMPANY' ? '기업 로그인' : '개인 로그인'
        }
      />
      <div className="auth-links">
        {adminOnly ? (
          <Link to="/login">일반 회원 로그인으로 이동</Link>
        ) : (
          <>
            <Link to="/find-password">비밀번호 찾기</Link>
            <Link to="/signup">회원가입 →</Link>
          </>
        )}
      </div>
    </div>
  );
}

export function Signup() {
  const [kind, setKind] = useState('job-seeker');
  const navigate = useNavigate();

  // --- 문자 인증 및 비밀번호 확인 상태 ---
  const [phoneValue, setPhoneValue] = useState('');
  const [passwordValue, setPasswordValue] = useState('');
  const [passwordConfirmValue, setPasswordConfirmValue] = useState('');
  const [verificationCode, setVerificationCode] = useState('');
  const [isCodeSent, setIsCodeSent] = useState(false);
  const [isPhoneVerified, setIsPhoneVerified] = useState(false);
  const [timer, setTimer] = useState(180); // 3분 타이머 (초)
  const [isTimerActive, setIsTimerActive] = useState(false);

  // 타이머 작동 로직
  useEffect(() => {
    let interval = null;
    if (isTimerActive && timer > 0) {
      interval = setInterval(() => setTimer((prev) => prev - 1), 1000);
    } else if (timer === 0) {
      setIsTimerActive(false);
    }
    return () => clearInterval(interval);
  }, [isTimerActive, timer]);

  // 인증번호 전송 요청 함수
  const handleSendSms = async () => {
    if (!phoneValue || phoneValue.length < 10) {
      alert('올바른 휴대폰 번호를 입력해주세요.');
      return;
    }
    try {
      await client.post('/auth/sms/send', { phone: phoneValue });
      alert('인증번호가 발송되었습니다.');
      setIsCodeSent(true);
      setTimer(180);
      setIsTimerActive(true);
    } catch (error) {
      alert('인증번호 전송에 실패했습니다. 다시 시도해주세요.');
    }
  };

  // 인증번호 확인 요청 함수
  const handleVerifySms = async () => {
    if (!verificationCode) {
      alert('인증번호를 입력해주세요.');
      return;
    }
    try {
      await client.post('/auth/sms/verify', { phone: phoneValue, code: verificationCode });
      alert('휴대폰 인증이 완료되었습니다.');
      setIsPhoneVerified(true);
      setIsTimerActive(false);
    } catch (error) {
      alert('인증번호가 일치하지 않거나 만료되었습니다.');
    }
  };

  // 💡 아이디 바로 다음(비밀번호, 비밀번호 확인)과 기본 정보 필드 구성
  const fields = [
    {
      name: 'loginId',
      label: '아이디 (영문·숫자, 4~30자)',
      required: true,
      minLength: 4,
      maxLength: 30,
      pattern: '[A-Za-z0-9]{4,30}',
      sanitize: 'alphanumeric',
      autoComplete: 'username',
      autoCapitalize: 'none',
    },
    {
      name: 'password',
      label: '비밀번호 (공백 없이 8~20자)',
      type: 'password',
      required: true,
      minLength: 8,
      maxLength: 20,
      pattern: '\\S{8,20}',
      autoComplete: 'new-password',
      onChange: (e) => setPasswordValue(e.target.value),
    },
    {
      name: 'passwordConfirm',
      label: '비밀번호 확인',
      type: 'password',
      required: true,
      minLength: 8,
      maxLength: 20,
      autoComplete: 'new-password',
      onChange: (e) => setPasswordConfirmValue(e.target.value),
    },
    { name: 'name', label: '이름', required: true, maxLength: 100 },
    {
      name: 'birthDate',
      label: '생년월일 (8자리)',
      required: true,
      inputMode: 'numeric',
      placeholder: '예: 20000123',
      minLength: 8,
      maxLength: 8,
      pattern: '[0-9]{8}',
      sanitize: 'digits',
      autoComplete: 'bday',
    },
    {
      name: 'gender',
      label: '성별',
      required: true,
      options: [
        { value: 'MALE', label: '남자' },
        { value: 'FEMALE', label: '여자' },
      ],
    },
    {
      name: 'email',
      label: '이메일',
      type: 'email-select',
      required: true,
      maxLength: 255,
    },
  ];

  if (kind === 'company')
    fields.push(
      { name: 'companyName', label: '기업명', required: true, maxLength: 200 },
      {
        name: 'businessNumber',
        label: '사업자등록번호 (10자리)',
        required: true,
      },
    );

  return (
    <div className="auth-shell">
      <p className="eyebrow">JOIN JOBBRIDGE</p>
      <h1>새로운 시작, 함께해요.</h1>
      <div className="tabs">
        <button
          className={kind === 'job-seeker' ? '' : 'secondary'}
          onClick={() => setKind('job-seeker')}
        >
          구직자 회원
        </button>
        <button
          className={kind === 'company' ? '' : 'secondary'}
          onClick={() => setKind('company')}
        >
          기업 회원
        </button>
      </div>
      {kind === 'company' && (
        <p className="callout">기업 회원은 관리자 승인 후 공고 심사를 요청할 수 있습니다.</p>
      )}
      <Form
        key={kind}
        fields={fields}
        onSubmit={async (body) => {
          // 비밀번호 일치 여부 확인
          if (body.password !== passwordConfirmValue) {
            alert('비밀번호가 일치하지 않습니다.');
            return;
          }

          // 휴대폰 인증 완료 여부 확인
          if (!isPhoneVerified) {
            alert('휴대폰 문자 인증을 완료해주세요.');
            return;
          }

          // 서버로 회원가입 요청 (전화번호 값 명시적 전달)
          await client.post(`/auth/signup/${kind}`, {
            ...body,
            phone: phoneValue,
          });

          navigate('/login', {
            replace: true,
            state: {
              signupComplete: true,
              memberType: kind === 'company' ? 'COMPANY' : 'JOB_SEEKER',
            },
          });
        }}
        submit="가입하기"
      >
        {/* 💡 휴대폰 번호 입력 및 문자 인증 UI 영역 */}
        <div className="sms-auth-section" style={{ marginTop: '5px', marginBottom: '15px' }}>
          <label style={{ display: 'block', fontSize: '0.9rem', fontWeight: '500', marginBottom: '5px' }}>
            휴대폰번호 <span style={{ color: 'red' }}>*</span>
          </label>
          <div style={{ display: 'flex', gap: '8px', marginBottom: '8px' }}>
            <input
              type="tel"
              placeholder="숫자만 입력 (예: 01012345678)"
              value={phoneValue}
              onChange={(e) => setPhoneValue(e.target.value)}
              disabled={isPhoneVerified}
              style={{ flex: 1, padding: '8px' }}
            />
            <button 
              type="button" 
              className="secondary" 
              onClick={handleSendSms} 
              disabled={isPhoneVerified}
            >
              {isCodeSent ? '인증번호 재전송' : '인증번호 전송'}
            </button>
          </div>

          {isCodeSent && !isPhoneVerified && (
            <div style={{ display: 'flex', gap: '8px', alignItems: 'center', marginBottom: '8px' }}>
              <input
                type="text"
                placeholder="인증번호 6자리 입력"
                value={verificationCode}
                onChange={(e) => setVerificationCode(e.target.value)}
                maxLength={6}
                style={{ flex: 1, padding: '8px' }}
              />
              <button type="button" onClick={handleVerifySms}>
                확인
              </button>
              <span style={{ color: 'red', fontSize: '0.9rem', whiteSpace: 'nowrap' }}>
                {Math.floor(timer / 60)}:{String(timer % 60).padStart(2, '0')}
              </span>
            </div>
          )}

          {isPhoneVerified && (
            <p style={{ color: 'green', fontSize: '0.9rem', marginTop: '5px' }}>
              ✓ 휴대폰 인증이 완료되었습니다.
            </p>
          )}
        </div>
      </Form>
      <p>
        이미 계정이 있으신가요? <Link to="/login">로그인</Link>
      </p>
    </div>
  );
}

export function FindPassword() {
  return (
    <div className="auth-shell">
      <h1>비밀번호 재설정</h1>
      <p className="callout">
        메일 서비스가 아직 설정되지 않았습니다. 운영자에게 비밀번호 재설정을 문의해 주세요.
      </p>
      <Link to="/login">로그인으로 돌아가기</Link>
    </div>
  );
}