import { useEffect, useRef } from 'react';
import { useResource } from '../../hooks/useResource';
import client from '../../api/client';
import { Empty, Form, Resource } from './UI';

export default function ApplyForm({ id, onDone }) {
  const resource = useResource('/resumes');
  const assessment = useResource('/career-assessments/submission', { refreshInterval: 15000 });
  const heading = useRef(null);
  useEffect(() => {
    window.scrollTo(0, 0);
    heading.current?.focus();
  }, []);
  return (
    <section className="panel narrow">
      <h2 ref={heading} tabIndex={-1}>입사 지원</h2>
      <p className="muted">제출 시점의 이력서·연락처와 최신 완료 인적성검사 결과가 지원한 기업에 함께 전달됩니다.</p>
      <div className="callout" role="status">{assessment.loading?'함께 제출할 검사 결과를 확인하는 중입니다.':assessment.error?<><p>{assessment.error}</p><button type="button" className="secondary" onClick={assessment.reload}>검사 결과 다시 확인</button></>:assessment.data?.assessment?<><strong>인적성검사 결과 자동 제출</strong><p>55문항을 완료한 최신 검사 결과가 이력서와 함께 제출됩니다. 별도 파일 첨부는 필요 없습니다.</p></>:<><strong>완료한 인적성검사 결과가 없습니다.</strong><p>검사 없이 이력서만 제출할 수 있습니다. 검사 후 지원하려면 <a href="/career-test">인적성검사</a>를 먼저 완료해 주세요.</p></>}<small>진로 탐색 결과와 개별 응답은 기업에 전달하지 않습니다.</small></div>
      <Resource resource={resource}>
        {(rows) =>
          rows.length ? (
            <Form
              submitDisabled={assessment.loading || !!assessment.error}
              fields={[
                {
                  name: 'resumeId',
                  label: '제출 이력서',
                  required: true,
                  options: rows.map((r) => ({
                    value: r.resumeId,
                    label: r.title,
                  })),
                },
                { name: 'coverLetter', label: '자기소개서', type: 'textarea' },
              ]}
              onSubmit={async (body) => {
                await client.post(`/jobs/${id}/applications`, body);
                onDone();
              }}
              submit="지원서 제출"
            />
          ) : (
            <Empty to="/resumes/new" action="이력서 작성">
              먼저 이력서를 작성해 주세요.
            </Empty>
          )
        }
      </Resource>
    </section>
  );
}
