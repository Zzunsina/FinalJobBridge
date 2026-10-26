import { Link } from 'react-router-dom';

export default function AssessmentRanking({ rows }) {
  const top = rows.filter((a) => a.score != null)
    .sort((a,b) => b.score-a.score || a.applicationId-b.applicationId).slice(0,10);
  return <section className="panel"><h2>예시 검사 점수 상위 10명</h2><p className="muted">동점은 지원 접수 순입니다. 아래 전체 지원자 조회도 유지됩니다.</p>
    {top.length ? <ol>{top.map((a) => <li key={a.applicationId}><Link to={'/company/applications/'+a.applicationId}>{a.name} · {a.score}점</Link></li>)}</ol> : <p>검사를 완료한 지원자가 없습니다.</p>}
  </section>;
}
