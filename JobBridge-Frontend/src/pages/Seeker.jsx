import { notificationTarget } from '../utils/notificationTarget';
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom';
import { useResource } from '../hooks/useResource';
import { useAuth } from '../stores/auth';
import client from '../api/client';
import ResumeEditor from '../components/resume/ResumeEditor';
import ResumeDocument from '../components/resume/ResumeDocument';
import BookmarkButton from '../components/common/BookmarkButton';
import ProfilePhoto from '../components/common/ProfilePhoto';
import WithdrawAccount from '../components/common/WithdrawAccount';
import { ResumeCollection, ApplicationCollection } from '../components/common/CareerLists';
import {
  Action,
  Badge,
  Empty,
  Form,
  JobCard,
  PagedResource,
  Resource,
  Title,
} from '../components/common/UI';

export function Resumes() {
  const location = useLocation();
  const resource = useResource('/resumes');
  return (
    <>
      <Title
        description="나의 경험과 강점이 다음 기회로 이어집니다."
        action={
          <Link className="button" to="/resumes/new">
            ＋ 새 이력서
          </Link>
        }
      >
        이력서 관리
      </Title>
      {location.state?.resumeSaved && (
        <p className="form-success" role="status">
          이력서를 저장했습니다.
        </p>
      )}
      <Resource resource={resource}>{(rows) => <ResumeCollection rows={rows} reload={resource.reload} />}</Resource>
    </>
  );
}

export function ResumeForm() {
  const { id } = useParams();
  const navigate = useNavigate();
  const resource = useResource(id ? `/resumes/${id}` : '/resumes');
  return (
    <>
      <Title description="나의 경험과 강점을 한 장의 이력서로 정리해 보세요.">
        {id ? '이력서 수정' : '새 이력서 작성'}
      </Title>
      <Resource resource={resource}>
        {(data) => (
          <ResumeEditor
            initial={id ? data : { isPublic: false }}
            onSave={async (body) => {
              if (id) await client.put(`/resumes/${id}`, body);
              else await client.post('/resumes', body);
              navigate('/resumes', { state: { resumeSaved: true } });
            }}
          />
        )}
      </Resource>
    </>
  );
}
export function Applications() {
  const resource = useResource('/applications', { refreshInterval: 30000 });
  return (
    <>
      <Title description="제출한 지원서와 채용 진행 상황을 확인하세요.">지원 내역</Title>
      <Resource resource={resource}>{(rows) => <ApplicationCollection rows={rows} />}</Resource>
    </>
  );
}

export function ApplicationDetail() {
  const { id } = useParams();
  const resource = useResource(`/applications/${id}`, {
    refreshInterval: 30000,
  });
  return (
    <Resource resource={resource}>
      {(a) => (
        <>
          <Title description={a.companyName}>{a.title}</Title>
          <div className="panel">
            <Badge value={a.status} />
            <h2>지원 당시 제출 내용</h2>
            <Snapshot data={a.snapshot} />
            {a.snapshot?.resume?.attachment && <a className="button secondary" href={`/api/applications/${id}/attachment`}>제출한 첨부파일 다운로드</a>}
            <h3>자기소개서</h3>
            <p className="prose">{a.coverLetter || '작성한 자기소개서가 없습니다.'}</p>
            <h2>면접 일정</h2>
            <Interviews rows={a.interviews} />
            {['APPLIED', 'UNDER_REVIEW', 'DOCUMENT_PASSED', 'INTERVIEW'].includes(a.status) && (
              <Action
                url={`/applications/${id}/cancel`}
                onDone={resource.reload}
                confirm="지원을 취소할까요? 취소한 지원은 복구할 수 없습니다."
              >
                지원 취소
              </Action>
            )}
          </div>
        </>
      )}
    </Resource>
  );
}

export function Snapshot({ data }) {
  if (!data)
    return (
      <p className="callout">
        이전 시스템에서 등록한 지원입니다. 지원 당시 제출 스냅샷은 보관되어 있지 않습니다.
      </p>
    );
  if (data.resume.details) return <ResumeDocument resume={data.resume} contact={data.contact} />;
  return (
    <div className="snapshot">
      <h3>{data.resume.title}</h3>
      <p>
        {data.contact.name} · {data.contact.email} · {data.contact.phone}
      </p>
      {[
        ['학력', data.resume.education],
        ['경력', data.resume.careerDescription],
        ['기술', data.resume.skills],
      ].map(([k, v]) => (
        <section key={k}>
          <h4>{k}</h4>
          <p className="prose">{v || '미입력'}</p>
        </section>
      ))}
      {data.resume.portfolioUrl && (
        <a href={data.resume.portfolioUrl} target="_blank" rel="noreferrer">
          포트폴리오 ↗
        </a>
      )}
    </div>
  );
}

export function Interviews({ rows, onDelete }) {
  return rows.length ? (
    rows.map((i) => (
      <div className="callout" key={i.interviewId}>
        <Badge value={i.status} />
        <p>
          {new Date(i.interviewAt).toLocaleString('ko-KR', {
            timeZone: 'Asia/Seoul',
          })}{' '}
          (한국 시간)
        </p>
        <p>
          {i.interviewType === 'ONLINE' ? '온라인' : '오프라인'} · {i.location}
        </p>
        {i.meetingUrl && (
          <a href={i.meetingUrl} target="_blank" rel="noreferrer">
            면접 참여 링크 ↗
          </a>
        )}
        {i.result && <p>{i.result}</p>}
        {onDelete && (
          <Action method="delete" url={`/company/interviews/${i.interviewId}`} onDone={onDelete}
            confirm="이 면접 일정을 삭제할까요? 삭제한 일정은 복구할 수 없습니다.">
            일정 삭제
          </Action>
        )}
      </div>
    ))
  ) : (
    <p className="muted">등록된 면접 일정이 없습니다.</p>
  );
}

export function Bookmarks() {
  const resource = useResource('/bookmarks');
  return (
    <>
      <Title description="관심 있는 기회를 한곳에 모아보세요.">관심 공고</Title>
      <PagedResource resource={resource}>
        {(rows) =>
          rows.length ? (
            <div className="job-grid">
              {rows.map((r) => (
                <div key={r.bookmarkId}>
                  {r.job ? (
                    <JobCard job={r.job} onBookmarkChange={resource.reload} />
                  ) : (
                    <div className="panel">
                      비공개되었거나 조회할 수 없는 공고입니다.
                      <BookmarkButton jobId={r.jobId} onChanged={resource.reload} />
                    </div>
                  )}
                </div>
              ))}
            </div>
          ) : (
            <Empty to="/jobs" action="공고 탐색">
              관심 공고가 없습니다.
            </Empty>
          )
        }
      </PagedResource>
    </>
  );
}

export function Employment() {
  const user = useAuth((s) => s.user);
  const resource = useResource('/employment-links');
  return (
    <>
      <Title
        eyebrow="YOUR CAREER PARTNER"
        description="상담부터 기업 매칭까지, 더 나은 내일을 함께 준비합니다."
      >
        취업연계 서비스
      </Title>
      <div className="process">
        {['01 신청 접수', '02 맞춤 상담', '03 기업 매칭', '04 면접 진행', '05 취업 확인'].map(
          (x) => (
            <div key={x}>{x}</div>
          ),
        )}
      </div>
      <div className="panel">
        <h2>당신의 다음 걸음을 돕습니다.</h2>
        <p>신청 후 담당자가 상담을 진행하고, 적합한 기업과 채용 기회를 연결합니다.</p>
        <Action url="/employment-links" onDone={resource.reload}>
          취업연계 신청하기 →
        </Action>
      </div>
      <h2 className="section">나의 진행 내역</h2>
      <PagedResource resource={resource}>
        {(rows) =>
          rows.length ? (
            <div className="stack">
              {rows.map((r) => (
                <div className="panel" key={r.employmentLinkId}>
                  <div className="row">
                    <h3>취업연계({user?.name || '이름 미등록'})</h3>
                    <Badge value={r.status} />
                  </div>
                  <p>신청일 {r.requestedAt?.slice(0, 10)}</p>
                  <p className="prose">{r.publicMessage || '담당자의 안내를 기다려 주세요.'}</p>
                  <Link className="button secondary" to={`/employment/${r.employmentLinkId}`}>진행 상황 자세히 보기 →</Link>
                </div>
              ))}
            </div>
          ) : (
            <Empty>진행 중인 취업연계가 없습니다.</Empty>
          )
        }
      </PagedResource>
    </>
  );
}

export function MyPage() {
  const user = useAuth((s) => s.user);
  const setUser = useAuth((s) => s.setUser);
  const navigate = useNavigate();

  return (
    <>
      <Title>내 정보</Title>
      <div className="panel narrow">
        <ProfilePhoto key={user.userId} user={user} />
        <h2>기본 정보</h2>
        <p>아이디: {user.loginId}</p>
        <p>{user.email}</p>
        <Form
          initial={user}
          fields={[
            { name: 'name', label: '이름', required: true, maxLength: 100 },
            { name: 'phone', label: '휴대폰번호', type: 'tel', required: true },
          ]}
          onSubmit={async (body) => {
            const { data } = await client.patch('/me', body);
            setUser(data);
            window.alert('내 정보를 저장했습니다.');
          }}
        />
        <hr />
        <h2>비밀번호 변경</h2>
        <p className="muted">현재 비밀번호와 최근 6개월 이내 사용한 비밀번호는 다시 사용할 수 없습니다.</p>
        <Form
          key="password-change-form"
          method="patch" // 💡 PATCH 메서드로 전송되도록 명시
          fields={[
            {
              name: 'currentPassword',
              label: '현재 비밀번호',
              type: 'password',
              required: true,
              maxLength: 64,
            },
            {
              name: 'password',
              label: '새 비밀번호 (공백 없이 8~20자)',
              type: 'password',
              required: true,
              minLength: 8,
              maxLength: 20,
              pattern: '\\S{8,20}',
            },
            {
              name: 'passwordConfirm',
              label: '새 비밀번호 확인 (공백 없이 8~20자)',
              type: 'password',
              required: true,
              maxLength: 20,
              validate: (value, values) =>
                value === values.password ? '' : '새 비밀번호가 일치하지 않습니다.',
            },
          ]}
          onSubmit={async (body) => {
            // passwordConfirm을 제외하고 currentPassword와 password만 전달
            await client.patch('/me/password', {
              currentPassword: body.currentPassword,
              password: body.password,
            });
            window.alert('비밀번호가 성공적으로 변경되었습니다.');
            navigate('/');
          }}
          submit="변경하기"
        />
        <WithdrawAccount />
      </div>
    </>
  );
}


export function Notifications() {
  const user = useAuth((s) => s.user);
  const resource = useResource('/notifications', { refreshInterval: 30000 });
  return (
    <>
      <Title>내 알림</Title>
      <PagedResource resource={resource}>
        {(rows) =>
          rows.length ? (
            <div className="stack">
              {rows.map((r) => (
                <article className="panel row" key={r.notificationId}>
                  <NotificationContent notification={r} role={user?.role} onRead={async () => {
                    if (r.isRead) return;
                    try {
                      await client.patch('/notifications/' + r.notificationId + '/read');
                      window.dispatchEvent(new Event('notifications-changed'));
                      await resource.reload();
                    } catch {
                      // Keep navigation available when updating the read status fails.
                    }
                  }} />
                  {r.isRead ? (
                    <Badge value="읽음" />
                  ) : (
                    <Action
                      url={`/notifications/${r.notificationId}/read`}
                      method="patch"
                      onDone={async () => {
                        await resource.reload();
                        window.dispatchEvent(new Event('notifications-changed'));
                      }}
                    >
                      읽음 처리
                    </Action>
                  )}
                </article>
              ))}
            </div>
          ) : (
            <Empty>새로운 알림이 없습니다.</Empty>
          )
        }
      </PagedResource>
    </>
  );
}

function NotificationContent({ notification, role, onRead }) {
  const to = notificationTarget(notification, role);
  const content = <><h3>{notification.title}</h3><p>{notification.content}</p><small>{notification.createdAt?.slice(0, 10)}</small>{to && <span className="notification-open">관련 화면 보기 →</span>}</>;
  return to ? <Link className="notification-content" to={to} onClick={onRead}>{content}</Link> : <div>{content}</div>;
}

export function InquiryWrite({ onSubmitted }) {
  return (
    <>
      <h2>1:1 문의하기</h2>
      <p className="muted">문의 내용은 작성자와 관리자만 확인할 수 있습니다.</p>
      <div className="panel">
        <Form
          fields={[
            { name: 'title', label: '문의 제목', required: true, maxLength: 200 },
            { name: 'content', label: '문의 내용', type: 'textarea', required: true },
          ]}
          onSubmit={async (body) => {
            await client.post('/inquiries', body);
            window.alert('문의가 접수되었습니다. 나의 문의에서 답변을 확인할 수 있습니다.');
            onSubmitted();
          }}
          submit="문의 접수"
        />
      </div>
    </>
  );
}

export function Inquiries() {
  const resource = useResource('/inquiries');
  return (
    <>
      <div className="row inquiry-history-heading"><div><h2>나의 문의</h2><p className="muted">접수한 문의와 관리자의 답변을 확인하세요.</p></div><Link className="button secondary" to="/inquiries?view=inquiry">새 문의 작성</Link></div>
      <PagedResource resource={resource}>
        {(rows) =>
          rows.length ? (
            <div className="stack">
              {rows.map((r) => (
                <article className="panel" key={r.inquiryId}>
                  <div className="row">
                    <h3>{r.title}</h3>
                    <Badge value={r.status} />
                  </div>
                  <p className="prose">{r.content}</p>
                  {r.answer && (
                    <div className="callout">
                      <strong>운영자 답변</strong>
                      <p className="prose">{r.answer}</p>
                    </div>
                  )}
                  {r.status === 'ANSWERED' || r.answer ? <p className="muted">답변을 받은 문의는 삭제할 수 없습니다.</p> : <Action
                    method="delete"
                    url={`/inquiries/${r.inquiryId}`}
                    onDone={resource.reload}
                    confirm="작성한 문의를 삭제할까요?"
                  >
                    삭제
                  </Action>}
                </article>
              ))}
            </div>
          ) : (
            <Empty>작성한 문의가 없습니다.</Empty>
          )
        }
      </PagedResource>
    </>
  );
}
