import { useState } from 'react';

export default function CompanyImage({ companyId, companyName, className = 'company-symbol', admin = false }) {
  const [failed, setFailed] = useState(null);
  if (!companyId || failed === companyId) return <span className={className} aria-hidden="true">{companyName?.slice(0, 1) || '기업'}</span>;
  return <img className={className + ' company-image'} src={(admin ? '/api/admin/companies/' : '/api/companies/') + companyId + '/image'} alt={(companyName || '기업') + ' 대표 이미지'} loading="lazy" onError={() => setFailed(companyId)} />;
}
