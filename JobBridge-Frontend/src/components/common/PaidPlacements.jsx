import { Link } from 'react-router-dom';
import { useResource } from '../../hooks/useResource';
import '../../assets/styles/advertising.css';

export default function PaidPlacements({ placement = 'COMPANY' }) {
  const resource = useResource('/ads/active?placement=' + placement, { refreshInterval: 30000 });
  const rows = resource.data || [];
  if (!rows.length) return null;
  return (
    <section className="section paid-placement">
      <div className="section-heading">
        <div>
          <p className="eyebrow">
            {placement === 'COMPANY' ? 'MEET YOUR NEXT TEAM' : 'FEATURED OPPORTUNITIES'}
          </p>
          <h2>{placement === 'COMPANY' ? '함께 성장할 추천 기업' : '주목할 채용공고'}</h2>
        </div>
        <Link to="/company/advertising">광고 상품 안내</Link>
      </div>
      <div className={placement === 'COMPANY' ? 'paid-company-grid' : 'paid-job-list'}>
        {rows.map((r) => {
          const content = (
            <>
              {placement === 'COMPANY' && <img src={r.imageUrl} alt={r.companyName + ' 광고'} />}
              <div>
                <span className="paid-ad-tag">광고</span>
                <h3>{placement === 'COMPANY' ? r.companyName : r.title}</h3>
                <p>{placement === 'COMPANY' ? r.industry : r.companyName}</p>
              </div>
            </>
          );
          return r.linkType === 'external' ? (
            <a
              className="paid-company-card"
              key={r.orderId}
              href={r.to}
              target="_blank"
              rel="noopener noreferrer sponsored"
            >
              {content}
            </a>
          ) : (
            <Link
              className={placement === 'COMPANY' ? 'paid-company-card' : ''}
              key={r.orderId}
              to={r.to}
            >
              {content}
            </Link>
          );
        })}
      </div>
    </section>
  );
}
