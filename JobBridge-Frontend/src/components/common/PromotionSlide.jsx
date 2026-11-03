import { Link } from 'react-router-dom';
import LegacyPromotionSlide from './LegacyPromotionSlide';

export default function PromotionSlide({ slide, preview = false, searchContent }) {
  // Preserve previously customized compositions until their completed artwork is uploaded.
  if (slide.presentation !== 'banner') return <LegacyPromotionSlide slide={slide} preview={preview} searchContent={searchContent} />;
  const nativeArtwork = /^\/images\/home-banners-v1\/(career|team|resume|matching|support|interview)\.png$/.test(slide.imageUrl);
  const image=<img className={'promotion-complete-image' + (nativeArtwork ? ' promotion-layout-v2' : '')} src={nativeArtwork ? slide.imageUrl + '?layout=photo-v3' : slide.imageUrl} alt={slide.imageAlt} width="1200" height={nativeArtwork ? '600' : '520'} decoding="async" />;
  return preview ? <div className="promotion-complete-banner">{image}</div> : <Link className="promotion-complete-banner" to={slide.to}>{image}</Link>;
}
