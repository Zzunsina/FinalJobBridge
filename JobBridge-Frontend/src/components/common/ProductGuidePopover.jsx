import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { useResource } from '../../hooks/useResource';
import { useAuth } from '../../stores/auth';

const placementText = {
  SLIDE: '대형 이미지 카드 · 한 줄 3개',
  COMPANY: '이미지형 카드 · 한 줄 4개',
  JOB: '간결한 정보 카드 · 한 줄 4개',
};

export default function ProductGuidePopover() {
  const [open, setOpen] = useState(false);
  const root = useRef(null);
  const trigger = useRef(null);
  const resource = useResource('/ads/products');
  const user = useAuth((s) => s.user);

  useEffect(() => {
    if (!open) return;

    const outside = (e) => {
      if (!root.current?.contains(e.target)) setOpen(false);
    };

    const key = (e) => {
      if (e.key === 'Escape') {
        setOpen(false);
        trigger.current?.focus();
      }
    };

    document.addEventListener('pointerdown', outside);
    document.addEventListener('keydown', key);

    return () => {
      document.removeEventListener('pointerdown', outside);
      document.removeEventListener('keydown', key);
    };
  }, [open]);

  // 구직자(JOB_SEEKER)이거나 로그인하지 않은 경우 버튼을 숨김
  if (!user || user.role === 'JOB_SEEKER') {
    return null;
  }

  return (
    <div className="tier-product-guide" ref={root}>
      <button
        type="button"
        className="tier-product-guide-trigger"
        ref={trigger}
        aria-expanded={open}
        aria-controls="home-product-guide"
        title="상품안내"
        onClick={() => setOpen(!open)}
      >
        <svg width="15" height="15" viewBox="0 0 20 20" fill="none" aria-hidden="true">
          <circle cx="10" cy="10" r="8" stroke="currentColor" />
          <path d="M10 9v5M10 5.5v1" stroke="currentColor" strokeWidth="1.6" />
        </svg>
        상품안내
      </button>

      {open && (
        <section className="tier-product-guide-popover" id="home-product-guide" role="dialog" aria-label="채용 상품 안내">
          <header>
            <h3>채용 상품 안내</h3>
            <button
              type="button"
              className="tier-guide-close"
              aria-label="상품 안내 닫기"
              onClick={() => {
                setOpen(false);
                trigger.current?.focus();
              }}
            >
              ×
            </button>
          </header>

          {resource.loading && !resource.data ? (
            <p>상품 정보를 불러오는 중입니다.</p>
          ) : resource.error ? (
            <p>
              상품 정보를 불러오지 못했습니다.{' '}
              <button className="secondary" onClick={resource.reload}>다시 시도</button>
            </p>
          ) : resource.data?.length ? (
            resource.data.map((p) => (
              <article className="tier-guide-product" key={p.productCode}>
                <div>
                  <h4>{p.name}</h4>
                  <p>
                    <strong>{Number(p.price).toLocaleString('ko-KR')}원</strong>
                    <span> / {Number(p.durationDays) === 7 ? '주' : p.durationDays + '일'}</span>
                  </p>
                  <details>
                    <summary>노출 방식 보기</summary>
                    <div>
                      {placementText[p.productCode]}
                      <br />
                      {Number(p.maxSlots) === 0 ? '노출 개수 제한 없음' : '동시 노출 최대 ' + p.maxSlots + '개'}
                    </div>
                  </details>
                </div>
                <Link className="button" to={'/company/advertising?product=' + p.productCode}>
                  신청하기
                </Link>
              </article>
            ))
          ) : (
            <p>판매 중인 상품이 없습니다.</p>
          )}

          <small className="tier-guide-note">부가세 별도 · 심사 승인 후 결제 및 노출</small>
        </section>
      )}
    </div>
  );
}