import fs from "node:fs/promises";
import {FileBlob,SpreadsheetFile} from "file:///C:/Program%20Files/WindowsApps/OpenAI.CodexPrimaryRuntime.v26-1104-2876-0_26.1104.2876.0_x64__3k8sg7r9htsxt/dependencies/node/node_modules/@oai/artifact-tool/dist/artifact_tool.mjs";

const source="C:/FinalJobBridge/outputs/trouble-sheet-design-20261110/잡브릿지_트러블슈팅_단독시트_디자인통일.xlsx";
const output="C:/FinalJobBridge/outputs/trouble-sheet-design-20261110/잡브릿지_트러블슈팅_단독시트_50건.xlsx";
if(await fs.stat(output).then(()=>true).catch(()=>false))throw new Error("출력 파일이 이미 있어 덮어쓰지 않습니다.");

const wb=await SpreadsheetFile.importXlsx(await FileBlob.load(source));
const sheet=wb.worksheets.getItem("트러블 슈팅");
const previous=sheet.getRange("A5:I44").values.map(row=>row.slice());
if(previous.length!==40||previous.some(row=>!row[0]))throw new Error("기존 40건을 확인하지 못했습니다.");

const status="예방 점검 · 실제 재현 미확인";
const added=[
 ["16-P01","예방 16","카카오 로그인 callback 실패 / redirect URI 불일치","격리된 로컬 설정에서 로그인 시작 URL과 카카오 개발자 설정의 허용 redirect URI를 대조한다.","미재현. 기대: URI가 맞으면 callback 후 서비스 세션이 생성되고, 불일치하면 인증을 완료하지 않고 오류 안내를 표시한다.","가능 원인: 개발·배포 도메인, scheme, callback 경로 또는 client 설정이 서로 다름.","테스트용 앱 설정과 브라우저 요청 URI를 비교하고 callback 응답 및 서버 로그의 오류 코드를 확인한다. 키·인가 코드는 문서나 로그에 남기지 않는다.",status,"Auth.jsx; AuthController.java; Kakao OAuth environment settings"],
 ["16-P02","예방 16","진로검사 답안 저장 누락 / 재진입 시 임시 답변 불일치","격리된 구직자 계정으로 검사 일부 답변을 저장하고 같은 검사 회차를 다시 연다.","미재현. 기대: 저장한 답변이 같은 회차에서 복원되고 제출 후 결과·이력이 해당 사용자에게 연결된다.","가능 원인: 저장 요청 미완료, 오래된 회차 ID 사용 또는 화면 상태와 서버 응답 간 불일치.","Network 탭에서 답안 저장 응답과 회차 ID를 확인하고 재진입·제출 후 본인 이력과 대조한다. DB를 직접 수정하지 않는다.",status,"CareerAssessment.jsx; CareerAssessmentController.java; CareerAssessments.java"],
 ["16-P03","예방 16","진로탐색 시간 만료 / 만료된 회차에 추가 답안 제출","격리 테스트에서 시작한 탐색 회차의 만료 상태를 확인한 뒤 답안 저장·제출 응답을 확인한다.","미재현. 기대: 만료 회차는 정의된 만료 응답으로 처리되고 새 회차를 시작할 수 있으며 기존 결과·이력은 혼동되지 않는다.","가능 원인: 만료 시각과 서버 기준 시각 차이, 재시도 요청 또는 만료 회차 상태를 화면에서 갱신하지 않음.","회차 상태와 서버 응답을 확인하고 새 탐색 회차로 재시도한다. 시스템 시각이나 공유 DB 데이터를 임의 변경하지 않는다.",status,"CareerExploration.jsx; CareerExplorationController.java; CareerExplorations.java"],
 ["17-P01","예방 17","작성한 면접 후기가 기업 상세에 보이지 않음 / 심사 상태 확인","격리 계정으로 후기를 작성한 뒤 본인 후기 목록, 관리자 심사 화면, 기업 공개 후기 목록을 각각 확인한다.","미재현. 기대: 작성 직후에는 공개 심사 상태가 보이고, 승인된 후기만 기업 공개 목록에 표시된다.","가능 원인: 관리자 승인 대기·반려 상태, 다른 기업을 조회하거나 캐시된 목록을 확인함.","후기 ID와 기업 ID, 관리자 처리 상태를 대조한다. 공개되지 않은 초안이나 미승인 후기를 직접 DB에서 노출 상태로 바꾸지 않는다.",status,"ReviewFeatures.jsx; InterviewReviewController.java; InterviewReviews.java"],
 ["17-P02","예방 17","면접 참석 증빙 조회 권한 오류 / 다른 계정에서 파일 요청","격리 테스트 파일로 참석 확인 증빙을 등록한 뒤 요청자·대상 기업·관리자와 무관한 계정별 조회 응답을 비교한다.","미재현. 기대: 권한이 있는 주체만 증빙을 조회하고 무관한 계정에는 접근이 거부된다.","가능 원인: 사용자·요청 ID 검증 누락, 만료 세션 또는 잘못된 역할로 증빙 URL을 요청함.","각 계정의 응답 코드와 요청 소유자를 확인하고 접근 거부가 유지되는지 본다. 실제 사용자 증빙 파일은 사용하지 않는다.",status,"ReviewFeatures.jsx; InterviewReviewController.java evidence endpoint; InterviewReviews.java"],
 ["17-P03","예방 17","이전 면접 제안에 응답 / 최신 제안 상태 불일치","격리된 취업연계 건에서 기업이 면접 제안을 수정·재제안한 뒤 구직자가 이전 제안 ID와 최신 제안 ID에 각각 응답한다.","미재현. 기대: 현재 유효한 제안만 응답 대상으로 처리되고 이전 제안은 이력으로 남는다.","가능 원인: 오래 열린 화면, 이전 제안 ID 재사용 또는 추천 건과 제안 건을 혼동함.","추천 ID·제안 ID·상태 이력을 다시 조회한 뒤 최신 제안에서만 응답한다. 실제 구직자에게 제안을 발송하지 않는다.",status,"EmploymentReferralController.java; EmploymentInterviewProposals.java; EmploymentReferrals.java"],
 ["18-P01","예방 18","이력서·지원서 첨부파일 접근 거부 / 소유권 확인","격리 테스트 파일로 본인 이력서 첨부를 조회하고, 다른 구직자와 권한 없는 기업 계정으로 같은 파일 경로를 요청한다.","미재현. 기대: 본인 또는 해당 지원 건을 볼 권한이 있는 기업만 조회하고 그 외에는 접근이 거부된다.","가능 원인: 세션 역할·지원 관계가 맞지 않거나 파일이 이력서·지원서에 연결되지 않음.","요청 사용자와 이력서·지원서 소유 관계, HTTP 응답을 확인한다. 운영 파일을 내려받거나 공유하지 않는다.",status,"ResumeExtrasController.java; ResumeFiles.java; /resumes/{id}/attachment; /applications/{id}/attachment"],
 ["18-P02","예방 18","결제 webhook 재전송·금액 불일치 / 주문 상태 중복 반영","실제 결제 없이 격리된 테스트 주문과 모의 webhook으로 같은 이벤트를 재전송하고 주문 ID·결제 ID·금액 불일치 응답을 확인한다.","미재현. 기대: 서버 검증이 일치하는 경우에만 상태가 반영되고 중복·불일치 알림은 결제 완료로 처리되지 않는다.","가능 원인: 재전송 이벤트, 주문과 결제 식별자 혼용 또는 서버 조회 금액과 주문 금액 불일치.","모의 결제 경로와 webhook 처리 로그에서 검증 결과·주문 상태를 대조한다. 실제 PG 승인·취소나 운영 키는 사용하지 않는다.",status,"AdvertisingController.java webhook; AdvertisingService.java; PortOneWebhookTests.java"],
 ["18-P03","예방 18","환불 광고가 계속 노출됨 / 결제·환불·게시 상태 불일치","격리 광고 주문에서 테스트 환불 처리 전후로 주문 상세와 공개 광고 목록을 조회한다.","미재현. 기대: 환불 완료 또는 노출 조건을 충족하지 않는 주문은 공개 광고 목록에 포함되지 않는다.","가능 원인: 환불 응답 미확인, 상태 갱신 지연 또는 공개 조회 조건과 관리자 주문 상태 불일치.","테스트 주문의 결제·환불·심사·노출 기간을 함께 대조하고 상태 갱신 후 공개 목록을 다시 조회한다. 실결제·실환불은 수행하지 않는다.",status,"Advertising.jsx; AdvertisingController.java; AdvertisingLifecycle.java; /ads/active"],
 ["19-P01","예방 19","홈 프로모션 노출 기간 경계 오류 / 공개 시작·종료 시각 확인","격리 프로모션의 게시 여부와 시작·종료 시각을 기간 전·중·후 조건으로 비교한다.","미재현. 기대: 게시 상태이며 현재 시각이 설정 기간에 포함되는 항목만 공개 목록에 나타난다.","가능 원인: 시간대 해석 차이, 시작·종료 시각 역전 또는 저장 후 공개 목록을 갱신하지 않음.","관리자 화면의 저장값과 공개 API 응답을 비교하고 시간대를 확인한다. 공유 DB의 프로모션이나 서버 시각을 변경하지 않는다.",status,"AdminHomePromotions.jsx; HomePromotionController.java; HomePromotionService.java; /home-promotions"]
];
if(added.length!==10||added.some(row=>row.length!==9||row[7]!==status))throw new Error("추가 항목 구성 오류");

sheet.getRange("A2").values=[["기존 보고서에 기록된 사례 19건과 예방 점검 항목 31건, 총 50건입니다. 예방 항목은 실제 장애 재현이나 수정 완료를 뜻하지 않습니다."]];
for(let i=0;i<added.length;i++){
  const destRow=45+i;
  sheet.getRange(`A${destRow}:I${destRow}`).copyFrom(sheet.getRange(`A${i%2===0?43:44}:I${i%2===0?43:44}`),"all");
  sheet.getRange(`A${destRow}:I${destRow}`).values=[added[i]];
  sheet.getRange(`A${destRow}:I${destRow}`).format.wrapText=true;
  const widths=[9,10,25,40,40,40,40,18,31];
  const lineCounts=added[i].map((value,col)=>String(value??"").split("\n").reduce((sum,line)=>sum+Math.max(1,Math.ceil([...line].length/widths[col])),0));
  sheet.getRange(`A${destRow}:I${destRow}`).format.rowHeight=Math.min(144,Math.max(48,Math.max(...lineCounts)*12+8));
}

sheet.freezePanes.unfreeze();
wb.recalculate();
const xlsx=await SpreadsheetFile.exportXlsx(wb);await xlsx.save(output);

const saved=await SpreadsheetFile.importXlsx(await FileBlob.load(output));
if(saved.worksheets.items.length!==1||saved.worksheets.items[0].name!=="트러블 슈팅")throw new Error("트러블 슈팅 단독 시트 구성이 아닙니다.");
const finalSheet=saved.worksheets.getItem("트러블 슈팅");
const finalRows=finalSheet.getRange("A5:I54").values;
if(finalRows.length!==50||finalRows.some(row=>!row[0]))throw new Error("저장 후 50개 항목을 확인하지 못했습니다.");
for(let i=0;i<40;i++)if(JSON.stringify(finalRows[i])!==JSON.stringify(previous[i]))throw new Error(`기존 항목이 바뀌었습니다: ${i+1}`);
const ids=finalRows.map(row=>row[0]);
if(added.some(row=>!ids.includes(row[0])))throw new Error("추가한 ID 일부가 저장되지 않았습니다.");
if(finalRows.slice(40).some(row=>row[7]!==status))throw new Error("예방 상태 표기가 누락되었습니다.");
const preview=await saved.render({sheetName:"트러블 슈팅",range:"A1:I54",scale:1,format:"png"});
await fs.writeFile("C:/FinalJobBridge/outputs/trouble-sheet-design-20261110/trouble-50-preview.png",new Uint8Array(await preview.arrayBuffer()));
console.log(JSON.stringify({output,sheetCount:saved.worksheets.items.length,originalItemsPreserved:40,addedPreventionItems:added.length,totalItems:finalRows.length,actualIncidentsInvented:false,freezePanesUnfrozen:true}));
