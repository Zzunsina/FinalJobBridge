import { useEffect, useId, useRef } from 'react';

export const AGREEMENT_VERSION = '2026-10-21';
export const emptyAgreements = () => ({ termsAccepted: false, privacyAccepted: false, marketingAccepted: false });

const agreements = [
  {
    key: 'termsAccepted', title: '이용약관 동의', required: true,
    sections: [
      ['서비스 이용', '잡브릿지는 채용공고 조회, 이력서 작성, 입사지원, 기업의 채용 관리와 취업연계 기능을 제공합니다. 개인회원과 기업회원은 본인 또는 소속 기업의 정확한 정보로 가입해야 합니다.'],
      ['회원의 책임', '타인의 계정이나 개인정보를 사용하거나 허위 이력서·채용공고를 등록할 수 없습니다. 회원은 자신의 계정과 비밀번호를 관리하고, 등록·제출하는 정보의 정확성을 확인해야 합니다.'],
      ['채용과 취업연계', '기업은 지원자를 검토하고 채용 여부를 결정합니다. 취업연계는 상담과 기업 매칭을 지원하는 서비스이며, 가입이나 신청만으로 채용이 보장되지는 않습니다.'],
      ['이용 중단 및 탈퇴', '회원은 내 정보에서 탈퇴할 수 있습니다. 탈퇴하면 로그인할 수 없으며 진행 중 지원과 예정된 면접은 취소되고, 기업의 채용공고는 마감됩니다. 문의는 서비스 내 고객센터를 이용해 주세요.'],
    ],
  },
  {
    key: 'privacyAccepted', title: '개인정보 수집·이용 동의', required: true,
    sections: [
      ['수집 항목', '가입 시 아이디, 비밀번호, 이름, 생년월일, 성별, 이메일, 휴대폰번호를 수집합니다. 기업회원은 기업명과 사업자등록번호도 입력합니다. 프로필 사진과 이력서 등 추가 정보는 회원이 등록하는 경우 저장됩니다.'],
      ['이용 목적', '회원 식별과 로그인, 본인 계정 관리, 입사지원 및 기업의 지원자 검토, 면접 안내, 취업연계 상담과 고객 문의 처리를 위해 사용합니다. 비밀번호는 암호화된 해시로 저장됩니다.'],
      ['보관 및 탈퇴 처리', '서비스 이용 중 계정 정보와 등록·제출한 활동 기록이 저장됩니다. 현재 탈퇴 처리에서는 프로필 사진이 삭제되고 계정 이용이 차단되며, 계정 정보·이력서·제출된 지원 및 면접 기록 등은 남습니다.'],
      ['동의 거부', '필수 개인정보 수집·이용에 동의하지 않으면 회원가입을 진행할 수 없습니다.'],
    ],
  },
  {
    key: 'marketingAccepted', title: '광고성 정보 수신 동의', required: false,
    sections: [
      ['선택 동의', '입력한 이메일과 휴대폰번호로 채용 관련 소식, 서비스 혜택 및 이벤트 등 광고성 정보를 받는 데 동의합니다.'],
      ['동의 거부', '동의하지 않아도 회원가입과 기본 서비스 이용에 제한이 없습니다. 입사지원 상태·면접 등 서비스 이용에 필요한 안내와는 별개의 선택 항목입니다.'],
    ],
  },
];

export default function SignupAgreements({ value, onChange }) {
  const id = useId();
  const allInput = useRef(null);
  const count = agreements.filter((item) => value[item.key]).length;
  useEffect(() => { if (allInput.current) allInput.current.indeterminate = count > 0 && count < agreements.length; }, [count]);
  return (
    <fieldset className="signup-agreements">
      <legend>약관 및 개인정보 동의</legend>
      <label className="agreement-all">
        <input ref={allInput} type="checkbox" checked={count === agreements.length} onChange={(e) => onChange(Object.fromEntries(agreements.map((item) => [item.key, e.target.checked])))} />
        <span>전체 동의<small>필수 항목과 선택 광고성 정보 수신에 모두 동의합니다.</small></span>
      </label>
      {agreements.map((item) => (
        <div className="agreement-item" key={item.key}>
          <label className="agreement-check" htmlFor={id + item.key}>
            <input id={id + item.key} type="checkbox" required={item.required} checked={value[item.key]} onChange={(e) => onChange({ ...value, [item.key]: e.target.checked })} />
            <span><strong className={item.required ? 'agreement-required' : 'agreement-optional'}>[{item.required ? '필수' : '선택'}]</strong> {item.title}</span>
          </label>
          <details>
            <summary>{item.title} 내용 보기</summary>
            <div className="agreement-body">
              <p>버전: {AGREEMENT_VERSION}</p>
              {item.sections.map(([title, content]) => <section key={title}><h3>{title}</h3><p>{content}</p></section>)}
            </div>
          </details>
        </div>
      ))}
      <p className="agreement-note">필수 항목에 동의하면 가입할 수 있습니다. 선택 항목은 동의하지 않아도 됩니다.</p>
    </fieldset>
  );
}
