import { Link } from 'react-router-dom';

export default function PromotionSlide({ slide, preview = false, searchContent }) {
  const tags = slide.tags || String(slide.tagsText || '').split(',').map((tag) => tag.trim()).filter(Boolean);
  const photoThemes = ['career', 'team', 'resume', 'matching', 'support', 'interview'];
  return <div className="promo-slide">
    <div className="hero-copy">
      <p className="eyebrow">{slide.eyebrow}</p>
      <h1>{slide.title}<br /><em>{slide.highlight}</em>{slide.titleSuffix}</h1>
      <p style={{ whiteSpace: 'pre-line' }}>{slide.description}</p>
      {slide.search ? <>
        {preview ? <div className="search"><span aria-hidden="true">⌕</span><input aria-label="검색창 미리보기" placeholder="어떤 기회를 찾고 계신가요?" disabled /><span className="button">검색하기</span></div> : searchContent}
        <div className="popular"><span>추천 검색어</span>{tags.map((tag, index) => preview ? <span key={index}>{tag}</span> : <Link key={index} to={`/jobs?q=${encodeURIComponent(tag)}`}>{tag}</Link>)}</div>
      </> : <>
        {preview ? <span className="button promo-cta">{slide.cta} ↗</span> : <Link className="button promo-cta" to={slide.to}>{slide.cta} ↗</Link>}
        <div className="promo-keywords">{tags.map((tag, index) => <span key={index}>{tag}</span>)}</div>
      </>}
    </div>
    <div className={'promo-photo-visual promo-photo-' + (photoThemes[Number(slide.theme)] || 'career')}>
      <span className="promo-photo-halo" aria-hidden="true" />
      <div className="promo-photo-frame"><img src={slide.imageUrl} alt={slide.imageAlt} width="1200" height="900" decoding="async" /></div>
      {slide.photoTag && <span className="promo-photo-tag"><span aria-hidden="true">✦</span> {slide.photoTag}</span>}
      <div className="promo-photo-card"><span className="promo-photo-mark" aria-hidden="true">{slide.cardMark}</span><div><small>{slide.cardEyebrow}</small><strong>{slide.cardTitle}</strong><p>{slide.cardSubtitle}</p></div></div>
    </div>
  </div>;
}
