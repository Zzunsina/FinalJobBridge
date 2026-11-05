import { useEffect, useId, useState } from 'react';
import { Link } from 'react-router-dom';
import client from '../../api/client';

const pages = [
  ['/', '홈 화면'], ['/jobs', '채용공고 목록'], ['/companies', '기업정보 목록'],
  ['/resumes', '이력서 관리 (로그인 필요)'], ['/employment', '취업연계 서비스'],
  ['/notices', '공지사항 목록'], ['/inquiries', '고객센터'], ['/login', '로그인 화면'], ['/signup', '회원가입 화면'],
];
const kinds = { company:'기업 소개', job:'채용공고 상세', notice:'공지 상세' };
const names = { company:'기업명', job:'공고명 또는 기업명', notice:'공지 제목' };
function inferKind(value) {
  if (/^\/companies\/\d+$/.test(value)) return 'company';
  if (/^\/jobs\/\d+$/.test(value)) return 'job';
  if (/^\/notices\/\d+$/.test(value)) return 'notice';
  return 'page';
}

export default function BannerDestinationPicker({ value, onChange, id }) {
  const uid = useId();
  const [kind, setKind] = useState(() => inferKind(value || ''));
  const [query, setQuery] = useState('');
  const [refresh, setRefresh] = useState(0);
  const [result, setResult] = useState({ rows:[], loading:true, error:'' });
  const selectedId = inferKind(value || '') === kind ? (value || '').match(/\/(\d+)$/)?.[1] || '' : '';
  useEffect(() => {
    if (kind === 'page') return;
    let current = true;
    const timer = window.setTimeout(async () => {
      setResult((old) => ({ ...old, loading:true, error:'' }));
      try {
        const { data } = await client.get('/admin/home-promotion-targets', { params:{ kind, q:query, selectedId:selectedId || undefined } });
        if (current) setResult({ rows:data, loading:false, error:'' });
      } catch {
        if (current) setResult({ rows:[], loading:false, error:'목록 오류' });
      }
    }, 200);
    return () => { current=false; window.clearTimeout(timer); };
  }, [kind, query, selectedId, refresh]);
  const selected = result.rows.find((row) => row.to === value);
  const base = pages.find(([path]) => path === (value || '').split('?')[0]);
  return <div className="banner-target-picker">
    <label htmlFor={uid + '-kind'}>연결 방식</label>
    <select id={uid + '-kind'} value={kind} onChange={(event) => {
      const next=event.target.value; setKind(next); setQuery(''); setResult({ rows:[], loading:true, error:'' });
      onChange(next === 'page' ? '/jobs' : '');
    }}>
      <option value="page">사이트 내 메뉴</option>
      {Object.entries(kinds).map(([key, label]) => <option key={key} value={key}>{label}</option>)}
    </select>
    {kind === 'page' ? <>
      <label htmlFor={id}>연결할 메뉴</label>
      <select id={id} required value={value || ''} onChange={(event) => onChange(event.target.value)}>
        <option value="">메뉴를 선택해 주세요</option>
        {base && !pages.some(([path]) => path === value) && <option value={value}>{base[1]} · 기존 검색 조건</option>}
        {pages.map(([path, label]) => <option key={path} value={path}>{label}</option>)}
      </select>
    </> : <>
      <label htmlFor={uid + '-search'}>{names[kind]} 검색</label>
      <div className="banner-target-search"><input id={uid + '-search'} type="search" value={query} maxLength={100} placeholder={names[kind] + ' 입력'} onChange={(event) => setQuery(event.target.value)} /><button type="button" className="secondary" onClick={() => setRefresh((old) => old + 1)}>목록 새로고침</button></div>
      <label htmlFor={id}>{kinds[kind]} 선택</label>
      <select id={id} required value={value || ''} onChange={(event) => onChange(event.target.value)} disabled={result.loading || Boolean(result.error)}>
        <option value="">{result.loading ? '목록을 불러오는 중…' : '검색 결과에서 선택해 주세요'}</option>
        {value && !selected && <option value={value} disabled>{result.loading ? '선택한 항목 확인 중…' : '선택했던 항목을 사용할 수 없습니다. 다시 선택해 주세요.'}</option>}
        {result.rows.map((row) => <option key={row.to} value={row.to}>{row.label}</option>)}
      </select>
      {result.error ? <p className="error" role="alert">목록을 불러오지 못했습니다. ‘목록 새로고침’을 눌러 주세요.</p> : !result.loading && !result.rows.length && <p className="muted">공개된 검색 결과가 없습니다. 다른 검색어로 찾아보세요.</p>}
      <small>공개된 항목을 검색합니다. 새로 등록한 항목은 목록 새로고침 후 선택할 수 있습니다.</small>
    </>}
    {value && (kind === 'page' ? Boolean(base) : Boolean(selected)) && <div className="banner-target-selected"><strong>선택한 화면: {kind === 'page' ? base?.[1] : selected.label}</strong><Link to={value} target="_blank" rel="noopener noreferrer">선택한 화면 미리보기</Link></div>}
  </div>;
}
