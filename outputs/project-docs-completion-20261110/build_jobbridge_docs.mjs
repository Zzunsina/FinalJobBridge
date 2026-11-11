import fs from "node:fs/promises";
import { FileBlob, SpreadsheetFile } from "file:///C:/Program%20Files/WindowsApps/OpenAI.CodexPrimaryRuntime.v26-1104-2876-0_26.1104.2876.0_x64__3k8sg7r9htsxt/dependencies/node/node_modules/@oai/artifact-tool/dist/artifact_tool.mjs";

const source = "C:/Users/hi101/Desktop/잡브릿지.xlsx";
const out = "C:/FinalJobBridge/outputs/project-docs-completion-20261110/잡브릿지_프로젝트기준_전체보완.xlsx";
const wb = await SpreadsheetFile.importXlsx(await FileBlob.load(source));
const sheet = n => wb.worksheets.getItem(n);
const serial = (y,m,d) => Math.round((Date.UTC(y,m-1,d)-Date.UTC(1899,11,30))/86400000);
const beforeTest1 = JSON.stringify(sheet("통합테스트 (1)").getRange("A1:M41").values);
const oldTableRows = JSON.stringify(sheet("테이블 명세서").getRange("A1:G579").values);
const assert = (ok,msg) => { if(!ok) throw new Error(msg); };

// 1. Requirements: expand from implemented source features, not the incomplete workbook draft.
const req = sheet("요구사항 정의서");
req.getRange("A2").values = [["기능 요구사항 81개 · 구현 현황과 기능별 개정일 반영 · 최종 현황 2026-11-10"]];
req.getRange("B6").values = [["v1.3 · 현재 구현 기능 및 기능별 개정일 반영 · 2026-11-10"]];
req.getRange("J8:J72").values = req.getRange("C8:C72").values.map(([id])=>{
  const group=String(id||"").split("-")[1]||"";
  const dateMap={"001":serial(2026,10,20),"002":serial(2026,10,22),"003":serial(2026,10,24),"004":serial(2026,10,25),"005":serial(2026,10,27),"006":serial(2026,10,28),"007":serial(2026,10,29),"008":serial(2026,10,30),"009":serial(2026,10,31),"010":serial(2026,11,2),"011":serial(2026,11,3),"012":serial(2026,11,5),"013":serial(2026,11,6),"014":serial(2026,11,7)};
  return [dateMap[group]||serial(2026,11,7)];
});
req.getRange("J8:J72").setNumberFormat("yyyy-mm-dd");
const addedReq = [
 [66,"기능","SFR-001-12","회원","카카오 로그인","부분 완료","- 카카오 OAuth 인증 결과를 이용해 기존 계정을 확인하거나 계정을 생성한다.\n- 카카오 callback 이후 로그인 상태로 서비스에 돌아온다.","보통","AuthController.kakaoCallback / AuthService.kakaoLogin. Kakao 앱 키·redirect URI 등 환경 설정이 유효한 환경에서만 연동 가능.",serial(2026,10,23),serial(2026,11,10)],
 [67,"기능","SFR-015-01","진로·적성","적성검사 응시·결과·이력","완료","- 검사 문항을 조회하고 답안을 저장·제출한다.\n- 제출 결과와 응시 이력을 본인 계정으로 조회한다.\n- 지원 시 최신 제출 결과를 제출 스냅샷에 포함한다.","높음","CareerAssessmentController / CareerAssessments; career_assessment_attempts; 검사 모드·질문은 리소스 파일 기준.",serial(2026,10,28),serial(2026,11,10)],
 [68,"기능","SFR-015-02","진로·적성","진로탐색 응시·결과·이력","완료","- 진로탐색을 시작하고 답안을 임시 저장 또는 제출한다.\n- 제출 결과와 본인 응시 이력을 확인한다.","높음","CareerExplorationController / CareerExplorations; career_exploration_attempts.",serial(2026,10,29),serial(2026,11,10)],
 [69,"기능","SFR-016-01","취업도구","맞춤법 검사·수정","완료","- 문장을 입력해 맞춤법 검사 결과를 확인한다.\n- 제안된 교정 항목을 선택해 문장에 적용한다.","보통","CareerTools.jsx / koreanSpellChecker.js / POST /api/tools/spelling. 맞춤형 규칙 기반 도구로 외부 전문 사전 연동을 뜻하지 않음.",serial(2026,10,30),serial(2026,11,10)],
 [70,"기능","SFR-016-02","취업도구","급여 실수령액 계산","완료","- 연봉 또는 월급, 월 비과세액, 부양가족 수로 예상 공제 및 실수령액을 계산한다.\n- 계산 가정과 실제 명세액 차이를 안내한다.","보통","CareerTools.jsx / utils/salaryCalculator.js. 2026년 적용 기준과 계산 가정이 코드에 명시됨; 세무 확정값으로 사용하지 않음.",serial(2026,10,30),serial(2026,11,10)],
 [71,"기능","SFR-017-01","면접후기","후기 작성·수정·삭제·공개","완료","- 면접 후기를 작성하고 수정 또는 삭제한다.\n- 관리자의 승인 완료 후 공개 기업 화면에 표시한다.","높음","InterviewReviewController / InterviewReviews; interview_reviews, interview_review_moderation. 승인된 후기만 공개.",serial(2026,10,31),serial(2026,11,10)],
 [72,"기능","SFR-017-02","면접후기","면접 참석 확인·이의 제기","완료","- 면접일 경과 후 참석 확인을 요청하고 증빙 파일을 첨부할 수 있다.\n- 기업이 응답하고, 거절 또는 기한 경과 시 관리자가 이의 제기를 처리한다.","높음","InterviewReviewController; attendance request/evidence/company response/admin resolve 경로. 증빙 접근 권한 확인.",serial(2026,11,1),serial(2026,11,10)],
 [73,"기능","SFR-017-03","면접후기","후기 신고·관리자 중재","완료","- 기업이 후기 신고를 제출하고 처리 상태를 조회한다.\n- 관리자가 신고와 후기 중재 결과·사유를 기록한다.","높음","InterviewReviewController / InterviewReviews; member_reports 및 후기 중재 API.",serial(2026,11,2),serial(2026,11,10)],
 [74,"기능","SFR-018-01","취업연계","추천 배정·재매칭·취소","완료","- 관리자가 신청 건에 담당자·기업·공고를 지정해 추천을 전달한다.\n- 진행 중 연계를 재매칭하거나 취소하고 처리 이력을 확인한다.","높음","EmploymentReferralController / EmploymentReferrals; employment_links, employment_referrals. 완료된 연계 취소는 제한.",serial(2026,11,3),serial(2026,11,10)],
 [75,"기능","SFR-018-02","취업연계","기업 면접 제안·구직자 응답","완료","- 기업이 추천 후보에게 면접 일정을 제안한다.\n- 구직자가 수락·변경 요청·참여 어려움을 응답하고 변경 제안 이력을 확인한다.","높음","EmploymentInterviewProposals / EmploymentReferrals; employment_interview_proposals 및 coordination 기록.",serial(2026,11,4),serial(2026,11,10)],
 [76,"기능","SFR-018-03","취업연계","입사 안내·참여 결과","완료","- 기업이 첫 출근 정보·준비물·연락처를 등록한다.\n- 구직자가 확인, 출근 또는 미출근 사유를 기록한다.","높음","EmploymentOnboarding; employment_onboarding. 참여 상태와 안내 확인을 연계 이력에 기록.",serial(2026,11,5),serial(2026,11,10)],
 [77,"기능","SFR-019-01","유료광고","광고 상품·기간 확인·주문","부분 완료","- 기업이 광고 상품·공고·희망 기간을 선택해 가용 기간을 확인하고 주문한다.\n- 상품 가격·부가세·기간·주문 금액을 구분한다.","높음","AdvertisingController / AdvertisingService; /api/ads/products, /company/ad-availability, /company/ad-orders; ad_products, ad_orders.",serial(2026,11,5),serial(2026,11,10)],
 [78,"기능","SFR-019-02","유료광고","테스트 결제·서버 검증","부분 완료","- 승인된 광고 주문에서만 결제를 진행·확인한다.\n- webhook 요청의 주문번호·결제 식별자를 서버에서 재조회해 결제 상태를 판정한다.\n- 테스트 PG/격리 모의 결제와 운영 결제를 구분한다.","높음","/company/ad-orders/{id}/checkout, /confirm, /payments/portone/webhook. 운영 PG 자격증명·실결제 연동은 별도 설정이 필요하며 이 문서에서 진행하지 않음.",serial(2026,11,6),serial(2026,11,10)],
 [79,"기능","SFR-019-03","유료광고","소재 심사·게시·환불 처리","부분 완료","- 기업이 광고 소재를 등록하고 철회·환불을 요청한다.\n- 관리자가 소재 승인·반려, 결제 상태 동기화, 환불을 처리한다.\n- 결제·심사·기간이 유효한 광고만 노출한다.","높음","/admin/advertising, /admin/ad-orders/{id}/review|sync|refund, /ads/active. 실시간 노출 성과(조회·클릭) 계측은 구현된 API 범위로 기록하지 않음.",serial(2026,11,7),serial(2026,11,10)],
 [80,"기능","SFR-020-01","홈프로모션","홈 프로모션 관리·공개","완료","- 관리자가 홍보 배너의 문구·이미지·기간·이동 링크를 등록·수정·삭제한다.\n- 노출 기간 내 공개 항목을 사용자 홈에서 확인한다.","보통","HomePromotionController / HomePromotionService; home_promotions, home_promotion_deletions 및 이미지 경로.",serial(2026,11,8),serial(2026,11,10)],
 [81,"기능","SFR-021-01","파일첨부","이력서·지원서 첨부파일 조회","완료","- 이력서/지원서에 연결된 PDF 첨부 파일을 제한 조건에 따라 내려받는다.\n- 본인 또는 권한 있는 기업만 접근한다.","보통","ResumeExtrasController; /resumes/{id}/attachment, /applications/{id}/attachment. PDF 형식·용량 및 소유권 정책을 적용.",serial(2026,11,9),serial(2026,11,10)]
];
req.getRange("A73:K88").copyFrom(req.getRange("A72:K72"),"all");
req.getRange("A73:K88").values=addedReq;
req.getRange("J73:K88").setNumberFormat("yyyy-mm-dd");
req.getRange("A73:K88").format.wrapText=true;
req.getRange("A73:K88").format.rowHeight=52;
req.freezePanes.freezeRows(7);

// 2. WBS: add omitted implemented modules, with schedule dates distributed across the project period.
const wbs=sheet("WBS");
const addedWbs=[
 ["기능 보완","이석현","구직자 도구","적성검사·진로탐색 화면",1,serial(2026,10,28),serial(2026,11,2),6,"완료","응시·결과·이력 화면"],
 [null,"전진영","구직자 도구","적성검사 저장·채점·이력 API",1,serial(2026,10,28),serial(2026,11,1),5,"완료","career_assessment_attempts"],
 [null,"전진영","구직자 도구","진로탐색 답안·결과·이력 API",1,serial(2026,10,29),serial(2026,11,2),5,"완료","career_exploration_attempts"],
 [null,"이석현","취업도구","맞춤법 검사·교정 UI 및 급여 계산기",1,serial(2026,10,30),serial(2026,11,3),5,"완료","규칙 기반 맞춤법·급여 계산 유틸리티"],
 [null,"정영록","면접후기","후기·참석확인·증빙·신고 API",1,serial(2026,10,31),serial(2026,11,5),6,"완료","면접 후기·신고 처리"],
 [null,"서상훈","면접후기","기업 후기·참석 요청·관리자 중재 UI",1,serial(2026,11,1),serial(2026,11,6),6,"완료","기업 및 관리자 화면"],
 [null,"김경우","취업연계","추천 배정·재매칭·취소 API 보완",1,serial(2026,10,30),serial(2026,11,4),6,"완료","employment_referrals"],
 [null,"김경우","취업연계","기업 면접 제안·구직자 응답·온보딩",1,serial(2026,11,2),serial(2026,11,7),6,"완료","제안·참여 상태 이력"],
 [null,"이석현","취업연계","추천·면접 제안·입사 안내 화면",1,serial(2026,11,2),serial(2026,11,7),6,"완료","구직자·기업 연계 흐름"],
 [null,"정영록","유료광고","상품·가용성·광고 주문·결제 API",1,serial(2026,11,3),serial(2026,11,8),6,"완료","PortOne 테스트/서버 검증 코드 포함"],
 [null,"서상훈","유료광고","기업 광고 주문·관리자 심사 UI",1,serial(2026,11,3),serial(2026,11,8),6,"완료","운영 PG 실결제는 미설정 범위"],
 [null,"정영록","유료광고","광고 소재 심사·게시·환불·노출 수명주기",1,serial(2026,11,4),serial(2026,11,9),6,"완료","조회·클릭 계측 기능은 제외"],
 [null,"서상훈","홈 프로모션","관리자 배너 등록·기간·이미지 관리 UI",1,serial(2026,11,4),serial(2026,11,8),5,"완료","공개 홈 노출 경로"],
 [null,"김경우","홈 프로모션","프로모션 CRUD·이미지 저장 API",1,serial(2026,11,4),serial(2026,11,8),5,"완료","home_promotions"],
 [null,"전진영","첨부파일","이력서·지원서 첨부 저장·권한 API",1,serial(2026,10,26),serial(2026,10,30),5,"완료","파일 형식·용량·권한 점검"],
 [null,"이석현","인증","카카오 OAuth 로그인·callback 화면 연결",1,serial(2026,10,23),serial(2026,10,27),5,"완료","환경 키·redirect URI 별도 구성"],
 [null,"공통","문서·통합","기능·요구사항·테이블·유스케이스 문서 정합",1,serial(2026,11,8),serial(2026,11,10),3,"완료","코드 확인 기준 문서 보완"]
];
wbs.getRange("A109:J125").copyFrom(wbs.getRange("A108:J108"),"all");
wbs.getRange("A109:J125").values=addedWbs;
wbs.getRange("F109:G125").setNumberFormat("yyyy-mm-dd");
wbs.getRange("A109:J125").format.wrapText=true;
wbs.getRange("A109:J125").format.rowHeight=30;

// 3. Schedule: keep the 4-week calendar, replace vague repeated placeholders with completed project work.
const plan=sheet("일정표");
plan.getRange("B2").values=[["JobBridge 개발 일정 | 2026.10.19–2026.11.12 · 완료 프로젝트 기준"]];
plan.getRange("B6:H6").values=[["요구사항·WBS 정리\n개발 환경·DB 구조\n프로젝트 범위 확정","역할 분장\n화면·API·데이터 흐름 설계","공통 레이아웃·인증\n초기 화면 구성","회원·프로필·기업 정보\n승인 흐름 구현","이력서·공고 작성\n초기 API 연동",null,null]];
plan.getRange("B8:H8").values=[["채용 검색·기업 조회\nDB 테이블 확정","공고 심사·마감\n지원·스냅샷","지원 취소·상태 전이\n면접·알림 연결","면접 흐름·고객센터\n공통 오류 처리","기능 통합·보완\n취업연계 기능 추가",null,null]];
plan.getRange("B10:H10").values=[["진로검사·진로탐색\n후기·참석 확인","맞춤법·급여 도구\n추천·면접 제안","광고 상품·주문\n홈 프로모션 관리","테스트 결제·웹훅 검증\n소재 심사·광고 게시","권한·환불·통합 흐름\n격리 테스트·문서 정리",null,null]];
plan.getRange("B12:H12").values=[["오류 수정\n발표자료·산출물 정리","요구사항·테이블·WBS\n최종 문서 확인","발표 리허설\n시연 경로 확인","최종 발표\n산출물 제출",null,null,null]];
plan.getRange("B2:H2").format.wrapText=true;
plan.getRange("B6:H12").format.wrapText=true;

// 4. Database specification: append the 4 advertising tables from ads-schema.sql only.
const tables=sheet("테이블 명세서");
tables.getRange("B5").values=[["명세 범위 53개 테이블 · 414개 컬럼 · schema.sql 및 기능별 DDL 기준(광고는 ads-schema.sql 포함)"]];
tables.getRange("B4").values=[["v1.3 · 광고 주문/결제 테이블 추가 · 기능 DDL 기준 · 2026-11-10"]];
const ads=[
 {name:"ad_products",src:"resources/db/ads-schema.sql",rows:[
 ["product_code","VARCHAR(20)","NOT NULL","PK",null,"광고 상품 코드"],["name","VARCHAR(80)","NOT NULL",null,null,"상품명"],["description","VARCHAR(300)","NOT NULL",null,null,"상품 설명"],["price","INT","NOT NULL",null,null,"공급가액"],["duration_days","INT","NOT NULL",null,null,"노출 기간(일)"],["max_slots","INT","NOT NULL",null,null,"동시 노출 수량 제한"],["is_active","BOOLEAN","NOT NULL",null,"TRUE","판매 여부"],["version","INT","NOT NULL",null,"0","동시 수정 버전"],["updated_at","TIMESTAMP","NOT NULL",null,"CURRENT_TIMESTAMP","수정 시각"]]},
 {name:"ad_orders",src:"resources/db/ads-schema.sql",rows:[
 ["order_id","VARCHAR(50)","NOT NULL","PK",null,"주문 ID"],["company_id","BIGINT","NOT NULL","FK companies.company_id",null,"주문 기업"],["product_code","VARCHAR(20)","NOT NULL","FK ad_products.product_code",null,"상품 코드"],["product_name","VARCHAR(80)","NOT NULL",null,null,"주문 당시 상품명"],["price","INT","NOT NULL",null,null,"공급가액"],["vat","INT","NOT NULL",null,null,"부가세"],["total_amount","INT","NOT NULL",null,null,"결제 합계"],["duration_days","INT","NOT NULL",null,null,"노출 기간(일)"],["start_date","DATE","NOT NULL",null,null,"노출 시작일"],["end_date","DATE","NOT NULL",null,null,"노출 종료일"],["job_id","BIGINT","NULL 허용",null,null,"연결 공고(선택)"],["link_type","VARCHAR(20)","NOT NULL",null,null,"광고 링크 유형"],["link_url","VARCHAR(500)","NOT NULL",null,null,"광고 링크"],["image_data","MEDIUMBLOB","NULL 허용",null,null,"광고 소재 이미지"],["image_type","VARCHAR(30)","NULL 허용",null,null,"이미지 MIME 유형"],["payment_status","VARCHAR(30)","NOT NULL",null,"READY","결제 상태"],["review_status","VARCHAR(30)","NOT NULL",null,"AWAITING_PAYMENT","소재 심사·노출 상태"],["refund_status","VARCHAR(20)","NOT NULL",null,"NONE","환불 상태"],["refund_reason","VARCHAR(500)","NULL 허용",null,null,"환불 사유"],["review_memo","VARCHAR(1000)","NULL 허용",null,null,"심사 메모"],["paid_at","TIMESTAMP","NULL 허용",null,null,"결제 완료 시각"],["reviewed_at","TIMESTAMP","NULL 허용",null,null,"심사 시각"],["reviewed_by","BIGINT","NULL 허용",null,null,"심사 관리자"],["created_at","TIMESTAMP","NOT NULL",null,"CURRENT_TIMESTAMP","생성 시각"],["updated_at","TIMESTAMP","NOT NULL",null,"CURRENT_TIMESTAMP","수정 시각"]]},
 {name:"ad_feature_upgrades",src:"resources/db/ads-schema.sql",rows:[["upgrade_key","VARCHAR(80)","NOT NULL","PK",null,"기능 DDL 적용 키"],["applied_at","TIMESTAMP","NOT NULL",null,"CURRENT_TIMESTAMP","기록 시각"]]},
 {name:"ad_order_simulations",src:"resources/db/ads-schema.sql",rows:[["order_id","VARCHAR(50)","NOT NULL","PK, FK ad_orders.order_id",null,"격리 결제 모의 주문"],["created_at","TIMESTAMP","NOT NULL",null,"CURRENT_TIMESTAMP","생성 시각"]]}
];
let tr=580;
for(const t of ads){
 tables.getRange(`A${tr}:G${tr}`).copyFrom(tables.getRange("A572:G572"),"all");
 tables.getRange(`A${tr}:G${tr}`).clear({applyTo:"contents"});
 tables.getRange(`A${tr}`).values=[[`${t.name} 테이블명세서 · 유료 광고`]];
 tables.getRange(`A${tr}:G${tr}`).merge();
 tr++;
 tables.getRange(`A${tr}:G${tr}`).copyFrom(tables.getRange("A573:G573"),"all");
 tables.getRange(`A${tr}:G${tr}`).clear({applyTo:"contents"});
 tables.getRange(`A${tr}`).values=[[`출처 ${t.src}`]];
 tables.getRange(`A${tr}:G${tr}`).merge();
 tr++;
 tables.getRange(`A${tr}:G${tr}`).copyFrom(tables.getRange("A574:G574"),"all");
 tables.getRange(`A${tr}:G${tr}`).values=[["테이블명","컬럼명","데이터타입","NULL 허용","제약조건","기본값","코멘트"]];
 tr++;
 for(const row of t.rows){tables.getRange(`A${tr}:G${tr}`).copyFrom(tables.getRange("A575:G575"),"all");tables.getRange(`A${tr}:G${tr}`).values=[[t.name,...row]];tr++;}
 tr++;
}

// 5. Use cases: add only user-facing flows absent from the source sheet.
const uc=sheet("유스케이스 명세서");
const addUc=[
 ["G05","비회원","홈 프로모션·광고 영역 조회","없음","홈 진입 → 공개 기간의 프로모션과 현재 노출 가능한 광고 확인","공개 상태·기간·결제·심사가 유효한 항목만 표시. 실제 조회/클릭 성과 계측은 미포함"],
 ["J17","구직자","맞춤법 검사·교정","취업도구 화면 진입","문장 입력 → 검사 → 제안 교정 선택 → 교정문 복사 또는 편집","규칙 기반 결과로 외부 문서 저장·자동 전송은 수행하지 않음"],
 ["J18","구직자","급여 실수령액 계산","취업도구 화면 진입","연봉/월급·비과세액·부양가족 수 입력 → 공제 항목 및 예상 실수령액 조회","코드에 명시된 2026년 계산 기준의 추정값이며 실제 급여명세액과 다를 수 있음"],
 ["J19","비회원·구직자","카카오 로그인","카카오 앱 키·허용 redirect URI 설정","카카오 인증 → callback → 서비스 세션 생성 → 홈 복귀","앱 키·도메인·redirect URI가 맞지 않으면 연동 실패. 비밀 키는 문서에 기재하지 않음"]
];
uc.getRange("A59:F62").copyFrom(uc.getRange("A58:F58"),"all");
uc.getRange("A59:F62").values=addUc;
uc.getRange("A59:F62").format.wrapText=true;
uc.getRange("A59:F62").format.rowHeight=48;

// 6. Project specification: explain delivered modules and their source/API/data locations.
const ps=sheet("프로젝트 명세서");
ps.getRange("A3").values=[["주요 기능 | 회원·카카오 인증, 채용/기업 탐색, 이력서·첨부·지원·면접, 진로검사·진로탐색, 맞춤법·급여 도구, 후기·신고·참석 확인, 취업연계 제안·온보딩, 유료광고(테스트 PG), 홈 프로모션, 관리자 운영·알림"]];
ps.getRange("A54:E54").merge();
ps.getRange("A54").values=[["추가 구현 기능 · 현재 저장소 코드 기준"]];
ps.getRange("A54:E54").format.fill="#244987";
ps.getRange("A54:E54").format.font={name:"맑은 고딕",size:10,bold:true,color:"#FFFFFF"};
const projectRows=[
 ["이석현","진로검사·진로탐색·취업도구","CareerAssessmentController / CareerExplorationController / CareerTools","/career-test · /career-exploration · /tools/spelling · /tools/salary","career_assessment_attempts · career_exploration_attempts"],
 ["정영록","면접후기·참석확인·신고","InterviewReviewController / InterviewReviews","공개 후기 · 참석 요청/증빙 · 신고 · 관리자 중재","interview_reviews · member_reports · moderation tables"],
 ["김경우","추천·면접 제안·온보딩","EmploymentReferralController / EmploymentReferrals / EmploymentOnboarding","관리자 배정 · 기업 제안 · 구직자 응답 · 입사 안내","employment_referrals · employment_interview_proposals · employment_onboarding"],
 ["정영록","유료 광고·테스트 결제","AdvertisingController / AdvertisingService","상품 · 주문 · 결제 확인 · webhook · 심사 · 환불","ad_products · ad_orders · ad_feature_upgrades · ad_order_simulations"],
 ["서상훈","기업 광고·관리자 광고 화면","CompanyAds / Admin advertising pages","주문 현황 · 소재 등록 · 심사·노출 상태","운영 PG 실결제 미설정 · 조회/클릭 계측 별도"],
 ["김경우","홈 프로모션 API","HomePromotionController / HomePromotionService","관리자 CRUD · 공개 조회 · 이미지","home_promotions · home_promotion_deletions"],
 ["서상훈","홈 프로모션·면접 후기 화면","Public / Admin / Company pages","공개 기간 · 사용자/관리자 화면","home_promotions · interview_reviews"],
 ["전진영","첨부파일·소유권 API","ResumeExtrasController","이력서·지원서 첨부 조회","resume_files · application_snapshots"],
 ["이석현","카카오 소셜 로그인","Auth.jsx / App.jsx / AuthController","OAuth callback · 세션 생성","Kakao 앱 키·redirect URI 설정 필요"]
];
ps.getRange("A55:E55").copyFrom(ps.getRange("A47:E47"),"all");
ps.getRange("A55:E55").values=[["담당자","추가 구현 기능","주요 백엔드·프론트 모듈","화면·API 범위","데이터·환경 참고"]];
ps.getRange("A56:E64").copyFrom(ps.getRange("A52:E52"),"all");
ps.getRange("A56:E64").values=projectRows;
ps.getRange("A55:E64").format.wrapText=true;
ps.getRange("A56:E64").format.rowHeight=48;

// 7. Bring the main integration-test sheet to the reference sheet's exact 13-column layout.
// Keep the reference tab untouched; do not report unexecuted scenarios as passing tests.
const ref=sheet("통합테스트 (1)"), test=sheet("통합테스트");
test.getRange("A1:U80").unmerge();
test.getRange("A1:U80").clear({applyTo:"all"});
test.getRange("A1:M41").copyFrom(ref.getRange("A1:M41"),"all");
for(let r=1;r<=4;r++)test.getRange(`A${r}:M${r}`).merge();
test.getRange("A2").values=[["기준: WBS 기능 일정·요구사항 및 현재 구현 현황. 아래 날짜는 기능별 일정에 배정한 검증 계획일이며, 실제 테스트 실행 일자가 아닙니다."]];
test.getRange("A3").values=[["‘구현 상태’는 문서 기준 기능 상태이며, 실제 실행 결과는 ‘실행 기록 상태’에 별도로 표시합니다. 미실행 시나리오는 통과로 표시하지 않았습니다."]];
test.getRange("L7").values=[["검증 계획일"]];
test.getRange("L8:L41").values=Array.from({length:34},(_,i)=>[serial(2026,10,20)+Math.floor(i*21/33)]);
test.getRange("L8:L41").setNumberFormat("yyyy-mm-dd");
const addedTests=[
 [35,"IT-035","진로·적성","SFR-015-01","적성검사 응시·결과·이력","구직자 계정; 격리 환경","문항 조회 → 시작 → 답안 저장 → 제출 → 결과/이력 조회","본인 결과가 저장되고 타 계정 응시 이력은 조회되지 않는다.","구현 경로 확인용 시나리오; 이 문서 편집 중 미실행.","완료","미실행·케이스 정리",serial(2026,11,1),"실제 점수 타당도 검증을 뜻하지 않음."],
 [36,"IT-036","진로탐색","SFR-015-02","진로탐색 임시 저장·제출·이력","구직자 계정; 격리 환경","탐색 시작 → 일부 답변 저장 → 재조회 → 제출","저장 답안이 복원되고 제출 결과가 본인 이력에 남는다.","구현 경로 확인용 시나리오; 이 문서 편집 중 미실행.","완료","미실행·케이스 정리",serial(2026,11,2),""],
 [37,"IT-037","취업도구","SFR-016-01","맞춤법 검사와 선택 교정","검사/정상/빈 문장 예시","문장 검사 → 결과 확인 → 일부 교정 적용","교정 결과가 원문 위치에 적용되고 미선택 부분은 유지된다.","구현 경로 확인용 시나리오; 이 문서 편집 중 미실행.","완료","미실행·케이스 정리",serial(2026,11,2),"규칙 기반 처리 범위."],
 [38,"IT-038","취업도구","SFR-016-02","급여 계산 입력 경계와 결과","연봉/월급·비과세·가족 입력 예시","각 급여 유형 계산 → 음수/범위 초과/비정상 입력 확인","유효 입력에 계산 결과를 제공하고 경계 밖 값은 거부한다.","구현 경로 및 저장소 테스트 정의 확인; 이번 작업 미실행.","완료","미실행·케이스 정리",serial(2026,11,3),"예상치이며 실제 급여 명세 대체 아님."],
 [39,"IT-039","면접후기","SFR-017-01","후기 작성·중복·관리자 승인 노출","완료 면접·구직자·관리자 계정","후기 제출 → 중복 제출 → 관리자 승인/반려 → 공개 기업 조회","면접별 중복은 제한되고 승인된 후기에 한해 공개된다.","구현 경로 확인용 시나리오; 이 문서 편집 중 미실행.","완료","미실행·케이스 정리",serial(2026,11,3),""],
 [40,"IT-040","참석확인","SFR-017-02","참석 확인·증빙·기업 응답·이의 제기","경과된 면접; 구직자·기업·관리자","확인 요청/증빙 → 기업 응답 → 거절 또는 만료 → 관리자 이의 처리","권한별 상태 전이와 증빙 접근이 적용된다.","구현 경로 확인용 시나리오; 이 문서 편집 중 미실행.","완료","미실행·케이스 정리",serial(2026,11,4),"실제 파일 업로드는 수행하지 않음."],
 [41,"IT-041","후기 신고","SFR-017-03","기업 후기 신고·관리자 처리","공개 후기·기업·관리자 계정","신고 제출 → 중복 신고 확인 → 관리자 결과/사유 저장","중복 신고가 제한되고 신고자·관리자 권한 및 처리 이력이 적용된다.","구현 경로 확인용 시나리오; 이 문서 편집 중 미실행.","완료","미실행·케이스 정리",serial(2026,11,4),""],
 [42,"IT-042","취업연계","SFR-018-01","관리자 추천 배정·재매칭·취소","진행 중인 신청; 관리자·기업·구직자 계정","배정 → 전달 → 재매칭 → 취소 조건 확인","추천 이력이 유지되고 완료 건 취소 등 비허용 전이는 거절된다.","구현 경로 확인용 시나리오; 이 문서 편집 중 미실행.","완료","미실행·케이스 정리",serial(2026,11,5),"실제 참여자에게 추천하지 않음."],
 [43,"IT-043","취업연계","SFR-018-02","면접 제안·구직자 응답·재제안","전달된 취업연계 추천","기업 제안 → 구직자 수락/변경/불가 → 재제안","최신 제안만 응답 대상으로 처리되고 이전 제안은 이력으로 보존된다.","구현 경로 확인용 시나리오; 이 문서 편집 중 미실행.","완료","미실행·케이스 정리",serial(2026,11,6),""],
 [44,"IT-044","취업연계","SFR-018-03","입사 안내 확인·출근/미출근 처리","유효한 최신 추천·입사 안내","기업 안내 등록 → 구직자 확인 → 출근 또는 미출근 사유 저장","참여 결과가 최신 추천에 연결되고 미출근 시 취소/재매칭 흐름이 반영된다.","구현 경로 확인용 시나리오; 이 문서 편집 중 미실행.","완료","미실행·케이스 정리",serial(2026,11,6),""],
 [45,"IT-045","유료광고","SFR-019-01","상품·가용 기간·주문 생성","승인 기업·광고 상품·격리 주문 데이터","상품 조회 → 공고/기간 선택 → 가용 수량 확인 → 주문 생성","기간 전체의 수량·공고 마감일을 검증하고 주문 금액을 저장한다.","광고 코드/기존 테스트 흐름 기반; 이번 작업 미실행.","부분 완료","미실행·격리 환경 필요",serial(2026,11,7),"공유 DB 및 실 주문 생성 금지."],
 [46,"IT-046","유료광고","SFR-019-02","심사 전 결제 차단·테스트 결제 확인·webhook 재검증","테스트 키 또는 격리 모의 게이트웨이","주문 승인 전 checkout → 승인 후 결제 확인 → 변조 상태 포함 webhook 전달","승인 전 결제 차단; 서버 재조회 금액과 결제 식별자가 일치할 때만 결제 상태를 변경한다.","코드/테스트 사례 참조; 실제 PG 결제는 수행하지 않음.","부분 완료","미실행·실결제 금지",serial(2026,11,8),"운영 키·실제 결제 미사용."],
 [47,"IT-047","유료광고","SFR-019-03","소재 심사·환불·공개 노출 조건","결제된 광고 주문; 관리자·기업 테스트 계정","소재 등록 → 관리자 승인/반려 → 환불 요청/처리 → 공개 위치 조회","결제·심사·노출 기간·환불 상태가 모두 유효한 경우에만 노출한다.","코드/테스트 사례 참조; 이번 작업 미실행.","부분 완료","미실행·격리 환경 필요",serial(2026,11,9),"조회/클릭 성과 계측 여부를 이 케이스에서 주장하지 않음."],
 [48,"IT-048","홈 프로모션","SFR-020-01","프로모션 기간·이미지·관리자 권한","관리자와 비관리자 계정; 격리 이미지","등록/수정/삭제 → 기간 전·중·후 공개 목록 확인 → 비관리자 쓰기 시도","관리자만 변경 가능하고 공개 기간·이미지·링크가 일관되게 적용된다.","구현 경로 확인용 시나리오; 이 문서 편집 중 미실행.","완료","미실행·격리 환경 필요",serial(2026,11,9),""],
 [49,"IT-049","첨부파일","SFR-021-01","이력서·지원서 첨부 조회 권한","본인/타인 사용자 및 기업 계정; 격리 PDF","본인 첨부 조회 → 권한 밖 사용자/기업 조회 → 잘못된 파일 유형 요청","허용된 사용자에게만 파일이 제공되고 비허용 접근은 차단된다.","구현 경로 확인용 시나리오; 이번 작업 미실행.","완료","미실행·격리 파일 필요",serial(2026,11,10),"실제 파일 업로드·다운로드를 수행하지 않음."],
 [50,"IT-050","소셜 로그인","SFR-001-12","카카오 OAuth callback·환경 설정","격리 계정과 허용된 테스트 redirect URI","카카오 동의 → callback → 세션 생성 → 올바른 프런트 주소 복귀","등록된 키/redirect URI 환경에서 계정 세션이 연결된다.","구현 경로 확인용 시나리오; 외부 계정 인증은 이번 작업 미실행.","부분 완료","미실행·환경 설정 필요",serial(2026,11,10),"자격증명이나 개인정보를 문서에 기록하지 않음."]
];
const autoRecord=test.getRange("A41:M41").values[0];
const addedCases=addedTests.map((row,i)=>[34+i,"IT-"+String(34+i).padStart(3,"0"),...row.slice(2)]);
test.getRange("A41:M56").copyFrom(test.getRange("A40:M40"),"all");
test.getRange("A41:M56").values=addedCases;
test.getRange("A57:M57").copyFrom(test.getRange("A41:M41"),"all");
test.getRange("A57:M57").values=[["기록",autoRecord[1],...autoRecord.slice(2)]];
test.getRange("A41:M57").format.wrapText=true;
test.getRange("A41:M57").format.rowHeight=72;
test.getRange("L41:L56").setNumberFormat("yyyy-mm-dd");
// Ensure the layout ends at M and use the reference tab's column proportions.
test.getRange("N1:U80").clear({applyTo:"all"});
const widths=[5,12,13,24,29,26,29,30,31,13,20,14,28];
for(let i=0;i<widths.length;i++)test.getRangeByIndexes(0,i,1,1).format.columnWidth=widths[i];
test.freezePanes.freezeRows(7);

// Cross-sheet and safety checks before export.
assert(JSON.stringify(sheet("통합테스트 (1)").getRange("A1:M41").values)===beforeTest1,"Reference test sheet was modified.");
assert(JSON.stringify(tables.getRange("A1:G579").values).split('ACTIVE').length===oldTableRows.split('ACTIVE').length,"Existing table-spec text changed unexpectedly.");
assert(!JSON.stringify(test.getRange("A1:M57").values).toLowerCase().includes("flyway"),"Flyway reference remains in the main integration-test sheet.");
assert(req.getRange("C73:C88").values.flat().length===16,"Requirement additions missing.");
assert(test.getRange("B41:B56").values.flat().filter(Boolean).length===16,"Integration scenarios missing.");
assert(wbs.getRange("D109:D125").values.flat().length===17,"WBS additions missing.");

const file=await SpreadsheetFile.exportXlsx(wb); await file.save(out);
const saved=await SpreadsheetFile.importXlsx(await FileBlob.load(out));
assert(saved.worksheets.items.length===10,"Output sheet count changed.");
assert(JSON.stringify(saved.worksheets.getItem("통합테스트 (1)").getRange("A1:M41").values)===beforeTest1,"Reference test sheet changed after export.");
assert(!JSON.stringify(saved.worksheets.getItem("통합테스트").getRange("A1:M57").values).toLowerCase().includes("flyway"),"Flyway remains after export.");
assert(saved.worksheets.getItem("요구사항 정의서").getRange("C73:C88").values.flat().filter(Boolean).length===16,"Requirements did not persist.");
assert(saved.worksheets.getItem("테이블 명세서").getRange("A580:G650").values.flat().some(v=>v==="ad_orders"),"Ad table specification did not persist.");
console.log(JSON.stringify({out,sheets:saved.worksheets.items.map(x=>x.name),requirementsAdded:16,wbsAdded:17,adTablesAdded:4,useCasesAdded:4,integrationScenarios:49,historicalAutoRecordPreserved:true,referenceTestSheetPreserved:true,flywayInMainTest:false,realPaymentPerformed:false}));

