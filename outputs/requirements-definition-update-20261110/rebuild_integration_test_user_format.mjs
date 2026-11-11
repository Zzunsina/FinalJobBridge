import fs from "node:fs/promises";
import {FileBlob,SpreadsheetFile} from "file:///C:/Program%20Files/WindowsApps/OpenAI.CodexPrimaryRuntime.v26-1104-2876-0_26.1104.2876.0_x64__3k8sg7r9htsxt/dependencies/node/node_modules/@oai/artifact-tool/dist/artifact_tool.mjs";

const templatePath="C:/Users/hi101/Desktop/제목 없는 스프레드시트.xlsx";
const currentCasesPath="C:/FinalJobBridge/outputs/requirements-definition-update-20261110/잡브릿지_통합테스트_단독시트.xlsx";
const output="C:/FinalJobBridge/outputs/requirements-definition-update-20261110/잡브릿지_통합테스트_수정본_최종.xlsx";
if(await fs.stat(output).then(()=>true).catch(()=>false))throw new Error("출력 파일이 이미 있어 덮어쓰지 않습니다.");

const wb=await SpreadsheetFile.importXlsx(await FileBlob.load(templatePath));
const styleWb=await SpreadsheetFile.importXlsx(await FileBlob.load(templatePath));
const currentWb=await SpreadsheetFile.importXlsx(await FileBlob.load(currentCasesPath));
const sheet=wb.worksheets.getItem("통합테스트");
const style=styleWb.worksheets.getItem("통합테스트");
const current=currentWb.worksheets.getItem("통합테스트");
const currentRows=current.getRange("A8:M41").values;
const manual=currentRows.slice(0,33);
const priorAuto=currentRows[33];
if(manual.length!==33||manual.some((r,i)=>r[1]!==`IT-${String(i+1).padStart(3,"0")}`)||priorAuto[1]!=="AUTO-043")throw new Error("기존 통합 시나리오나 테스트 기록을 확인하지 못했습니다.");

const additions=[
 {area:"인증·카카오",req:"SFR-001-12",title:"카카오 로그인 callback·세션 생성",pre:"카카오 테스트 앱과 격리된 개발 redirect URI; 로그인하지 않은 테스트 계정",steps:"카카오 인증 시작 → callback 반환 → 서비스 로그인 상태 및 로그아웃 확인",expected:"허용된 callback에서만 서비스 세션이 생성되고, callback 실패 시 로그인 상태가 만들어지지 않는다.",evidence:"Auth.jsx; App.jsx; AuthController.java; 카카오 키·redirect URI는 환경별 별도 설정",priority:"P1",impact:"높음 · 인증/세션",type:"외부 인증 통합"},
 {area:"진로·적성",req:"SFR-015-01",title:"인적성 검사 답안 저장·제출·결과 이력",pre:"격리 H2의 구직자 계정과 테스트 문항",steps:"검사 시작 → 일부 답안 저장 → 회차 재조회 → 제출 → 결과·이력 확인",expected:"저장 답안이 같은 회차에서 복원되고 제출 결과가 본인 이력에 연결된다.",evidence:"CareerAssessment.jsx; CareerAssessmentController.java; CareerAssessments.java",priority:"P2",impact:"보통 · 결과 데이터",type:"기능 통합"},
 {area:"진로탐색",req:"SFR-015-02",title:"진로탐색 임시 저장·제출·이력 분리",pre:"격리 H2의 구직자 계정과 진로탐색 회차",steps:"탐색 시작 → 답안 일부 저장 → 다시 조회 → 제출 → 새 회차 시작",expected:"저장 답안은 기존 회차에 유지되고 제출 결과와 새 회차가 혼합되지 않는다.",evidence:"CareerExploration.jsx; CareerExplorationController.java; CareerExplorations.java",priority:"P2",impact:"보통 · 결과 데이터",type:"기능 통합"},
 {area:"취업도구",req:"SFR-016-01, SFR-016-02",title:"맞춤법 교정과 급여 실수령액 계산 입력 경계",pre:"취업도구 화면; 정상 입력·빈 문장·범위 밖 급여 입력 예시",steps:"문장 검사 → 일부 교정 적용 → 급여 유형·비과세액·부양가족 입력 → 경계값 확인",expected:"교정 제안이 선택 적용되고 유효한 급여 입력에만 가정이 명시된 예상 결과를 제공한다.",evidence:"CareerTools.jsx; koreanSpellChecker.js; salaryCalculator.js; 외부 전문 사전·세무 확정값 아님",priority:"P2",impact:"보통 · 사용자 계산",type:"입력 경계"},
 {area:"면접후기",req:"SFR-017-01, SFR-017-03",title:"면접 후기 작성·수정·관리자 심사·공개",pre:"격리 구직자·기업·관리자 계정과 테스트 면접",steps:"후기 작성·수정 → 관리자 승인/반려 → 기업 공개 후기와 신고 목록 확인",expected:"승인된 후기만 공개되고 작성자·기업·관리자의 권한과 처리 상태가 구분된다.",evidence:"ReviewFeatures.jsx; InterviewReviewController.java; InterviewReviews.java; 후기·신고 API",priority:"P1",impact:"높음 · 공개/중재",type:"권한·상태 전이"},
 {area:"면접 참석확인",req:"SFR-017-02",title:"참석 확인 증빙·기업 응답·관리자 이의 처리",pre:"격리 면접·테스트 파일·구직자·기업·관리자 계정",steps:"면접 참석 확인 요청·증빙 → 기업 응답 → 거절 또는 기한 경과 시 이의 제기 → 관리자 처리",expected:"요청 상태가 정의된 순서로 전이되고 증빙은 권한 있는 주체만 조회한다.",evidence:"InterviewReviewController.java; InterviewReviews.java; 증빙은 실제 사용자 파일 미사용",priority:"P1",impact:"높음 · 증빙/권한",type:"권한·상태 전이"},
 {area:"취업연계",req:"SFR-018-01, SFR-018-02, SFR-018-03",title:"추천 재배정·면접 제안·구직자 응답·온보딩",pre:"격리 추천 건과 관리자·기업·구직자 테스트 계정",steps:"관리자 추천 배정·재매칭 → 기업 면접 제안 → 구직자 응답 → 입사 안내·참여 결과 저장",expected:"추천·제안·온보딩 상태가 동일한 연계 건에 연결되고 권한별 허용 전이만 처리된다.",evidence:"EmploymentReferralController.java; EmploymentReferrals.java; EmploymentInterviewProposals.java; EmploymentOnboarding.java",priority:"P1",impact:"높음 · 연계 상태",type:"다중 역할 통합"},
 {area:"홈 프로모션",req:"SFR-020-01",title:"프로모션 등록·이미지·공개 기간·권한",pre:"격리 관리자 계정·테스트 이미지·공개 기간 전/중/후 조건",steps:"프로모션 등록·수정·삭제 → 기간별 공개 목록 확인 → 비관리자 변경 요청",expected:"관리자만 수정 가능하고 공개 상태·기간 조건을 만족하는 항목만 사용자 화면에 표시된다.",evidence:"AdminHomePromotions.jsx; HomePromotionController.java; HomePromotionService.java",priority:"P2",impact:"보통 · 공개 콘텐츠",type:"권한·기간 경계"},
 {area:"첨부파일",req:"SFR-021-01",title:"이력서·지원서 첨부 조회와 소유권 검증",pre:"격리 PDF 첨부가 연결된 이력서·지원서와 소유자/비소유자 계정",steps:"소유자·연결된 권한 계정·무관한 계정에서 이력서 및 지원서 첨부를 각각 조회",expected:"본인 또는 해당 지원을 열람할 권한이 있는 기업만 파일을 받고 다른 계정은 접근이 거부된다.",evidence:"ResumeExtrasController.java; ResumeFiles.java; resume_files; 운영 파일 미사용",priority:"P1",impact:"높음 · 개인정보/권한",type:"접근 제어"},
 {area:"광고",req:"요구사항 ID 미등록 · 유료광고 추가 기능",title:"광고 소재 심사·환불 상태와 공개 노출 조건",pre:"격리 광고 주문·모의 PG 응답·기업 및 관리자 계정",steps:"소재 제출 → 승인/반려 → 모의 환불 → 공개 광고 목록에서 노출 여부 확인",expected:"결제·심사·환불·노출 기간 조건이 유효한 주문만 노출되고 운영 실결제는 발생하지 않는다.",evidence:"Advertising.jsx; AdvertisingController.java; AdvertisingLifecycle.java; 실 PG 미사용",priority:"P1",impact:"높음 · 결제/노출",type:"상태 전이·모의 결제"}
];
if(additions.length!==10)throw new Error("추가 시나리오는 10건이어야 합니다.");

const categoryId={"회원":1,"회원·구직자":1,"회원·SMS":1,"회원·비밀번호":1,"채용":2,"채용·지원":2,"기업":3,"기업 홈페이지":3,"기업·채용":3,"관리자·채용":4,"관리자":4,"이력서":5,"이력서 파일":5,"입사지원":6,"기업·지원자":6,"면접":7,"알림":8,"취업연계":9,"고객센터":10,"보안·오류":11,"공통 화면":12,"미디어":13,"개발 시드":14,"통합 흐름":15,"광고 추가 기능":16,"인증·카카오":17,"진로·적성":18,"진로탐색":19,"취업도구":20,"면접후기":21,"면접 참석확인":22,"홈 프로모션":23,"첨부파일":24,"광고":25};
const highRisk=new Set(["회원·비밀번호","보안·오류","이력서 파일","입사지원","인증·카카오","면접후기","면접 참석확인","취업연계","첨부파일","광고"]);
const data=[];
for(let i=0;i<manual.length;i++){
  const r=manual[i],area=String(r[2]);
  const group=categoryId[area]??30;
  const p=highRisk.has(area)?"P1":area==="개발 시드"?"P3":"P2";
  const impact=p==="P1"?"높음 · 권한/데이터":p==="P3"?"낮음 · 개발 환경":"보통 · 기능 흐름";
  data.push([r[1],r[4],group,r[3],p,impact,"통합 시나리오",`사전조건: ${r[5]}\n절차: ${r[6]}`,"미실행 — 이번 문서 수정 중 재실행하지 않음",`격리 테스트 환경 · ${area==="개발 시드"?"H2 전용":area.includes("광고")?"PG 모의·실결제 금지":"H2 또는 테스트 계정"}`,"미실행 (오류 메시지 미기록)",r[7],`현재 구현·근거: ${r[12]}\n기능 상태: ${r[9]}`,null]);
}
for(let i=0;i<additions.length;i++){
  const t=additions[i];
  data.push([`IT-${String(i+34).padStart(3,"0")}`,t.title,categoryId[t.area],t.req,t.priority,t.impact,t.type,`사전조건: ${t.pre}\n절차: ${t.steps}`,"미실행 — 이번 문서 수정 중 재실행하지 않음",`격리 테스트 환경 · H2 · ${t.area==="광고"?"PG 모의·실결제 금지":"테스트 계정"}`,"미실행 (오류 메시지 미기록)",t.expected,`현재 구현 근거: ${t.evidence}`,null]);
}
const auto={id:priorAuto[1],name:priorAuto[4],group:99,req:priorAuto[3]};
data.push([auto.id,auto.name,auto.group,auto.req,"P1","높음 · 전체 백엔드","기존 자동 테스트 기록","기존 프로젝트의 전체 백엔드 테스트 결과 확인. 이번 문서 수정 중 테스트 명령은 재실행하지 않음.","기존 확인 기록: 백엔드 테스트 43개 통과. 이번 작업에서는 재실행하지 않음.","이전 테스트 환경 (세부 설정은 기존 실행 기록 기준)","이번 작업 중 새 오류 메시지 없음","기존 기록의 43개 통과 결과를 보존한다.","기존 프로젝트 확인 기록; 실행일은 기존 기록에 기재되지 않음",null]);
if(data.length!==44||data.filter(r=>r[0].startsWith("IT-")).length!==43)throw new Error(`시나리오 수가 맞지 않습니다: ${data.length}`);

// Correct the owner block and preserve each person's original color.
sheet.getRange("B1").copyFrom(style.getRange("B2"),"all");
sheet.getRange("C1").copyFrom(style.getRange("C1"),"all");
sheet.getRange("B2").copyFrom(style.getRange("B1"),"all");
sheet.getRange("C2").copyFrom(style.getRange("C2"),"all");
sheet.getRange("B3").copyFrom(style.getRange("B3"),"all");
sheet.getRange("B1:C3").values=[["서상훈","이석현"],["전진영","정영록"],["김경우",null]];
sheet.getRange("A4:O4").unmerge();
sheet.getRange("A4:O4").merge();
sheet.getRange("A4").values=[["담당 구분: 프론트엔드 서상훈·이석현 / 백엔드 전진영·정영록·김경우 | 수동 시나리오 43건 미실행 · 백엔드 테스트 43개 통과는 이전 기록 · 개발 데이터는 격리 H2 전용"]];
sheet.getRange("A4:O4").format.wrapText=true;
sheet.getRange("A4:O4").format.rowHeight=24;
sheet.getRange("A4:O4").format.fill="#E8F0FB";
sheet.getRange("A4:O4").format.font={size:9,color:"#49607D"};

// Retain the supplied two-level header design while renaming error-report fields for integration tests.
sheet.getRange("B5").values=[["테스트 ID"]];
sheet.getRange("C5").values=[["테스트 시나리오"]];
sheet.getRange("D5").values=[["기능 그룹 ID"]];
sheet.getRange("E5").values=[["요구사항 ID"]];
sheet.getRange("F5").values=[["분류"]];
sheet.getRange("I5").values=[["테스트 절차"]];
sheet.getRange("J5").values=[["실행 결과"]];
sheet.getRange("K5").values=[["검증 정보"]];
sheet.getRange("F6:H6").values=[["우선순위","영향도","테스트 유형"]];
sheet.getRange("K6:O6").values=[["테스트 환경","오류·특이사항","기대 결과","근거·기록 상태","실행일자"]];

sheet.getRange("A7:O90").clear({applyTo:"contents"});
const widths=[2,12,44,14,20,10,20,20,58,48,40,38,52,42,14];
for(let i=0;i<data.length;i++){
  const row=7+i;
  const styleRow=17+(i%2);
  sheet.getRange(`A${row}:O${row}`).copyFrom(style.getRange(`A${styleRow}:O${styleRow}`),"all");
  sheet.getRange(`A${row}:O${row}`).values=[[null,...data[i]]];
  sheet.getRange(`A${row}:O${row}`).format.wrapText=true;
  const cells=[null,...data[i]];
  const lineCounts=cells.map((v,col)=>String(v??"").split("\n").reduce((sum,line)=>sum+Math.max(1,Math.ceil([...line].length/widths[col])),0));
  sheet.getRange(`A${row}:O${row}`).format.rowHeight=Math.min(132,Math.max(38,Math.max(...lineCounts)*12+8));
}
sheet.getRange("O7:O50").setNumberFormat("yyyy-mm-dd");
sheet.getRange("E1:E50").format.columnWidth=22;
sheet.freezePanes.unfreeze();
wb.recalculate();
if(wb.worksheets.items.length!==1||sheet.name!=="통합테스트")throw new Error("통합테스트 단독 시트가 아닙니다.");
const xlsx=await SpreadsheetFile.exportXlsx(wb);await xlsx.save(output);

const saved=await SpreadsheetFile.importXlsx(await FileBlob.load(output));
const final=saved.worksheets.getItem("통합테스트");
const check=final.getRange("A7:O50").values;
if(check.length!==44||check.some(r=>!r[1]))throw new Error("저장 후 테스트 시나리오가 누락되었습니다.");
if(check.some(r=>JSON.stringify(r).toLowerCase().includes("flyway")))throw new Error("Flyway 내용이 남았습니다.");
if(check.filter(r=>String(r[1]).startsWith("IT-")).length!==43||check.at(-1)[1]!=="AUTO-043")throw new Error("ID 개수/자동 테스트 기록 확인 실패");
if(final.getRange("B1:C3").values.map(r=>r.filter(Boolean).join("/")).join("|")!=="서상훈/이석현|전진영/정영록|김경우")throw new Error("담당자 배치 검증 실패");
const preview=await saved.render({sheetName:"통합테스트",range:"A1:O50",scale:1,format:"png"});
await fs.writeFile("C:/FinalJobBridge/outputs/requirements-definition-update-20261110/integration-user-format-preview-final3.png",new Uint8Array(await preview.arrayBuffer()));
console.log(JSON.stringify({output,sheetCount:saved.worksheets.items.length,manualScenarios:43,priorAutoTestRecordPreserved:true,flywayRemoved:true,teamRolesCorrected:true,freezePanesUnfrozen:true,manualCasesMarkedUnexecuted:true}));
