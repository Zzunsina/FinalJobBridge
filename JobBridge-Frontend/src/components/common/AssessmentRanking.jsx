import { Link } from 'react-router-dom';

export function rankedApplicants(rows) {
  const seen=new Set();
  return rows.filter((row) => row.status !== 'CANCELED' && row.score != null && Number.isFinite(Number(row.score)))
    .toSorted((a,b) => Number(b.score)-Number(a.score) || (Date.parse(a.appliedAt) || 0)-(Date.parse(b.appliedAt) || 0) || a.applicationId-b.applicationId)
    .filter((row) => { const key=row.applicantUserId ?? row.applicationId; if(seen.has(key))return false;seen.add(key);return true; });
}
export default function AssessmentRanking({ rows }) {
  const ranked=rankedApplicants(rows), top=ranked.slice(0,10);
  return <section className="panel assessment-ranking"><h2>지원 당시 적성 점수 상위 10명</h2>
    <p className="muted">지원 당시 제출된 인적성검사의 적성 점수 기준입니다. 동점은 지원 접수 순이며, 지원자별 최대 한 번 표시됩니다.</p>
    <p className="assessment-ranking-count">검사 결과 제출 지원자 {ranked.length}명 중 {top.length}명 표시 · 인성 성향 점수는 순위에 포함하지 않습니다.</p>
    {top.length ? <ol>{top.map((row) => <li key={row.applicationId}><Link to={'/company/applications/'+row.applicationId}><strong>{row.name}</strong><span>{row.score}점</span></Link></li>)}</ol> : <p>제출된 검사 결과가 없습니다.</p>}
  </section>;
}
