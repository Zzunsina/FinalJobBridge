import { useEffect, useState } from 'react';
import client from '../../api/client';

function period(row) {
  return [
    row.startDate?.replace('-', '.'),
    row.current ? '재직 중' : row.endDate?.replace('-', '.'),
  ]
    .filter(Boolean)
    .join(' — ');
}
function DocumentSection({ title, children }) {
  return (
    <section className="resume-document-section">
      <h3>{title}</h3>
      {children}
    </section>
  );
}
export default function ResumeDocument({ resume, contact = {}, useProfilePhoto = false }) {
  const [profilePhoto, setProfilePhoto] = useState(null);
  useEffect(() => {
    if (!useProfilePhoto) return;
    let active = true;
    let url;
    client.get('/me/photo', { responseType: 'blob' }).then(({ data, status }) => {
      if (active && status === 200) {
        url = URL.createObjectURL(data);
        setProfilePhoto({ userId: contact.userId, url });
      }
    }).catch(() => {});
    return () => {
      active = false;
      if (url) URL.revokeObjectURL(url);
    };
  }, [useProfilePhoto, contact.userId]);
  const photo = useProfilePhoto
    ? (profilePhoto?.userId === contact.userId ? profilePhoto?.url : null)
    : contact.photo;
  const details = resume.details || {};
  const skills = (resume.skills || '')
    .split(/[,，\n]/)
    .map((s) => s.trim())
    .filter(Boolean);
  return (
    <article className="resume-document">
      <div className="resume-document-top">
        <span>CURRICULUM VITAE</span>
        <span>JOBBRIDGE</span>
      </div>
      <header className="resume-document-header">
        <div>
          <h2>{contact.name || '이력서'}</h2>
          {details.desiredRole && <p className="resume-desired-role">{details.desiredRole}</p>}
          <div className="resume-contact">
            <span>{contact.email}</span>
            <span>{contact.phone}</span>
          </div>
        </div>
        {photo ? (
          <img className="resume-monogram resume-photo" src={photo} alt={(contact.name || '지원자') + '님의 프로필 사진'} />
        ) : (
          <span className="resume-monogram" aria-hidden="true">
            {contact.name?.slice(0, 1) || 'CV'}
          </span>
        )}
      </header>
      <h1 className="resume-document-title">{resume.title || '나의 이력서'}</h1>
      {details.summary && (
        <DocumentSection title="PROFILE · 자기소개">
          <p className="prose">{details.summary}</p>
        </DocumentSection>
      )}
      {((details.educations || []).length > 0 ||
        details.legacyEducation ||
        (!resume.details && resume.education)) && (
        <DocumentSection title="EDUCATION · 학력">
          {(details.educations || []).map((row, index) => (
            <div className="resume-document-entry" key={index}>
              <span className="resume-period">{period(row)}</span>
              <div>
                <h4>{row.school}</h4>
                <p>{[row.major, row.degree, row.status].filter(Boolean).join(' · ')}</p>
              </div>
            </div>
          ))}
          {(details.legacyEducation || (!resume.details && resume.education)) && (
            <p className="prose">{details.legacyEducation || resume.education}</p>
          )}
        </DocumentSection>
      )}
      {((details.experiences || []).length > 0 ||
        details.legacyCareerDescription ||
        (!resume.details && resume.careerDescription)) && (
        <DocumentSection title="EXPERIENCE · 경력">
          {(details.experiences || []).map((row, index) => (
            <div className="resume-document-entry" key={index}>
              <span className="resume-period">{period(row)}</span>
              <div>
                <h4>{row.company}</h4>
                <p className="resume-entry-role">{row.position}</p>
                <p className="prose">{row.description}</p>
              </div>
            </div>
          ))}
          {(details.legacyCareerDescription || (!resume.details && resume.careerDescription)) && (
            <p className="prose">{details.legacyCareerDescription || resume.careerDescription}</p>
          )}
        </DocumentSection>
      )}
      {(details.projects || []).length > 0 && (
        <DocumentSection title="PROJECTS · 프로젝트">
          {details.projects.map((row, index) => (
            <div className="resume-document-entry" key={index}>
              <span className="resume-period">{period(row)}</span>
              <div>
                <h4>{row.name}</h4>
                <p className="resume-entry-role">{row.role}</p>
                <p className="prose">{row.description}</p>
                {row.url && (
                  <a href={row.url} target="_blank" rel="noreferrer">
                    프로젝트 링크 ↗
                  </a>
                )}
              </div>
            </div>
          ))}
        </DocumentSection>
      )}
      {skills.length > 0 && (
        <DocumentSection title="SKILLS · 보유 기술">
          <div className="resume-skill-tags">
            {skills.map((skill, index) => (
              <span key={index}>{skill}</span>
            ))}
          </div>
        </DocumentSection>
      )}
      {(details.certificates || []).length > 0 && (
        <DocumentSection title="CERTIFICATES · 자격·어학">
          {details.certificates.map((row, index) => (
            <div className="resume-document-entry" key={index}>
              <span className="resume-period">{row.date?.replace('-', '.')}</span>
              <div>
                <h4>{row.name}</h4>
                <p>{[row.issuer, row.score].filter(Boolean).join(' · ')}</p>
              </div>
            </div>
          ))}
        </DocumentSection>
      )}
      {resume.portfolioUrl && (
        <DocumentSection title="PORTFOLIO · 포트폴리오">
          <a href={resume.portfolioUrl} target="_blank" rel="noreferrer">
            {resume.portfolioUrl}
          </a>
        </DocumentSection>
      )}
      <p className="resume-document-end">이력서에 기재된 내용은 본인이 작성한 정보입니다.</p>
    </article>
  );
}
