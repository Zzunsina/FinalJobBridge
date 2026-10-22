# 수정 프로젝트 전달 안내

브랜치: fix/flyway-startup-20261022
커밋: ac778a4

전체 추적 소스와 Gradle Wrapper를 포함합니다. Git 이력, 로컬 비밀 설정, node_modules와 의존성 캐시는 제외했습니다. 프론트엔드는 원본 그대로입니다.

관리자 전달 파일:
- docs/db-admin/01-read-only-checks.sql: 읽기 전용 확인 쿼리
- docs/flyway-startup-recovery.md: 원인, 복구 조건과 절차, 변경 파일 및 검증 결과

쿼리 파일에는 변경 명령이 없습니다. 복구 절차를 먼저 읽고 관리자 승인과 복제 DB 검증 후 조치해야 합니다. V14는 성공 환경의 체크섬을 보존하기 위해 유지했습니다. 공용 DB의 실패 이력을 관리자가 정합화하기 전에는 해당 DB에서 기동 실패가 계속됩니다.

백엔드: JobBridge/employment-backend
Java 17에서 gradlew.bat clean test bootJar로 빌드합니다. 의존성 다운로드에는 인터넷이 필요할 수 있습니다. 테스트 DB는 H2 메모리 DB로 고정되어 있습니다.

서버 실행 전 DB_URL, DB_USERNAME, DB_PASSWORD, JWT_SECRET을 설정합니다. DB 복구 확인 후 gradlew.bat bootRun으로 실행합니다.

검증 결과: clean test bootJar 성공, 45개 테스트 모두 통과.
보고서: evidence/tests/index.html
공용 DB에는 접속하거나 변경하지 않았습니다. push도 수행하지 않았습니다.