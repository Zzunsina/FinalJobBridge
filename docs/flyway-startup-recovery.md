# Spring Boot 실행 실패 분석 및 공용 DB 복구 인계

## 소스 변경

- application.properties를 유일한 기본 설정으로 사용한다. 중복 application.yml 및 application-mariadb.yml을 제거했다. 기존 YAML에서 jpa/flyway가 coolsms 아래에 있던 잘못된 계층과 필수 SMS 환경변수의 중복 선언을 없앴다.
- DB_URL, DB_USERNAME, DB_PASSWORD를 필수 입력으로 전환했다. 하드코딩된 root/비밀번호와 기본 employment_db 접속을 제거했다. JDBC 드라이버는 URL에서 추론한다. 기존 SPRING_DATASOURCE_* 환경변수 직접 지정도 가능하다.
- JWT_SECRET은 기존대로 필수다. SMS 설정은 기존 application.properties의 선택적 별칭 지원을 유지한다. 실제 SMS 발송에는 공급자 설정이 필요하다.
- Flyway validate 유지, clean 금지, 자동 baseline 및 out-of-order 금지를 명시했다. 자동 repair나 실패 무시는 추가하지 않았다.
- Gradle 테스트는 JVM 속성으로 H2 메모리 DB, test 프로필, 로컬 설정 import 차단을 고정한다. IDE 직접 테스트도 src/test/resources/application.properties를 사용하되 외부 환경변수로 덮어쓰지 않아야 한다.
- V14/V15/V16 및 다른 기존 마이그레이션은 수정하지 않았다. 성공한 DB의 체크섬 호환성을 유지한다.

## 실행 설정

백엔드 실행 디렉터리: JobBridge/employment-backend

Java 17에서 DB_URL, DB_USERNAME, DB_PASSWORD, JWT_SECRET을 설정한 뒤 gradlew.bat bootRun으로 실행한다. 예: DB_URL=jdbc:mariadb://승인된호스트:3306/승인된DB. 실제 접속정보를 저장소에 커밋하지 않는다. mariadb 프로필은 더 이상 별도 설정을 덮어쓰지 않으며 공통 설정을 사용한다. .env / .env.local 선택적 로딩은 유지된다.

공용 DB에 dev 프로필과 DEV_SEED_PASSWORD를 설정하면 초기 데이터 생성 코드가 실행될 수 있으므로 관리자 승인 없이 사용하지 않는다. 이 작업에서는 공용 DB로 애플리케이션을 실행하지 않았다.

검증 명령: gradlew.bat --offline --no-daemon clean test bootJar
테스트 보고서: build/reports/tests/test/index.html
실행 JAR: build/libs/employment-backend-1.0.0.jar

## 원인과 소스만으로 해결할 수 없는 부분

사용자 제공 상태는 V14 success=0, V15/V16 success=1이며 users(phone)에 uq_users_phone UNIQUE 인덱스가 이미 존재한다. V14는 CREATE UNIQUE INDEX를 무조건 실행하므로 중복 이름으로 실패한다. 실패 이력이 남아 있으면 Flyway 검증이 Spring 초기화를 차단한다. 이 DB 상태는 여기에서 직접 조회한 사실이 아니라 전달받은 정보다.

V14에 IF NOT EXISTS만 넣는 것은 성공 환경의 체크섬을 바꾸며, 같은 이름의 잘못된 인덱스까지 정상으로 오인할 수 있다. V17 추가나 Flyway 비활성화로 기존 V14 실패 이력을 해결할 수도 없다.

## DB 관리자 별도 조치 (이 문서는 실행 스크립트가 아님)

1. 애플리케이션 및 팀원들의 마이그레이션 실행을 중단하고 스키마/이력 백업과 복제 DB를 확보한다.
2. 아래 읽기 전용 쿼리를 승인된 세션에서 실행해 실제 DB, 인덱스, 전체 이력을 확인한다. 비슷한 이름의 인덱스가 아니라 정확히 users(phone) 단일 컬럼 전체 길이의 UNIQUE 인덱스인지 확인한다. NON_UNIQUE=0, SEQ_IN_INDEX=1, COLUMN_NAME=phone, SUB_PART IS NULL이며 추가 컬럼이 없어야 한다.

```sql
SELECT DATABASE(), VERSION();
SHOW CREATE TABLE employment_db.users;
SELECT INDEX_NAME, NON_UNIQUE, SEQ_IN_INDEX, COLUMN_NAME, SUB_PART
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA='employment_db' AND TABLE_NAME='users'
ORDER BY INDEX_NAME, SEQ_IN_INDEX;
SELECT installed_rank, version, description, type, script, checksum, success
FROM employment_db.flyway_schema_history ORDER BY installed_rank;
```

3. 동일 배포본의 V14 원본 및 Flyway 버전으로 체크섬을 대조한다. V14 실패가 단 한 행인지, 그 작업이 인덱스 생성 단 한 문장인지, V15/V16이 성공했으며 실제 스키마와 일치하는지 확인한다. 다른 실패/누락/체크섬 불일치가 있으면 중단하고 별도로 조사한다.
4. 정확한 인덱스가 이미 있고 V14의 의도된 결과가 완전히 충족된 경우, 관리자 정책이 허용한다면 **예외적인 수동 이력 정합화**를 복제 DB에서 먼저 검토한다. 검증된 installed_rank/version/script/checksum 및 success=0을 모두 조건으로 지정하여 해당 V14 행의 success만 1로 정정하는 방식이다. 변경 행 수는 정확히 1이어야 한다. 원본 checksum, V15/V16 및 다른 이력은 그대로 둔다. 트랜잭션/행 잠금/감사 기록과 DBA 승인을 적용한다. 이는 실제로 실패했던 실행을 결과 충족 상태로 인정하는 관리 판단이므로 자동화하거나 일반 애플리케이션 시작에 넣지 않는다.
5. 수동 이력 정정이 조직 정책상 허용되지 않으면 중단하고 DBA와 승인된 Flyway 이력 복구 방식을 결정한다. 정확한 인덱스가 없거나 정의가 다르면 위 정정을 적용하지 않는다. 인덱스 삭제나 재생성은 본 수정 범위에 포함하지 않는다.
6. 같은 배포본/클래스패스/마이그레이션 locations로 Flyway validate 및 info를 실행하여 V14~V16이 성공으로 해석되고 체크섬이 일치하는지 확인한다. 새 마이그레이션이 없는 현재 배포본에서는 migrate가 0건이어야 한다. 검토 후 애플리케이션을 시작해 JPA validate와 API를 확인한다.

단순 repair는 실패 행을 제거할 뿐 users의 인덱스를 정합화하거나 V14를 성공 처리하지 않는다. V15/V16보다 낮은 V14가 미적용/ignored 상태가 될 수 있고, out-of-order 재실행은 같은 인덱스 오류를 재발시킨다. repair는 다른 체크섬 및 누락 이력도 변경할 수 있으므로 이 DB에 무조건 실행하지 않는다. clean, 데이터 삭제, 인덱스 삭제, 전체 schema_history 삭제, 자동 baseline, validate 비활성화는 사용하지 않는다.

공식 참고: https://documentation.red-gate.com/flyway/reference/commands/repair

## 검증 범위

H2 테스트는 마이그레이션 전체 적용, 중복 인덱스 실패 재현, V14 실패/V15·V16 성공 이력 상태의 검증 차단, 검토된 이력만 정정한 뒤 migrate 0건, 사용자 행 보존과 전화번호 UNIQUE 유지 여부를 확인한다. MariaDB의 DDL 자동 커밋 및 실제 공용 DB 상태까지 보장하지 않으므로 관리자 복제 DB 검증이 필요하다.

## 이번 작업의 실행 결과 (2026-10-22)

- Java 17 / Gradle 8.14.3, 오프라인 캐시 사용.
- clean test bootJar 성공: 45개 테스트, 실패 0, 오류 0.
- Spring Boot 컨텍스트 초기화 및 H2 V1~V16 적용 성공. 신규 회귀 테스트 2개 통과.
- git diff --check 통과. 기존 마이그레이션 파일 변경 없음.
- 공용 DB 접속, repair, 이력 수정, 데이터 삭제 및 push 수행 안 함.
- 실제 MariaDB 기동 검증은 공용 DB 무접속 조건에 따라 수행하지 않음.
