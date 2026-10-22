# 💼 취업연계 플랫폼 프로젝트 개요서

작성일: 2026-10-19  
프로젝트 가칭: 취업연계(Employment)  
문서 목적: 취업연계 서비스 개발에 필요한 기능·데이터·보안·API·화면 디자인·검증 기준 정의

> 이 문서는 `DataBase.txt`, `구조도1.txt`, `구조도2.txt`, `css선택자.txt`를 바탕으로 작성했다. 기존 `ProjectSummary.md`의 문서 구성을 참고했으며 쇼핑몰의 상품·주문·결제 요구 사항은 가져오지 않는다.
>
> **원본 기준**: 12개 테이블, 역할 및 상태 enum, React/JSX·Vite 프런트 구조, Java·Gradle 백엔드 구조, CSS 선택자와 색상 예시.  
> **보완 제안**: 원본에 없는 인증 방식, API 계약, 입력 제한, 상태 전이, 승인 정책, 반응형 수치, DB 보완 및 검증 기준. 아래에서 별도 출처 표기가 없는 구체적인 동작·수치는 구현을 위한 제안 기준이다.  
> 이 문서는 개발 명세이며 실제 프로젝트 구현·DB 생성·외부 서비스 연동을 실행한 결과가 아니다.

---

## 1. 목표와 기본 전제

구직자는 채용공고·기업을 탐색하고 이력서를 작성하여 입사 지원한다. 기업은 채용공고를 등록하고 지원자와 면접 일정을 관리한다. 관리자는 회원·기업 승인·공고 심사·취업연계·고객센터를 운영한다.

- 1차 버전은 학습 및 시연용 MVP로 구현한다.
- 모든 고객 화면과 안내 문구는 한국어로 제공한다.
- 역할은 원본 DB의 `JOB_SEEKER`, `COMPANY`, `ADMIN`을 유지한다.
- 비회원은 공개 채용공고·승인 기업·게시된 공지사항을 조회할 수 있다.
- 구직자의 이력서·지원 내역·면접·상담 내용은 보호 정보로 취급한다.
- 기업 가입만으로 공고 게시나 지원자 조회 권한을 부여하지 않는다. 관리자 승인이 필요하다.
- 결제·AI 자동 매칭·외부 채용 사이트 수집·문자 발송·소셜 로그인은 후속 범위다.
- 알림은 우선 서비스 내부 알림으로 제공한다. 실제 이메일은 비밀번호 재설정에 필요한 범위로 분리한다.
- 작업 루트는 실제 개발 시작 시 정한다. 쇼핑몰 경로인 `C:\computer-shop`을 그대로 사용하지 않는다.

### 1.1 주요 사용자 흐름

```text
구직자: 공고 탐색 → 회원가입·로그인 → 이력서 작성 → 입사 지원
       → 지원 상태 조회 → 면접 일정 확인 → 결과 확인

기업: 기업 회원가입 → 관리자 승인 → 공고 작성 → 심사 요청
     → 관리자 게시 승인 → 지원자 검토 → 면접 등록 → 결과 처리

관리자: 기업 승인 → 공고 심사 → 취업연계 요청 접수
       → 상담·매칭 관리 → 공지·문의 운영
```

## 2. 기술 스택

| 구분 | 기준 | 근거 및 확정 여부 |
|---|---|---|
| 운영 환경 | Windows 기준 실행 안내 | 현재 작업 환경에 맞춘 제안 |
| 프런트엔드 | React, JavaScript/JSX, Vite | 구조도1 원본 |
| 백엔드 | Java, Gradle, Spring Boot 기반 REST API | Java·Gradle은 원본, Spring Boot는 구조에 맞춘 제안 |
| 서버 계층 | Controller → Service → Repository, Entity·DTO 분리 | 구조도2 원본 |
| DB | MySQL, `employment_db`, `utf8mb4_unicode_ci` | DataBase 원본 |
| 데이터 접근 | Spring Data JPA, Bean Validation | 보완 제안 |
| 인증·인가 | Spring Security, JWT HttpOnly 쿠키, CSRF | 보완 제안 |
| HTTP·라우팅·상태 | axios, react-router-dom, zustand | 보완 제안 |
| 스타일 | 공통 CSS 변수 + 공통 컴포넌트 + 화면별 CSS | CSS 예시를 확장한 제안 |

[//]: # (| DB 변경 관리 | Flyway, JPA `ddl-auto=validate` | 보완 제안 |)
| 검증 | 백엔드 통합 테스트, 프런트 build·lint, 브라우저 시나리오 | 보완 제안 |

원본에는 Java·Spring Boot·React·MySQL의 정확한 버전이 없다. 기존 쇼핑몰 문서의 버전을 자동 적용하지 않는다. 구현 시작 시 공식 호환성을 확인하고 JDK·Gradle Wrapper·Node.js·각 패키지 버전을 README와 lockfile에 고정한다.

### 2.1 환경 설정

- DB 비밀번호·JWT 키·SMTP 비밀번호는 환경 변수로 주입한다.
- `.env.example`에는 변수명과 예시만 작성한다. 실제 비밀값을 Git에 넣지 않는다.
- 프런트의 `VITE_*` 값은 브라우저에 공개되므로 비밀키를 넣지 않는다.
- 개발 포트 제안: Vite `5173`, 백엔드 `8080`. Vite `/api`를 백엔드로 proxy한다.
- 운영에서는 HTTPS와 동일 출처 reverse proxy를 기본으로 한다.

## 3. 화면과 사용자 흐름

아래 경로는 원본 컴포넌트에 대응하는 제안이다. 원본에 없는 화면은 ‘추가’로 표시한다.

### 3.1 공통·공개 화면

| 경로 | 컴포넌트 | 주요 기능 |
|---|---|---|
| `/` | Home.jsx | 공고 검색, 최신 공고, 기업 안내, 취업연계 진입 |
| `/login` | Login.jsx | 이메일·비밀번호 로그인, 역할에 맞는 이동 |
| `/signup` | Signup.jsx | 구직자·기업 가입 구분 |
| `/find-password` | FindPassword.jsx | 재설정 요청 및 토큰 기반 비밀번호 변경 |
| `/jobs` | JobList.jsx | 검색·지역·고용형태·경력 필터, 페이지 이동 |
| `/jobs/:id` | JobDetail.jsx | 공고 상세, 관심 등록, 지원 |
| `/companies` | CompanyList.jsx | 승인 기업 검색·목록 |
| `/companies/:id` | CompanyDetail.jsx | 공개 기업 소개와 게시 공고 |
| `/notices`, `/notices/:id` | 추가 | 게시 공지 목록·상세 |

### 3.2 구직자 화면

| 경로 | 컴포넌트 | 주요 기능 |
|---|---|---|
| `/resumes` | ResumeList.jsx | 본인 이력서 목록, 기본 이력서 지정 |
| `/resumes/new`, `/resumes/:id/edit` | ResumeForm.jsx | 작성·수정, 공개 여부 설정 |
| `/applications` | ApplicationList.jsx | 본인 지원 목록·상태 |
| `/applications/:id` | ApplicationDetail.jsx | 제출 내용·진행 상태·면접 일정·지원 취소 |
| `/employment` | EmploymentLink.jsx | 취업연계 신청, 본인 진행 내역 |
| `/mypage` | MyPage.jsx | 계정·구직자 정보, 비밀번호 변경 |
| `/bookmarks` | 추가 | 관심 공고 목록·해제 |

### 3.3 기업 화면

| 경로 | 컴포넌트 | 주요 기능 |
|---|---|---|
| `/company` | CompanyDashboard.jsx | 승인 상태, 공고 수, 지원 현황, 예정 면접 |
| `/company/jobs` | company/JobManage.jsx | 본인 기업 공고 목록·작성·수정·심사 요청·마감 |
| `/company/jobs/new`, `/company/jobs/:id/edit` | 추가 | 공고 입력 폼 |
| `/company/jobs/:id/applicants` | ApplicantManage.jsx | 해당 기업 공고의 지원자 목록·검토 |
| `/company/applications/:id` | 추가 | 제출 이력서·자기소개·상태 변경·면접 관리 |
| `/company/profile` | 추가 | 기업 소개 수정, 승인 상태 확인 |

### 3.4 공통 회원·관리자 화면

| 경로 | 컴포넌트 | 주요 기능 |
|---|---|---|
| `/notifications` | 추가 | 본인 알림 목록·읽음 처리 |
| `/inquiries`, `/inquiries/:id` | 추가 | 본인 문의 등록·조회 |
| `/admin` | AdminDashboard.jsx | 승인·심사·문의·연계 처리 대기 현황 |
| `/admin/members` | MemberManage.jsx | 회원 검색·정지·해제 |
| `/admin/companies` | 추가 | 기업 승인·반려 |
| `/admin/jobs` | admin/JobManage.jsx | 공고 심사·게시·반려·마감 |
| `/admin/employment` | 추가 | 상담·매칭·취업연계 관리 |
| `/admin/notices` | NoticeManage.jsx | 공지 작성·수정·게시 |
| `/admin/inquiries` | 추가 | 문의 조회·답변 |

로그인 복원이 완료된 후 권한별 화면을 표시한다. 로그인 후 이동 경로는 내부 URL만 허용한다. 프런트 Guard와 별개로 모든 보호 API에서 역할·소유권을 확인한다.

## 4. 기능 요구 사항

### 4.1 계정과 회원 상태

- 로그인 식별자는 원본 `users.email`을 사용한다. 별도 아이디 필드는 만들지 않는다.
- 이메일은 앞뒤 공백을 제거하고 서비스 정책상 소문자로 정규화하여 중복을 검사한다. 최대 255자다.
- 비밀번호 제안: 8~64자, 공백·개행 금지, BCrypt 기준 UTF-8 72바이트 이하. 임의 trim은 하지 않는다.
- 원본 `password` 컬럼에는 원문 대신 BCrypt 해시를 저장한다. 컬럼명을 변경한다면 신규 migration에 기록한다.
- 이름은 1~100자, 전화번호는 구분자를 제거한 10~11자리 숫자로 검증한다.
- 공개 가입은 구직자 또는 기업 전용 요청으로 나누고 서버에서 역할을 고정한다. `ADMIN` 가입은 허용하지 않는다.
- 구직자 가입은 `users`와 `job_seekers`, 기업 가입은 `users`와 `companies`를 각각 하나의 트랜잭션으로 생성한다.
- `SUSPENDED`, `WITHDRAWN` 회원은 로그인·재발급·보호 기능을 사용할 수 없다.
- 회원 상태 변경·비밀번호 변경 시 기존 로그인 세션을 취소한다.
- 비밀번호 찾기는 계정 존재 여부를 드러내지 않는 응답을 사용한다. 일회용 토큰은 해시로 저장하고 만료·사용 여부를 검증한다.
- 실제 SMTP 미설정 환경에서는 재설정 메일 발송이 완료됐다고 표시하지 않는다.
- 회원 탈퇴는 상태 변경을 기본으로 한다. 개인정보 보관·익명화 정책은 운영 전 별도로 확정한다.

### 4.2 구직자 정보와 기업 승인

- 생년월일은 미래일 수 없고 경력 연수는 0~80 정수다. 선택 정보는 필수로 강제하지 않는다.
- 기업명은 1~200자, 소개는 최대 10,000자, 홈페이지는 HTTP/HTTPS URL만 허용한다.
- 사업자번호는 입력 시 숫자 10자리로 정규화하고 중복을 방지한다. 형식 검증과 실제 사업자 진위 확인은 구분한다.
- 가입 직후 기업 승인 상태는 `PENDING`이다. 관리자가 `APPROVED` 또는 `REJECTED`로 처리한다.
- 반려 기업은 정보를 보완하여 재심사를 요청할 수 있다.
- 기업 미승인 상태에서는 프로필과 공고 초안만 관리할 수 있다. 게시·지원자 열람은 금지한다.
- 승인된 기업의 사업자번호 변경은 재심사를 요구한다. 승인 취소 시 공개 공고와 지원자 접근도 서버에서 제한한다.
- 공개 기업 응답에 계정 이메일·개인 전화번호·미승인 정보는 포함하지 않는다.

### 4.3 채용공고

- 필수값: 제목, 고용형태, 경력 구분, 근무지역, 상세 설명, 모집 인원, 접수 시작일·마감일.
- 제목 1~200자, 고용형태·경력 구분 50자 이하, 학력·급여 100자 이하, 근무지역 255자 이하.
- 상세 설명·자격요건·우대사항은 각 최대 10,000자, 모집 인원은 1~10,000명으로 검증한다.
- `salary`는 원본의 문자열 구조를 유지한다. 숫자 금액으로 간주한 급여순 정렬은 MVP에서 제외한다.
- 설명은 일반 텍스트로 저장·표시한다. 임의 HTML을 실행하지 않는다.
- 공고 상태는 원본의 `DRAFT`, `PENDING`, `PUBLISHED`, `CLOSED`, `REJECTED`를 유지한다.
- 공개 목록·상세에는 승인된 활성 기업의 `PUBLISHED` 공고만 노출한다. 마감된 공고는 공개 상세에서 ‘마감’으로 안내할 수 있으나 지원은 거절한다.
- 날짜는 Asia/Seoul 기준이다. 접수 시작일 00:00부터 마감일 다음 날 00:00 직전까지 지원할 수 있다.
- 시작일은 마감일보다 늦을 수 없다. 서버가 매 요청 시 접수 기간을 확인한다.
- 기본 정렬은 최신순, 선택 정렬은 마감 임박순이다. 동률은 ID로 정렬한다.
- 목록 기본 12개, 최대 100개. 음수 페이지·허용하지 않은 정렬값은 400으로 처리한다.
- 직종 별도 컬럼이 없으므로 MVP의 직종 검색은 제목·설명 키워드 검색으로 한정한다. 정규화된 직종 필터는 후속 스키마 확장이 필요하다.

```text
DRAFT → PENDING                  기업의 심사 요청
PENDING → PUBLISHED | REJECTED    관리자 심사
REJECTED → DRAFT                 기업의 수정 시작
PUBLISHED → CLOSED              기업·관리자의 마감 또는 기간 만료
```

게시 중인 공고의 내용을 변경하려면 `DRAFT`로 되돌려 공개 노출을 중단한 뒤 재심사한다. 기존 지원 기록은 유지한다. `CLOSED`는 재개하지 않고 새 공고를 작성한다.

### 4.4 이력서와 관심 공고

- 구직자는 본인 이력서만 생성·조회·수정·삭제할 수 있다.
- 제목은 1~200자, 학력은 100자 이하, 경력·기술 내용은 각각 최대 10,000자다.
- 포트폴리오 URL은 500자 이하의 HTTP/HTTPS URL로 제한한다. 서버에서 임의 URL을 대신 접속하지 않는다.
- 파일 첨부는 원본에 없으므로 MVP에서 제외한다.
- 기본 이력서는 구직자별 최대 하나다. 구직자 행을 잠근 트랜잭션 안에서 기존 기본값 해제와 새 지정을 처리한다.
- `is_public=false`를 기본으로 한다. MVP에서는 공개 여부를 저장하되 기업의 전체 이력서 검색은 제공하지 않는다.
- 지원에 사용된 이력서는 삭제를 거절하고 사용 이유를 안내한다. 수정은 가능하되 제출된 지원 내용에는 영향을 주지 않는다.
- 관심 공고는 `(job_seeker_id, job_id)` 유일 제약을 유지한다. 중복 등록은 기존 결과를 반환한다.
- 마감·비공개 공고가 관심 목록에 남으면 상태 안내만 표시하고 비공개 상세 내용은 노출하지 않는다.

### 4.5 입사 지원과 지원자 관리

- 지원은 활성 구직자만 할 수 있다. 본인 소유 이력서를 선택해야 한다.
- 접수 기간, 공고 상태, 기업 승인·회원 상태를 서버에서 검증한다.
- 자기소개서는 최대 10,000자다.
- `(job_id, job_seeker_id)` 유일 제약으로 공고당 한 번만 지원한다. 취소 후 재지원은 MVP에서 허용하지 않는다.
- 지원 생성 시 이력서·구직자 제출 정보와 공고 제목을 스냅샷으로 저장한다. 원본 DB에는 없으므로 6.2의 보완이 필요하다.
- 지원 생성·스냅샷 저장·기업 알림 생성은 하나의 트랜잭션으로 처리한다.
- 지원자는 자신의 기록만, 기업은 자신이 소유한 공고의 지원 기록만 조회한다.
- 기업에 제공하는 개인정보는 제출 스냅샷으로 제한한다. 구직자의 다른 이력서나 이후 수정 정보에 접근할 수 없다.
- 기업의 상태 변경과 지원자의 취소가 겹치면 지원 행 잠금으로 현재 상태를 확인한다.

```text
APPLIED → UNDER_REVIEW | REJECTED
UNDER_REVIEW → DOCUMENT_PASSED | REJECTED
DOCUMENT_PASSED → INTERVIEW | REJECTED
INTERVIEW → FINAL_PASSED | REJECTED

APPLIED | UNDER_REVIEW | DOCUMENT_PASSED | INTERVIEW → CANCELED
```

`FINAL_PASSED`, `REJECTED`, `CANCELED`는 종료 상태다. 기업은 취소된 지원을 변경할 수 없다. 구직자 취소 시 예정 면접을 함께 취소하고 기업에 내부 알림을 생성한다. 최종 합격은 실제 입사 완료와 구분한다.

### 4.6 면접 일정

- 한 지원에 여러 차수의 면접을 등록할 수 있다.
- 해당 기업의 `DOCUMENT_PASSED` 또는 `INTERVIEW` 지원에만 미래 면접을 등록한다. 최초 등록 시 지원 상태를 `INTERVIEW`로 변경한다.
- `ONLINE`은 회의 URL, `OFFLINE`은 장소를 필수로 받는다. URL은 HTTP/HTTPS만 허용한다.
- 상태는 `SCHEDULED → COMPLETED | CANCELED`로만 전이한다.
- 면접 완료가 자동으로 최종 합격을 의미하지 않는다. 지원 상태는 별도로 처리한다.
- 구직자는 자신의 일정과 공개 안내만 조회한다. 기업 내부 평가·메모는 응답에서 제외한다.
- 일정 생성·변경·취소 시 구직자 내부 알림을 생성한다. 중복 요청에 의한 동일 일정 중복 등록을 방지한다.

### 4.7 취업연계

- 구직자는 취업연계를 신청하고 본인 진행 상태를 조회한다.
- 활성 연계 건이 있으면 추가 신청은 409로 거절한다. 구직자 행 잠금으로 동시 중복 신청을 방지한다.
- 관리자는 담당자, 연계 기업·공고, 상태와 내부 메모를 관리한다.
- 담당자는 활성 `ADMIN`만 지정할 수 있다. 공고 지정 시 해당 공고의 기업과 `company_id`가 일치해야 한다.
- 상담·매칭 없이 취업 성과를 자동 확정하지 않는다. 관리자가 확인한 경우에만 `EMPLOYED`로 처리한다.
- 구직자 응답에는 내부 메모를 제외한다. 공개 안내를 제공하려면 별도 컬럼을 추가한다.

```text
REQUESTED → COUNSELING → MATCHING → INTERVIEW → EMPLOYED
INTERVIEW → MATCHING             재매칭
REQUESTED | COUNSELING | MATCHING | INTERVIEW → CLOSED
```

`EMPLOYED`, `CLOSED` 진입 시 `completed_at`을 기록하고 종료 후 직접 재개하지 않는다.

### 4.8 공지사항·문의·알림

- 공지는 관리자만 작성·수정·게시한다. `is_published=true`인 공지만 공개한다.
- 공지·문의 제목은 1~200자, 본문·답변은 최대 10,000자, 일반 텍스트로 처리한다.
- 문의는 작성자와 관리자만 조회한다. 기업은 다른 기업·구직자의 문의를 볼 수 없다.
- 답변 시 `ANSWERED`, `answered_by`, `answered_at`을 함께 저장한다. 답변 수정도 관리자만 할 수 있다.
- 알림은 서버가 업무 이벤트로 생성하며 회원은 본인 알림만 조회·읽음 처리한다.
- 알림 제목은 200자, 내용은 1,000자 이하다. 개인정보·내부 심사 의견을 알림 문구에 포함하지 않는다.
- 알림의 `related_id`만으로 접근을 허용하지 않는다. 연결된 상세 API에서 다시 소유권을 검증한다.

## 5. 인증·보안 설계

이 절은 원본에 없는 보완 제안이며, 기존 쇼핑몰 문서의 보안 원칙을 취업 서비스 역할에 맞게 적용한다.

### 5.1 로그인과 세션

- access JWT 30분, refresh JWT 최종 만료 7일을 제안한다.
- JWT claim: `sub` 회원 ID, `sid` 세션 ID, `jti`, `type`, `iat`, `exp`.
- JWT는 HttpOnly 쿠키에만 저장한다. JSON·localStorage·sessionStorage·zustand에 저장하지 않는다.
- 세션 테이블에 access·refresh의 SHA-256 해시, 만료 시각과 취소 여부를 저장한다.
- access 검증 시 서명·용도·만료·세션 취소·해시·회원 상태와 현재 DB 역할을 확인한다.
- refresh는 세션 잠금 아래 두 토큰을 교체한다. 최초 refresh 최종 만료 시각을 늘리지 않는다.
- 로그아웃은 세션 취소와 쿠키 삭제를 처리한다. 비밀번호 변경·회원 정지는 해당 회원의 모든 세션을 취소한다.
- 로그인 실패 메시지는 계정 존재 여부를 구분하지 않는다. 로그인·재설정 요청에 횟수 제한을 적용한다.

### 5.2 쿠키·CSRF·권한

| 쿠키 | HttpOnly | Path | SameSite | Secure |
|---|---|---|---|---|
| accessToken | true | `/api` | Lax | 개발 HTTP false / 운영 HTTPS true |
| refreshToken | true | `/api/auth` | Lax | 개발 HTTP false / 운영 HTTPS true |
| XSRF-TOKEN | false | `/` | Lax | 환경에 맞게 설정 |

- `GET /api/auth/csrf`로 CSRF 토큰을 받고 변경 요청의 `X-XSRF-TOKEN` 헤더로 전송한다.
- 가입·로그인·refresh·로그아웃을 포함한 POST/PUT/PATCH/DELETE에 CSRF를 적용한다.
- CORS는 명시한 출처만 허용한다. credentials와 `*` 출처를 함께 사용하지 않는다.
- 서버는 `STATELESS`로 구성하고 기본 formLogin·httpBasic을 비활성화한다.
- `ROLE_JOB_SEEKER`, `ROLE_COMPANY`, `ROLE_ADMIN`을 역할별로 검사한다.
- 정의하지 않은 API는 기본 거절한다. 공개 API·정적 리소스는 필요한 경로만 허용한다.
- 기업 승인 검사와 공고·지원자 소유권 검사는 역할 검사에 추가한다.
- 다른 회원의 비공개 리소스는 404, 역할 부족·CSRF 실패는 403, 비로그인은 401로 처리한다.
- DTO로 응답을 제한하고 비밀번호 해시·토큰·내부 메모를 노출하지 않는다.
- 개인정보·자기소개·이력서 본문·메일 재설정 토큰은 로그에 남기지 않는다.

### 5.3 프런트 인증 흐름

```text
앱 시작 → CSRF 조회 → refresh 시도
→ 성공: user 저장 / 401: 비로그인 / 네트워크 실패: 재시도 안내
→ 초기화 완료 후 라우트와 역할별 Guard 표시
```

- 인증 상태는 `user`, `initialized`를 기본으로 한다.
- 동시에 발생한 refresh는 하나의 Promise를 공유한다.
- 인증 만료로 해결 가능한 보호 API의 401만 refresh 후 한 번 재시도한다.
- 인증 API 자체, 400·403·404·409는 자동 refresh 대상에서 제외한다.
- 지원·면접 등록 같은 생성 요청은 재시도에도 동일한 요청 키를 유지한다.

## 6. 데이터 모델

### 6.1 원본 12개 테이블

DB명은 `employment_db`다. PK 이름과 enum은 `DataBase.txt`를 기준으로 유지한다.

| 테이블 | 주요 컬럼·관계 | 핵심 제약 |
|---|---|---|
| users | user_id, email, password, name, phone, role, status, created_at, updated_at | email UNIQUE |
| job_seekers | job_seeker_id, user_id, birth_date, address, desired_job, desired_region, experience_years, introduction | user_id UNIQUE FK |
| companies | company_id, user_id, company_name, business_number, ceo_name, industry, company_size, address, website, description, approval_status | user_id UNIQUE FK, business_number UNIQUE |
| job_postings | job_id, company_id, title, job_type, career_type, education, salary, work_location, description, requirements, preferred_qualifications, headcount, start_date, end_date, status | company_id FK |
| resumes | resume_id, job_seeker_id, title, education, career_description, skills, portfolio_url, is_public, is_default | job_seeker_id FK |
| job_bookmarks | bookmark_id, job_seeker_id, job_id, created_at | UNIQUE(job_seeker_id, job_id) |
| applications | application_id, job_id, job_seeker_id, resume_id, cover_letter, status, applied_at, updated_at | UNIQUE(job_id, job_seeker_id), 각 FK |
| interviews | interview_id, application_id, interview_at, interview_type, location, meeting_url, status, result, memo | application_id FK |
| employment_links | employment_link_id, job_seeker_id, company_id, job_id, manager_user_id, status, memo, requested_at, completed_at | 각 FK, 기업·공고·담당자 nullable |
| notices | notice_id, title, content, author_user_id, is_published, created_at | author_user_id FK |
| inquiries | inquiry_id, user_id, title, content, answer, status, answered_by, created_at, answered_at | 작성자·답변자 FK |
| notifications | notification_id, user_id, title, content, notification_type, related_id, is_read, created_at | user_id FK |

```text
users 1 ─ 0..1 job_seekers      users 1 ─ 0..1 companies
companies 1 ─ N job_postings
job_seekers 1 ─ N resumes / applications / job_bookmarks / employment_links
job_postings 1 ─ N applications / job_bookmarks
resumes 1 ─ N applications
applications 1 ─ N interviews
users 1 ─ N notices / inquiries / notifications
```

역할과 상세 테이블의 일치는 FK만으로 보장되지 않는다. 가입 서비스에서 역할별 상세정보를 생성하고 다른 역할의 상세정보 생성을 차단한다.

### 6.2 구현에 필요한 보완 제안

다음 항목은 원본 SQL에 포함되어 있지 않다. 채택 시 새 migration으로 명시적으로 추가한다.

| 보완 | 데이터·처리 기준 | 목적 |
|---|---|---|
| auth_sessions 테이블 | UUID PK, user_id FK, access_hash, refresh_hash, access_expires_at, refresh_expires_at, revoked, created_at | JWT 세션 취소·회전 |
| password_reset_tokens 테이블 | id, user_id FK, token_hash UNIQUE, expires_at, used_at, created_at | 일회용 재설정 |
| application_snapshots 테이블 | application_id UNIQUE FK, snapshot_json, schema_version, created_at | 제출 시점의 이력서·연락처·공고 제목 보존 |
| 심사 정보 | companies·job_postings에 review_reason, reviewed_by FK, reviewed_at | 승인·반려 근거 표시 |
| 공개 연계 안내 | employment_links에 public_message | 내부 memo와 구직자 안내 분리 |
| 중복 생성 방지 | interviews에 request_key, request_hash 및 UNIQUE(application_id, request_key) | 네트워크 재시도 시 중복 면접 방지 |
| 변경 시각 | 수정 가능한 테이블의 누락된 updated_at 추가 | 수정 추적 |

스냅샷 JSON에는 제출에 필요한 필드만 넣고 비밀번호·토큰·내부 메모를 포함하지 않는다. 스냅샷 버전을 기록하고 응답 DTO에서 명시적으로 읽는다.

### 6.3 무결성·인덱스·시간

- 원본 FK와 UNIQUE 제약을 유지한다. 참조 중인 회원·기업·공고·이력서를 임의 물리 삭제하지 않는다.
- `headcount`, 경력 연수, 날짜 범위는 서버 검증과 가능한 DB 제약으로 보호한다.
- 공고 마감과 지원 생성은 공고 행 잠금 아래 검증한다. 관리 작업도 같은 잠금 정책을 따른다.
- 기본 이력서 지정·연계 중복 신청은 구직자 행을 잠가 직렬화한다.
- 원본 인덱스에 더해 applications(job_seeker_id, applied_at), applications(job_id, status), employment_links(status, requested_at), inquiries(status, created_at)를 제안한다.
- `created_at`, `updated_at`, `interview_at` 등 시각은 UTC로 저장하고 Asia/Seoul로 표시한다. 공고 시작·마감은 날짜 의미를 유지한다.
- 실제 데이터는 초기화하지 않는다. 

[//]: # (- Flyway로 스키마를 관리하고 dev 데이터는 없을 때만 생성한다.)

## 7. API 계약

기본 경로는 `/api`, JSON은 camelCase, ID는 숫자, enum은 원본 문자열을 사용한다. 로그인 회원 ID·기업 ID·구직자 ID는 인증 principal과 서버 조회로 결정한다.

### 7.1 인증·공개·공통 회원

| 메서드 | 경로 | 권한 | 기능 |
|---|---|---|---|
| GET | `/auth/csrf` | 공개 | CSRF 조회 |
| POST | `/auth/signup/job-seeker` | 공개 | 구직자 가입, 201 |
| POST | `/auth/signup/company` | 공개 | 기업 가입, 201 |
| POST | `/auth/login` | 공개 | 로그인 |
| POST | `/auth/refresh` | 공개 | 쿠키 기반 재발급 |
| POST | `/auth/logout` | 공개 | 세션 취소, 204 |
| POST | `/auth/password-reset/request` | 공개 | 재설정 요청 |
| POST | `/auth/password-reset/confirm` | 공개 | 토큰 검증·변경 |
| GET/PATCH | `/me` | 활성 회원 | 본인 정보 조회·수정 |
| PATCH | `/me/password` | 활성 회원 | 현재 비밀번호 검증·변경 |
| GET | `/jobs` | 공개 | 공고 검색·필터·페이지 |
| GET | `/jobs/{id}` | 공개 | 공개 공고 상세 |
| GET | `/companies`, `/companies/{id}` | 공개 | 승인 기업 목록·상세 |
| GET | `/notices`, `/notices/{id}` | 공개 | 게시 공지 목록·상세 |
| GET | `/notifications` | 활성 회원 | 본인 알림 목록 |
| PATCH | `/notifications/{id}/read` | 소유자 | 읽음 처리 |
| GET/POST | `/inquiries` | 활성 회원 | 본인 문의 목록·등록 |
| GET | `/inquiries/{id}` | 소유자 | 본인 문의 상세 |

### 7.2 구직자

| 메서드 | 경로 | 기능 |
|---|---|---|
| GET/PATCH | `/job-seekers/me` | 구직자 상세정보 |
| GET/POST | `/resumes` | 본인 이력서 목록·생성 |
| GET/PUT/DELETE | `/resumes/{id}` | 본인 이력서 조회·수정·삭제 |
| PUT | `/resumes/{id}/default` | 기본 이력서 지정 |
| GET | `/bookmarks` | 관심 공고 목록 |
| PUT/DELETE | `/bookmarks/{jobId}` | 관심 등록·해제 |
| POST | `/jobs/{id}/applications` | 이력서 선택·지원, 201 |
| GET | `/applications`, `/applications/{id}` | 본인 지원 목록·상세 |
| POST | `/applications/{id}/cancel` | 지원 취소 |
| GET/POST | `/employment-links` | 본인 연계 목록·신청 |
| GET | `/employment-links/{id}` | 본인 연계 상세 |

위 API는 `JOB_SEEKER` 역할과 소유권을 함께 검사한다. 삭제 참조 충돌·중복 지원·마감 충돌은 409로 반환한다.

### 7.3 기업

| 메서드 | 경로 | 기능 |
|---|---|---|
| GET/PUT | `/company/profile` | 본인 기업 정보 |
| POST | `/company/profile/review-request` | 반려 후 재심사 요청 |
| GET | `/company/dashboard` | 본인 기업 집계 |
| GET/POST | `/company/jobs` | 공고 목록·초안 생성 |
| GET/PUT | `/company/jobs/{id}` | 본인 공고 조회·수정 |
| POST | `/company/jobs/{id}/review-request` | 공고 심사 요청 |
| POST | `/company/jobs/{id}/close` | 공고 마감 |
| GET | `/company/jobs/{id}/applications` | 공고별 지원자 |
| GET | `/company/applications/{id}` | 제출 스냅샷·지원 상세 |
| PATCH | `/company/applications/{id}/status` | 지원 상태 변경 |
| POST | `/company/applications/{id}/interviews` | 면접 등록, 201 |
| PUT | `/company/interviews/{id}` | 예정 면접 일시·안내 수정 |
| PATCH | `/company/interviews/{id}/status` | 면접 완료·취소 |

기업 API는 `COMPANY`와 소유권을 검사한다. 프로필·초안 관리 외 업무는 승인된 기업에 한정한다.

### 7.4 관리자

| 메서드 | 경로 | 기능 |
|---|---|---|
| GET | `/admin/dashboard` | 처리 대기 요약 |
| GET | `/admin/users` | 회원 검색 |
| PATCH | `/admin/users/{id}/status` | 회원 정지·해제 |
| GET | `/admin/companies`, `/admin/companies/{id}` | 기업 심사 목록·상세 |
| PATCH | `/admin/companies/{id}/approval` | 승인·반려 |
| GET | `/admin/jobs`, `/admin/jobs/{id}` | 공고 심사 목록·상세 |
| PATCH | `/admin/jobs/{id}/status` | 공고 승인·반려·마감 |
| GET | `/admin/employment-links`, `/admin/employment-links/{id}` | 연계 목록·상세 |
| PATCH | `/admin/employment-links/{id}` | 담당자·매칭·상태·메모 변경 |
| GET/POST | `/admin/notices` | 전체 공지 목록·작성 |
| GET/PUT | `/admin/notices/{id}` | 공지 조회·수정·게시 여부 변경 |
| GET | `/admin/inquiries`, `/admin/inquiries/{id}` | 문의 목록·상세 |
| PUT | `/admin/inquiries/{id}/answer` | 문의 답변 |

관리자 API는 `ADMIN` 전용이다. 공개 요청에서 관리자 역할 부여·변경은 제공하지 않는다.

### 7.5 응답 형식과 오류

JPA Entity·Page를 그대로 응답하지 않고 고정 DTO를 사용한다.

```json
{
  "content": [],
  "page": 0,
  "size": 12,
  "totalElements": 0,
  "totalPages": 0,
  "first": true,
  "last": true
}
```

```json
{
  "code": "JOB_CLOSED",
  "message": "접수가 마감된 공고입니다.",
  "fieldErrors": [],
  "traceId": "요청 추적 ID"
}
```

400 입력 오류, 401 비로그인, 403 권한·CSRF, 404 없음·소유권 위반, 409 상태·중복 충돌, 429 요청 제한, 500 서버 오류를 구분한다. SQL·스택·개인정보는 오류 응답에 포함하지 않는다.

면접 등록에는 `Idempotency-Key`를 사용한다. 동일 지원·키·본문은 기존 결과를 반환하고 같은 키의 다른 본문은 409로 처리한다. 지원 생성은 원본 복합 유일 제약으로 중복을 방지하며 중복 요청은 409와 기존 지원 확인 안내를 반환한다.

## 8. UI 디자인과 스타일

### 8.1 시각 방향과 원본 CSS 반영

짙은 파란 헤더, 흰색 콘텐츠, 파란 지원 버튼을 사용한다. 공고 제목·기업명·근무조건·마감일·지원 행동이 먼저 보이도록 구성한다.

| 원본 선택자 | 적용 기준 |
|---|---|
| `*` | `box-sizing: border-box` |
| `#header` | 공통 헤더, 배경 `#1E3A8A`, 기본 padding 20px |
| `.logo` | 흰색 로고 |
| `.menu a` | 흰색 메뉴, 밑줄 없음, PC 간격 20px |
| `.menu a:hover` | `#FACC15`, 키보드 focus도 별도 표시 |
| `.job-list` | 공고 목록 컨테이너 |
| `.job-card` | padding 20px, 테두리 1px `#DDDDDD`, radius 12px |
| `.job-card:hover` | 선 `#2563EB`, 그림자 `0 4px 12px #0001` |
| `.company` | `#2563EB`, 굵은 기업명 |
| `.apply-btn` | 배경 `#2563EB`, 흰 글자, radius 6px, padding 10px 20px |
| `.apply-btn:hover` | 배경 `#1D4ED8` |
| `.job-card:nth-child(2)` | 원본의 선택자 학습 예시. 두 번째 카드만 다르게 보이는 표현은 실제 화면의 상태 의미로 사용하지 않는다. |

원본 HTML은 화면 예시로 취급한다. 실제 JSX에서는 `className`을 사용하고 탐색은 링크, 제출·상태 변경은 버튼으로 구현한다. 공통 로고는 링크로 두고 각 페이지 제목에 h1을 하나 사용한다.

### 8.2 공통 토큰

```css
:root {
  --color-header: #1e3a8a;
  --color-primary: #2563eb;
  --color-primary-hover: #1d4ed8;
  --color-menu-hover: #facc15;
  --color-bg: #f8fafc;
  --color-surface: #ffffff;
  --color-text: #0f172a;
  --color-muted: #475569;
  --color-border: #dddddd;
  --color-input-border: #cbd5e1;
  --color-danger: #b91c1c;
  --color-success: #166534;
  --color-warning: #92400e;
  --radius-button: 6px;
  --radius-input: 8px;
  --radius-card: 12px;
  --shadow-card-hover: 0 4px 12px #0001;
  --space-1: 4px;
  --space-2: 8px;
  --space-3: 12px;
  --space-4: 16px;
  --space-5: 20px;
  --space-6: 24px;
  --space-8: 32px;
  --space-12: 48px;
}
```

폰트는 `"Pretendard", "Noto Sans KR", system-ui, sans-serif`를 제안한다. 폰트를 포함하면 라이선스를 확인하고 시스템 폰트 대체를 제공한다.

### 8.3 제목·본문·폼·버튼

| 요소 | PC / 모바일 | 기준 |
|---|---|---|
| 홈 큰 제목 | 40px / 28px | 800, 줄 높이 1.25 |
| 페이지 h1 | 32px / 26px | 700, 아래 24px |
| 섹션 h2 | 24px / 21px | 700, 아래 16px |
| 카드 h3 | 18px / 18px | 600, 줄 높이 1.5 |
| 본문 | 16px | 줄 높이 1.6 |
| 보조·라벨 | 14px | 라벨 600, 입력 위 8px |
| 입력·select | 높이 44px | 선 1px, radius 8px, padding 12px |
| textarea | 최소 140px | 라벨 유지, 세로 크기 조절 허용 |
| 기본 버튼 | 최소 높이 44px | 15px/600, 좌우 20px |
| 주요 지원 버튼 | 높이 48px | 모바일 전체 폭 |
| 확인 모달 | 최대 폭 440px | padding 24px, radius 12px |

- Primary는 지원·저장·심사 요청, Secondary는 관심 등록·보조 이동, Neutral은 닫기·이전, Danger는 취소·정지에 사용한다.
- hover 외에 `focus-visible` 3px 외곽선과 2px offset을 제공한다.
- disabled에는 마감·미승인·처리 중 등의 이유를 텍스트로 표시한다.
- 처리 중에는 버튼 폭을 유지하고 중복 제출을 차단한다.
- 오류는 필드 아래 문구와 `aria-describedby`, 중요 오류는 `role=alert`, 성공은 `role=status`를 사용한다.
- 모달은 포커스를 내부에 유지하고 닫으면 원래 버튼으로 복원한다.
- 지원 취소·회원 정지·공고 마감은 대상을 명시한 확인 모달을 사용한다.

### 8.4 레이아웃과 반응형

- 콘텐츠 최대 폭 1,200px, PC 좌우 32px, 모바일 16px.
- 헤더 최소 높이 PC 72px, 모바일 60px. 좁은 화면에서는 메뉴를 접고 검색을 별도 행으로 표시한다.
- 1,024px 이상: 공고 카드 3열, 상세 본문·요약 2열, 관리 사이드바 240px.
- 640~1,023px: 카드 2열, 관리 메뉴 축소.
- 640px 미만: 카드 1열, 폼 1열, 사이드바 접기.
- 관리 표는 모바일 카드 또는 이름이 있는 가로 스크롤 영역을 사용한다.
- 360px에서도 페이지 가로 넘침을 방지한다. 긴 기업명·URL은 줄바꿈한다.
- 로딩·빈 상태·오류·마감·미승인·접근 제한 화면을 각각 준비한다.

### 8.5 화면별 스토리보드

요소 ID는 `SB-화면번호-요소번호` 형식이다. 아래 수치는 원본에 없는 상세 디자인 제안이다.

#### SB-01 홈 `/`

```text
[로고] [채용정보] [기업정보] [고객센터] [로그인/회원 메뉴]
[h1 취업을 향한 다음 기회]
[키워드 입력] [지역] [검색]
[h2 최신 채용공고] [공고 카드 × 6]
[기업 서비스 안내] [취업연계 신청 안내]
[Footer]
```

| ID | 요소 | 규격·행동 |
|---|---|---|
| SB-01-01 | 검색 영역 | padding 32px / 모바일 24px, 검색 높이 48px, 검색 시 `/jobs` 이동 |
| SB-01-02 | 최신 공고 | PC 3열·모바일 1열, gap 20px, 제목·기업·지역·마감일 표시 |
| SB-01-03 | 연계 CTA | 안내 카드 padding 24px, 버튼 48px, 비로그인은 로그인 후 이동 |

#### SB-02 공고 목록 `/jobs`

| ID | 요소 | 규격·행동 |
|---|---|---|
| SB-02-01 | 검색·필터 | 입력 flex, select 160px, gap 12px, 모바일 세로 배치 |
| SB-02-02 | 공고 카드 | `.job-card`, 제목 최대 두 줄, 조건·마감은 14px, 상세 링크 분리 |
| SB-02-03 | 정렬·건수 | 목록 위 16px, 검색 조건 변경 시 첫 페이지로 |
| SB-02-04 | 페이지 이동 | 버튼 최소 44×44px, 간격 8px, 위 32px |

#### SB-03 공고 상세 `/jobs/:id`

```text
[채용정보 > 기업명 > 공고명]
[h1 공고명 / 기업명 / 근무조건]      [마감일]
[h2 직무 설명]                      [이력서 선택]
[자격요건 / 우대사항]                [지원하기]
[근무지역 / 기업 소개]               [관심 등록]
```

| ID | 요소 | 규격·행동 |
|---|---|---|
| SB-03-01 | 상세 배치 | 본문 flex + 요약 320px, gap 24px, 모바일 한 열 |
| SB-03-02 | 조건 요약 | 카드 padding 24px, 항목 간 12px, 마감일 날짜·상태 동시 표시 |
| SB-03-03 | 지원 | 48px Primary, 이력서·자기소개 확인 모달 후 제출, 성공 시 지원 상세 이동 |
| SB-03-04 | 예외 상태 | 이력서 없음은 작성 링크, 기지원은 내역 링크, 마감은 지원 불가 안내 |

#### SB-04 로그인·가입·재설정

| ID | 요소 | 규격·행동 |
|---|---|---|
| SB-04-01 | 인증 폼 | 최대 480px, 가운데, padding 32px / 모바일 24px |
| SB-04-02 | 가입 역할 | 구직자·기업 버튼, 선택 상태 텍스트·aria 표시, 관리자 선택 없음 |
| SB-04-03 | 필드 | 그룹 간 20px, 비밀번호 보기 터치 영역 44px |
| SB-04-04 | 제출·오류 | 제출 100%×48px, 오류 배경 `#FEF2F2`, 처리 중 중복 방지 |

#### SB-05 기업 목록·상세

| ID | 요소 | 규격·행동 |
|---|---|---|
| SB-05-01 | 기업 카드 | padding 20px, 기업명 18px/600, 업종·규모 14px |
| SB-05-02 | 소개 섹션 | h2와 일반 텍스트, 섹션 간 32px, 홈페이지는 안전한 외부 링크 |
| SB-05-03 | 채용 중 공고 | 공개 공고만 표시, 없으면 빈 상태 안내 |

#### SB-06 이력서 목록·작성

| ID | 요소 | 규격·행동 |
|---|---|---|
| SB-06-01 | 목록 | 카드 padding 24px, 제목·수정일·기본/공개 배지 |
| SB-06-02 | 폼 | 최대 800px, 기본정보·학력·경력·기술·포트폴리오 섹션 |
| SB-06-03 | 하단 행동 | 취소+저장 gap 12px, 기본·공개 여부 별도 설명 |
| SB-06-04 | 삭제 충돌 | 지원에 사용 중인 이력서는 삭제 제한 이유 표시 |

#### SB-07 지원 목록·상세

| ID | 요소 | 규격·행동 |
|---|---|---|
| SB-07-01 | 지원 카드 | 공고명·기업·지원일·상태, 카드 간 16px |
| SB-07-02 | 제출 내용 | 스냅샷 표시, ‘지원 당시 제출 내용’ 안내 |
| SB-07-03 | 진행·면접 | 단계별 텍스트, 일정·방식·장소/URL 표시, 내부 메모 제외 |
| SB-07-04 | 취소 | Danger 44px, 확인 모달, 허용 상태에서만 표시 |

#### SB-08 마이페이지·관심 공고·알림

| ID | 요소 | 규격·행동 |
|---|---|---|
| SB-08-01 | 내 정보 | 최대 720px, 계정·구직자 정보 분리, 비밀번호 별도 폼 |
| SB-08-02 | 관심 공고 | 공고 카드 공통 사용, 마감·비공개 상태 안내 |
| SB-08-03 | 알림 목록 | 읽지 않음 텍스트·배지, 44px 읽음 버튼, 상세 권한 재검사 |

#### SB-09 기업 대시보드·공고 관리

| ID | 요소 | 규격·행동 |
|---|---|---|
| SB-09-01 | 기업 레이아웃 | 사이드바 240px, 본문 padding 32px / 모바일 16px |
| SB-09-02 | 승인 안내 | 상단 상태 카드, 반려 사유와 재심사 행동, 미승인 기능 제한 |
| SB-09-03 | 통계 | PC 4열·모바일 2열, 숫자 28px/700, 라벨 14px |
| SB-09-04 | 공고 폼 | 최대 900px, 조건 PC 2열·모바일 1열, 설명 textarea |
| SB-09-05 | 공고 표 | 헤더 48px·행 최소 64px, 상태별 허용 버튼만 표시 |

#### SB-10 기업 지원자·면접 관리

| ID | 요소 | 규격·행동 |
|---|---|---|
| SB-10-01 | 지원자 목록 | 상태 필터 44px, 이름·지원일·단계 표시 |
| SB-10-02 | 지원자 상세 | 제출 내용과 상태 변경 영역 분리, 본인 기업 소유권 확인 |
| SB-10-03 | 면접 폼 | 일시·온라인/오프라인·안내·내부 메모, 구직자 공개 범위 라벨 |
| SB-10-04 | 상태 변경 | 가능한 다음 단계만 제공, 충돌 시 최신 상태 재조회 |

#### SB-11 취업연계

| ID | 요소 | 규격·행동 |
|---|---|---|
| SB-11-01 | 신청 안내 | 목적·진행 단계·신청 버튼 48px, 진행 중 건이 있으면 상세 이동 |
| SB-11-02 | 본인 내역 | 상태·신청일·공개 안내·연계 기업, 내부 메모 제외 |
| SB-11-03 | 관리자 편집 | 담당자·기업·해당 기업 공고·상태·공개 안내·내부 메모를 분리 |

#### SB-12 관리자·고객센터

| ID | 요소 | 규격·행동 |
|---|---|---|
| SB-12-01 | 관리자 메뉴 | 대시보드·회원·기업·공고·연계·공지·문의, 활성 배경 `#EFF6FF` |
| SB-12-02 | 심사 화면 | 원문·현재 상태·사유 입력, 반려 시 사유 필수 |
| SB-12-03 | 공지 폼 | 제목·본문·게시 여부, 미게시 미리보기는 관리자 전용 |
| SB-12-04 | 문의 | 질문·답변 분리, 작성자 본인 조회와 관리자 답변 화면 분리 |

### 8.6 공통 상태와 접근성

- 빈 목록: 제목·설명·다음 행동 버튼, 상하 padding 48px.
- 로딩: 최소 높이 240px, 로딩 문구와 spinner, 장식 skeleton은 aria-hidden.
- 오류: 원인과 수정·재시도 행동 표시. 입력한 폼 내용은 가능한 유지한다.
- 상태 배지: 심사 중 황갈색, 게시·진행 파랑, 합격·완료 초록, 반려·취소 빨강/회색. 색 외에 상태명을 표시한다.
- 긴 제목·오류·모바일 확대에서도 버튼이나 문구가 잘리지 않도록 한다.
- 키보드만으로 검색·가입·이력서 작성·지원·지원 취소를 완료할 수 있어야 한다.

## 9. 프로젝트 구조와 실행 환경

원본 디렉터리명 `employment-frontend`, `employment-backend`와 도메인 구성을 유지한다. 공지·문의·보안·마이그레이션 등 보완 기능의 파일은 추가한다.

```text
employment/
  employment-frontend/
    public/
      images/
      favicon.ico
    src/
      assets/
        images/
        styles/              # tokens.css, global.css, 화면별 CSS
      components/
        common/              # Header, Footer, Sidebar, Pagination, Loading
                             # 추가: Button, Input, Modal, Message, Guard
        layout/              # MainLayout, CompanyLayout, AdminLayout
      pages/
        main/                # Home
        auth/                # Login, Signup, FindPassword
        jobs/                # JobList, JobDetail
        companies/           # CompanyList, CompanyDetail
        resume/              # ResumeList, ResumeForm
        applications/        # ApplicationList, ApplicationDetail
        mypage/              # MyPage
        company/             # CompanyDashboard, JobManage, ApplicantManage
        employment/          # EmploymentLink
        admin/               # AdminDashboard, MemberManage, JobManage, NoticeManage
        notices/             # 추가
        inquiries/           # 추가
        notifications/       # 추가
        bookmarks/           # 추가
      api/                   # client, authApi, jobApi, resumeApi,
                             # applicationApi, adminApi 및 도메인별 추가
      hooks/
      utils/
      stores/                # 추가: 인증 상태
      App.jsx
      main.jsx
    package.json
    package-lock.json
    vite.config.js
  employment-backend/
    src/main/java/com/employment/
      EmploymentApplication.java
      global/
        config/              # SecurityConfig, WebConfig
        exception/           # GlobalExceptionHandler
        common/
        security/            # 추가: JWT·세션·CSRF 보조 처리
      auth/
      user/
      jobseeker/
      company/
      job/
      resume/
      application/
      interview/
      employment/
      notification/
      notice/
      inquiry/
      admin/
    src/main/resources/
      application.yml
      static/
    src/test/java/com/employment/
    build.gradle
    settings.gradle
    gradlew
    gradlew.bat
  docs/
    api.md
    setup.md
    decisions.md
  .env.example
  README.md
  EmploymentProjectSummary.md
```

도메인 내부는 원본 `job/` 예시처럼 분리한다.

```text
job/
  controller/JobController.java
  service/JobService.java
  repository/JobRepository.java
  entity/JobPosting.java
  dto/
    JobCreateRequest.java
    JobUpdateRequest.java
    JobResponse.java
```

- 트랜잭션·상태 전이·소유권 검사는 서비스 계층에서 처리한다.
- 관리자 전용 컨트롤러가 동일 업무를 호출하더라도 도메인 규칙을 우회하지 않는다.
- 테스트 DB는 별도 MySQL 또는 Testcontainers로 격리한다. 개발 DB 초기화 테스트를 만들지 않는다.
- dev 샘플: 구직자·기업·관리자 계정, 승인 대기·승인 기업, 공개·마감·반려 공고, 지원·면접·연계 사례를 제공한다.
- 관리자 샘플 생성은 dev 프로필로 제한한다. 운영에서 기본 관리자 비밀번호를 자동 생성하지 않는다.
- 프런트 `npm ci`, `npm run dev`, `npm run build`, `npm run lint`, 백엔드 `gradlew.bat bootRun`, `gradlew.bat test`를 README에 작성한다.
- `.env`는 Spring Boot가 자동으로 읽는다고 가정하지 않는다. 환경 변수 전달·IDE 실행 설정 방법을 setup 문서에 명시한다.

## 10. 단계별 구현 순서

각 단계에서 실제 동작을 확인하고 변경 파일·실행 방법·검증 결과·제한 사항을 기록한다.

1. **환경과 기본 구조**: 버전 호환성 확인, Gradle/Vite, MySQL 연결, 환경 예제, 공통 오류 DTO.
2. **스키마**: 원본 12개 테이블 migration, 보완 테이블·컬럼, 인덱스, dev 샘플.
3. **인증**: 역할별 가입, 로그인, 쿠키·CSRF·refresh·로그아웃, 회원 상태, 비밀번호 재설정.
4. **공통 화면**: CSS 토큰, 세 레이아웃, 버튼·폼·모달·Guard, 반응형 메뉴.
5. **기업·공고**: 기업 승인, 공고 초안·심사·게시·마감, 공개 검색·기업 상세.
6. **구직자·이력서**: 정보 수정, 이력서 CRUD·기본 지정, 관심 공고.
7. **지원**: 소유권·기간 검증, 중복 방지, 제출 스냅샷, 지원 내역·취소.
8. **기업 채용 관리**: 지원자 조회·상태 전이, 면접 등록·변경·취소, 내부 메모 분리.
9. **취업연계·운영**: 신청·담당자·매칭·종료, 공지·문의·내부 알림, 관리자 집계.
10. **마무리**: 통합·동시성 검증, build·lint, 모바일·키보드 확인, README·API 문서.

큰 범위 변경·외부 서비스 도입은 `docs/decisions.md`에 근거와 영향을 기록한다. 이 문서 작성만으로 위 단계가 완료된 것은 아니다.

## 11. 검증 및 완료 기준

### 11.1 기능·권한

- 새 브라우저에서 비로그인 공개 목록이 정상 표시되고 로그인 복원 중 화면이 깜빡이지 않는다.
- 구직자·기업·관리자별 메뉴와 서버 API 권한이 일치한다.
- 공개 가입 요청의 역할 변조로 관리자 계정을 만들 수 없다.
- 미승인·정지 기업이 공고를 게시하거나 지원자를 조회할 수 없다.
- 구직자는 타인 이력서로 지원하거나 다른 지원자의 기록·면접·문의를 볼 수 없다.
- 기업은 다른 기업 공고·지원자·면접에 접근할 수 없다.
- 기업 응답·구직자 응답·공개 응답에서 내부 메모와 비밀번호 해시가 제외된다.
- 로그아웃·정지·비밀번호 변경 후 기존 토큰이 거절된다.
- CSRF 누락·변조는 403, 비로그인 보호 접근은 401로 처리한다.
- 만료·재사용된 재설정 토큰은 거절된다.

### 11.2 데이터·상태·동시성

- 동일 공고 동시 지원에서 지원 레코드가 하나만 생성된다.
- 타인 이력서·마감 공고·접수 시작 전 공고·미승인 기업 공고 지원이 거절된다.
- 마감과 지원이 동시에 발생해도 잠금 순서에 따른 유효한 결과만 저장된다.
- 이력서를 수정해도 이전 제출 스냅샷은 유지된다.
- 동시 기본 이력서 지정 후 기본값은 최대 하나다.
- 취소와 기업 상태 변경의 경합에서 금지된 상태 전이가 발생하지 않는다.
- 취소 시 예정 면접이 취소되며 기업이 취소된 지원을 재활성화할 수 없다.
- 면접 등록 재시도에서 동일 키는 하나만 생성되고 다른 본문 재사용은 409다.
- 취업연계 동시 신청 시 진행 중 연계는 하나만 생성된다.
- 연계 기업과 공고 불일치·비관리자 담당자 지정이 거절된다.
- 공개 공지·알림·문의의 작성자·게시·수신자 조건이 정확하다.

### 11.3 화면·실행

- 3절의 경로와 버튼이 실제 API에 연결된다.
- 데스크톱 1,440px, 태블릿 768px, 모바일 390px 및 최소 360px를 확인한다.
- SB 요소별 색상·간격·폰트·버튼·폼과 hover/focus/error/disabled/loading을 확인한다.
- 로딩·빈 상태·오류·마감·반려·미승인에서 다음 행동을 안내한다.
- 키보드·모바일 확대 상태에서 핵심 사용자 흐름을 완료할 수 있다.
- 백엔드 테스트와 프런트 build·lint가 통과한다.
- 빈 테스트 DB에 Flyway가 적용되고 README만으로 실행·가입·지원·기업 처리·관리자 승인을 재현할 수 있다.
- 미구현 항목과 외부 연동 미설정은 완료로 표시하지 않는다.

## 12. Codex에 전달할 시작 프롬프트

아래는 후속 개발 요청에 사용할 예시이며 현재 실행 지시가 아니다.

```text
첨부한 EmploymentProjectSummary.md와 원본 네 파일을 기준으로
취업연계 플랫폼을 구현해주세요.

1. 원본 employment-frontend / employment-backend 구조와
   employment_db의 12개 테이블·역할·상태 enum을 유지해주세요.
2. 구현 전에 Java·Spring Boot·Gradle·Node·React·MySQL의
   호환 버전을 공식 자료로 확인하고 README와 lockfile에 고정해주세요.
3. 구직자·기업·관리자 인증, HttpOnly JWT 쿠키, CSRF,
   로그인 복원·refresh·로그아웃을 구현해주세요.
4. 기업 승인·공고 심사·이력서·관심 공고·지원 스냅샷·중복 지원 방지·
   면접·취업연계·공지·문의·내부 알림을 연결해주세요.
5. 역할뿐 아니라 본인 소유권·기업 승인·상태 전이를 서버에서 검증하고,
   이력서 개인정보와 기업·관리자 내부 메모의 노출 범위를 지켜주세요.
6. css선택자.txt의 색상과 선택자를 공통 컴포넌트로 정리하고,
   SB-01~SB-12 및 반응형·접근성·각종 상태 화면을 적용해주세요.
7. 비밀값은 환경 변수로 관리하고 실제 개발 DB를 초기화하지 마세요.
8. 단계별로 실제 동작을 확인하며 테스트·build·lint 결과,
   환경 예제·README·API 문서·제한 사항을 함께 제공해주세요.
9. 명세 보완이나 변경은 docs/decisions.md에 기록해주세요.
```

### 12.1 구현 시작 시 확정할 항목

- 실제 프로젝트 루트와 기술 스택의 정확한 버전.
- 보완 제안의 채택 여부와 원본 DB에서 변경할 migration 범위.
- 비밀번호 재설정 메일의 SMTP 환경과 개발용 대체 동작.
- 기업 진위 확인을 수동 심사로 할지 외부 검증을 연결할지 여부.
- 이력서 공개 검색·파일 첨부·이메일/문자 알림의 후속 범위.
- 운영 단계의 개인정보 보관·파기·익명화 정책.

이 항목들은 원본 자료에 명시되지 않았으므로 이미 합의된 요구 사항으로 간주하지 않는다.
