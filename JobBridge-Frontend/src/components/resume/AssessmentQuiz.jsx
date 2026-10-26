import { useState } from 'react';
import { useResource } from '../../hooks/useResource';
import { Resource } from '../common/UI';

export default function AssessmentQuiz({ result, answers, onChange }) {
  const resource = useResource('/assessments/questions');
  const [started, setStarted] = useState(false);
  return <section className="resume-sheet" id="resume-assessment">
    <h2>예시 인적성검사</h2>
    <p className="callout">직무기초 예시 10문항입니다. 실제 인적성 진단이나 검증된 채용 평가도구가 아닙니다. 점수만으로 합격·불합격을 결정하지 않습니다.</p>
    {result && <p>저장된 예시 검사 점수: <strong>{result.score}점 / 100점</strong></p>}
    {!started ? <button type="button" className="secondary" onClick={() => setStarted(true)}>{result ? '예시 검사 다시 풀기' : '예시 검사 시작'}</button> :
      <Resource resource={resource}>{(data) => <><p>{data.label} · 모든 문항에 답한 뒤 이력서를 저장하면 서버에서 자동 채점합니다.</p>
        {data.questions.map((question,index) => <fieldset key={question.id} className="assessment-question"><legend>{index+1}. {question.prompt}</legend>
          {question.choices.map((choice,answer) => <label className="assessment-choice" key={answer}><input type="radio" name={'assessment-'+question.id} required checked={answers?.[index]===answer} onChange={() => { const next=answers ? [...answers] : Array(data.questions.length).fill(null);next[index]=answer;onChange(next); }} /> {choice}</label>)}
        </fieldset>)}</>}</Resource>}
  </section>;
}
