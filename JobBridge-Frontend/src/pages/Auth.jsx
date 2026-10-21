import { useState } from 'react';
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
    { name: 'phone', label: '전화번호', type: 'tel', required: true },
    {
      name: 'password',
      label: '비밀번호 (공백 없이 8~20자)',
      type: 'password',
      required: true,
      minLength: 8,
      maxLength: 20,
      pattern: '\\S{8,20}',
      autoComplete: 'new-password',
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
          await client.post(`/auth/signup/${kind}`, body);
          navigate('/login', {
            replace: true,
            state: {
              signupComplete: true,
              memberType: kind === 'company' ? 'COMPANY' : 'JOB_SEEKER',
            },
          });
        }}
        submit="가입하기"
      />
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
