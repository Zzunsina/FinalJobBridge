import { useEffect, useRef, useState } from 'react';

let postcodeLoader;
function loadPostcode() {
  if (window.kakao?.Postcode) return Promise.resolve(window.kakao.Postcode);
  if (!postcodeLoader) {
    postcodeLoader = new Promise((resolve, reject) => {
      const script = document.createElement('script');
      script.src = 'https://t1.kakaocdn.net/mapjsapi/bundle/postcode/prod/postcode.v2.js';
      script.onload = () => {
        if (window.kakao?.Postcode) resolve(window.kakao.Postcode);
        else {
          postcodeLoader = undefined;
          script.remove();
          reject(
            new Error(
              '주소 검색을 불러오지 못했습니다. 다시 시도하거나 주소를 직접 입력해 주세요.',
            ),
          );
        }
      };
      script.onerror = () => {
        postcodeLoader = undefined;
        script.remove();
        reject(
          new Error(
            '주소 검색에 연결할 수 없습니다. 인터넷 연결을 확인하거나 주소를 직접 입력해 주세요.',
          ),
        );
      };
      document.head.appendChild(script);
    });
  }
  return postcodeLoader;
}

export function AddressInput({ id, name, value, onChange, required, maxLength = 255 }) {
  const [open, setOpen] = useState(false);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [base, setBase] = useState('');
  const [detail, setDetail] = useState('');
  const dialog = useRef(null);
  const host = useRef(null);
  const detailInput = useRef(null);
  const onSelect = useRef(onChange);
  onSelect.current = onChange;
  useEffect(() => {
    if (!open) return;
    let active = true;
    const modal = dialog.current;
    modal.showModal();
    setLoading(true);
    setError('');
    loadPostcode()
      .then((Postcode) => {
        if (!active) return;
        new Postcode({
          width: '100%',
          height: '100%',
          oncomplete(data) {
            if (!active) return;
            const address = data.userSelectedType === 'R' ? data.roadAddress : data.jibunAddress;
            const fullAddress = data.buildingName ? `${address} (${data.buildingName})` : address;
            setBase(fullAddress);
            setDetail('');
            onSelect.current(fullAddress);
            setOpen(false);
            detailInput.current?.focus();
          },
        }).embed(host.current);
        setLoading(false);
      })
      .catch((e) => {
        if (active) {
          setError(e.message);
          setLoading(false);
        }
      });
    return () => {
      active = false;
      modal.close();
    };
  }, [open]);
  return (
    <div className="address-field">
      <div className="input-with-action">
        <input
          id={id}
          name={name}
          value={value}
          required={required}
          maxLength={maxLength}
          placeholder="주소·건물명 찾기 또는 직접 입력"
          onChange={(e) => {
            setBase(e.target.value);
            setDetail('');
            onChange(e.target.value);
          }}
        />
        <button type="button" className="secondary" onClick={() => setOpen(true)}>
          찾기
        </button>
      </div>
      <input
        ref={detailInput}
        aria-label="상세주소 또는 장소"
        value={detail}
        placeholder="상세주소·층·호수·면접실 (선택)"
        maxLength={100}
        onChange={(e) => {
          setDetail(e.target.value);
          onChange(`${base || value} ${e.target.value}`.trim());
        }}
      />
      <dialog ref={dialog} className="address-dialog" onCancel={() => setOpen(false)}>
        <div className="row">
          <h2>주소·장소 찾기</h2>
          <button type="button" className="secondary" onClick={() => setOpen(false)}>
            닫기
          </button>
        </div>
        <p className="muted">도로명, 지번, 건물명으로 검색해 주세요.</p>
        {loading && <p role="status">주소 검색을 불러오는 중입니다…</p>}
        {error && (
          <p className="error" role="alert">
            {error}
          </p>
        )}
        <div ref={host} className="postcode-host" />
      </dialog>
    </div>
  );
}

export function ConfirmedDateTime({ id, name, value, onChange, required }) {
  const [draft, setDraft] = useState(value || '');
  const [acknowledged, setAcknowledged] = useState(Boolean(value));
  const input = useRef(null);
  const confirmed = acknowledged && Boolean(value) && value === draft;
  return (
    <div>
      <div className="input-with-action">
        <input
          ref={input}
          id={id}
          name={name}
          type="datetime-local"
          value={draft}
          required={required}
          onChange={(e) => {
            setAcknowledged(false);
            setDraft(e.target.value);
            e.target.setCustomValidity('면접 날짜와 시간을 선택한 후 확인 버튼을 눌러 주세요.');
          }}
        />
        <button
          type="button"
          className={confirmed ? 'secondary' : ''}
          disabled={!draft}
          onClick={() => {
            input.current.setCustomValidity('');
            if (input.current.reportValidity()) {
              setAcknowledged(true);
              onChange(draft);
            }
          }}
        >
          {confirmed ? '확인됨' : '확인'}
        </button>
      </div>
      <small className={confirmed ? 'success-text' : 'muted'} role="status">
        {confirmed
          ? `${new Date(`${value}:00+09:00`).toLocaleString('ko-KR', { timeZone: 'Asia/Seoul' })} (한국 시간)으로 선택했습니다.`
          : '날짜와 시간을 선택한 뒤 확인을 눌러 주세요.'}
      </small>
    </div>
  );
}

export function AlphanumericInput({ id, value, onChange, ...props }) {
  const [draft, setDraft] = useState('');
  const [warning, setWarning] = useState(false);
  const composing = useRef(false);
  const [isComposing, setIsComposing] = useState(false);
  function commit(text) {
    setWarning(/[^A-Za-z0-9]/.test(text));
    onChange(text.replace(/[^A-Za-z0-9]/g, ''));
  }
  return (
    <>
      <input
        {...props}
        id={id}
        value={isComposing ? draft : value}
        aria-describedby={warning ? id + '-hint' : undefined}
        onCompositionStart={(e) => {
          composing.current = true;
          setDraft(e.currentTarget.value);
          setIsComposing(true);
          setWarning(true);
        }}
        onCompositionEnd={(e) => {
          composing.current = false;
          setIsComposing(false);
          commit(e.currentTarget.value);
        }}
        onChange={(e) => {
          if (composing.current || e.nativeEvent.isComposing) {
            setDraft(e.target.value);
          } else {
            commit(e.target.value);
          }
        }}
      />
      {warning && (
        <p id={id + '-hint'} className="input-guidance" role="status">
          아이디는 영문과 숫자만 입력할 수 있어요. 한/영 키로 영문으로 전환해 주세요.
        </p>
      )}
    </>
  );
}
