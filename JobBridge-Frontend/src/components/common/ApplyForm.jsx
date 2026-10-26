import { useEffect, useRef } from 'react';
import { useResource } from '../../hooks/useResource';
import client from '../../api/client';
import { Empty, Form, Resource } from './UI';

export default function ApplyForm({ id, onDone }) {
  const resource = useResource('/resumes');
  const heading = useRef(null);
  useEffect(() => {
    window.scrollTo(0, 0);
    heading.current?.focus();
  }, []);
  return (
    <section className="panel narrow">
      <h2 ref={heading} tabIndex={-1}>입사 지원</h2>
      <p className="muted">제출 시점의 이력서와 연락처가 기업에 전달됩니다.</p>
      <Resource resource={resource}>
        {(rows) =>
          rows.length ? (
            <Form
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
