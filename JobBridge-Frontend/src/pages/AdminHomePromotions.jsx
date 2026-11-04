import { useState } from 'react';
import client from '../api/client';
import { useResource } from '../hooks/useResource';
import { Action, Badge, Form, Resource, Title } from '../components/common/UI';
import PromotionSlide from '../components/common/PromotionSlide';
import NoticeImageInput from '../components/common/NoticeImageInput';
import BannerDestinationPicker from '../components/common/BannerDestinationPicker';
import '../assets/styles/home-promotions-admin.css';

const displayLabels = { LIVE:'게시 중', SCHEDULED:'게시 예정', EXPIRED:'기간 종료', HIDDEN:'비공개' };

export default function AdminHomePromotions() {
  const resource = useResource('/admin/home-promotions');
  const [selected, setSelected] = useState(null);
  const [draft, setDraft] = useState(null);
  const [message, setMessage] = useState('');
  return <div className="admin-promotion-management">
    <Title eyebrow="HOME BANNERS" description="배너 이미지와 연결 주소, 노출 기간을 관리합니다." action={<div className="actions">
      <button type="button" disabled={!resource.data || resource.loading} onClick={() => { setMessage(''); setDraft({ key:'new', version:0, presentation:'banner', label:'', imageUrl:'', imageAlt:'', to:'/jobs', linkType:'internal', advertiserName:'', isAd:false, startDate:'', endDate:'', sortOrder:Math.min(99,Math.max(0,...resource.data.map((row) => Number(row.sortOrder)))+1), isPublished:false }); }}>＋ 배너 추가</button>
      <button type="button" className="secondary" onClick={resource.reload}>새로고침</button>
    </div>}>홈 홍보 관리</Title>
    {message && <p className="form-success" role="status">{message}</p>}
    <Resource resource={resource}>{(rows) => {
      const current = draft || rows.find((row) => row.key === selected) || rows[0];
      return <>
        <div className="admin-promotion-workspace"><div className="admin-promotion-tabs" role="group" aria-label="배너 선택">{rows.map((row) => <button type="button" key={row.key} aria-pressed={row.key === current?.key} className={row.key === current?.key ? 'selected' : ''} onClick={() => { setDraft(null); setSelected(row.key); setMessage(''); }}><span>{row.sortOrder}. {row.label}</span><Badge value={displayLabels[row.displayStatus] || (row.isPublished ? '게시 중' : '비공개')} /></button>)}</div>
        {current ? <BannerEditor key={`${current.key}:${current.version}`} banner={current} isNew={Boolean(draft)} onCancel={() => setDraft(null)} onDeleted={async () => { setSelected(null); await resource.reload(); setMessage('배너를 삭제했습니다.'); }} onSaved={async (data) => { setDraft(null); setSelected(data.key); await resource.reload(); setMessage('저장했습니다. 공개한 배너는 홈에 반영됩니다.'); }} /> : <p className="callout">등록된 배너가 없습니다. ‘배너 추가’로 완성된 이미지를 등록해 주세요.</p>}</div>
      </>;
    }}</Resource>
  </div>;
}

function BannerEditor({ banner, isNew, onCancel, onDeleted, onSaved }) {
  const [uploading, setUploading] = useState(false);
  const external = banner.linkType === 'external';
  const initial = { ...banner, linkType:banner.linkType || 'internal', advertiserName:banner.advertiserName || '', isAd:Boolean(banner.isAd), startDate:banner.startDate || '', endDate:banner.endDate || '', internalTo:external ? '/jobs' : banner.to, externalTo:external ? banner.to : '' };
  const fields = [
    { name:'advertiserName', label:'광고 기업', maxLength:100, placeholder:'기업명 또는 광고주 이름', validate:(value, values) => values.isAd && !String(value || '').trim() ? '광고 기업을 입력해 주세요.' : '' },
    { name:'label', label:'배너 이름 (관리용)', required:true, maxLength:50 },
    { name:'imageUrl', label:'완성된 배너 이미지', validate:(value) => value ? '' : '완성된 배너 이미지를 등록해 주세요.', render:({ values, setValues, error, clearError }) => <div className="admin-banner-upload">
      <NoticeImageInput id="banner-upload" value={values.imageUrl} onBusyChange={setUploading} onChange={(value) => { clearError(); setValues((old) => ({ ...old, imageUrl:value })); }} helpText="디자인·문구·버튼 모양까지 포함된 완성 이미지를 올려 주세요. 권장 비율 2:1 · JPG, PNG, WEBP · 저장 시 JPG/PNG로 변환" previewAlt="업로드한 배너 이미지" removeLabel="선택한 이미지 지우기" />
      {error && <p className="error" role="alert">{error}</p>}
    </div> },
    { name:'preview', label:'홈 노출 미리보기', render:({ values }) => values.imageUrl ? <div className="admin-banner-preview"><PromotionSlide slide={{ ...values, presentation:values.imageUrl === banner.imageUrl ? banner.presentation : 'banner' }} preview /></div> : <p className="callout">이미지를 등록하면 미리보기가 표시됩니다.</p> },
    { name:'imageAlt', label:'이미지 설명 (이미지를 볼 수 없는 사용자를 위한 안내)', required:true, type:'textarea', maxLength:300 },
    { name:'linkType', label:'배너 클릭 시 이동', render:({ values, setValues }) => <div className="admin-banner-link-types" role="group" aria-label="배너 클릭 시 이동">{[{ value:'internal', label:'사이트 내부 화면' }, { value:'external', label:'외부 홈페이지' }].map((option) => <label key={option.value}><input type="radio" name={'banner-link-type-' + banner.key} value={option.value} checked={values.linkType === option.value} onChange={() => setValues((old) => ({ ...old, linkType:option.value, to:option.value === 'external' ? old.externalTo || '' : old.internalTo || '/jobs' }))} />{option.label}</label>)}</div> },
    { name:'to', label:'배너 이동 대상', required:true, validate:(value, values) => {
      if (!value) return values.linkType === 'external' ? '홈페이지 주소를 입력해 주세요.' : '이동할 화면을 선택해 주세요.';
      if (values.linkType !== 'external') return '';
      try { const url=new URL(value); return ['https:', 'http:'].includes(url.protocol) && !url.username && !url.password && !/[\\\s]/.test(value) ? '' : 'http:// 또는 https://로 시작하는 주소를 입력해 주세요.'; } catch { return '올바른 홈페이지 주소를 입력해 주세요.'; }
    }, render:({ id, values, setValues, error }) => <div className="admin-banner-destination">{values.linkType === 'external' ? <><input id={id} type="url" required maxLength={300} value={values.to || ''} placeholder="https://www.company.com" onChange={(event) => setValues((old) => ({ ...old, to:event.target.value, externalTo:event.target.value }))} /><small>배너를 누르면 새 탭으로 열립니다.</small></> : <BannerDestinationPicker key={values.linkType} id={id} value={values.to} onChange={(to) => setValues((old) => ({ ...old, to, internalTo:to }))} />}{error && <p className="error">{error}</p>}</div> },
    { name:'startDate', label:'노출 시작일', type:'date' },
    { name:'endDate', label:'노출 종료일', type:'date', validate:(value, values) => value && values.startDate && value < values.startDate ? '종료일은 시작일 이후로 선택해 주세요.' : '' },
    { name:'sortOrder', label:'노출 순서 (작은 숫자가 먼저 표시됩니다)', required:true, type:'number', min:1, max:99 },
    { name:'isPublished', label:'홈에 공개', type:'checkbox' },
    { name:'isAd', label:'유료 광고 표시', type:'checkbox' },
  ];
  const orderedFields = ['preview', 'imageUrl', 'advertiserName', 'label', 'imageAlt', 'linkType', 'to', 'startDate', 'endDate', 'sortOrder', 'isPublished', 'isAd'].map((name) => fields.find((field) => field.name === name));
  return <div className="panel">
    <p className="muted">문구나 디자인 변경은 이미지 원본을 수정해 새 파일로 교체해 주세요. 배너 전체를 누르면 지정한 화면으로 이동합니다. 날짜를 비우면 기간 제한 없이 표시되며, 종료일은 한국 시간 기준 당일까지 노출됩니다.</p>
    <Form initial={initial} fields={orderedFields} submitDisabled={uploading} submit={isNew ? '배너 등록' : '변경사항 저장'} onSubmit={async (values) => {
      const body={ label:values.label, imageUrl:values.imageUrl, imageAlt:values.imageAlt, to:values.to, sortOrder:Number(values.sortOrder), isPublished:Boolean(values.isPublished), linkType:values.linkType, advertiserName:values.advertiserName, isAd:Boolean(values.isAd), startDate:values.startDate || null, endDate:values.endDate || null, version:banner.version };
      const { data }=isNew ? await client.post('/admin/home-promotions',body) : await client.put('/admin/home-promotions/'+banner.key,body);
      await onSaved(data);
    }} />
    <div className="actions">{isNew ? <button type="button" className="secondary" onClick={onCancel}>작성 취소</button> : <Action method="delete" url={'/admin/home-promotions/'+banner.key} body={{ version:banner.version }} confirm={'‘'+banner.label+'’ 배너를 삭제할까요?'} onDone={onDeleted}>배너 삭제</Action>}</div>
  </div>;
}
