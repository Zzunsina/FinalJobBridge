import ProductGuidePopover from './ProductGuidePopover';
import { Link } from 'react-router-dom';
import { useResource } from '../../hooks/useResource';
import CompanyImage from './CompanyImage';
import { koreanToday } from '../../utils/memberInputs';
import '../../assets/styles/tiered-job-ads.css';

const tiers = [
  { code: 'SLIDE', name: '프리미엄', style: 'premium', limit: 9 },
  { code: 'COMPANY', name: '스탠다드', style: 'standard', limit: 20 },
  { code: 'JOB', name: '베이직', style: 'basic', limit: null },
];
function AdCard({ ad, tier }) {
  const days = Math.round(
    (Date.parse(ad.endDate + 'T00:00:00+09:00') - Date.parse(koreanToday() + 'T00:00:00+09:00')) /
      86400000,
  );
  const content = (
    <>
      {tier.style !== 'basic' && <img className="tier-ad-cover" src={ad.imageUrl} alt="" loading="lazy" />}
      <div className="tier-ad-shade" />
      <div className="tier-ad-top">
        {tier.style === 'premium' && <span className="tier-ad-premium-tag">프리미엄</span>}
        <span className="tier-ad-flags">
          <strong>
            {Number.isFinite(days) ? (days === 0 ? '오늘 마감' : 'D-' + days) : '채용 중'}
          </strong>
        </span>
      </div>
      <div className="tier-ad-body">
        {tier.style !== 'basic' && <CompanyImage companyId={ad.companyId} companyName={ad.companyName} />}
        <div>
          <p>{ad.companyName}</p>
          <h3>{ad.title || ad.companyName + ' 채용 안내'}</h3>
          <small>
            {ad.workLocation || ad.address} {ad.careerType && ' · ' + ad.careerType}
          </small>
        </div>
        {tier.style === 'premium' && <span className="tier-ad-cta">공고 보기</span>}
      </div>
    </>
  );
  return (
    <article className={'tier-ad-card tier-ad-' + tier.style} data-ad-order={ad.orderId}>
      {ad.linkType === 'external' ? (
        <a
          href={ad.to}
          target="_blank"
          rel="noopener noreferrer sponsored"
          aria-label={ad.companyName + ' ' + ad.title + ' (새 탭)'}
        >
          {content}
        </a>
      ) : (
        <Link to={ad.to} aria-label={ad.companyName + ' ' + ad.title}>
          {content}
        </Link>
      )}
    </article>
  );
}
export default function TieredJobAds() {
  const premium = useResource('/ads/active?placement=SLIDE', { refreshInterval: 30000 }),
    standard = useResource('/ads/active?placement=COMPANY', { refreshInterval: 30000 }),
    basic = useResource('/ads/active?placement=JOB', { refreshInterval: 30000 });
  const resources = [premium, standard, basic];
  return (
    <section className="section tiered-job-ads" data-scroll-loading={resources.some(resource => resource.loading && !resource.data) ? "true" : undefined}>
      <div className="section-heading">
        <div>
          <p className="eyebrow">FIND YOUR OPPORTUNITY</p>
          <h2>지금, 주목할 만한 채용</h2>
          <p>성장을 함께할 기업과 새로운 기회를 만나보세요.</p>
        </div>
        <div className="tier-heading-actions"><Link className="tier-all-jobs" to="/jobs">전체 채용공고 보기</Link><ProductGuidePopover /></div>
      </div>
      {tiers.map((tier, i) => {
        const resource = resources[i],
          rows = resource.data || [];
        return (
          <section
            className={'tier-ad-section tier-section-' + tier.style}
            key={tier.code}
            aria-label={tier.name + ' 채용 정보'}
          >
            <div className="tier-ad-heading">
              <h3>{tier.name}</h3>
              {rows.length > 1 && !resource.error && (
                <span className="tier-ad-swipe-hint">옆으로 밀어 더 보기 →</span>
              )}
            </div>
            {resource.error ? (
              <p className="tier-ad-empty">
                채용 정보를 불러오지 못했습니다.{' '}
                <button className="secondary" onClick={resource.reload}>
                  다시 시도
                </button>
              </p>
            ) : resource.loading && !resource.data ? (
              <p className="tier-ad-empty">채용 정보를 불러오는 중입니다.</p>
            ) : rows.length ? (
              <div className={'tier-ad-grid tier-grid-' + tier.style}>
                {(tier.limit == null ? rows : rows.slice(0, tier.limit)).map((ad) => (
                  <AdCard key={ad.orderId} ad={ad} tier={tier} />
                ))}
              </div>
            ) : (
              <div className="tier-ad-empty">{tier.name} 채용 정보를 준비 중입니다.</div>
            )}
          </section>
        );
      })}
    </section>
  );
}
