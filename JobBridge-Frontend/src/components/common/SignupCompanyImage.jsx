import { useState } from 'react';
import { prepareCompanyImage } from './CompanyImageUpload';
export default function SignupCompanyImage({id,value,onChange,onBusy}) {
 const [error,setError]=useState(''),[busy,setBusy]=useState(false);
 async function choose(event){const file=event.target.files?.[0];event.target.value='';if(!file)return;setBusy(true);onBusy(true);setError('');try{onChange(await prepareCompanyImage(file));}catch(e){setError(e.message);}finally{setBusy(false);onBusy(false);}}
 return <div className="signup-company-image">{value&&<img src={value} alt="기업 대표 이미지 미리보기" width="96" height="96"/>}<input id={id} type="file" accept="image/jpeg,image/png,image/webp" disabled={busy} onChange={choose}/>{value&&<button type="button" className="secondary" disabled={busy} onClick={()=>{onChange('');setError('');}}>선택 취소</button>}<p className="muted">{busy?'이미지를 준비하고 있습니다.':'선택 사항입니다. 기업 프로필과 공고에서 함께 사용하며 가입 후에도 기업·계정 정보에서 변경할 수 있습니다. JPG, PNG, WEBP · 최대 10MB'}</p>{error&&<p role="alert" className="error">{error}</p>}</div>;
}
