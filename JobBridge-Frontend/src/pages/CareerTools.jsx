import { useState } from 'react';
import { Link, NavLink } from 'react-router-dom';
import { calculateSalary, SALARY_RULES } from '../utils/salaryCalculator';
import { applySpellingCorrections, checkKoreanText } from '../utils/koreanSpellChecker';
import '../assets/styles/career-tools.css';

export function CareerToolIcon({ type }) {
 return type==='salary'?<svg viewBox="0 0 28 28" fill="none" aria-hidden="true"><rect x="6" y="3" width="16" height="22" rx="3"/><path d="M10 8h8M10 13h1m6 0h1m-8 4h1m6 0h1m-8 4h1m6 0h1"/></svg>:<svg viewBox="0 0 28 28" fill="none" aria-hidden="true"><path d="M6 5h15v10M6 5v18h9M10 10h7m-7 4h4m-4 4h3"/><path d="m17 21 3 3 6-7"/></svg>;
}
export function CareerUtilityLinks() {
 return <div className="career-utility-links" aria-label="간편 취업 도구">
  <Link to="/tools/spelling"><span><CareerToolIcon type="spelling"/></span><strong>맞춤법 검사</strong></Link>
  <Link to="/tools/salary"><span><CareerToolIcon type="salary"/></span><strong>연봉계산기</strong></Link>
 </div>;
}
function ToolShell({title,description,children}) {
 return <section className="career-utilities-page"><p className="utility-breadcrumb"><Link to="/">홈</Link><span>/</span>취업 도구</p><header className="utility-heading"><p className="eyebrow">JOBBRIDGE CAREER TOOLS</p><h1>{title}</h1><p>{description}</p></header>
  <nav className="utility-tabs" aria-label="취업 도구"><NavLink to="/tools/spelling"><CareerToolIcon type="spelling"/>맞춤법 검사</NavLink><NavLink to="/tools/salary"><CareerToolIcon type="salary"/>연봉계산기</NavLink></nav>{children}</section>;
}
export function SpellingTool() {
 const [text,setText]=useState(''),[result,setResult]=useState(null),[selected,setSelected]=useState([]),[error,setError]=useState(''),[notice,setNotice]=useState('');
 const chosen=result?.issues.filter(item=>selected.includes(item.id))||[];
 const corrected=result?applySpellingCorrections(result.source,chosen):'';
 function inspect(event){event?.preventDefault();setNotice('');setError('');try{const next=checkKoreanText(text);setResult(next);setSelected(next.issues.map(item=>item.id));}catch(problem){setError(problem.message);}}
 function change(value){setText(value);setResult(null);setSelected([]);setError('');setNotice('');}
 async function copy(){try{await navigator.clipboard.writeText(corrected);setNotice('검사 결과를 복사했습니다.');}catch{setNotice('복사하지 못했습니다. 검사 결과를 직접 선택해 복사해 주세요.');}}
 return <ToolShell title="맞춤법 검사" description="이력서와 자기소개서, 제출 전에 한 번 더 확인하세요.">
  <p className="utility-scope">자주 틀리는 표현과 일부 띄어쓰기를 확인하는 기본 검사입니다. 문맥에 따른 모든 오류를 찾거나 문장 전체를 교정하지는 않습니다. 입력 내용은 저장하거나 외부로 전송하지 않습니다.</p>
  <form onSubmit={inspect} className="spell-layout"><section className="utility-panel"><div className="utility-panel-heading"><h2>검사할 내용</h2><button type="button" className="utility-text-button" onClick={()=>change('저는 책임감을 가지고 역활을 수행할수있습니다. 최선을 다할께요.')}>문장 예시</button></div><label htmlFor="spelling-text" className="sr-only">검사할 내용</label><textarea id="spelling-text" maxLength={5000} spellCheck={false} value={text} onChange={event=>change(event.target.value)} placeholder="이력서 또는 자기소개서 내용을 입력해 주세요. (최대 5,000자)"/>
   <div className="utility-counter">공백 포함 {text.length.toLocaleString()}자 · 공백 제외 {text.replace(/\s/g,'').length.toLocaleString()}자 <span>/ 5,000자</span></div>
   {error&&<p role="alert" className="error">{error}</p>}<div className="utility-actions"><button type="button" className="secondary" onClick={()=>change('')}>초기화</button><button type="submit">맞춤법 검사하기</button></div></section>
   <section className="utility-panel spell-result" aria-live="polite"><div className="utility-panel-heading"><h2>검사 결과</h2>{result&&<span className="utility-count">수정 제안 {result.issues.length}개</span>}</div>
    {!result?<div className="utility-placeholder"><CareerToolIcon type="spelling"/><strong>전달하고 싶은 이야기를 더 정확하게</strong><p>내용을 입력하고 검사 버튼을 눌러 주세요.</p></div>:<>{result.issues.length===0?<p className="utility-empty-result">기본 검사에서 수정할 표현을 찾지 못했습니다. 문맥과 고유명사는 직접 확인해 주세요.</p>:<><div className="utility-selection"><label><input type="checkbox" checked={selected.length===result.issues.length} onChange={event=>setSelected(event.target.checked?result.issues.map(item=>item.id):[])}/>전체 선택</label><span>적용할 항목을 선택하세요.</span></div><ul className="spell-issues">{result.issues.map(item=><li key={item.id}><label><input type="checkbox" checked={selected.includes(item.id)} onChange={event=>setSelected(previous=>event.target.checked?[...previous,item.id]:previous.filter(id=>id!==item.id))}/><span><span className="spell-category">{item.type}</span><strong><del>{item.before}</del><span aria-label="수정 제안"> → </span><ins>{item.after}</ins></strong><small>{item.message}</small></span></label></li>)}</ul></>}
    <label className="spell-output-label" htmlFor="spelling-output">선택 항목을 반영한 내용</label><textarea id="spelling-output" readOnly value={corrected}/><div className="utility-actions"><button type="button" className="secondary" onClick={copy}>결과 복사</button><button type="button" disabled={!chosen.length} onClick={()=>change(corrected)}>선택 항목 적용</button></div></>}
   </section></form>{notice&&<p className="utility-notice" role="status">{notice}</p>}
 </ToolShell>;
}
const money=value=>Math.round(value).toLocaleString('ko-KR')+'원';
const deductionLabels=[['pension','국민연금'],['health','건강보험'],['care','장기요양보험'],['employment','고용보험'],['incomeTax','소득세 (추정)'],['localTax','지방소득세 (추정)']];
const initialSalary={mode:'annual',amount:'3600',nonTaxable:'20',dependents:'1'};
export function SalaryTool() {
 const [values,setValues]=useState(initialSalary),[result,setResult]=useState(null),[error,setError]=useState('');
 function field(name,value){setValues(old=>({...old,[name]:value}));setResult(null);setError('');}
 function submit(event){event.preventDefault();try{setResult(calculateSalary(values));setError('');}catch(problem){setResult(null);setError(problem.message);}}
 return <ToolShell title="연봉계산기" description="세전 급여를 입력하고 예상 월 실수령액을 확인하세요.">
  <div className="salary-layout"><form className="utility-panel salary-form" onSubmit={submit}><div className="utility-panel-heading"><h2>급여 조건 입력</h2><span className="utility-count">{SALARY_RULES.period} 기준</span></div>
   <fieldset className="salary-pay-type"><legend>급여 입력 방식</legend>{[['annual','연봉'],['monthly','월급']].map(([mode,label])=><label key={mode} className={values.mode===mode?'selected':''}><input type="radio" name="salary-mode" checked={values.mode===mode} onChange={()=>{setValues(old=>({...old,mode,amount:mode==='annual'?'3600':'300'}));setResult(null);setError('');}}/>{label}</label>)}</fieldset>
   <label className="salary-field" htmlFor="salary-amount">세전 {values.mode==='annual'?'연봉':'월급'}<span className="salary-unit-input"><input id="salary-amount" type="number" min="0.1" step="0.1" max={values.mode==='annual'?30000:2500} required value={values.amount} onChange={event=>field('amount',event.target.value)}/><span>만원</span></span></label>
   <label className="salary-field" htmlFor="salary-exempt">월 비과세액<span className="salary-unit-input"><input id="salary-exempt" type="number" min="0" step="0.1" required value={values.nonTaxable} onChange={event=>field('nonTaxable',event.target.value)}/><span>만원</span></span><small>세전 급여에 포함된 비과세액입니다. 식대 등 실제 비과세 대상 금액을 입력하세요.</small></label>
   <label className="salary-field" htmlFor="salary-family">부양가족 수 (본인 포함)<select id="salary-family" value={values.dependents} onChange={event=>field('dependents',event.target.value)}>{Array.from({length:11},(_,index)=><option value={index+1} key={index+1}>{index+1}명{index===0?' (본인만)':''}</option>)}</select><small>기본공제 요건을 충족하는 가족만 포함하세요.</small></label>
   {error&&<p className="error" role="alert">{error}</p>}<div className="utility-actions"><button type="button" className="secondary" onClick={()=>{setValues(initialSalary);setResult(null);setError('');}}>초기화</button><button type="submit">실수령액 계산하기</button></div></form>
   <section className="utility-panel salary-result" aria-live="polite"><h2>예상 실수령액</h2>{!result?<div className="utility-placeholder"><CareerToolIcon type="salary"/><strong>내 월급, 얼마나 받을까요?</strong><p>급여 조건을 입력하고 계산해 주세요.</p></div>:<><div className="salary-net"><span>한 달에 받는 예상 금액</span><strong>{money(result.monthlyNet)}</strong><small>연간 예상 실수령액 {money(result.annualNet)}</small></div><dl className="salary-lines"><div><dt>세전 월급</dt><dd>{money(result.monthlyGross)}</dd></div>{deductionLabels.map(([key,label])=><div key={key}><dt>{label}</dt><dd>− {money(result[key])}</dd></div>)}<div className="salary-total"><dt>월 공제 합계</dt><dd>{money(result.totalDeduction)}</dd></div></dl></>}
   <p className="salary-result-note">일반 근로자·퇴직금 별도·매달 같은 급여를 받는 경우를 가정합니다. 실제 급여명세서의 지급액과 다를 수 있습니다.</p></section></div>
  <details className="utility-calculation-basis"><summary>계산 기준과 반영 범위</summary><p>2026년 하반기 근로자 부담 기준: 국민연금 4.75% (기준소득월액 41만~659만 원), 건강보험 3.595%, 장기요양은 건강보험료 × (0.9448 ÷ 7.19), 고용보험 0.9%입니다. 산재보험은 사업주 부담으로 공제하지 않습니다.</p><p>소득세는 근로소득공제·기본공제·연금보험료 공제·근로소득세액공제를 반영한 연간 추정액을 12개월로 나눕니다. 보험료 특별소득공제와 표준세액공제 중 유리한 쪽을 적용하며, 국세청 월별 간이세액표와 동일한 계산은 아닙니다. 연봉은 3억 원 이하로 계산할 수 있습니다.</p><p>자녀 세액공제, 신용카드·주택·연금저축 공제, 중소기업 소득세 감면, 상여·연말정산·보험료 정산과 가입 예외는 반영하지 않습니다. 비과세액은 입력한 금액 전체를 가정합니다.</p><div className="utility-source-links"><a href="https://www.nps.or.kr/eng/ntnlpnsplan/cntb/getOHAI0013M0.do" target="_blank" rel="noreferrer">국민연금 기준</a><a href="https://edi.nhis.or.kr/portal/images/popup/20251204_pop01longdesc.html" target="_blank" rel="noreferrer">건강·장기요양보험 기준</a><a href="https://moel.go.kr/info/astmgmt/employ/employList.do" target="_blank" rel="noreferrer">고용보험 기준</a><a href="https://t.nts.go.kr/andong/na/ntt/selectNttInfo.do?mi=2201&nttSn=1347723" target="_blank" rel="noreferrer">국세청 계산 안내</a></div></details>
 </ToolShell>;
}
