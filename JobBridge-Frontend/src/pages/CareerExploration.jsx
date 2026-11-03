import { Link, useParams } from 'react-router-dom';
import { useAuth } from '../stores/auth';
import { MemberTests, TestAttempt } from './CareerAssessment';
import { formatDateTime } from '../utils/dateTime';

export default function CareerExploration() {
  const user=useAuth(s=>s.user),{id}=useParams();
  if(id&&user?.role==='JOB_SEEKER')return <TestAttempt key={`${user.userId}:${id}`} id={id} kind="exploration" Report={ExplorationReport}/>;
  return <div className="career-page exploration-page"><div className="career-intro"><p className="eyebrow">JOBBRIDGE CAREER EXPLORER</p><h1>어떤 일이 나와 맞을까?<br/><em>내 진로 찾기</em></h1><p>관심 있는 활동과 일에서 중요하게 생각하는 가치를 돌아보세요.<br/>취업 방향을 고민할 때 참고할 나만의 탐색 지도를 만들어 봅니다.</p><div className="career-facts"><span>총 36문항</span><span>최대 20분</span><span>무료 자체 탐색</span></div></div>
    <div className="career-test-sections"><section><span>01 · INTEREST</span><h2>어떤 활동이 끌리나요?</h2><p>실무·기술 / 탐구·분석 / 창의·표현 / 교육·지원 / 기획·설득 / 체계·운영</p><strong>흥미 24문항</strong></section><section><span>02 · WORK VALUES</span><h2>일에서 무엇이 중요한가요?</h2><p>성장·학습 / 안정·예측 / 기여·의미 / 자율·주도 / 보상·인정 / 생활·균형</p><strong>직업가치관 12문항</strong></section></div>
    <div className="career-notice"><strong>정답보다 나의 생각이 중요해요</strong><p>모든 문항에 정답은 없습니다. 평소의 관심과 생각에 가까운 응답을 골라 주세요. 잡브릿지가 만든 예시 검사이며 전문적으로 표준화된 진로 진단이 아닙니다. 결과는 개인 참고용으로, 기업에 자동 제출되지 않습니다.</p><p>응답은 자동 저장되며 새로고침 후 이어서 진행할 수 있습니다. 20분이 지나면 저장된 응답으로 결과를 만듭니다.</p></div>
    {!user?<div className="career-start"><Link className="button" to="/login" state={{careerExploration:true}}>개인회원 로그인 후 탐색하기</Link></div>:user.role!=='JOB_SEEKER'?<div className="career-notice">개인회원 전용 기능입니다.</div>:<MemberTests key={user.userId} kind="exploration"/>}
  </div>;
}
function RankedDomains({rows,title}){
  return <section className="career-report-panel"><h2>{title}</h2>{rows.map(row=><div className="career-trait" key={row.axis}><div><strong>{row.label}</strong><span>{row.score===null?'응답 없음':row.score+' / 100'}</span></div><progress value={row.score??0} max="100" aria-label={row.label}/><p>{row.description}{row.answered<row.total?` (${row.answered}/${row.total}문항 응답)`:''}</p></div>)}</section>;
}
function ExplorationReport({attempt}) {
  const report=attempt.report,rank=(rows)=>rows.filter(r=>r.score!==null).toSorted((a,b)=>b.score-a.score),interests=rank(report.interests),values=rank(report.values),interestTied=interests.length>1&&interests.every(r=>r.score===interests[0].score),valueTied=values.length>1&&values.every(r=>r.score===values[0].score);
  const topLabel=(rows,tied)=>tied?'고른 '+(rows===interests?'관심':'가치관'):rows.filter(r=>r.score===rows[0]?.score).map(r=>r.label).join(' · ')||'응답 부족';
  const suggestions=(interestTied?interests:interests.filter(r=>r.score>=(interests[1]?.score??interests[0]?.score))).flatMap(r=>(r.keywords||[]).map(keyword=>({keyword,label:r.label})));
  return <article className="career-page career-report exploration-page"><p className="eyebrow">MY CAREER EXPLORATION</p><h1>나의 진로 탐색 지도</h1><p className="muted">{formatDateTime(attempt.completedAt)} 완료 · {report.answeredCount} / 36문항 응답</p><div className="career-notice">{report.notice}{report.timedOut&&<p>시간이 종료되어 저장된 응답으로 결과를 만들었습니다. 미응답 영역은 해석에 유의해 주세요.</p>}<p>점수는 본인의 응답을 환산한 값이며, 백분위나 직무 적합성 점수가 아닙니다. 같은 점수의 영역은 순위를 구분하지 않습니다.</p></div>
    <div className="career-report-summary"><section><span>관심 활동</span><h2>{topLabel(interests,interestTied)}</h2><p>{interestTied?'여러 활동에 비슷한 관심을 보였습니다. 각 활동을 경험하며 비교해 보세요.':'상대적으로 관심이 높게 나타난 활동입니다. 실제 업무 내용과 함께 살펴보세요.'}</p></section><section><span>중요하게 생각하는 가치</span><h2>{topLabel(values,valueTied)}</h2><p>직장을 알아볼 때 근무 조건과 조직 문화를 함께 확인해 보세요.</p></section></div>
    <RankedDomains rows={report.interests} title="관심 활동 · 6개 영역"/><RankedDomains rows={report.values} title="직업가치관 · 6개 영역"/>
    <section className="career-report-panel"><h2>관심 분야를 더 알아보세요</h2><p className="muted">이번 응답과 연결해 탐색할 수 있는 검색어입니다. 직업 추천이나 적합성 판정은 아닙니다.</p><div className="exploration-keywords">{suggestions.map(({keyword,label})=><Link key={label+keyword} to={'/jobs?q='+encodeURIComponent(keyword)}>{keyword} 공고 보기</Link>)}</div><p className="muted">관심 분야의 실제 업무와 요구 역량을 살펴보고, 가치관에 맞는 기업인지 비교해 보세요.</p></section>
    <div className="career-result-actions"><Link className="button" to="/jobs">채용공고 탐색하기</Link><Link className="button secondary" to="/career-exploration#history">내 탐색 내역</Link><button className="secondary" onClick={()=>window.print()}>결과 인쇄·PDF 저장</button></div>
  </article>;
}
