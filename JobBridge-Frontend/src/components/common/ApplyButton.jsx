import { Link } from 'react-router-dom';
import { useResource } from '../../hooks/useResource';
import { Resource } from './UI';

export default function ApplyButton({ id, accepting, onApply }) {
  const resource = useResource('/applications', { refreshInterval: 30000 });
  return <Resource resource={resource}>{(rows) => {
    const pending=rows.find((row) => String(row.jobId)===String(id) && ['APPLIED','UNDER_REVIEW','DOCUMENT_PASSED','INTERVIEW'].includes(row.status));
    return pending ? <p className="callout" role="status">이미 지원한 공고입니다. 전형 결과가 나오기 전까지 중복 지원할 수 없습니다. <Link to={'/applications/'+pending.applicationId}>지원 내역 보기</Link></p> :
      <button disabled={!accepting} onClick={onApply}>입사 지원하기 →</button>;
  }}</Resource>;
}
