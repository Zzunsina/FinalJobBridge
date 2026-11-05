import { preservedCompanyCovers } from './preservedCompanyCovers.js';

export function companyCoverSources({ companyId, bannerImageUrl, profileImageUrl, theme = 'business' }) {
  const uploaded = typeof bannerImageUrl === 'string' ? bannerImageUrl.trim() : '';
  const profile = profileImageUrl || (Number.isInteger(Number(companyId)) && Number(companyId) > 0 ? `/api/companies/${companyId}/image` : '');
  return [...new Set([uploaded, preservedCompanyCovers[Number(companyId)], profile, `/api/site-images/images/company-covers-v2/${theme}.jpg`].filter(Boolean))];
}
