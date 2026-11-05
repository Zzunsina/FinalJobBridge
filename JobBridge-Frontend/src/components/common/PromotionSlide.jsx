import { Link } from 'react-router-dom';
import LegacyPromotionSlide from './LegacyPromotionSlide';

export default function PromotionSlide({ slide, preview = false, searchContent }) {
  // Preserve previously customized compositions until their completed artwork is uploaded.
  if (slide.presentation !== 'banner') return <LegacyPromotionSlide slide={slide} preview={preview} searchContent={searchContent} />;
  const nativeArtwork = slide.artworkLayout === 'native-2-1' || /^\/(?:api\/site-images\/)?images\/home-banners-v1\/(career|team|resume|matching|support|interview)\.png$/.test(slide.imageUrl);
  const image=<img className={'promotion-complete-image' + (nativeArtwork ? ' promotion-layout-v2' : '')} src={nativeArtwork ? slide.imageUrl + (slide.imageUrl.includes('?') ? '&' : '?') + 'layout=photo-v3' : slide.imageUrl} alt={slide.imageAlt} width="1200" height={nativeArtwork ? '600' : '520'} decoding="async" />;
  const content=<>{image}{slide.isAd && <span className="promotion-ad-label">광고</span>}</>;
  if (preview) return <div className="promotion-complete-banner">{content}</div>;
  return slide.linkType === 'external'
    ? <a className="promotion-complete-banner" href={slide.to} target="_blank" rel={slide.isAd ? 'noopener noreferrer sponsored' : 'noopener noreferrer'} aria-label={slide.imageAlt + ' (새 탭에서 열림)'}>{content}</a>
    : <Link className="promotion-complete-banner" to={slide.to}>{content}</Link>;
}
