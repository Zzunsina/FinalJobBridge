import { useEffect, useMemo, useState } from 'react';
import { useResource } from '../../hooks/useResource';
import PromotionSlide from './PromotionSlide';

export default function HomePromotion({ searchContent }) {
  const resource = useResource('/home-promotions', { refreshInterval: 30000 });
  const slides = useMemo(
    () => (Array.isArray(resource.data) ? resource.data.filter((slide) => !slide.isAd) : []),
    [resource.data],
  );
  const [activeKey, setActiveKey] = useState(null);
  const [paused, setPaused] = useState(false);
  const [interacting, setInteracting] = useState(false);
  const [reducedMotion, setReducedMotion] = useState(
    () => window.matchMedia('(prefers-reduced-motion: reduce)').matches,
  );
  const index = Math.max(
    0,
    slides.findIndex((slide) => slide.key === activeKey),
  );
  const current = slides[index];
  useEffect(() => {
    const query = window.matchMedia('(prefers-reduced-motion: reduce)');
    const update = () => setReducedMotion(query.matches);
    query.addEventListener('change', update);
    return () => query.removeEventListener('change', update);
  }, []);
  useEffect(() => {
    if (paused || interacting || reducedMotion || slides.length <= 1) return;
    const timer = window.setTimeout(
      () => setActiveKey(slides[(index + 1) % slides.length].key),
      5000,
    );
    return () => window.clearTimeout(timer);
  }, [paused, interacting, reducedMotion, slides, index]);
  if (!current) return null;
  const advance = (delta) =>
    setActiveKey(slides[(index + slides.length + delta) % slides.length].key);
  return (
    <section
      className={
        'hero promo-carousel ' +
        (current.presentation === 'banner'
          ? 'promo-banner-carousel'
          : 'promo-theme-' + current.theme)
      }
      data-promotion-key={current.key}
      aria-label="잡브릿지 홍보 배너"
      aria-roledescription="캐러셀"
      onFocusCapture={() => setInteracting(true)}
      onBlurCapture={(event) => {
        if (!event.currentTarget.contains(event.relatedTarget)) setInteracting(false);
      }}
    >
      <div
        key={`${current.key}:${current.version}`}
        role="group"
        aria-roledescription="슬라이드"
        aria-label={`${index + 1} / ${slides.length} · ${current.label}`}
      >
        <PromotionSlide slide={current} searchContent={searchContent} />
      </div>
      {slides.length > 1 && (
        <div className="promo-controls">
          <div className="promo-dots">
            {slides.map((slide, i) => (
              <button
                key={slide.key}
                type="button"
                className={i === index ? 'active' : ''}
                aria-label={slide.label + ' 배너 보기'}
                aria-current={i === index ? 'true' : undefined}
                onClick={() => setActiveKey(slide.key)}
              />
            ))}
          </div>
          <span className="promo-counter">
            {index + 1} / {slides.length}
          </span>
          <button
            type="button"
            className="promo-control"
            aria-label="이전 배너"
            onClick={() => advance(-1)}
          >
            ‹
          </button>
          <button
            type="button"
            className="promo-control"
            aria-label={paused || reducedMotion ? '배너 자동 전환 시작' : '배너 자동 전환 일시정지'}
            onClick={() => {
              setPaused(!(paused || reducedMotion));
              setReducedMotion(false);
            }}
          >
            {paused || reducedMotion ? '▶' : 'Ⅱ'}
          </button>
          <button
            type="button"
            className="promo-control"
            aria-label="다음 배너"
            onClick={() => advance(1)}
          >
            ›
          </button>
        </div>
      )}
    </section>
  );
}
