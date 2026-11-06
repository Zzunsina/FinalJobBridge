import { hasFormChanges, NO_CHANGES } from '../utils/formChanges';
import AdAvailability from '../components/common/AdAvailability';
import { useAdAvailability } from '../hooks/useAdAvailability';
import { ordersForAdminTab } from '../utils/advertisingOrders';
import { requestPortOneV1 } from '../api/portoneV1';
import { useEffect, useRef, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import client, { message } from '../api/client';
import { useResource } from '../hooks/useResource';
import { Resource, Title } from '../components/common/UI';
import { koreanToday } from '../utils/memberInputs';
import { formatDateTime } from '../utils/dateTime';
import '../assets/styles/advertising.css';

const won = (n) => Number(n || 0).toLocaleString('ko-KR') + '원';
const labels = {
  READY: '결제 미완료',
  FAILED: '결제 실패',
  PAID: '결제 완료',
  UNPAID: '미결제',
  PAYMENT_PENDING: '결제 대기',
  PAYMENT_EXPIRED: '결제 기한 경과',
  WITHDRAWN: '신청 철회',
  CANCELLED: '결제 취소',
  PARTIAL_CANCELLED: '일부 취소',
  AWAITING_PAYMENT: '신청 미접수',
  PENDING_REVIEW: '심사 대기',
  CHANGES_REQUESTED: '보완 요청',
  APPROVED: '승인',
  PAUSED: '노출 중지',
  STOPPED: '종료',
  JOB_CLOSED: '공고 마감·변경으로 신청 취소',
  JOB_STOPPED: '공고 마감·변경으로 노출 중단',
  NONE: '없음',
  REQUESTED: '환불 요청',
  COMPLETED: '환불 완료',
  REFUND_REQUESTED: '환불 요청',
  REFUND_COMPLETED: '환불 완료',
  REFUND_PARTIAL: '일부 환불',
  LIVE: '노출 중',
  SCHEDULED: '노출 예정',
  EXPIRED: '기간 종료',
};
function Status({ value }) {
  return <span className={'ad-status ad-status-' + value}>{labels[value] || value}</span>;
}

async function imageFile(file) {
  if (!file || !['image/jpeg', 'image/png'].includes(file.type) || file.size > 4 * 1024 * 1024)
    throw new Error('4MB 이하 JPG 또는 PNG 파일을 선택해 주세요.');
  return new Promise((resolve, reject) => {
    const r = new FileReader();
    r.onload = () => resolve(r.result);
    r.onerror = () => reject(new Error('이미지를 읽을 수 없습니다.'));
    r.readAsDataURL(file);
  });
}
function Upload({ value, onChange }) {
  const [error, setError] = useState('');
  return (
    <div className="ad-upload">
      {value && <img src={value} alt="등록한 광고 미리보기" />}
      <label className="ad-file-label">
        광고 이미지 등록
        <input
          type="file"
          accept="image/jpeg,image/png"
          onChange={async (e) => {
            try {
              onChange(await imageFile(e.target.files[0]));
              setError('');
            } catch (err) {
              setError(err.message);
            }
            e.target.value = '';
          }}
        />
      </label>
      <small>JPG·PNG, 최대 4MB · 가로형 이미지 권장</small>
      {error && (
        <p role="alert" className="error">
          {error}
        </p>
      )}
    </div>
  );
}
function Metrics({ orders, admin = false }) {
  const month = koreanToday().slice(0, 7);
  const paid = orders
    .filter(
      (o) =>
        o.paymentStatus === 'PAID' &&
        o.paidAt &&
        new Intl.DateTimeFormat('sv-SE', {
          timeZone: 'Asia/Seoul',
          year: 'numeric',
          month: '2-digit',
        }).format(new Date(o.paidAt)) === month,
    )
    .reduce((s, o) => s + Number(o.totalAmount), 0);
  const values = [
    ['노출 중', orders.filter((o) => o.displayStatus === 'LIVE').length + '건'],
    [
      '심사 대기',
      orders.filter((o) => o.reviewStatus === 'PENDING_REVIEW' && o.refundStatus === 'NONE')
        .length + '건',
    ],
    ['이번 달 결제', won(paid)],
  ];
  if (admin)
    values.push(['환불 요청', orders.filter((o) => o.refundStatus === 'REQUESTED').length + '건']);
  return (
    <div className="ad-metrics">
      {values.map(([label, value]) => (
        <div key={label}>
          <span>{label}</span>
          <strong>{value}</strong>
        </div>
      ))}
    </div>
  );
}
export default function CompanyAdvertising() {
  const resource = useResource('/company/advertising', { refreshInterval: 15000 });
  const [params, setParams] = useSearchParams();
  const payment = params.get('merchant_uid'),
    impUid = params.get('imp_uid'),
    code = params.get('error_code');
  const handled = useRef('');
  const [notice, setNotice] = useState('');
  useEffect(() => {
    if (!payment || handled.current === payment) return;
    handled.current = payment;
    if (code) {
      setNotice('결제가 완료되지 않았습니다. 주문 내역에서 결제 상태를 확인해 주세요.');
      setParams({}, { replace: true });
      return;
    }
    client
      .post('/company/ad-orders/' + encodeURIComponent(payment) + '/confirm', { impUid })
      .then(({ data }) =>
        setNotice(
          data.paymentStatus === 'PAID'
            ? '결제가 확인되었습니다. 승인된 광고는 지정한 기간에 노출됩니다.'
            : '결제 상태: ' + (labels[data.paymentStatus] || data.paymentStatus),
        ),
      )
      .catch((e) => setNotice(message(e)))
      .finally(() => {
        resource.reload();
        setParams({}, { replace: true });
      });
  }, [payment, impUid, code, resource, setParams]);
  return (
    <div className="advertising-page">
      <Title
        eyebrow="BUSINESS ADVERTISING"
        description="우리 기업과 채용공고를 더 많은 구직자에게 알리세요."
      >
        광고·결제
      </Title>
      {notice && (
        <p className="callout" role="status">
          {notice}
        </p>
      )}
      <Resource resource={resource}>
        {(data) => <CompanyAdContent data={data} reload={resource.reload} setNotice={setNotice} />}
      </Resource>
    </div>
  );
}
function CompanyAdContent({ data, reload, setNotice }) {
  const [productParams] = useSearchParams();
  const [code, setCode] = useState(() => ['SLIDE','COMPANY','JOB'].includes(productParams.get('product')) ? productParams.get('product') : 'SLIDE'),
    [start, setStart] = useState(koreanToday()),
    [jobId, setJob] = useState(''),
    [image, setImage] = useState(''),
    [consent, setConsent] = useState(false),
    [busy, setBusy] = useState(false),
    [error, setError] = useState('');
  const selected = data.products.find((p) => p.productCode === code) || data.products[0];
  const availability=useAdAvailability(selected?.productCode||code,jobId);
  const spaceAvailable=!!availability.data?.days.find(d=>d.startDate===start)?.available;
  async function pay(checkout) {
    const response = await requestPortOneV1(checkout, window.location.origin + '/company/advertising');
    const result = await client.post(
      '/company/ad-orders/' + encodeURIComponent(checkout.paymentId) + '/confirm',
      { impUid: response.imp_uid },
    );
    if (result.data.paymentStatus !== 'PAID')
      throw new Error('결제 승인이 아직 확인되지 않았습니다. 내역에서 결제 확인을 눌러 주세요.');
    setNotice('결제가 확인되었습니다. 승인된 광고는 지정한 기간에 노출됩니다.');
  }
  async function submit(e) {
    e.preventDefault();
    if (busy || !selected || !spaceAvailable) return;
    setBusy(true);
    setError('');
    try {
      await client.post('/company/ad-orders', {
        productCode: selected.productCode,
        startDate: start,
        linkType: 'internal',
        jobId,
        image,
        consent,
      });
      setNotice(
        '광고 심사 신청이 접수되었습니다. 관리자 승인 후 이 신청 건에서 결제할 수 있습니다.',
      );
    } catch (e) {
      setError(e.response ? message(e) : e.message);
      setNotice(e.response ? message(e) : e.message);
    } finally {
      setBusy(false);
      reload();
    }
  }
  async function action(order, action, body = {}) {
    if (busy) return;
    setBusy(true);
    setError('');
    try {
      if (action === 'checkout') {
        const { data: checkout } = await client.post(
          '/company/ad-orders/' + order.orderId + '/checkout',
        );
        await pay(checkout);
      } else {
        const { data: result } = await client.post('/company/ad-orders/' + order.orderId + '/' + action, body);
        setNotice(
          action === 'refund-request'
            ? '환불 요청이 접수되었습니다.'
            : action === 'withdraw'
              ? '광고 신청을 철회했습니다.'
              : '결제 상태: ' + (labels[result.paymentStatus] || result.paymentStatus),
        );
      }
    } catch (e) {
      setError(e.response ? message(e) : e.message);
      setNotice(e.response ? message(e) : e.message);
    } finally {
      setBusy(false);
      reload();
    }
  }
  return (
    <>
      <Metrics orders={data.orders} />
      {!data.gateway.ready && (
        <p className="callout">
          광고 심사 신청은 가능합니다. 결제 기능은 준비 중이며, 승인 후 결제 시 운영자에게 문의해
          주세요.
        </p>
      )}
      <form onSubmit={submit} className="ad-checkout-layout">
        <section className="panel">
          <h2>광고 상품 선택</h2>
          <div className="ad-product-grid">
            {data.products.map((p) => (
              <button
                type="button"
                className={
                  'ad-product ' + (p.productCode === selected?.productCode ? 'selected' : '')
                }
                aria-pressed={p.productCode === selected?.productCode}
                key={p.productCode}
                onClick={() => setCode(p.productCode)}
              >
                <span className="ad-placement">
                  {p.productCode === 'SLIDE'
                    ? '홈 메인'
                    : p.productCode === 'COMPANY'
                      ? '추천 기업'
                      : '채용정보'}
                </span>
                <h3>{p.name}</h3>
                <strong>
                  {won(p.price)} <small>/ {p.durationDays}일</small>
                </strong>
                <p>{p.description}</p>
                <div className={'ad-placement-demo demo-' + p.productCode}>
                  <span />
                  <span />
                  <span />
                </div>
              </button>
            ))}
          </div>
          {selected && (
            <>
              <div className="ad-form-grid">
                <label>
                  홍보 기업
                  <input readOnly value={data.company.companyName} />
                </label>
                <AdAvailability resource={availability} start={start} onChange={setStart} />
                <label className="ad-full">
                  홍보할 채용공고
                  <select required value={jobId} onChange={(e) => setJob(e.target.value)}>
                    <option value="">공고 선택</option>
                    {data.jobs.map((j) => (
                      <option key={j.jobId} value={j.jobId}>
                        {j.title} · {j.endDate} 마감
                      </option>
                    ))}
                  </select>
                  <small>카드를 누르면 선택한 공고로 이동합니다.</small>
                </label>
              </div>
              <h3>광고 이미지</h3>
              <Upload value={image} onChange={setImage} />
              <p className="ad-info">
                광고 심사를 신청한 뒤 관리자 승인 후 결제합니다. 결제가 완료되면 요청한 기간에
                노출됩니다. 선택한 기간의 잔여 자리는 관리자 승인 전까지 변경될 수 있습니다.
              </p>
            </>
          )}
        </section>
        <aside className="panel ad-payment-summary">
          <span className="ad-test-label">포트원 V1 테스트 결제</span>
          <h2>광고 신청 금액</h2>
          {selected ? (
            <>
              <dl>
                <div>
                  <dt>상품</dt>
                  <dd>{selected.name}</dd>
                </div>
                <div>
                  <dt>상품 금액</dt>
                  <dd>{won(selected.price)}</dd>
                </div>
                <div>
                  <dt>부가세 (10%)</dt>
                  <dd>{won(selected.price / 10)}</dd>
                </div>
                <div className="ad-total">
                  <dt>승인 후 결제 금액</dt>
                  <dd>{won(selected.price * 1.1)}</dd>
                </div>
              </dl>
              <p>카드 결제 · 테스트 채널 사용</p>
              <label className="ad-consent">
                <input
                  type="checkbox"
                  required
                  checked={consent}
                  onChange={(e) => setConsent(e.target.checked)}
                />
                광고 이용 및 환불 규정에 동의합니다.
              </label>
              <details>
                <summary>이용·환불 규정 보기</summary>
                <p>
                  광고 신청 후 관리자 승인을 받아 결제하면 지정한 기간에 노출됩니다. 결제 전에는
                  신청을 철회할 수 있으며, 결제 후 노출 시작 전에는 전액 취소를 요청할 수 있습니다.
                  노출이 시작된 광고는 고객센터로 문의해 주세요. 테스트 거래만 지원합니다.
                </p>
              </details>
              <button type="submit" disabled={busy || !image || !consent || !spaceAvailable}>
                {busy ? '신청 접수 중…' : '광고 심사 신청'}
              </button>
              {(!image || !consent || !spaceAvailable) && (
                <div className="ad-checkout-requirements" role="status" aria-live="polite">
                  <p>신청 전 확인해 주세요.</p>
                  <ul>
                    {!spaceAvailable && <li>노출 가능한 시작일을 선택해 주세요.</li>}
                    {!image && <li>광고 이미지를 등록해 주세요.</li>}
                    {!consent && <li>이용·환불 규정에 동의해 주세요.</li>}
                  </ul>
                </div>
              )}
            </>
          ) : (
            <p>판매 중인 광고 상품이 없습니다.</p>
          )}
        </aside>
      </form>
      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}
      <section className="panel ad-history">
        <div className="row">
          <h2>광고 신청·진행 내역</h2>
          <button type="button" className="secondary" onClick={reload}>
            새로고침
          </button>
        </div>
        <OrderTable
          orders={data.orders}
          onSelect={null}
          actions={(o) => (
            <>
              {(o.paymentStatus === 'PAID' || o.reviewStatus === 'APPROVED') && (
                <button className="secondary" disabled={busy} onClick={() => action(o, 'confirm')}>
                  결제 확인
                </button>
              )}
              {['READY', 'FAILED'].includes(o.paymentStatus) && o.reviewStatus === 'APPROVED' && (
                <details>
                  <summary>포트원 거래번호로 결제 확인</summary>
                  <form onSubmit={(event) => {
                    event.preventDefault();
                    const impUid = new FormData(event.currentTarget).get('impUid').trim();
                    action(o, 'confirm', { impUid });
                  }}>
                    <label>
                      포트원 거래번호
                      <input name="impUid" placeholder="imp_로 시작하는 번호" maxLength={68} required />
                    </label>
                    <button className="secondary" disabled={busy}>승인 건 확인</button>
                  </form>
                </details>
              )}
              {['READY', 'FAILED'].includes(o.paymentStatus) && o.reviewStatus === 'APPROVED' && (
                <button
                  disabled={busy || !data.gateway.ready || o.startDate < koreanToday()}
                  onClick={() => action(o, 'checkout')}
                >
                  결제하기
                </button>
              )}
              {['READY', 'FAILED'].includes(o.paymentStatus) &&
                ['PENDING_REVIEW', 'CHANGES_REQUESTED', 'APPROVED'].includes(o.reviewStatus) && (
                  <button
                    className="secondary"
                    disabled={busy}
                    onClick={() => {
                      if (window.confirm('이 광고 신청을 철회할까요?')) action(o, 'withdraw');
                    }}
                  >
                    신청 철회
                  </button>
                )}
              {o.paymentStatus === 'PAID' && o.refundStatus === 'NONE' && (
                <RefundRequest
                  order={o}
                  busy={busy}
                  onRequest={(reason) => action(o, 'refund-request', { reason })}
                />
              )}
            </>
          )}
        />
        {data.orders
          .filter((o) => o.reviewStatus === 'CHANGES_REQUESTED' && o.refundStatus === 'NONE')
          .map((o) => (
            <Creative key={o.orderId} order={o} reload={reload} />
          ))}
      </section>
    </>
  );
}
function RefundRequest({ onRequest, busy }) {
  const [open, setOpen] = useState(false),
    [reason, setReason] = useState('');
  return open ? (
    <div className="ad-refund-form">
      <input
        aria-label="환불 사유"
        maxLength={500}
        value={reason}
        onChange={(e) => setReason(e.target.value)}
        placeholder="환불 사유"
      />
      <button
        disabled={busy || !reason.trim()}
        onClick={() => {
          onRequest(reason);
          setOpen(false);
        }}
      >
        요청 제출
      </button>
      <button className="secondary" onClick={() => setOpen(false)}>
        닫기
      </button>
    </div>
  ) : (
    <button className="secondary" disabled={busy} onClick={() => setOpen(true)}>
      환불 요청
    </button>
  );
}
function Creative({ order, reload }) {
  const [image, setImage] = useState(''),
    [startDate, setStartDate] = useState(
      order.startDate < koreanToday() ? koreanToday() : order.startDate,
    ),
    [error, setError] = useState(''),
    [busy, setBusy] = useState(false);
  const availability=useAdAvailability(order.productCode,order.jobId||'',order.orderId);
  const spaceAvailable=!!availability.data?.days.find(d=>d.startDate===startDate)?.available;
  return (
    <section className="ad-creative">
      <h3>{order.productName} · 광고 이미지 보완</h3>
      <p>{order.reviewMemo}</p>
      <AdAvailability resource={availability} start={startDate} onChange={setStartDate} />
      <Upload value={image} onChange={setImage} />
      <button
        disabled={!image || busy || !spaceAvailable}
        onClick={async () => {
          setBusy(true);
          try {
            await client.put('/company/ad-orders/' + order.orderId + '/creative', {
              image,
              startDate,
            });
            reload();
          } catch (e) {
            setError(message(e));
          } finally {
            setBusy(false);
          }
        }}
      >
        보완 이미지 제출
      </button>
      {error && <p role="alert">{error}</p>}
    </section>
  );
}
function OrderTable({ orders, onSelect, actions, actionLabel = '검토하기' }) {
  return orders.length ? (
    <div className="ad-table-wrap">
      <table>
        <thead>
          <tr>
            {onSelect && <th>기업</th>}
            <th>상품·주문번호</th>
            <th>노출 기간</th>
            <th>결제</th>
            <th>광고 상태</th>
            <th>관리</th>
          </tr>
        </thead>
          {orders.map((o) => (
            <tbody key={o.orderId} className="ad-order-group"><tr>
              {onSelect && <td>{o.companyName}</td>}
              <td>
                <strong>{o.productName}</strong>
                {o.paymentSource === 'SIMULATION' && <small>시연 결제</small>}
                <small>{o.orderId}</small>
                
              </td>
              <td>
                {o.startDate}
                <small>~ {o.endDate}</small>
              </td>
              <td>
                {won(o.totalAmount)}
                <small>
                  <Status
                    value={
                      ['READY', 'FAILED'].includes(o.paymentStatus)
                        ? o.reviewStatus === 'APPROVED'
                          ? 'PAYMENT_PENDING'
                          : 'UNPAID'
                        : o.paymentStatus
                    }
                  />
                </small>
              </td>
              <td>
                <Status value={o.displayStatus} />
              </td>
              <td>
                <div className="ad-table-actions">
                  {onSelect ? (
                    <button className="secondary" onClick={() => onSelect(o.orderId)}>
                      {actionLabel}
                    </button>
                  ) : (
                    actions(o)
                  )}
                </div>
              </td>
            </tr>{o.reviewMemo && <tr className="ad-order-memo-row"><td colSpan={onSelect ? 6 : 5}><span>검토 의견</span>{o.reviewMemo}</td></tr>}</tbody>
          ))}
      </table>
    </div>
  ) : (
    <p className="ad-empty">아직 광고 신청 내역이 없습니다.</p>
  );
}

export function AdminAdvertising() {
  const resource = useResource('/admin/advertising', { refreshInterval: 15000 });
  return (
    <div className="advertising-page">
      <Title
        eyebrow="ADVERTISING CENTER"
        description="결제 내역과 광고 심사를 한곳에서 관리하세요."
        action={
          <button className="secondary" onClick={resource.reload}>
            새로고침
          </button>
        }
      >
        광고·결제 관리
      </Title>
      <Resource resource={resource}>
        {(data) => <AdminContent data={data} reload={resource.reload} />}
      </Resource>
    </div>
  );
}
function AdminContent({ data, reload }) {
  const [tab, setTab] = useState('review'),
    [query, setQuery] = useState(''),
    [status, setStatus] = useState(''),
    [selected, setSelected] = useState(''),
    [productNotice, setProductNotice] = useState('');
  const tabOrders = ordersForAdminTab(data.orders, tab);
  const current = tabOrders.find((o) => o.orderId === selected);
  const filtered = tabOrders.filter(
    (o) =>
      (o.companyName + ' ' + o.orderId).toLowerCase().includes(query.toLowerCase()) &&
      (!status ||
        o.reviewStatus === status ||
        o.paymentStatus === status ||
        o.refundStatus === status ||
        o.displayStatus === status),
  );
  return (
    <>
      <Metrics orders={data.orders} admin />
      <p className="ad-info">
        광고 심사 신청을 먼저 검토하고 승인해 주세요. 승인 후 기업이 결제하며, 결제창을 닫아도 같은
        신청 건을 유지합니다.
      </p>
      <div className="ad-tabs" role="group" aria-label="광고 관리 메뉴">
        {[
          ['review', '광고 신청 심사'],
          ['refund', '결제·환불 내역'],

          ['products', '광고 상품 설정'],
        ].map(([key, label]) => (
          <button
            className={tab === key ? '' : 'secondary'}
            aria-pressed={tab === key}
            key={key}
            onClick={() => {
              setTab(key);
              setSelected('');
              setStatus('');
            }}
          >
            {label}
          </button>
        ))}
      </div>
      {productNotice && (
        <p className="ad-save-success" role="status" aria-live="polite">
          {productNotice}
        </p>
      )}
      {tab === 'products' ? (
        <div className="ad-admin-products">
          {data.products.map((p) => (
            <ProductEditor
              key={p.productCode + ':' + p.version}
              product={p}
              reload={reload}
              onSaved={setProductNotice}
            />
          ))}
        </div>
      ) : (
        <>
          <div className="ad-filter">
            <input
              aria-label="기업명 또는 주문번호 검색"
              placeholder="기업명 또는 주문번호 검색"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
            />
            <select
              aria-label="상태 선택"
              value={status}
              onChange={(e) => setStatus(e.target.value)}
            >
              <option value="">전체 상태</option>
              {[
                'PENDING_REVIEW',
                'CHANGES_REQUESTED',
                'APPROVED',
                'PAID',
                'PAYMENT_PENDING',
                'REQUESTED',
                'COMPLETED',
              ].map((s) => (
                <option key={s} value={s}>
                  {labels[s]}
                </option>
              ))}
            </select>
          </div>
          <div className={'ad-admin-layout ' + (current ? 'has-detail' : '')}>
            <section className="panel">
              <OrderTable
                orders={filtered}
                onSelect={setSelected}
                actionLabel={tab === 'review' ? '검토하기' : '상세 보기'}
              />
            </section>
            {current && (
              <AdminOrder
                key={current.orderId + ':' + current.updatedAt}
                order={current}
                reload={reload}
                close={() => setSelected('')}
              />
            )}
          </div>
        </>
      )}
      <p className="ad-info">
        심사 신청 → 관리자 승인 → 기업 결제 순서로 진행됩니다. 승인·결제·노출 기간이 모두 유효한
        광고만 공개됩니다.
      </p>
    </>
  );
}
function ProductEditor({ product: p, reload, onSaved }) {
  const [draft, setDraft] = useState({ ...p, isActive: Boolean(p.isActive) }),
    [busy, setBusy] = useState(false),
    [notice, setNotice] = useState('');
  const change = (key, value) => setDraft({ ...draft, [key]: value });
  return (
    <form
      className="panel ad-product-editor"
      onSubmit={async (e) => {
        e.preventDefault();
        if (busy) return;
        if (!hasFormChanges(p,draft,['name','description','price','durationDays','isActive'].map(name=>({name,type:name==='isActive'?'checkbox':'text'})))) { setNotice(NO_CHANGES); return; }
        setBusy(true);
        setNotice('');
        try {
          await client.put('/admin/ad-products/' + p.productCode, draft);
          onSaved(p.name + ' 상품이 저장되었습니다.');
          await reload();
        } catch (e) {
          setNotice(message(e));
        } finally {
          setBusy(false);
        }
      }}
    >
      <span className="ad-placement">{p.productCode}</span>
      <label>
        상품명
        <input
          maxLength={80}
          required
          value={draft.name}
          onChange={(e) => change('name', e.target.value)}
        />
      </label>
      <label>
        상품 설명
        <textarea
          required
          maxLength={300}
          value={draft.description}
          onChange={(e) => change('description', e.target.value)}
        />
      </label>
      <label>
        공급가액 (부가세 별도)
        <input
          type="number"
          min="1000"
          max="10000000"
          step="10"
          required
          value={draft.price}
          onChange={(e) => change('price', e.target.value)}
        />
      </label>
      <label>
        노출 일수
        <input
          type="number"
          min="1"
          max="90"
          required
          value={draft.durationDays}
          onChange={(e) => change('durationDays', e.target.value)}
        />
      </label>
      <label className="ad-consent">
        <input
          type="checkbox"
          checked={draft.isActive}
          onChange={(e) => change('isActive', e.target.checked)}
        />
        상품 판매
      </label>
      <p>{Number(p.maxSlots) === 0 ? '동시 노출 개수 제한 없음' : '동시 노출 최대 ' + p.maxSlots + '개'}</p>
      <button disabled={busy}>{busy ? '저장 중…' : '상품 저장'}</button>
      {notice && <p role="status">{notice}</p>}
    </form>
  );
}
function AdminOrder({ order: o, reload, close }) {
  const [memo, setMemo] = useState(o.reviewMemo || ''),
    [busy, setBusy] = useState(false),
    [error, setError] = useState('');
  async function act(action) {
    setBusy(true);
    setError('');
    try {
      await client.post(
        '/admin/ad-orders/' +
          o.orderId +
          '/' +
          (action === 'sync' || action === 'refund' ? action : 'review'),
        { action, memo },
      );
      reload();
    } catch (e) {
      setError(message(e));
    } finally {
      setBusy(false);
    }
  }
  return (
    <aside className="panel ad-order-detail">
      <div className="row">
        <h2>신청 상세</h2>
        <button className="secondary" onClick={close}>
          닫기
        </button>
      </div>
      <img src={o.imageUrl} alt={o.companyName + ' 광고 이미지'} />
      <h3>{o.companyName}</h3>
      {o.paymentSource === 'SIMULATION' && (
        <p className="ad-info">시연 결제 데이터입니다. 실제 PG 거래는 발생하지 않았습니다.</p>
      )}
      <dl>
        <div>
          <dt>상품</dt>
          <dd>{o.productName}</dd>
        </div>
        <div>
          <dt>결제 금액</dt>
          <dd>{won(o.totalAmount)} (VAT 포함)</dd>
        </div>
        <div>
          <dt>노출 기간</dt>
          <dd>
            {o.startDate} ~ {o.endDate}
          </dd>
        </div>
        <div>
          <dt>결제 상태</dt>
          <dd>
            <Status
              value={
                ['READY', 'FAILED'].includes(o.paymentStatus)
                  ? o.reviewStatus === 'APPROVED'
                    ? 'PAYMENT_PENDING'
                    : 'UNPAID'
                  : o.paymentStatus
              }
            />
          </dd>
        </div>
        <div>
          <dt>광고 상태</dt>
          <dd>
            <Status value={o.displayStatus} />
          </dd>
        </div>
        <div>
          <dt>결제 확인일</dt>
          <dd>{o.paidAt ? formatDateTime(o.paidAt) : '미결제'}</dd>
        </div>
      </dl>
      {o.linkType === 'external' ? (
        <a href={o.linkUrl} target="_blank" rel="noopener noreferrer">
          광고 이동 페이지 확인
        </a>
      ) : (
        <Link to={o.linkUrl}>광고 이동 페이지 확인</Link>
      )}
      <label>
        운영자 검토 메모
        <textarea
          maxLength={1000}
          value={memo}
          onChange={(e) => setMemo(e.target.value)}
          placeholder="이미지·이동 대상·노출 기간 등을 확인해 주세요."
        />
      </label>
      <div className="ad-detail-actions">
        {(o.paymentStatus === 'PAID' || o.reviewStatus === 'APPROVED') && (
          <button className="secondary" disabled={busy} onClick={() => act('sync')}>
            결제 상태 확인
          </button>
        )}
        {['READY', 'FAILED', 'PAID'].includes(o.paymentStatus) && o.refundStatus === 'NONE' && (
          <>
            {['PENDING_REVIEW', 'PAUSED'].includes(o.reviewStatus) && (
              <button disabled={busy} onClick={() => act('approve')}>
                승인하기
              </button>
            )}
            {['PENDING_REVIEW', 'CHANGES_REQUESTED'].includes(o.reviewStatus) && (
              <button
                className="secondary"
                disabled={busy || !memo.trim()}
                onClick={() => act('changes')}
              >
                보완 요청
              </button>
            )}
            {o.paymentStatus === 'PAID' && o.reviewStatus === 'APPROVED' && (
              <button className="secondary" disabled={busy} onClick={() => act('pause')}>
                노출 중지
              </button>
            )}
          </>
        )}
        {o.refundStatus === 'REQUESTED' && (
          <>
            <p>환불 사유: {o.refundReason}</p>
            <button
              disabled={busy}
              onClick={() => {
                if (window.confirm('테스트 결제를 전액 취소할까요?')) act('refund');
              }}
            >
              전액 취소 처리
            </button>
          </>
        )}
      </div>
      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}
    </aside>
  );
}

export function AdvertisingSummary({ admin = false }) {
  const resource = useResource(admin ? '/admin/advertising' : '/company/advertising', {
    refreshInterval: 30000,
  });
  if (!resource.data) return null;
  const orders = resource.data.orders;
  return (
    <section className="panel ad-dashboard-summary">
      <div>
        <p className="eyebrow">{admin ? 'ADVERTISING CENTER' : 'BUSINESS ADVERTISING'}</p>
        <h2>{admin ? '광고 심사·결제 관리' : '우리 기업의 다음 인재를 만나세요'}</h2>
        <p>
          {admin
            ? `심사 대기 ${orders.filter((o) => o.reviewStatus === 'PENDING_REVIEW').length}건 · 환불 요청 ${orders.filter((o) => o.refundStatus === 'REQUESTED').length}건`
            : `노출 중 ${orders.filter((o) => o.displayStatus === 'LIVE').length}건 · 심사 대기 ${orders.filter((o) => o.reviewStatus === 'PENDING_REVIEW').length}건`}
        </p>
      </div>
      <Link className="button" to={admin ? '/admin/advertising' : '/company/advertising'}>
        {admin ? '광고 관리하기' : '광고 상품 알아보기'}
      </Link>
    </section>
  );
}
