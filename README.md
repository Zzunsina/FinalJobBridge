# FinalJobBridge 수정본 — IntelliJ 실행 안내

기준 브랜치: fix/flyway-startup-20261022
소스 커밋: ac778a4
검증 결과: Java 17 / Gradle 8.14.3에서 clean test bootJar 성공, 45개 테스트 통과.

공용 DB에는 접속하거나 변경하지 않았습니다. V14 원본을 유지했으므로 관리자가 실패 이력을 정합화하기 전에는 해당 DB로 서버를 실행할 수 없습니다.

## 1. 압축 해제 및 IntelliJ 설정

1. ZIP을 새 폴더에 압축 해제합니다. 기존 프로젝트에 덮어쓰지 마세요.
2. IntelliJ의 Open에서 FinalJobBridge/JobBridge/employment-backend 폴더를 선택합니다.
3. Gradle 프로젝트로 불러옵니다.
4. File → Project Structure → Project SDK를 JDK 17로 설정합니다.
5. Settings → Build, Execution, Deployment → Build Tools → Gradle에서 Gradle 배포판은 프로젝트 Wrapper, Gradle JVM은 JDK 17로 설정합니다.
6. Gradle 동기화가 완료될 때까지 기다립니다. 최초 의존성 다운로드에는 인터넷이 필요할 수 있습니다.

## 2. DB 관리자 조치 전에 내가 할 일

IntelliJ 하단 Terminal에서 현재 경로가 JobBridge/employment-backend인지 확인하고 실행합니다.

```powershell
.\gradlew.bat clean test bootJar
```

Gradle 테스트는 H2 메모리 DB로 고정되어 있습니다. 테스트용 DB_URL, DB_USERNAME, DB_PASSWORD 또는 JWT_SECRET을 따로 입력할 필요가 없습니다. IntelliJ에서 테스트를 직접 실행할 때도 Gradle로 실행하도록 설정하면 같은 보호 설정을 사용합니다.

관리자 조치 전에는 EmploymentApplication, bootRun, adoptLegacyDatabase를 실행하지 마세요. repair도 직접 실행하지 마세요.

관리자에게 다음 두 파일을 전달합니다.

- docs/db-admin/01-read-only-checks.sql: 읽기 전용 확인 쿼리
- docs/flyway-startup-recovery.md: 원인, 복구 조건, 복구 및 검증 절차

관리자는 실제 인덱스 정의와 전체 이력, V14 체크섬, V15/V16의 스키마 적용 상태를 확인하고 복제 DB 검증 후 정책에 맞게 이력을 정합화해야 합니다. 단순 repair만으로 중복 인덱스 문제를 해결할 수 없습니다.

## 3. IntelliJ 서버 실행 설정

Run → Edit Configurations에서 EmploymentApplication 실행 설정을 선택하거나 생성합니다. Spring Boot 항목이 없으면 Application으로 생성합니다.

| 항목 | 값 |
| --- | --- |
| Main class | com.employment.EmploymentApplication |
| Module classpath | 백엔드의 main 모듈 |
| JRE | JDK 17 |
| Working directory | 압축 해제한 JobBridge/employment-backend 폴더의 절대 경로 |
| Active profiles | 비워 두기 |

Environment variables가 보이지 않으면 Modify options에서 표시합니다. 아래 값을 각각 추가합니다. 실제 값에는 따옴표를 붙이지 않습니다.

### 필수 환경변수

| 변수 | 설정값 |
| --- | --- |
| DB_URL | jdbc:mariadb://관리자가알려준호스트:3306/employment_db |
| DB_USERNAME | 관리자가 발급한 애플리케이션 계정 |
| DB_PASSWORD | 해당 계정의 실제 비밀번호 |
| JWT_SECRET | 팀에서 정한 32자 이상의 비밀 문자열 |

위 URL의 호스트는 예시 자리표시자이므로 실제 승인된 호스트로 바꿉니다. 실제 비밀번호와 비밀 문자열을 소스나 공유 README에 기록하지 마세요.

### SMS 기능 사용 시 필요한 환경변수

| 변수 | 설정값 |
| --- | --- |
| COOLSMS_API_KEY | SOLAPI API 키 |
| COOLSMS_API_SECRET | SOLAPI API 시크릿 |
| COOLSMS_SENDER_NUMBER | SOLAPI에 등록된 발신번호 |

SMS 환경변수는 서버 시작 자체의 필수값은 아니지만 실제 문자 발송 및 SMS 인증 기능에는 필요합니다.

### 기존 설정 확인

- DEV_SEED_PASSWORD는 설정하지 않고 dev 프로필도 활성화하지 않습니다. 공용 DB에서 초기 데이터 생성 코드가 실행되는 상황을 피하기 위한 설정입니다.
- 기존 SPRING_DATASOURCE_* 환경변수, 프로그램 인자 또는 -D 옵션에 다른 DB 설정이 있으면 제거하거나 승인된 값과 일치시킵니다.
- .env 및 .env.local 파일이 있다면 이전 DB 접속정보가 남아 있는지 확인합니다. 이 ZIP에는 개인 로컬 설정 파일을 포함하지 않았습니다.
- Flyway enabled/validate와 JPA ddl-auto=validate를 그대로 유지합니다. 오류 우회를 위해 비활성화하지 마세요.

## 4. DB 관리자 조치 후에 내가 할 일

1. 관리자에게 V14 이력 정합화 및 Flyway 검증이 완료됐고, 이 배포본에서 추가 적용할 마이그레이션이 없음을 확인받습니다.
2. IntelliJ의 환경변수와 승인된 접속 대상을 다시 확인합니다.
3. EmploymentApplication을 실행합니다.
4. 콘솔에서 Started EmploymentApplication 메시지를 확인합니다. 기본 서버 포트는 8080입니다.
5. 프론트엔드를 연결해 로그인·조회 기능을 확인합니다. SMS 기능은 공급자 환경변수까지 설정한 뒤 확인합니다.

V14 실패 또는 체크섬 오류가 다시 나오면 서버를 중지하고 로그를 관리자에게 전달합니다. 직접 repair, 인덱스 삭제, 데이터 삭제, V14 변경 또는 Flyway 비활성화를 하지 마세요.

8080 포트가 이미 사용 중이면 기존 서버 프로세스를 확인하거나 SERVER_PORT 환경변수로 승인된 다른 포트를 지정합니다. 다른 포트를 쓰는 경우 프론트엔드의 BACKEND_URL도 같은 서버 주소로 맞춥니다.

## 5. ZIP 구성 및 검증 자료

- JobBridge/employment-backend/: 수정된 백엔드 소스와 Gradle Wrapper
- JobBridge-Frontend/: 기존 프론트엔드 소스
- docs/db-admin/01-read-only-checks.sql: 관리자용 읽기 전용 쿼리
- docs/flyway-startup-recovery.md: 변경 내용, DB 관리자 복구 절차 및 검증 범위
- evidence/tests/index.html: 45개 테스트 통과 보고서
- HANDOFF.md: 간단한 전달 안내
- README-original.md: 원본 프로젝트 README

Git 이력, node_modules, 로컬 비밀 설정 및 의존성 캐시는 포함하지 않았습니다. 실제 공용 MariaDB 기동은 검증하지 않았으며, 관리자 조치 이후 확인해야 합니다.