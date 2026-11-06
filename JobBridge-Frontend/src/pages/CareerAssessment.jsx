import { CareerUtilityLinks } from './CareerTools';
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom';
import { useEffect, useRef, useState } from 'react';
import client, { message } from '../api/client';
import { useAuth } from '../stores/auth';
import { useResource } from '../hooks/useResource';
import { formatDateTime } from '../utils/dateTime';
import '../assets/styles/career-assessment.css';

export function CareerTestCard() {
  return <aside className="career-home-card career-home-tools has-career-utilities" aria-label="취업 준비 도구"><p className="career-card-eyebrow">JOBBRIDGE CAREER LAB</p><h2>오늘의 취업 준비</h2>
    <Link className="career-tool-card aptitude" to="/career-test"><span className="career-tool-label">인적성검사</span><strong>취업 준비 UP</strong><small>인성 · 적성 연습 · 적성 실전</small><span className="career-tool-action">무료 검사 시작</span></Link>
    <Link className="career-tool-card exploration" to="/career-exploration"><span className="career-tool-label">진로 탐색 검사</span><strong>내 진로 찾기</strong><small>흥미·가치관 36문항</small><span className="career-tool-action">무료로 탐색하기</span></Link>
    <CareerUtilityLinks />
    <div className="career-card-links"><Link to="/career-test#history">내 인적성 결과</Link><Link to="/career-exploration#history">내 진로 탐색 결과</Link></div><p className="career-card-note">잡브릿지 자체 취업 준비용 예시 검사</p>
  </aside>;
}

export default function CareerAssessment() {
  const user = useAuth(s => s.user);
  const { id } = useParams();
  if (id && user?.role === 'JOB_SEEKER') return <TestAttempt key={`${user.userId}:${id}`} id={id} />;
  return <div className="career-page"><div className="career-intro"><p className="eyebrow">JOBBRIDGE CAREER LAB</p><h1>내 강점을 발견하는<br /><em>인적성검사</em></h1><p>일하는 성향을 돌아보고, 직무 기초 문제를 풀어보세요.<br />정답이 없는 나의 성향과 연습으로 키울 수 있는 역량을 함께 살펴봅니다.</p><div className="career-facts"><span>목적에 맞는 검사 3종</span><span>검사별 시간 운영</span><span>무료 자체 진단</span></div></div>
    <div className="career-test-sections"><section><span>01 · PERSONALITY</span><h2>인성검사</h2><p>협업·책임·계획·적응·감정 조절 성향</p><strong>40문항 · 10분</strong></section><section><span>02 · PRACTICE</span><h2>적성 연습형</h2><p>언어·수리·논리를 연습하고 해설로 복습해요.</p><strong>30문항 · 30분 · 본인만 확인</strong></section><section><span>03 · MOCK TEST</span><h2>적성 실전형</h2><p>연습형과 다른 문제로 제한 시간 안에 풀어보세요.</p><strong>20문항 · 15분</strong></section></div>
    <div className="career-notice"><strong>기업에는 어떤 결과가 전달되나요?</strong><p>입사지원 시 최신 완료 인성검사와 적성 실전형 결과만 자동 제출됩니다. 연습형·진로 탐색 결과와 개별 응답은 기업에 전달하지 않습니다. 이미 지원한 건의 제출 결과는 이후 재검사해도 바뀌지 않습니다.</p><p>각 검사는 시작한 시점부터 시간이 흐르며, 새로고침 후에도 이어서 응시할 수 있습니다. 시간 종료 시 저장된 답안으로 자동 제출됩니다. 미응답 적성 문항은 오답으로 계산하고, 인성 미응답은 응답 부족으로 표시합니다. 잡브릿지 자체 취업 준비용 진단이며 표준화된 채용검사나 합격 예측은 아닙니다.</p></div>
    {!user ? <div className="career-start"><Link className="button" to="/login" state={{ careerAssessment: true }}>개인회원 로그인 후 검사하기</Link><p>결과 저장을 위해 로그인이 필요합니다.</p></div> : user.role !== 'JOB_SEEKER' ? <div className="career-notice">개인회원 전용 기능입니다. 기업·관리자 계정에서는 검사를 응시하지 않습니다.</div> : <MemberTests key={user.userId} />}
  </div>;
}
export function MemberTests({kind='assessment'}) {
  const exploration=kind==='exploration',endpoint=exploration?'/career-explorations':'/career-assessments',route=exploration?'/career-exploration':'/career-test';
  const resource = useResource(endpoint);
  const navigate = useNavigate();
  const location = useLocation();
  useEffect(()=>{if(!resource.loading&&location.hash==='#history')document.getElementById('history')?.scrollIntoView({block:'start'});},[resource.loading,location.hash]);
  const [busy,setBusy]=useState(false), [error,setError]=useState('');
  const active=resource.data?.find(row=>row.status==='IN_PROGRESS');
  async function start(mode){setBusy(true);setError('');try{const {data}=await client.post(exploration?endpoint:endpoint+'?mode='+mode);navigate(route+'/'+data.attemptId);}catch(e){setError(message(e));}finally{setBusy(false);}}
  const modes=[['personality','인성검사','40문항 · 10분'],['practice','적성 연습형','30문항 · 30분 · 비공개'],['mock','적성 실전형','20문항 · 15분']];
  return <><div className="career-start career-mode-start">{exploration?<button type="button" disabled={busy||resource.loading||!!resource.error} onClick={()=>start()}>{active?'진행 중인 검사 이어하기':'검사 시작하기'}</button>:modes.map(([mode,title,facts])=><section key={mode}><h3>{title}</h3><p>{facts}</p><button type="button" disabled={busy||resource.loading||!!resource.error} onClick={()=>start(mode)}>{resource.data?.some(row=>row.mode===mode&&row.status==='IN_PROGRESS')?'이어서 응시하기':'시작하기'}</button></section>)}{error&&<p role="alert">{error}</p>}</div><section className="career-history" id="history"><h2>내 검사 내역</h2>{resource.loading?<p>검사 내역을 불러오는 중입니다.</p>:resource.error?<div role="alert"><p>{resource.error}</p><button className="secondary" onClick={resource.reload}>다시 불러오기</button></div>:resource.data?.length?<div>{resource.data.map(row=><Link key={row.attemptId} to={route+'/'+row.attemptId}><span><strong>{row.title||'진로 탐색 검사'} · {row.status==='COMPLETED'?'완료':'진행 중'}</strong><small>{formatDateTime(row.completedAt||row.startedAt)}</small></span><b>{row.status==='COMPLETED'?'결과 보기':'이어서 응시'}</b></Link>)}</div>:<p>아직 검사 내역이 없습니다. 첫 검사를 시작해 보세요.</p>}</section></>;
}
export function TestAttempt({id,kind='assessment',Report=AssessmentReport}) {
  const endpoint=kind==='exploration'?'/career-explorations':'/career-assessments';
  const resource=useResource(endpoint+'/'+id);
  if(resource.loading)return <div className="career-page">검사를 불러오는 중입니다.</div>;
  if(resource.error)return <div className="career-page" role="alert"><p>{resource.error}</p><button className="secondary" onClick={resource.reload}>다시 불러오기</button><Link to={kind==='exploration'?'/career-exploration':'/career-test'}>검사 안내로 이동</Link></div>;
  return <AttemptSession key={id} initial={resource.data} Report={Report} />;
}
function AttemptSession({initial,Report}) {
  const Result=Report;
  const exploration=initial.kind==='exploration',endpoint=exploration?'/career-explorations':'/career-assessments',route=exploration?'/career-exploration':'/career-test', pageSize=exploration?6:5;
  const [attempt,setAttempt]=useState(initial), [answers,setAnswers]=useState(initial.answers),[page,setPage]=useState(()=>Math.max(0,Math.floor(initial.answers.findIndex(a=>a===null)/pageSize))),[seconds,setSeconds]=useState(0),[error,setError]=useState(''),[saving,setSaving]=useState(0),[busy,setBusy]=useState(false),[targetQuestion,setTargetQuestion]=useState(null),[navigationNote,setNavigationNote]=useState('');
  const revision=useRef(initial.revision),queue=useRef(Promise.resolve()),answersRef=useRef(initial.answers),submitting=useRef(false),expired=useRef(false),clock=useRef({base:Date.parse(initial.serverNow),started:performance.now()});
  const isComplete=attempt.status==='COMPLETED';
  useEffect(()=>{if(isComplete)window.scrollTo(0,0);},[isComplete]);
  useEffect(()=>{
    if(isComplete)return;
    let live=true;
    const tick=()=>{const remaining=Math.max(0,Math.ceil((Date.parse(initial.deadlineAt)-clock.current.base-(performance.now()-clock.current.started))/1000));setSeconds(remaining);if(!remaining&&!expired.current){expired.current=true;queue.current.finally(async()=>{try{const {data}=await client.get(endpoint+'/'+initial.attemptId);if(live)setAttempt(data);}catch(e){if(live){setError(message(e));expired.current=false;}}});}};
    tick();const timer=setInterval(tick,1000);return()=>{live=false;clearInterval(timer);};
  },[isComplete,initial.deadlineAt,initial.attemptId,endpoint]);
  function save(next){setSaving(n=>n+1);queue.current=queue.current.then(async()=>{try{const {data}=await client.put(endpoint+'/'+initial.attemptId+'/answers',{answers:next,revision:revision.current});revision.current=data.revision;if(data.status==='COMPLETED')setAttempt(data);setError('');}catch(e){setError('응답 저장 실패: '+message(e));}finally{setSaving(n=>n-1);}});}
  function choose(index,value){if(targetQuestion?.index===index)setNavigationNote('');const next=[...answersRef.current];next[index]=value;answersRef.current=next;setAnswers(next);save(next);}
  function move(next){setTargetQuestion(null);setNavigationNote('');setPage(next);}
  function goToQuestion(index){setPage(Math.floor(index/pageSize));setTargetQuestion({index});setNavigationNote((index+1)+'번 문항으로 이동했습니다.');}
  function goToMissing(){const index=answersRef.current.findIndex(value=>value===null);if(index<0){setNavigationNote('모든 문항에 응답했습니다.');return;}setPage(Math.floor(index/pageSize));setTargetQuestion({index});setNavigationNote('미응답 '+(index+1)+'번 문항으로 이동했습니다. 번호가 빠른 미응답부터 안내합니다.');}
  useEffect(()=>{if(isComplete)return;const element=targetQuestion?document.getElementById('career-question-'+targetQuestion.index):document.getElementById('career-question-heading');element?.focus({preventScroll:true});element?.scrollIntoView({block:targetQuestion?'center':'start',behavior:'instant'});},[page,targetQuestion,isComplete]);
  async function submit(){if(submitting.current)return;const missing=answersRef.current.findIndex(v=>v===null);if(missing!==-1){goToMissing();return;}submitting.current=true;setBusy(true);setError('');try{await queue.current;const {data}=await client.post(endpoint+'/'+initial.attemptId+'/submit',{answers:answersRef.current,revision:revision.current});setAttempt(data);}catch(e){setError(message(e));}finally{submitting.current=false;setBusy(false);}}
  if(isComplete)return <Result attempt={attempt}/>;
  const completed=answers.filter(a=>a!==null).length, start=page*pageSize, total=answers.length, pages=Math.ceil(total/pageSize), personality=exploration||attempt.questions[start]?.type==='personality';
  return <div className="career-page career-run"><header className="career-run-heading"><div><p className="eyebrow">MY CAREER CHECK</p><h1>{exploration?'잡브릿지 진로 탐색 검사':(attempt.title||'잡브릿지 인적성검사')}</h1></div></header><div className="career-progress"><span>응답 {completed} / {total}</span><small role="status">{saving?'저장 중…':error?'저장 상태를 확인해 주세요.':'응답 자동 저장'}</small><progress value={completed} max={total} aria-label="검사 진행률" /></div>
    <p className="career-timeout-policy">{exploration?'시간 종료 시 저장된 응답으로 진로 탐색 결과를 생성합니다. 진로 탐색 결과는 기업에 제출되지 않습니다.':attempt.mode==='practice'?'연습형 결과는 본인만 확인합니다. 시간 종료 시 저장된 답안으로 채점하며 미응답은 오답 처리합니다.':'시간 종료 시 저장된 답안으로 자동 제출됩니다. 미응답 적성은 오답, 인성은 응답 부족으로 표시합니다. 최신 인성·실전형 결과는 다음 입사지원 시 기업에 전달됩니다.'}</p>
    <div className="career-run-layout"><div><h2 id="career-question-heading" tabIndex="-1">{exploration?(start<24?'01. 관심 있는 활동':'02. 직업가치관'):(personality?'나의 일하는 방식':attempt.title||'직무 기초 문제')} <small>{page+1} / {pages}</small></h2><p className="muted">{exploration?'평소의 관심과 중요하게 생각하는 가치에 맞게 선택해 주세요. 정답은 없습니다.':personality?'평소의 모습을 떠올리며 솔직하게 선택해 주세요. 인성 문항에는 정답이 없습니다.':'문제를 읽고 가장 알맞은 답 하나를 선택해 주세요.'}</p>
    {attempt.questions.slice(start,start+pageSize).map((q,n)=><fieldset id={'career-question-'+(start+n)} tabIndex={-1} className={'career-question'+(targetQuestion?.index===start+n&&answers[start+n]===null?' is-missing-target':'')} key={q.id} disabled={busy||seconds===0}><legend><span>{start+n+1}</span>{q.prompt}</legend><div className={exploration||q.type==='personality'?'career-choices personality':'career-choices'}>{q.choices.map((choice,value)=><label key={value}><input type="radio" name={q.id} checked={answers[start+n]===value} onChange={()=>choose(start+n,value)}/><span>{choice}</span></label>)}</div></fieldset>)}
    {error&&<div className="career-save-error" role="alert"><p>{error}</p><button className="secondary" disabled={saving>0||busy} onClick={()=>save(answersRef.current)}>현재 답안 다시 저장</button></div>}
    <div className="career-pagination"><button className="secondary" disabled={page===0||busy} onClick={()=>move(page-1)}>이전 문항</button>{page<pages-1?<button disabled={busy} onClick={()=>move(page+1)}>다음 문항</button>:<button disabled={busy||seconds===0} onClick={submit}>{busy?'결과 생성 중…':'제출하고 결과 보기'}</button>}</div></div>
    <aside className="career-question-nav"><div className="career-nav-timer"><span>남은 시간</span><span className={seconds<120?'career-timer urgent':'career-timer'} aria-label="남은 시간">{Math.floor(seconds/60).toString().padStart(2,'0')}:{(seconds%60).toString().padStart(2,'0')}</span></div><h3>응답 현황</h3><div className="career-nav-summary"><strong>응답 {completed} / {total}</strong><span>미응답 {total-completed}개</span><progress value={completed} max={total} aria-label="전체 응답률" /></div><section className="career-nav-legend" aria-label="응답 현황 범례"><span><i className="answered" />응답 완료</span><span><i />미응답</span></section><p className="career-nav-description">{exploration?'흥미 1–24 · 가치관 25–36':attempt.mode==='legacy'?'인성 1–40 · 적성 41–55':(attempt.title+' · '+total+'문항')}</p><div className="career-question-groups">{Array.from({length:pages},(_,group)=><div key={group} className={'career-question-group'+(group===page?' is-current':'')} role="group" aria-label={(group*pageSize+1)+'–'+Math.min((group+1)*pageSize,total)+'번 문항'+(group===page?' · 현재 화면':'')} style={{'--question-columns':pageSize===6?3:5}}>{answers.slice(group*pageSize,(group+1)*pageSize).map((answer,offset)=>{const index=group*pageSize+offset;return <button key={index} type="button" className={answer!==null?'answered':''} aria-label={(index+1)+'번 문항 '+(answer!==null?'응답 완료':'미응답')} aria-current={group===page?'step':undefined} onClick={()=>goToQuestion(index)}>{index+1}</button>;})}</div>)}</div><p className="career-current-range">현재 문항 <strong>{start+1}–{Math.min(start+pageSize,total)}</strong></p><button type="button" className="career-nav-missing" disabled={busy||seconds===0||completed===total} onClick={goToMissing}>첫 미응답으로 이동</button><p className="career-nav-feedback" role="status" aria-live="polite">{navigationNote}</p><Link to={route}>검사 안내·내역</Link></aside></div>
  </div>;
}
const traitNotes={cooperation:['다양한 의견을 조율하고 정보를 나누는 방식을 돌아보세요.','협업 시 필요한 정보와 기대 역할을 먼저 공유해 보세요.'],responsibility:['맡은 일을 마무리하고 결과를 점검하는 경향을 살펴봅니다.','마감 전 중간 점검과 완료 기준을 정해 보세요.'],planning:['우선순위를 정하고 작업을 구조화하는 경향을 살펴봅니다.','오늘의 중요한 일 세 가지를 정리하는 연습을 해보세요.'],adaptability:['새로운 방법을 시도하고 변화에 적응하는 방식을 살펴봅니다.','익숙하지 않은 도구를 작은 과제부터 사용해 보세요.'],composure:['부담과 예상 밖 상황에서 대응하는 방식을 돌아보세요.','급할수록 사실 확인과 다음 행동을 구분해 정리해 보세요.']};
function AssessmentReport({attempt}){
  const report=attempt.report, total=report.questionCount||55, aptitudeCount=report.aptitudeCount??15, practice=attempt.mode==='practice', ranked=(report.traits||[]).filter(t=>t.score!==null).toSorted((a,b)=>b.score-a.score),uniform=new Set(attempt.answers.slice(0,40).filter(a=>a!==null)).size===1;
  return <article className="career-page career-report"><p className="eyebrow">MY CAREER REPORT</p><h1>나를 이해하는 새로운 출발</h1><p className="muted">{formatDateTime(attempt.completedAt)} 완료 · {report.answeredCount} / {total}문항 응답</p><div className="career-notice">{report.notice}<p>{practice?'연습형 결과는 본인만 확인하며 기업에 제출하지 않습니다.':'최신 인성검사·적성 실전형 결과는 다음 지원 시 자동 제출됩니다. 기존 지원 건에는 재검사 결과가 반영되지 않습니다.'}</p>{report.timedOut&&<p><strong>응시 시간이 종료되어 저장된 답안으로 결과를 만들었습니다. 미응답 적성 문항은 오답으로 처리됩니다.</strong></p>}{report.traits.length>0&&uniform&&<p>인성 문항에 모두 같은 응답을 선택했습니다. 자신의 평소 모습을 충분히 반영했는지 확인해 주세요.</p>}</div>
    <div className="career-report-summary">{report.traits.length>0&&<section><span>{attempt.title||'인적성검사'}</span><h2>{ranked.length>1&&ranked.every(t=>t.score===ranked[0].score)?'고르게 나타난 성향':ranked[0]?.label||'응답 부족'}</h2><p>이번 응답에서 나타난 나의 성향입니다. 영역별 설명을 함께 확인해 보세요.</p></section>}{aptitudeCount>0&&<section><span>{practice?'적성 연습 점수':'적성 점수'}</span><h2>{report.aptitudeScore}<small> / 100점</small></h2><p>{aptitudeCount}문항 중 {report.correctCount}문항 정답 · 백분위가 아닌 정답률입니다.</p></section>}</div>
    {report.traits.length>0&&<section className="career-report-panel"><h2>일하는 방식 · 5개 성향</h2><p className="muted">응답을 0–100으로 환산한 값입니다. 점수가 높거나 낮다고 우수·부적합을 의미하지 않습니다.</p>{report.traits.map(t=><div className="career-trait" key={t.axis}><div><strong>{t.label}</strong><span>{t.score===null?'응답 없음':t.score+' / 100'}</span></div><progress value={t.score??0} max="100" aria-label={t.label}/><p>{traitNotes[t.axis][0]} {t.score!==null&&t.score<50?traitNotes[t.axis][1]:''}{t.answered<8?' (일부 문항 미응답: '+t.answered+'/8문항)':''}</p></div>)}</section>}
    {aptitudeCount>0&&<section className="career-report-panel"><h2>직무 기초 · 영역별 결과</h2><div className="career-abilities">{report.abilities.map(a=><div key={a.label}><strong>{a.label}</strong><b>{a.score}<small>점</small></b><progress value={a.score} max="100" aria-label={a.label}/><p>{a.correct} / {a.total}문항 정답</p><small>{a.score<60?'풀이 과정을 정리하며 기초 문제부터 연습해 보세요.':'이번 문항에서 잘 풀어낸 영역입니다. 다양한 유형도 연습해 보세요.'}</small></div>)}</div></section>}
    {aptitudeCount>0&&<section className="career-report-panel"><h2>적성 문항 해설</h2>{report.review.map((q,i)=><details className="career-review" key={i}><summary>{q.number??i+41}번 · {q.selected===q.correct?'정답':'오답 또는 미응답'}</summary><p>{q.prompt}</p><p>선택: {q.selected===null?'미응답':q.choices[q.selected]}</p><strong>정답: {q.choices[q.correct]}</strong><p>{q.explanation}</p></details>)}</section>}
    <div className="career-result-actions"><Link className="button" to="/jobs">채용공고 탐색하기</Link><Link className="button secondary" to="/career-test#history">내 검사 내역</Link><button className="secondary" onClick={()=>window.print()}>결과 인쇄·PDF 저장</button></div>
  </article>;
}
