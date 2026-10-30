import { useRecentJobs } from '../stores/recentJobs';
import CompanyCover from '../components/common/CompanyCover';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { useEffect, useState, useRef } from 'react';
import { useResource } from '../hooks/useResource';
import { useAuth } from '../stores/auth';
import ApplyForm from '../components/common/ApplyForm';
import ApplyButton from '../components/common/ApplyButton';
import RegionPicker from '../components/common/RegionPicker';
import CompanyImage from '../components/common/CompanyImage';
import BookmarkButton from '../components/common/BookmarkButton';
import {
  Action,
  Badge,
  Empty,
  JobCard,
  PagedResource,
  PagedRows,
  Pagination,
  Resource,
  Title,
} from '../components/common/UI';



export function Home() {
  const jobs = useResource('/jobs?size=10&page=0');
  const notices = useResource('/notices');

  return (
    <div className="home-page">
      <HomePromotion />

      <section className="quick-links">
        {[
          ['⌕', '나에게 맞는 채용', '조건에 맞는 기회를 찾아보세요', '/jobs'],
          ['▤', '이력서 관리', '나의 경험과 강점을 정리하세요', '/resumes'],
          ['↗', '취업연계 서비스', '상담부터 취업까지 함께해요', '/employment'],
          ['▦', '함께하는 기업', '성장할 수 있는 팀을 만나세요', '/companies'],
        ].map(([icon, title, desc, to]) => (
          <Link key={to} to={to}>
            <span className="quick-icon">{icon}</span>
            <div>
              <strong>{title}</strong>
              <small>{desc}</small>
            </div>
            <span>↗</span>
          </Link>
        ))}
      </section>

      <section className="section">
        <div className="section-heading">
          <div>
            <p className="eyebrow">FIND YOUR OPPORTUNITY</p>
            <h2>지금, 주목할 만한 채용</h2>
            <p>새로운 동료를 기다리는 기업들을 만나보세요.</p>
          </div>
          <Link className="text-link" to="/jobs">
            전체 공고 보기 →
          </Link>
        </div>

        <Resource resource={jobs}>
          {(data) => {
            const allJobs = data.content || [];

            if (!allJobs.length) {
              return <Empty>현재 게시된 채용공고가 없습니다.</Empty>;
            }

            return (
              <>
                <div className="regular-job-grid">
                  {allJobs.map((job) => (
                    <div className="regular-job-item" key={job.jobId}>
                      <JobCard job={job} />
                    </div>
                  ))}
                </div>

              </>
            );
          }}
        </Resource>
      </section>

      <section className="support-banner">
        <div>
          <p className="eyebrow">WE ARE HERE FOR YOU</p>
          <h2>취업 준비, 혼자 고민하지 마세요.</h2>
          <p>상담부터 기업 매칭까지, 취업연계 서비스가 함께합니다.</p>
        </div>
        <Link className="button" to="/employment">
          취업연계 알아보기 ↗
        </Link>
      </section>

      <section className="section notice-preview">
        <div className="section-heading">
          <h2>잡브릿지 소식</h2>
          <Link to="/notices">전체 보기 →</Link>
        </div>

        <Resource resource={notices}>
          {(rows) =>
            rows.length ? (
              rows.slice(0, 3).map((n) => (
                <Link
                  className="notice-row"
                  key={n.noticeId}
                  to={`/notices/${n.noticeId}`}
                >
                  <Badge value="공지" />
                  <strong>{n.title}</strong>
                  <span>{n.createdAt?.slice(0, 10)}</span>
                  <span>→</span>
                </Link>
              ))
            ) : (
              <p className="muted">등록된 공지사항이 없습니다.</p>
            )
          }
        </Resource>
      </section>
    </div>
  );
}



function HomePromotion() {
  const [slide, setSlide] = useState(0);
  const [paused, setPaused] = useState(false);
  const [interacting, setInteracting] = useState(false);
  const [reducedMotion, setReducedMotion] = useState(() => window.matchMedia('(prefers-reduced-motion: reduce)').matches);
  useEffect(() => {
    const query = window.matchMedia('(prefers-reduced-motion: reduce)');
    const update = () => setReducedMotion(query.matches);
    query.addEventListener('change', update);
    return () => query.removeEventListener('change', update);
  }, []);
  useEffect(() => {
    if (paused || interacting || reducedMotion) return;
    const timer = window.setInterval(() => setSlide((value) => (value + 1) % 3), 3000);
    return () => window.clearInterval(timer);
  }, [paused, interacting, reducedMotion, slide]);
  const labels = ['잡브릿지 소개', '함께하는 기업', '이력서 작성 안내'];
  return <section className={'hero promo-carousel promo-theme-' + slide} aria-label="잡브릿지 홍보 배너" aria-roledescription="캐러셀"
    onFocusCapture={() => setInteracting(true)} onBlurCapture={(event) => { if (!event.currentTarget.contains(event.relatedTarget)) setInteracting(false); }}>
    <div className="promo-slide" key={slide} role="group" aria-roledescription="슬라이드" aria-label={(slide + 1) + ' / 3 · ' + labels[slide]}>
      {slide === 0 ? <>
        <div className="hero-copy">
          <p className="eyebrow">YOUR NEXT CHAPTER STARTS HERE</p>
          <h1>
            당신의 가능성이,
            <br />
            <em>새로운 기회</em>를 만나는 곳.
          </h1>
          <p>
            나에게 맞는 일자리부터 든든한 취업 지원까지.
            <br />
            잡브릿지가 다음 커리어를 함께 연결합니다.
          </p>
          <Search />
          <div className="popular">
            <span>추천 검색어</span>
            {['개발자', '디자이너', '기획', '신입'].map((tag) => (
              <Link key={tag} to={`/jobs?q=${encodeURIComponent(tag)}`}>
                {tag}
              </Link>
            ))}
          </div>
        </div>
        <div className="hero-art" aria-hidden="true">
          <div className="orbit orbit-one" />
          <div className="orbit orbit-two" />
          <div className="floating float-top">
            <span className="art-icon">✦</span>
            <div>
              <strong>새로운 시작을 응원해요</strong>
              <small>당신에게 꼭 맞는 다음 기회</small>
            </div>
            <span className="tiny-check">✓</span>
          </div>
          <div className="bridge">
            <div className="bridge-line" />
            <div className="bridge-pillar left" />
            <div className="bridge-pillar right" />
            <span className="bridge-dot dot-one" />
            <span className="bridge-dot dot-two" />
          </div>
          <div className="floating float-bottom">
            <span className="art-icon blue">↗</span>
            <div>
              <strong>가능성을 커리어로</strong>
              <small>한 걸음 더 가까워진 내일</small>
            </div>
          </div>
          <span className="spark spark-one">✳</span>
          <span className="spark spark-two">＋</span>
        </div>
</> : slide === 1 ? <>
        <div className="hero-copy"><p className="eyebrow">MEET YOUR NEXT TEAM</p><h1>좋은 동료와 함께,<br /><em>더 크게 성장하세요.</em></h1><p>새로운 가능성을 만드는 기업들.<br />기업 소개와 채용 소식을 한곳에서 만나보세요.</p><Link className="button promo-cta" to="/companies">함께하는 기업 만나보기 ↗</Link><div className="promo-keywords"><span>기업 이야기</span><span>일하는 문화</span><span>새로운 기회</span></div></div>
        <div className="promo-illustration promo-teams" aria-hidden="true"><div className="promo-orb" /><div className="promo-team-card team-back"><span className="promo-logo purple">ON</span><div><strong>아이디어가 자라는 곳</strong><small>함께 만드는 더 나은 경험</small></div></div><div className="promo-team-card team-front"><span className="promo-logo">BL</span><div><small>YOUR NEXT TEAM</small><strong>함께 성장할 동료를 찾아요</strong><p>개발 · 디자인 · 기획</p><span className="promo-mini-tags">협업 &nbsp; / &nbsp; 성장 &nbsp; / &nbsp; 도전</span></div></div><div className="promo-team-note">✦ 가능성을 연결하는 팀</div></div>
      </> : <>
        <div className="hero-copy"><p className="eyebrow">BUILD YOUR CAREER STORY</p><h1>차곡차곡 쌓은 경험,<br /><em>멋진 기회가 되도록.</em></h1><p>나만의 강점을 담은 이력서를 준비해 보세요.<br />작성부터 지원까지, 잡브릿지가 함께합니다.</p><Link className="button promo-cta" to="/resumes">내 이력서 준비하기 ↗</Link><div className="promo-keywords"><span>경험 정리</span><span>강점 발견</span><span>커리어 시작</span></div></div>
        <div className="promo-illustration promo-resume" aria-hidden="true"><div className="promo-orb" /><div className="promo-paper"><span className="promo-paper-label">MY CAREER STORY</span><div className="promo-profile"><span>나</span><div><strong>나의 가능성을 소개합니다</strong><small>경험과 강점을 한눈에</small></div></div><div className="promo-paper-line" /><div className="promo-paper-line short" /><div className="promo-paper-section">01 &nbsp; 나의 경험과 프로젝트</div><div className="promo-paper-line" /><div className="promo-paper-line short" /><div className="promo-paper-section">02 &nbsp; 함께 성장할 나의 강점</div></div><div className="promo-resume-note">✓ 다음 기회를 향한 준비</div></div>
      </>}
    </div>
    <div className="promo-controls"><div className="promo-dots">{labels.map((label, index) => <button key={label} type="button" className={slide === index ? 'active' : ''} aria-label={label + ' 배너 보기'} aria-current={slide === index ? 'true' : undefined} onClick={() => setSlide(index)} />)}</div><span className="promo-counter">{slide + 1} / 3</span><button type="button" className="promo-control" aria-label="이전 배너" onClick={() => setSlide((slide + 2) % 3)}>‹</button><button type="button" className="promo-control" aria-label={paused || reducedMotion ? '배너 자동 전환 시작' : '배너 자동 전환 일시정지'} onClick={() => { setPaused(!(paused || reducedMotion)); setReducedMotion(false); }}>{paused || reducedMotion ? '▶' : 'Ⅱ'}</button><button type="button" className="promo-control" aria-label="다음 배너" onClick={() => setSlide((slide + 1) % 3)}>›</button></div>
  </section>;
}

function Search({ initial = '' }) {
  const [q, setQ] = useState(initial);
  const navigate = useNavigate();
  const [params] = useSearchParams();
  useEffect(() => setQ(initial), [initial]);
  return (
    <form
      className="search"
      autoComplete="off"
      onSubmit={(e) => {
        e.preventDefault();
        const next = new URLSearchParams(params);
        next.set('q', q.trim());
        next.set('page', '0');
        navigate(`/jobs?${next.toString()}`);
      }}
    >
      <span aria-hidden="true">⌕</span>
      <input
        aria-label="채용공고 검색"
        placeholder="어떤 기회를 찾고 계신가요?"
        value={q}
        onChange={(e) => setQ(e.target.value)}
      />
      <button>검색하기</button>
    </form>
  );
}

export function Jobs() {
  const [params, setParams] = useSearchParams();
  const resource = useResource(`/jobs?${params.toString()}`);
  const page = Number(params.get('page') || 0);
  function filter(key, value) {
    const next = new URLSearchParams(params);
    next.set(key, value);
    if (key !== 'page') next.set('page', '0');
    setParams(next);
  }
  return (
    <>
      <Title
        eyebrow="EXPLORE OPPORTUNITIES"
        description="당신의 다음 커리어를 이곳에서 찾아보세요."
      >
        채용공고
      </Title>
      <Search initial={params.get('q') || ''} />
      <div className="filters">
        <RegionPicker
          value={params.get('region') || ''}
          onChange={(value) => filter('region', value)}
        />
        {[
          ['type', '고용형태', ['정규직', '계약직', '인턴']],
          ['career', '경력', ['신입', '경력']],
        ].map(([key, label, options]) => (
          <select
            key={key}
            aria-label={label}
            aria-invalid={key === 'type' && !!params.get(key) && !options.includes(params.get(key))}
            value={params.get(key) || ''}
            onChange={(e) => filter(key, e.target.value)}
          >
            <option value="">{label} 전체</option>
            {key === 'type' && params.get(key) && !options.includes(params.get(key)) && (
              <option value={params.get(key)} disabled>
                잘못된 고용형태 · 다시 선택해 주세요
              </option>
            )}
            {options.map((x) => (
              <option key={x}>{x}</option>
            ))}
          </select>
        ))}
        <select
          aria-label="정렬"
          value={params.get('sort') || 'latest'}
          onChange={(e) => filter('sort', e.target.value)}
        >
          <option value="latest">최신 등록순</option>
          <option value="deadline">마감 임박순</option>
        </select>
      </div>
      <Resource resource={resource}>
        {(data) => (
          <>
            <p className="result-count">
              총 <strong>{data.totalElements}</strong>개의 기회
            </p>
            {data.content.length ? (
              <div className="job-grid">
                {data.content.map((j) => (
                  <JobCard key={j.jobId} job={j} />
                ))}
              </div>
            ) : (
              <Empty>검색 조건에 맞는 공고가 없습니다.</Empty>
            )}
            <Pagination
              page={page}
              totalPages={data.totalPages}
              total={data.totalElements}
              onChange={(next) => filter('page', String(next))}
            />
          </>
        )}
      </Resource>
    </>
  );
}

export function JobDetail() {
  const { id } = useParams();
  const resource = useResource(`/jobs/${id}`);
  const user = useAuth((s) => s.user);
  const [applying, setApplying] = useState(false);
  const [done, setDone] = useState(false);
  const remember = useRecentJobs((state) => state.remember);

  useEffect(() => {
    if (!resource.loading && !resource.error && String(resource.data?.jobId) === String(id)) {
      remember(user?.userId || 'guest', resource.data);
    }
  }, [id, resource.data, resource.loading, resource.error, user?.userId, remember]);

  return (
    <Resource resource={resource}>
      {(job) => applying && !done ? (
        <>
          <button className="plain back" onClick={() => setApplying(false)}>
            ← 공고 상세로 돌아가기
          </button>
          <ApplyForm
            id={id}
            onDone={() => {
              setDone(true);
              setApplying(false);
              window.scrollTo(0, 0);
            }}
          />
        </>
      ) : (
        <>
          <Link className="back" to="/jobs">
            ← 채용공고 목록
          </Link>
          <div className="detail-layout">
            <article className="panel job-detail">
              <p className="eyebrow">{job.companyName}</p>
              <h1>{job.title}</h1>
              <div className="tags">
                <span>{job.jobType}</span>
                <span>{job.careerType}</span>
                <Badge value={job.accepting ? 'PUBLISHED' : 'CLOSED'} />
              </div>
              <dl className="facts">
                {[
                  ['근무지역', job.workLocation],
                  ['경력', job.careerType],
                  ['학력', job.education],
                  ['급여', job.salary],
                  ['모집인원', `${job.headcount}명`],
                  ['접수기간', `${job.startDate} ~ ${job.endDate}`],
                ].map(([k, v]) => (
                  <div key={k}>
                    <dt>{k}</dt>
                    <dd>{v || '별도 안내'}</dd>
                  </div>
                ))}
              </dl>
              {[
                ['상세 소개', job.description],
                ['자격요건', job.requirements],
                ['우대사항', job.preferredQualifications],
              ].map(([k, v]) => (
                <section className="prose" key={k}>
                  <h2>{k}</h2>
                  <p>{v || '별도 안내'}</p>
                </section>
              ))}
            </article>

            <aside className="panel apply-panel">
              <CompanyImage companyId={job.companyId} companyName={job.companyName} className="company-symbol large" />
              <h3>{job.companyName}</h3>
              <p className="muted">
                새로운 가능성을 함께 만들어갈
                <br />
                동료를 기다립니다.
              </p>

              {/* 🗺️ 채용 공고 상세 우측에 지도 추가 */}
              <CompanyMap 
                address={job.address || job.workLocation} 
                latitude={job.latitude} 
                longitude={job.longitude} 
              />

              <Link to={`/companies/${job.companyId}`}>기업 자세히 보기 ↗</Link>
              <hr />
              <p>
                접수 마감 <strong>{job.endDate}</strong>
              </p>
              {done ? (
                <p className="success" role="status">
                  지원이 완료되었습니다. <Link to="/applications">내역 보기</Link>
                </p>
              ) : user?.role === 'JOB_SEEKER' ? (
                <>
                  <ApplyButton id={id} accepting={job.accepting} onApply={() => setApplying(!applying)} />
                </>
              ) : (
                <Link className="button" to="/login">
                  로그인하고 지원하기
                </Link>
              )}
              {user?.role === 'JOB_SEEKER' && <BookmarkButton jobId={id} />}
            </aside>
          </div>
        </>
      )}
    </Resource>
  );
}


// 지도 렌더링 컴포넌트

function CompanyMap({ address, latitude, longitude }) {
  const mapRef = useRef(null);
  const [mapError, setMapError] = useState('');

  useEffect(() => {
    let cancelled = false;
    let timer = null;
    let map = null;
    let marker = null;
    let attempts = 0;

    setMapError('');

    const cleanAddress = String(address ?? '').trim();

    const lat = Number(latitude);
    const lng = Number(longitude);

    const hasCoordinates =
      latitude !== null &&
      latitude !== undefined &&
      String(latitude).trim() !== '' &&
      longitude !== null &&
      longitude !== undefined &&
      String(longitude).trim() !== '' &&
      Number.isFinite(lat) &&
      Number.isFinite(lng) &&
      lat >= -90 &&
      lat <= 90 &&
      lng >= -180 &&
      lng <= 180 &&
      (lat !== 0 || lng !== 0);

    const initializeMap = () => {
      if (cancelled || !mapRef.current) return;

      const kakao = window.kakao;

      if (!kakao?.maps?.load) {
        if (attempts++ < 100) {
          timer = window.setTimeout(initializeMap, 100);
        } else {
          setMapError(
            '카카오 지도 SDK를 불러오지 못했습니다. 앱 키와 도메인을 확인해 주세요.'
          );
        }
        return;
      }

      kakao.maps.load(() => {
        if (cancelled || !mapRef.current) return;

        // 지도를 새로 만들기 전에 기존 DOM을 정리합니다.
        mapRef.current.innerHTML = '';

        // 주소 검색 전에는 전국이 보이는 위치를 사용합니다.
        const initialPosition = hasCoordinates
          ? new kakao.maps.LatLng(lat, lng)
          : new kakao.maps.LatLng(36.5, 127.8);

        map = new kakao.maps.Map(mapRef.current, {
          center: initialPosition,
          level: 3,
        });

        const showLocation = (position) => {
          if (cancelled || !map) return;

          marker = new kakao.maps.Marker({
            position,
            map,
          });

          map.setCenter(position);
          map.setLevel(3);
        };

        const useSavedCoordinates = () => {
          if (hasCoordinates) {
            showLocation(initialPosition);
            setMapError(
              '주소 검색에 실패하여 저장된 좌표를 표시하고 있습니다. 실제 위치와 일치하는지 확인해 주세요.'
            );
          } else {
            setMapError(
              '주소를 찾지 못했습니다. 기업 주소를 확인해 주세요.'
            );
          }
        };

        // 주소가 있으면 주소 검색 결과를 우선 사용합니다.
        if (cleanAddress) {
          if (!kakao.maps.services?.Geocoder) {
            setMapError(
              '주소 검색 기능을 사용할 수 없습니다. SDK의 libraries=services 설정을 확인해 주세요.'
            );
            useSavedCoordinates();
            return;
          }

          const geocoder =
            new kakao.maps.services.Geocoder();

          geocoder.addressSearch(
            cleanAddress,
            (result, status) => {
              if (cancelled) return;

              if (
                status === kakao.maps.services.Status.OK &&
                result?.length
              ) {
                // 검색 결과의 좌표가 유효한지 확인합니다.
                const resultLat = Number(result[0].y);
                const resultLng = Number(result[0].x);

                if (
                  Number.isFinite(resultLat) &&
                  Number.isFinite(resultLng) &&
                  resultLat >= -90 &&
                  resultLat <= 90 &&
                  resultLng >= -180 &&
                  resultLng <= 180
                ) {
                  showLocation(
                    new kakao.maps.LatLng(resultLat, resultLng)
                  );
                  return;
                }
              }

              useSavedCoordinates();
            }
          );

          return;
        }

        // 주소가 없을 때만 저장된 좌표를 사용합니다.
        if (hasCoordinates) {
          showLocation(initialPosition);
        } else {
          setMapError(
            '기업 주소 또는 유효한 위치 좌표가 등록되지 않았습니다.'
          );
        }
      });
    };

    initializeMap();

    return () => {
      cancelled = true;

      if (timer !== null) {
        window.clearTimeout(timer);
      }

      if (marker) {
        marker.setMap(null);
      }

      // 다음 초기화를 위해 지도 DOM을 정리합니다.
      if (mapRef.current) {
        mapRef.current.innerHTML = '';
      }

      map = null;
      marker = null;
    };
  }, [address, latitude, longitude]);

  return (
    <div
      className="company-map-wrapper"
      style={{ margin: '20px 0' }}
    >
      <div
        ref={mapRef}
        style={{
          width: '100%',
          height: '280px',
          borderRadius: '8px',
          backgroundColor: '#f0f0f0',
          border: '1px solid #ddd',
        }}
      />

      <p
        className="muted"
        style={{
          fontSize: '13px',
          marginTop: '8px',
          color: '#666',
        }}
      >
        📍 {address || '주소 정보가 없습니다.'}
      </p>

      {mapError && (
        <p
          role="alert"
          style={{
            color: '#c0392b',
            fontSize: '13px',
          }}
        >
          {mapError}
        </p>
      )}
    </div>
  );
}


export function Companies() {
  const resource = useResource('/companies');
  return (
    <>
      <Title description="함께 일하고 싶은 기업을 만나보세요.">함께하는 기업</Title>
      <PagedResource resource={resource}>
        {(rows) =>
          rows.length ? (
            <div className="job-grid">
              {rows.map((c) => (
                <Link
                  className="panel company-card company-card-photo"
                  key={c.companyId}
                  to={`/companies/${c.companyId}`}
                >
                  <CompanyCover companyName={c.companyName} industry={c.industry} bannerImageUrl={c.bannerImageUrl} className="company-list-cover" />
                  <CompanyImage companyId={c.companyId} companyName={c.companyName} className="company-symbol large" />
                  <div className="company-card-heading"><h2>{c.companyName}</h2>{c.description?.startsWith('[예시 기업]') && <span className="company-demo-label">예시 기업</span>}</div>
                  <p className="company-industry">{c.industry || '산업 정보 준비 중'}</p>
                  <p className="muted">
                    {[c.companySize, c.address].filter(Boolean).join(' · ') || '기업 정보를 준비하고 있습니다.'}
                  </p>
                  <p className="company-card-intro">{c.description?.replace(/^\[예시 기업\][^\n]*\n*/, '').split('\n').find((line) => line.trim()) || '새로운 가능성을 함께 만들어갈 팀을 만나보세요.'}</p><span className="text-link">기업 알아보기 ↗</span>
                </Link>
              ))}
            </div>
          ) : (
            <Empty />
          )
        }
      </PagedResource>
    </>
  );
}

export function CompanyDetail() {
  const { id } = useParams();
  const resource = useResource(`/companies/${id}`);
  
  return (
    <Resource resource={resource}>
      {(c) => (
        <>
          <CompanyCover companyName={c.companyName} industry={c.industry} bannerImageUrl={c.bannerImageUrl} className="company-detail-cover" />
          <CompanyImage companyId={c.companyId} companyName={c.companyName} className="company-symbol large" />
          <Title description={c.industry}>{c.companyName}</Title>
          <article className="panel prose">
            <h2>기업 소개</h2>
            <p>{c.description || '기업 소개를 준비하고 있습니다.'}</p>
            <p>규모: {c.companySize || '별도 안내'}</p>
            <p>주소: {c.address || '별도 안내'}</p>
            
            {/* 🗺️ 기업 위치 지도 컴포넌트 삽입 */}
            <CompanyMap 
              address={c.address} 
              latitude={c.latitude} 
              longitude={c.longitude} 
            />

            {c.website && (
              <a href={c.website} target="_blank" rel="noreferrer">
                기업 홈페이지 ↗
              </a>
            )}
          </article>
        </>
      )}
    </Resource>
  );
}

export function Notices() {
  const { id } = useParams();
  return <NoticeBoard key={id || 'list'} id={id} />;
}

function NoticeBoard({ id }) {
  const resource = useResource(id ? '/notices/' + id : '/notices');
  const [query, setQuery] = useState('');
  return (
    <div className="notice-board">
      <Title description="잡브릿지의 서비스 소식과 중요한 안내를 확인하세요.">공지사항</Title>
      <Resource resource={resource}>
        {(data) => {
          if (id) return <article className="notice-detail">
            <header className="notice-detail-heading"><span className="notice-category">{data.isPinned ? '중요 공지' : '공지사항'}</span><h2>{data.title}</h2><p>잡브릿지 운영팀 <span>·</span> {data.createdAt?.slice(0, 10).replaceAll('-', '.')}</p></header>
            <div className="notice-detail-body">
              {Boolean(data.hasImage) && <img className="notice-content-image" src={'/api/notices/' + data.noticeId + '/image'} alt={data.title + ' 안내 이미지'} />}
              <div className="notice-copy">{data.content.split(/\n\s*\n/).map((paragraph, index) => <p key={index}>{paragraph}</p>)}</div>
            </div>
            <footer className="notice-detail-footer"><Link className="button secondary" to="/notices">목록으로 돌아가기</Link></footer>
          </article>;
          const rows = data.filter((notice) => notice.title.toLocaleLowerCase().includes(query.trim().toLocaleLowerCase()));
          return <>
            <div className="notice-list-tools"><p>전체 <strong>{rows.length}</strong>건</p><label><span className="notice-search-label">공지 검색</span><input type="search" value={query} placeholder="제목으로 검색해 보세요" onChange={(e) => setQuery(e.target.value)} /></label></div>
            {rows.length ? <PagedRows key={query} rows={rows}>{(pageRows) => <div className="notice-table-wrap"><table className="notice-table"><thead><tr><th scope="col">구분</th><th scope="col">제목</th><th scope="col">등록일</th></tr></thead><tbody>{pageRows.map((notice) => <tr key={notice.noticeId} className={notice.isPinned ? 'notice-pinned' : ''}><td><span className="notice-table-category">{notice.isPinned ? '중요' : '공지'}</span></td><td><Link to={'/notices/' + notice.noticeId}>{notice.title}{Boolean(notice.hasImage) && <span className="notice-image-mark" aria-label="안내 이미지 포함">이미지</span>}</Link></td><td><time>{notice.createdAt?.slice(0, 10).replaceAll('-', '.')}</time></td></tr>)}</tbody></table></div>}</PagedRows> : <Empty>{query ? '검색 결과가 없습니다.' : '등록된 공지사항이 없습니다.'}</Empty>}
          </>;
        }}
      </Resource>
    </div>
  );

  
}
function PremiumJobBanner({ job }) {
  return (
    <Link
      to={`/jobs/${job.jobId}`}
      className="premium-banner"
      aria-label={`${job.companyName} ${job.title} 채용공고 보기`}
    >
      <img
        className="premium-banner-image"
        src={job.bannerImageUrl}
        alt=""
      />

      <div className="premium-banner-overlay">
        <span className="premium-banner-label">PREMIUM 채용</span>
        <h3>{job.title}</h3>
        <p>{job.companyName} · {job.workLocation}</p>
      </div>
    </Link>
  );
}

export default PremiumJobBanner;