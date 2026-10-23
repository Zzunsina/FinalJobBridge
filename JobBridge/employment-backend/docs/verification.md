# 검증 방법과 범위

백엔드: employment-backend에서 gradlew.bat clean test --rerun-tasks.
일반 SQL로 초기화한 H2 인메모리 DB를 사용하며 실제 DB 자격증명을 가져오지 않습니다.
SchemaInitializationTests는 업무 테이블만 생성되는지, 반복 초기화가 사용자 데이터·FAQ와 휴대폰 유일성을 보존하는지 검사합니다.

선택적 MariaDB 검사는 [DB 실행 설정](mariadb-intellij.md)의 임시 DB URL로
gradlew.bat mariaDbTest --rerun-tasks를 실행합니다.
JVM Asia/Seoul, DB 세션 UTC에서 Timestamp 읽기/쓰기, 문의·공지 API 시각, 이미지 boolean을 검사합니다.
HTTP 400/404/405·일반적인 500, 잘못된 날짜/일시·고용형태, 경력 연수 null과 생략도 회귀 테스트 대상입니다.
비밀번호 재설정은 메일·토큰 사용 흐름이 완성되지 않아 503 미제공 응답을 유지합니다.

프론트: JobBridge-Frontend에서 npm run lint, npm run build.
화면 통합 검사는 별도 로컬 서버·임시 DB·가상 계정과 모의 SMS로 수행합니다.
운영 DB, 실제 SMS·이메일, 다른 DB/JDBC 버전·시간대 조합·장시간 부하는 별도 검증 대상입니다.
실제 실행 결과와 건수는 해당 실행의 build/reports/tests를 확인합니다.
