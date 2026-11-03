import { useState } from 'react';
import { companyCoverSources } from '../../utils/companyCoverSources';

export function companyTheme({ companyName = '', industry = '' }) {
  const value = companyName + ' ' + industry;
  if (/에버그린|친환경|라이프스타일/.test(value)) return 'forest';
  if (/모빌리티|자동차|운송/.test(value)) return 'mobility';
  if (/데이터|인공지능|AI/.test(value)) return 'data';
  if (/마켓|커머스|유통/.test(value)) return 'commerce';
  if (/디자인|콘텐츠/.test(value)) return 'design';
  if (/브릿지|소프트웨어|개발|IT/.test(value)) return 'software';
  return 'business';
}

export default function CompanyCover({ companyId, companyName, industry, bannerImageUrl, profileImageUrl, className = '' }) {
  const theme = companyTheme({ companyName, industry });
  const [failedSources, setFailedSources] = useState([]);
  const candidates = companyCoverSources({ companyId, bannerImageUrl, profileImageUrl, theme });
  const source = candidates.find((item) => !failedSources.includes(item));
  return (
    <div className={`company-cover ${className}`} aria-hidden="true">
      {source && <img src={source} onError={() => setFailedSources((old) => old.includes(source) ? old : [...old, source])} alt="" loading="lazy" width="1774" height="887" />}
    </div>
  );
}
