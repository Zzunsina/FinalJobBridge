import { formatDateTime } from '../../utils/dateTime';
import '../../assets/styles/career-assessment.css';
export default function SubmittedAssessment({data}) {
 if(!data?.report)return <section className="submitted-assessment"><h3>지원 당시 인적성검사</h3><p className="muted">이 지원 건에 함께 제출된 인적성검사 결과가 없습니다.</p></section>;
 const report=data.report;
 return <section className="submitted-assessment"><h3>지원 당시 인적성검사</h3><p className="muted">{formatDateTime(data.completedAt)} 완료 · 지원 시점에 자동 제출된 결과</p><p>적성 연습 <strong>{report.aptitudeScore}점</strong> · 15문항 중 {report.correctCount}문항 정답</p><div className="submitted-assessment-grid">{report.traits.map(t=><div key={t.axis}><span>{t.label}</span><strong>{t.score??'미응답'}{t.score!==null?' / 100':''}</strong><progress value={t.score??0} max="100" aria-label={t.label}/></div>)}</div><details><summary>적성 영역별 결과</summary>{report.abilities.map(a=><p key={a.label}>{a.label} · {a.score}점 ({a.correct}/{a.total}문항 정답)</p>)}</details><p className="muted">{report.notice} 인성 점수는 성향 응답의 환산값이며 우열을 뜻하지 않습니다.</p><small>제출한 결과는 이후 재검사해도 바뀌지 않습니다. 진로 탐색 결과와 개별 응답은 포함하지 않습니다.</small></section>;
}
