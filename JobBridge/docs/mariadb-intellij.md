# DB 스키마와 실행 설정

현재 애플리케이션은 기존 DB의 테이블·컬럼·사용자 데이터를 자동 변경하지 않습니다.
일반 실행 설정은 spring.sql.init.mode=never입니다.

- 신규 빈 DB의 최종 정의: employment-backend/src/main/resources/db/schema.sql
- 공개 FAQ 예시: employment-backend/src/main/resources/db/reference-data.sql
- 두 SQL 파일은 UTF-8입니다. 버전 이력 테이블이나 자동 업그레이드 작업은 없습니다.
- SQL은 신규 DB용이며 기존 스키마를 최신 상태로 변환하는 도구가 아닙니다.
- 기존 DB의 누락 컬럼·필수값·제약은 별도 검토 대상이며, 앱 재시작으로 자동 보정하지 않습니다.

## 신규 로컬 DB

이미 준비한 신규 빈 DB의 URL·사용자·비밀번호를 IDE 환경 변수로 전달합니다.
자격증명이나 실제 고객 데이터를 소스에 넣지 않습니다.
신규 로컬 DB를 초기화할 때만 아래 실행 인자를 명시합니다.

    --spring.sql.init.mode=always
    --spring.sql.init.schema-locations=classpath:db/schema.sql
    --spring.sql.init.data-locations=classpath:db/reference-data.sql
    --spring.sql.init.encoding=UTF-8

개발 초기 계정은 dev 프로필에서 명시적으로 제공한 seed 비밀번호가 있을 때만 생성합니다.
운영 DB에는 초기화 인자를 적용하지 않습니다.
JDBC 연결 세션은 UTC이고, 날짜 자체와 한국 시간 표시를 구분합니다.

## 테스트

gradlew.bat test --rerun-tasks는 H2 인메모리 DB를 사용합니다.
선택적 mariaDbTest --rerun-tasks는 JOBBRIDGE_TEST_MARIADB_URL에
jdbc:mariadb://127.0.0.1:포트/jobbridge_regression_16진수식별자를 명시한
폐기 가능한 로컬 DB에서만 실행합니다.
JDBC 변환 전용 검사는 같은 URL 뒤에 _jdbc를 붙인 별도 빈 DB를 사용합니다.
테스트용 DB는 미리 준비해야 하며, 운영 DB나 실제 SMS·이메일은 사용하지 않습니다.
