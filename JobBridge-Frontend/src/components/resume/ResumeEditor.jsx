import { useId, useState } from 'react';
import { Link } from 'react-router-dom';
import { message } from '../../api/client';
import { useAuth } from '../../stores/auth';
import ResumeDocument from './ResumeDocument';
import AssessmentQuiz from './AssessmentQuiz';
import CalendarPicker from '../common/CalendarPicker';
import ProfileAvatar from '../common/ProfileAvatar';
import '../../assets/styles/resume.css';

const sections = [
  {
    key: 'educations',
    title: '학력',
    english: 'EDUCATION',
    description: '학교와 전공, 재학 기간을 정리해 주세요.',
    max: 10,
    fields: [
      { key: 'school', label: '학교명', placeholder: '학교명을 입력해 주세요', required: true },
      { key: 'major', label: '전공', placeholder: '전공 또는 계열' },
      {
        key: 'degree',
        label: '학위·구분',
        options: ['고등학교', '전문학사', '학사', '석사', '박사', '기타'],
      },
      {
        key: 'status',
        label: '학적 상태',
        options: ['졸업', '졸업 예정', '재학', '휴학', '수료', '중퇴'],
      },
      { key: 'startDate', label: '입학 연월', type: 'month' },
      { key: 'endDate', label: '졸업·종료 연월', type: 'month' },
    ],
  },
  {
    key: 'experiences',
    title: '경력',
    english: 'EXPERIENCE',
    description: '맡았던 역할과 성과를 구체적으로 작성해 주세요.',
    max: 20,
    fields: [
      { key: 'company', label: '회사명', placeholder: '근무한 회사명', required: true },
      { key: 'position', label: '부서·직무', placeholder: '개발팀 · 프런트엔드 개발자' },
      { key: 'startDate', label: '입사 연월', type: 'month' },
      { key: 'endDate', label: '퇴사 연월', type: 'month' },
      { key: 'current', label: '현재 재직 중', type: 'checkbox' },
      {
        key: 'description',
        label: '담당 업무·성과',
        type: 'textarea',
        wide: true,
        placeholder:
          '• 담당한 업무와 역할\n• 해결한 문제와 기여한 내용\n• 수치로 표현할 수 있는 성과',
      },
    ],
  },
  {
    key: 'projects',
    title: '프로젝트',
    english: 'PROJECTS',
    description: '실무, 팀 프로젝트, 개인 프로젝트 경험을 자유롭게 기록하세요.',
    max: 20,
    fields: [
      { key: 'name', label: '프로젝트명', required: true, placeholder: '프로젝트 이름' },
      { key: 'role', label: '참여 역할', placeholder: '기획 · 디자인 · 개발 등' },
      { key: 'startDate', label: '시작 연월', type: 'month' },
      { key: 'endDate', label: '종료 연월', type: 'month' },
      {
        key: 'description',
        label: '프로젝트 소개·기여도',
        type: 'textarea',
        wide: true,
        placeholder: '프로젝트의 목표, 사용한 기술, 본인의 기여와 결과를 작성해 주세요.',
      },
    ],
  },
  {
    key: 'certificates',
    title: '자격·어학',
    english: 'CERTIFICATES',
    description: '자격증, 어학 시험과 수상 실적을 추가해 보세요.',
    max: 20,
    fields: [
      {
        key: 'name',
        label: '자격·시험·수상명',
        required: true,
        placeholder: '정보처리기사 · TOEIC 등',
      },
      { key: 'issuer', label: '발급·주관 기관', placeholder: '발급 기관명' },
      { key: 'date', label: '취득 연월', type: 'month' },
      { key: 'score', label: '등급·점수', placeholder: '등급 또는 점수 (선택)' },
    ],
  },
];

function Field({ label, required, wide, children }) {
  return (
    <label className={`resume-field ${wide ? 'wide' : ''}`}>
      <span>
        {label}
        {required && <small> *</small>}
      </span>
      {children}
    </label>
  );
}

export default function ResumeEditor({ initial = {}, onSave }) {
  const user = useAuth((state) => state.user);
  const formId = useId();
  const [values, setValues] = useState(() => ({
    ...initial,
    title: initial.title || '',
    skills: initial.skills || '',
    portfolioUrl: initial.portfolioUrl || '',
    details: {
      desiredRole: '',
      summary: '',
      educations: [],
      experiences: [],
      projects: [],
      certificates: [],
      ...initial.details,
      legacyEducation:
        initial.details?.legacyEducation ?? (!initial.details ? initial.education || '' : ''),
      legacyCareerDescription:
        initial.details?.legacyCareerDescription ??
        (!initial.details ? initial.careerDescription || '' : ''),
    },
  }));
  const [preview, setPreview] = useState(false);
  const [attachmentChanged, setAttachmentChanged] = useState(false);
  const [assessmentAnswers, setAssessmentAnswers] = useState(undefined);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const details = values.details;
  const skills = values.skills
    .split(/[,，\n]/)
    .map((s) => s.trim())
    .filter(Boolean);
  const completed = [
    values.title.trim(),
    details.educations.length || details.legacyEducation,
    details.experiences.length || details.projects.length || details.legacyCareerDescription,
    skills.length,
    details.summary.trim(),
    values.attachment || details.certificates.length,
  ].filter(Boolean).length;
  function field(name, value) {
    setValues((old) => ({ ...old, [name]: value }));
  }
  function detail(name, value) {
    setValues((old) => ({ ...old, details: { ...old.details, [name]: value } }));
  }
  function updateRow(key, index, name, value) {
    detail(
      key,
      details[key].map((row, i) =>
        i === index
          ? { ...row, [name]: value, ...(name === 'current' && value ? { endDate: '' } : {}) }
          : row,
      ),
    );
  }
  async function save(event) {
    event.preventDefault();
    if (!initial.assessment && assessmentAnswers === undefined) {
      setError('인적성검사를 완료한 후 이력서를 저장해 주세요.');
      document.getElementById('resume-assessment')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
      return;
    }
    if (assessmentAnswers !== undefined && (assessmentAnswers.length !== 10 || assessmentAnswers.some((answer) => !Number.isInteger(answer) || answer < 0 || answer > 3))) {
      setError('인적성검사의 모든 문항에 답해 주세요.');
      document.getElementById('resume-assessment')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
      return;
    }
    setBusy(true);
    setError('');
    try {
      const education = [
        details.legacyEducation,
        ...details.educations.map((r) => [r.school, r.major, r.status].filter(Boolean).join(' · ')),
      ]
        .filter(Boolean)
        .join(' / ')
        .slice(0, 100);
      const careerDescription = [
        details.legacyCareerDescription,
        ...details.experiences.map((r) =>
          [r.company, r.position, r.description].filter(Boolean).join('\n'),
        ),
        ...details.projects.map((r) => [r.name, r.role, r.description].filter(Boolean).join('\n')),
      ]
        .filter(Boolean)
        .join('\n\n')
        .slice(0, 10000);
      const body = { ...values, education, careerDescription };
      if (!attachmentChanged) delete body.attachment;
      if (assessmentAnswers !== undefined) body.assessmentAnswers = assessmentAnswers;
      await onSave(body);
    } catch (e) {
      setError(message(e));
      setPreview(false);
    } finally {
      setBusy(false);
    }
  }
  return (
    <div className="resume-workspace">
      <div className="resume-toolbar">
        <div>
          <span className="resume-editor-label">MY CAREER STORY</span>
          <p>경험을 차곡차곡, 나를 보여주는 한 장의 이력서.</p>
        </div>
        <div className="actions">
          <button type="button" className="secondary" onClick={() => setPreview(!preview)}>
            {preview ? '작성으로 돌아가기' : '이력서 미리보기'}
          </button>
          {!preview && (
            <button type="submit" form={formId} disabled={busy}>
              {busy ? '저장 중…' : '이력서 저장'}
            </button>
          )}
        </div>
      </div>
      {preview ? (
        <div className="resume-preview">
          <ResumeDocument resume={values} contact={user} useProfilePhoto />
          <button className="secondary" onClick={() => setPreview(false)}>
            계속 작성하기
          </button>
        </div>
      ) : (
        <div className="resume-layout">
          <form id={formId} className="resume-editor" onSubmit={save}>
            <section className="resume-sheet resume-intro" id="resume-profile">
              <div className="resume-section-heading">
                <div>
                  <span>01 · BASIC INFORMATION</span>
                  <h2>기본 정보</h2>
                </div>
                <span className="resume-section-tag">나의 첫인상</span>
              </div>
              <Field label="이력서 제목" required>
                <input
                  value={values.title}
                  onChange={(e) => field('title', e.target.value)}
                  required
                  maxLength={200}
                  placeholder="나의 강점과 지원 직무를 담은 제목을 작성해 주세요"
                />
              </Field>
              <div className="resume-profile-card">
                <ProfileAvatar user={user} className="resume-profile-avatar" />
                <div>
                  <h3>{user?.name}</h3>
                  <p>{user?.email}</p>
                  <p>{user?.phone}</p>
                </div>
                <Link to="/mypage">내 정보 수정 ↗</Link>
              </div>
              <Field label="희망 직무">
                <input
                  value={details.desiredRole}
                  maxLength={100}
                  onChange={(e) => detail('desiredRole', e.target.value)}
                  placeholder="예: 프런트엔드 개발자"
                />
              </Field>
            </section>
            {sections.map((section, number) => (
              <section key={section.key} className="resume-sheet" id={`resume-${section.key}`}>
                <div className="resume-section-heading">
                  <div>
                    <span>
                      {String(number + 2).padStart(2, '0')} · {section.english}
                    </span>
                    <h2>{section.title}</h2>
                    <p>{section.description}</p>
                  </div>
                  <button
                    type="button"
                    className="secondary resume-add"
                    disabled={details[section.key].length >= section.max}
                    onClick={() => detail(section.key, [...details[section.key], {}])}
                  >
                    ＋ 추가
                  </button>
                </div>
                {!details[section.key].length && (
                  <div className="resume-section-empty">
                    <span aria-hidden="true">＋</span>
                    <p>{section.title} 항목을 추가해 주세요.</p>
                    <small>해당하는 내용이 없다면 비워두어도 괜찮습니다.</small>
                  </div>
                )}
                {details[section.key].map((row, index) => (
                  <fieldset className="resume-entry-form" key={index}>
                    <legend>
                      {section.title} {index + 1}
                    </legend>
                    <button
                      type="button"
                      className="plain resume-remove"
                      aria-label={`${section.title} 항목 ${index + 1} 삭제`}
                      onClick={() =>
                        detail(
                          section.key,
                          details[section.key].filter((_, i) => i !== index),
                        )
                      }
                    >
                      항목 삭제 ×
                    </button>
                    <div className="resume-entry-fields">
                      {section.fields.map((item) => (
                        <Field
                          key={item.key}
                          label={item.label}
                          required={item.required}
                          wide={item.wide}
                        >
                          {item.options ? (
                            <select
                              value={row[item.key] || ''}
                              onChange={(e) =>
                                updateRow(section.key, index, item.key, e.target.value)
                              }
                            >
                              <option value="">선택해 주세요</option>
                              {item.options.map((o) => (
                                <option key={o}>{o}</option>
                              ))}
                            </select>
                          ) : item.type === 'month' ? (
                            <CalendarPicker type="month" label={item.label} value={row[item.key] || ''} required={item.required} disabled={item.key === 'endDate' && Boolean(row.current)} min="1900-01" max="2100-12" onChange={(value) => updateRow(section.key, index, item.key, value)} />
                          ) : item.type === 'textarea' ? (
                            <textarea
                              value={row[item.key] || ''}
                              rows={5}
                              maxLength={5000}
                              placeholder={item.placeholder}
                              onChange={(e) =>
                                updateRow(section.key, index, item.key, e.target.value)
                              }
                            />
                          ) : (
                            <input
                              type={item.type || 'text'}
                              value={item.type === 'checkbox' ? undefined : row[item.key] || ''}
                              checked={
                                item.type === 'checkbox' ? Boolean(row[item.key]) : undefined
                              }
                              required={item.required}
                              disabled={item.key === 'endDate' && Boolean(row.current)}
                              maxLength={item.type === 'url' ? 500 : 100}
                              min={item.type === 'month' ? '1900-01' : undefined}
                              max={item.type === 'month' ? '2100-12' : undefined}
                              placeholder={item.placeholder}
                              onChange={(e) =>
                                updateRow(
                                  section.key,
                                  index,
                                  item.key,
                                  item.type === 'checkbox' ? e.target.checked : e.target.value,
                                )
                              }
                            />
                          )}
                        </Field>
                      ))}
                    </div>
                  </fieldset>
                ))}
              </section>
            ))}
            {(details.legacyEducation || details.legacyCareerDescription) && (
              <section className="resume-sheet">
                <div className="resume-section-heading">
                  <div>
                    <span>PREVIOUS CONTENT</span>
                    <h2>기존 이력서 내용</h2>
                    <p>이전에 작성한 내용입니다. 새 항목과 함께 저장됩니다.</p>
                  </div>
                </div>
                <Field label="기존 학력">
                  <input
                    maxLength={100}
                    value={details.legacyEducation}
                    onChange={(e) => detail('legacyEducation', e.target.value)}
                  />
                </Field>
                <Field label="기존 경력·프로젝트">
                  <textarea
                    rows={5}
                    maxLength={10000}
                    value={details.legacyCareerDescription}
                    onChange={(e) => detail('legacyCareerDescription', e.target.value)}
                  />
                </Field>
              </section>
            )}
            <section className="resume-sheet" id="resume-skills">
              <div className="resume-section-heading">
                <div>
                  <span>06 · SKILLS & PORTFOLIO</span>
                  <h2>보유 기술·포트폴리오</h2>
                  <p>자신 있게 활용할 수 있는 기술과 작업물을 소개하세요.</p>
                </div>
              </div>
              <Field label="보유 기술">
                <textarea
                  rows={2}
                  maxLength={10000}
                  value={values.skills}
                  onChange={(e) => field('skills', e.target.value)}
                  placeholder="Java, React, Figma처럼 쉼표로 구분해 주세요"
                />
              </Field>
              {skills.length > 0 && (
                <div className="resume-skill-tags">
                  {skills.map((skill, i) => (
                    <span key={i}>{skill}</span>
                  ))}
                </div>
              )}
              <Field label="포트폴리오 파일 첨부">
                <input type="file" accept=".pdf,application/pdf" onChange={async (e) => {
                  const file=e.target.files?.[0];
                  if(!file) return;
                  if(!file.name.toLowerCase().endsWith('.pdf') || file.size>5*1024*1024) {
                    setError('5MB 이하 PDF 파일을 선택해 주세요.');e.target.value='';return;
                  }
                  try {
                    const data=await new Promise((resolve,reject) => { const reader=new FileReader();reader.onload=()=>resolve(String(reader.result).split(',')[1]);reader.onerror=reject;reader.readAsDataURL(file); });
                    field('attachment',{fileName:file.name,data});setAttachmentChanged(true);setError('');
                  } catch { setError('파일을 읽지 못했습니다. 다시 선택해 주세요.'); }
                }} />
              </Field>
              <p className="muted">PDF 1개 · 최대 5MB. 제출한 파일은 지원 당시 버전으로 보관됩니다.</p>
              {values.attachment && <div className="actions"><span>{values.attachment.fileName}</span>{values.attachment.fileId && <a href={'/api/resumes/'+initial.resumeId+'/attachment'}>저장된 파일 다운로드</a>}<button type="button" className="secondary" onClick={() => { field('attachment',null);setAttachmentChanged(true); }}>첨부 제거</button></div>}
            </section>
            <AssessmentQuiz result={initial.assessment} answers={assessmentAnswers} onChange={setAssessmentAnswers} />
            <section className="resume-sheet" id="resume-summary">
              <div className="resume-section-heading">
                <div>
                  <span>07 · ABOUT ME</span>
                  <h2>자기소개</h2>
                  <p>일하는 방식, 강점, 앞으로 이루고 싶은 목표를 들려주세요.</p>
                </div>
              </div>
              <Field label="나를 소개하는 글">
                <textarea
                  rows={8}
                  maxLength={5000}
                  value={details.summary}
                  onChange={(e) => detail('summary', e.target.value)}
                  placeholder="어떤 경험을 해왔고, 어떤 강점을 가지고 있나요? 지원 직무와 연결해 작성해 보세요."
                />
              </Field>
              <span className="resume-counter">
                {details.summary.length.toLocaleString()} / 5,000
              </span>
            </section>
            <section className="resume-sheet resume-save-section">
              <h2>저장 설정</h2>
              <label className="resume-option">
                <input
                  type="checkbox"
                  checked={Boolean(values.isDefault)}
                  onChange={(e) => field('isDefault', e.target.checked)}
                />
                <span>
                  <strong>기본 이력서로 설정</strong>
                  <small>입사 지원 시 쉽게 선택할 수 있습니다.</small>
                </span>
              </label>
              <label className="resume-option">
                <input
                  type="checkbox"
                  checked={Boolean(values.isPublic)}
                  onChange={(e) => field('isPublic', e.target.checked)}
                />
                <span>
                  <strong>공개 여부 설정</strong>
                  <small>
                    공개 여부를 저장합니다. 기업의 전체 이력서 검색은 현재 제공하지 않습니다.
                  </small>
                </span>
              </label>
              <p className="resume-save-note">
                지원할 때 제출한 이력서는 별도로 보관됩니다. 이후 수정해도 기존 제출 내용은
                유지됩니다.
              </p>
              {error && (
                <p role="alert" className="error">
                  {error}
                </p>
              )}
              <div className="resume-save-actions">
                <Link className="button secondary" to="/resumes">
                  목록으로
                </Link>
                <button type="submit" disabled={busy}>
                  {busy ? '저장 중…' : '이력서 저장'}
                </button>
              </div>
            </section>
          </form>
          <aside className="resume-editor-sidebar">
            <div className="resume-progress-card">
              <span className="resume-editor-label">YOUR RESUME</span>
              <h3>
                조금씩 채워가는
                <br />
                나의 커리어
              </h3>
              <div className="resume-progress-label">
                <span>작성 현황</span>
                <strong>{Math.round((completed / 6) * 100)}%</strong>
              </div>
              <progress value={completed} max={6} />
              <p>
                제목 외 항목은 선택 사항입니다.
                <br />
                경험과 강점을 충분히 담아보세요.
              </p>
            </div>
            <nav className="resume-section-nav" aria-label="이력서 항목">
              <a href="#resume-profile">기본 정보</a>
              {sections.map((s) => (
                <a key={s.key} href={`#resume-${s.key}`}>
                  {s.title}
                  {details[s.key].length > 0 && <span>✓</span>}
                </a>
              ))}
              <a href="#resume-skills">보유 기술·포트폴리오</a>
              <a href="#resume-summary">자기소개</a>
            </nav>
            <div className="resume-writing-tip">
              <strong>좋은 이력서 작성 TIP</strong>
              <p>경험을 나열하기보다 나의 역할과 성과를 구체적으로 표현해 보세요.</p>
            </div>
          </aside>
        </div>
      )}
    </div>
  );
}
