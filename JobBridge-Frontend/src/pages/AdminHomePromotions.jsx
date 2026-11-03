import { useState } from 'react';
import client from '../api/client';
import { useResource } from '../hooks/useResource';
import { Action, Badge, Form, Resource, Title } from '../components/common/UI';
import PromotionSlide from '../components/common/PromotionSlide';
import NoticeImageInput from '../components/common/NoticeImageInput';
import '../assets/styles/home-promotions-admin.css';

const destinations = [
  { value: '/', label: '홈 화면' }, { value: '/jobs', label: '채용공고 목록' },
  { value: '/companies', label: '기업정보 목록' }, { value: '/resumes', label: '이력서 관리 (로그인 필요)' },
  { value: '/employment', label: '취업연계 서비스' }, { value: '/notices', label: '공지사항' },
  { value: '/inquiries', label: '고객센터' }, { value: '/login', label: '로그인 화면' }, { value: '/signup', label: '회원가입 화면' },
];

export default function AdminHomePromotions() {
  const resource = useResource('/admin/home-promotions');
  const [selected, setSelected] = useState(null);
  const [draft, setDraft] = useState(null);
  const [message, setMessage] = useState('');
  return <div className="admin-promotion-management">
    <Title eyebrow="HOME BANNERS" description="디자인과 문구가 포함된 완성 배너 이미지를 등록하고 노출을 관리합니다." action={<div className="actions">
      <button type="button" disabled={!resource.data || resource.loading} onClick={() => { setMessage(''); setDraft({ key:'new', version:0, presentation:'banner', label:'', imageUrl:'', imageAlt:'', to:'/jobs', sortOrder:Math.min(99,Math.max(0,...resource.data.map((row) => Number(row.sortOrder)))+1), isPublished:false }); }}>＋ 배너 추가</button>
      <button type="button" className="secondary" onClick={resource.reload}>새로고침</button>
    </div>}>홈 홍보 관리</Title>
    {message && <p className="form-success" role="status">{message}</p>}
    <Resource resource={resource}>{(rows) => {
      const current = draft || rows.find((row) => row.key === selected) || rows[0];
      return <>
        <div className="admin-promotion-tabs" role="group" aria-label="배너 선택">{rows.map((row) => <button type="button" key={row.key} aria-pressed={row.key === current?.key} className={row.key === current?.key ? 'selected' : ''} onClick={() => { setDraft(null); setSelected(row.key); setMessage(''); }}><span>{row.sortOrder}. {row.label}</span><Badge value={row.isPublished ? '공개' : '숨김'} /></button>)}</div>
        {current ? <BannerEditor key={`${current.key}:${current.version}`} banner={current} isNew={Boolean(draft)} onCancel={() => setDraft(null)} onDeleted={async () => { setSelected(null); await resource.reload(); setMessage('배너를 삭제했습니다.'); }} onSaved={async (data) => { setDraft(null); setSelected(data.key); await resource.reload(); setMessage('저장했습니다. 공개한 배너는 홈에 반영됩니다.'); }} /> : <p className="callout">등록된 배너가 없습니다. ‘배너 추가’로 완성된 이미지를 등록해 주세요.</p>}
      </>;
    }}</Resource>
  </div>;
}

function BannerEditor({ banner, isNew, onCancel, onDeleted, onSaved }) {
  const [uploading, setUploading] = useState(false);
  const fields = [
    { name:'label', label:'배너 이름 (관리용)', required:true, maxLength:50 },
    { name:'imageUrl', label:'완성된 배너 이미지', validate:(value) => value ? '' : '완성된 배너 이미지를 등록해 주세요.', render:({ values, setValues, error, clearError }) => <div className="admin-banner-upload">
      <NoticeImageInput id="banner-upload" value={values.imageUrl} onBusyChange={setUploading} onChange={(value) => { clearError(); setValues((old) => ({ ...old, imageUrl:value })); }} helpText="디자인·문구·버튼 모양까지 포함된 완성 이미지를 올려 주세요. 권장 크기 1200 × 520px · 최대 10MB · JPG, PNG, WEBP" previewAlt="업로드한 배너 이미지" removeLabel="선택한 이미지 지우기" />
      {error && <p className="error" role="alert">{error}</p>}
    </div> },
    { name:'preview', label:'홈 노출 미리보기', render:({ values }) => values.imageUrl ? <div className="admin-banner-preview"><PromotionSlide slide={{ ...values, presentation:values.imageUrl === banner.imageUrl ? banner.presentation : 'banner' }} preview /></div> : <p className="callout">이미지를 등록하면 미리보기가 표시됩니다.</p> },
    { name:'imageAlt', label:'이미지 설명 (이미지를 볼 수 없는 사용자를 위한 안내)', required:true, type:'textarea', maxLength:300 },
    { name:'to', label:'배너 클릭 시 이동할 화면', required:true, options:destinations.some((item) => item.value === banner.to) ? destinations : [{ value:banner.to, label:'현재 연결된 상세 화면 (유지)' }, ...destinations] },
    { name:'sortOrder', label:'노출 순서 (작은 숫자가 먼저 표시됩니다)', required:true, type:'number', min:1, max:99 },
    { name:'isPublished', label:'홈에 공개', type:'checkbox' },
  ];
  return <div className="panel">
    <p className="muted">문구나 디자인 변경은 이미지 원본을 수정해 새 파일로 교체해 주세요. 배너 전체를 누르면 선택한 화면으로 이동합니다.</p>
    <Form initial={banner} fields={fields} submitDisabled={uploading} submit={isNew ? '배너 등록' : '변경사항 저장'} onSubmit={async (values) => {
      const body={ label:values.label, imageUrl:values.imageUrl, imageAlt:values.imageAlt, to:values.to, sortOrder:Number(values.sortOrder), isPublished:Boolean(values.isPublished), version:banner.version };
      const { data }=isNew ? await client.post('/admin/home-promotions',body) : await client.put('/admin/home-promotions/'+banner.key,body);
      await onSaved(data);
    }} />
    <div className="actions">{isNew ? <button type="button" className="secondary" onClick={onCancel}>작성 취소</button> : <Action method="delete" url={'/admin/home-promotions/'+banner.key} body={{ version:banner.version }} confirm={'‘'+banner.label+'’ 배너를 삭제할까요?'} onDone={onDeleted}>배너 삭제</Action>}</div>
  </div>;
}
