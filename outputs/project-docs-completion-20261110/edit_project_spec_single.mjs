import fs from "node:fs/promises";
import {FileBlob,SpreadsheetFile} from "file:///C:/Program%20Files/WindowsApps/OpenAI.CodexPrimaryRuntime.v26-1104-2876-0_26.1104.2876.0_x64__3k8sg7r9htsxt/dependencies/node/node_modules/@oai/artifact-tool/dist/artifact_tool.mjs";

const source="C:/Users/hi101/Desktop/잡브릿지.xlsx";
const completed="C:/FinalJobBridge/outputs/project-docs-completion-20261110/잡브릿지_프로젝트기준_전체보완.xlsx";
const output="C:/FinalJobBridge/outputs/project-docs-completion-20261110/잡브릿지_프로젝트명세서_최종본.xlsx";
if(await fs.stat(output).then(()=>true).catch(()=>false))throw new Error("출력 파일이 이미 있어 덮어쓰지 않습니다.");

const wb=await SpreadsheetFile.importXlsx(await FileBlob.load(source));
const completedWb=await SpreadsheetFile.importXlsx(await FileBlob.load(completed));
const styleWb=await SpreadsheetFile.importXlsx(await FileBlob.load(source));
const out=wb.worksheets.getItem("프로젝트 명세서");
const prior=completedWb.worksheets.getItem("프로젝트 명세서");
const style=styleWb.worksheets.getItem("프로젝트 명세서");

// Keep the already reviewed project summary and test-history wording.
out.getRange("A1:H4").values=prior.getRange("A1:H4").values;
const existing=prior.getRange("A9:H41").values;
let owner="";
const byOwner={"서상훈":[],"이석현":[],"전진영":[],"정영록":[],"김경우":[],"공통":[]};
for(let i=0;i<existing.length;i++){
  const r=existing[i]; if(r[0])owner=String(r[0]);
  if(!owner||!byOwner[owner])throw new Error(`담당자 확인 실패: ${i+9}`);
  byOwner[owner].push({cells:r.slice(),styleRow:i+9});
}
const update=(team,index,fn)=>fn(byOwner[team][index].cells);

// Extend existing entries so one feature has one home in the original owner matrix.
update("이석현",0,r=>{r[1]="개인·기업·관리자 로그인 / 가입·카카오 OAuth";r[5]+="\nApp.jsx (OAuth callback 연결)";});
update("전진영",0,r=>{r[1]="가입·약관 동의·SMS·카카오 인증 API";r[2]+="\nauth/AuthController.java (Kakao callback)";});
update("전진영",4,r=>{r[1]="이력서 API·상세 검증·지원서 첨부 조회";r[2]+="\njob/controller/ResumeExtrasController.java (첨부 조회)";r[7]="resumes\njob_seekers\napplications\nresume_files\napplication_snapshots";});
update("김경우",2,r=>{r[1]="취업연계 신청·추천 배정·재매칭·면접 제안·온보딩 API";r[2]+="\njob/controller/EmploymentReferralController.java\njob/service/EmploymentReferrals.java\nEmploymentInterviewProposals.java\nEmploymentOnboarding.java";r[7]+="\nemployment_referrals\nemployment_interview_proposals\nemployment_onboarding";});

const make=(team,cells,styleRow)=>({cells:[team,...cells],styleRow});
byOwner["서상훈"].push(
  make("서상훈",["기업 광고·관리자 광고 주문·소재·심사 화면","—","미사용\nReact SPA\n(JSP 파일 없음)","프론트엔드:\napi/client.js로\nREST API 연동\n\n별도 MyBatis Mapper 없음","pages/Advertising.jsx (기업·관리자)\npages/Company.jsx","company-ads.css\nglobal.css","API 연동\nad_products\nad_orders\njob_postings\ncompanies"],12),
  make("서상훈",["홈 프로모션·면접 후기·참석 확인 화면","—","미사용\nReact SPA\n(JSP 파일 없음)","프론트엔드:\napi/client.js로\nREST API 연동\n\n별도 MyBatis Mapper 없음","pages/Public.jsx\npages/Company.jsx\npages/ReviewFeatures.jsx\npages/AdminHomePromotions.jsx","global.css","API 연동\nhome_promotions\ninterview_reviews\ncompanies"],14)
);
byOwner["이석현"].push(
  make("이석현",["진로검사·진로탐색·취업도구 화면","—","미사용\nReact SPA\n(JSP 파일 없음)","프론트엔드:\napi/client.js로\nREST API 연동\n\n별도 MyBatis Mapper 없음","pages/CareerAssessment.jsx\npages/CareerExploration.jsx\npages/CareerTools.jsx","global.css","API 연동\ncareer_assessment_attempts\ncareer_exploration_attempts"],17)
);
byOwner["전진영"].push(
  make("전진영",["진로검사·진로탐색 응시·결과·이력 API","job/controller/CareerAssessmentController.java\njob/service/CareerAssessments.java\njob/controller/CareerExplorationController.java\njob/service/CareerExplorations.java","미사용","백엔드 공통 SQL 접근:\nDatabase.java\n(JdbcTemplate)\n\n별도 MyBatis Mapper 없음","—","—","career_assessment_attempts\ncareer_exploration_attempts\njob_assessment_settings\napplication_assessments"],25)
);
byOwner["정영록"].push(
  make("정영록",["면접 후기·참석 확인·증빙·신고·관리자 중재 API","job/controller/InterviewReviewController.java\njob/service/InterviewReviews.java","미사용","백엔드 공통 SQL 접근:\nDatabase.java\n(JdbcTemplate)\n\n별도 MyBatis Mapper 없음","—","—","interview_reviews\ninterview_review_moderation\nmember_reports"],31),
  make("정영록",["유료광고 상품·주문·테스트 결제·webhook·심사·환불 API","ads/AdvertisingController.java\nads/AdvertisingService.java\nads/AdvertisingLifecycle.java","미사용","백엔드 공통 SQL 접근:\nDatabase.java\n(JdbcTemplate)\n\n별도 MyBatis Mapper 없음","—","—","ad_products\nad_orders\nad_feature_upgrades\nad_order_simulations\n운영 PG 실결제 미설정"],31)
);
byOwner["김경우"].push(
  make("김경우",["홈 프로모션 등록·수정·삭제·공개 조회 API","admin/HomePromotionController.java\nadmin/HomePromotionService.java\nadmin/HomePromotionTargets.java","미사용","백엔드 공통 SQL 접근:\nDatabase.java\n(JdbcTemplate)\n\n별도 MyBatis Mapper 없음","—","—","home_promotions\nhome_promotion_deletions"],36)
);

const ordered=[...byOwner["서상훈"],...byOwner["이석현"],...byOwner["전진영"],...byOwner["정영록"],...byOwner["김경우"],...byOwner["공통"]];
const counts=Object.fromEntries(Object.entries(byOwner).map(([name,rows])=>[name,rows.length]));
if(ordered.length!==40||JSON.stringify(counts)!==JSON.stringify({"서상훈":8,"이석현":7,"전진영":7,"정영록":8,"김경우":7,"공통":3}))throw new Error(`명세 행/담당자 수 불일치: ${JSON.stringify(counts)}`);

// Reuse the original sheet's row formats and palette; no new section is appended.
style.getRange("A9:A41").unmerge();
style.getRange("A46:E52").unmerge();
out.getRange("A9:H59").unmerge();
const ownerFirst={"서상훈":9,"이석현":15,"전진영":21,"정영록":27,"김경우":33,"공통":39};
const rows=[];
for(const item of ordered){
  const reference=style.getRange(`A${item.styleRow}:H${item.styleRow}`);
  const dest=9+rows.length;
  out.getRange(`A${dest}:H${dest}`).copyFrom(reference,"all");
  rows.push(item.cells);
}
out.getRange("A9:H48").values=rows;
out.getRange("A9:H48").format.wrapText=true;
const approximateWidths=[9,24,29,17,25,22,20,20];
for(let i=0;i<rows.length;i++){
  const lines=rows[i].map((value,col)=>String(value??"").split("\n").reduce((sum,line)=>sum+Math.max(1,Math.ceil([...line].length/approximateWidths[col])),0));
  out.getRange(`A${9+i}:H${9+i}`).format.rowHeight=Math.min(132,Math.max(34,Math.max(...lines)*12+8));
}

const groups=[{name:"서상훈",start:9,end:16},{name:"이석현",start:17,end:23},{name:"전진영",start:24,end:30},{name:"정영록",start:31,end:38},{name:"김경우",start:39,end:45},{name:"공통",start:46,end:48}];
for(const g of groups)if(g.end>g.start)out.getRange(`A${g.start}:A${g.end}`).merge();

// Move the note and role summary directly below the expanded owner matrix.
out.getRange("A51:H51").copyFrom(style.getRange("A44:H44"),"all");
out.getRange("A51:H51").values=prior.getRange("A44:H44").values;
out.getRange("A49:H50").clear({applyTo:"contents"});
out.getRange("A52:H52").clear({applyTo:"contents"});
out.getRange("A53:E53").copyFrom(style.getRange("A46:E46"),"all");
out.getRange("A54:E54").copyFrom(style.getRange("A47:E47"),"all");
for(let i=0;i<5;i++)out.getRange(`A${55+i}:E${55+i}`).copyFrom(style.getRange(`A${48+i}:E${48+i}`),"all");
out.getRange("A53:E53").merge();
out.getRange("A53").values=[["담당자별 역할 요약 (WBS 기준)"]];
out.getRange("A54:E54").values=[prior.getRange("A47:E47").values[0]];
out.getRange("A55:E59").values=[
 ["서상훈","프론트엔드","공통·관리자·기업 광고·프로모션·후기 화면","Admin.jsx · AdminDashboard.jsx · Advertising.jsx · ReviewFeatures.jsx","WBS 기준 담당"],
 ["이석현","프론트엔드","인증·구직자·기업 화면·취업도구","Auth.jsx · Seeker.jsx · CareerAssessment.jsx · CareerTools.jsx","WBS 기준 담당"],
 ["전진영","백엔드","회원·인증·프로필·이력서·첨부·진로검사 API","AuthController · ProfilePhotoController · ResumeExtrasController · Career*Controller","WBS 기준 담당"],
 ["정영록","백엔드","공개 채용·기업·지원·면접·후기·광고 API","JobController · InterviewReviewController · AdvertisingController","WBS 기준 담당"],
 ["김경우","백엔드","관리자·취업연계·공지·문의·알림·프로모션 API","OperationsController · EmploymentReferralController · HomePromotionController","WBS 기준 담당"]
];
out.getRange("A53:E59").format.wrapText=true;
const summaryWidths=[13,16,30,43,15];
for(let i=0;i<5;i++){
  const values=out.getRange(`A${55+i}:E${55+i}`).values[0];
  const lines=values.map((value,col)=>String(value??"").split("\n").reduce((sum,line)=>sum+Math.max(1,Math.ceil([...line].length/summaryWidths[col])),0));
  out.getRange(`A${55+i}:E${55+i}`).format.rowHeight=Math.max(28,Math.max(...lines)*12+8);
}
out.freezePanes.unfreeze();
wb.recalculate();
for(const other of [...wb.worksheets.items])if(other.name!=="프로젝트 명세서")other.delete();
if(wb.worksheets.items.length!==1||wb.worksheets.items[0].name!=="프로젝트 명세서")throw new Error("프로젝트 명세서 단독 파일 구성이 아닙니다.");
const xlsx=await SpreadsheetFile.exportXlsx(wb);await xlsx.save(output);

const saved=await SpreadsheetFile.importXlsx(await FileBlob.load(output));
const finalSheet=saved.worksheets.getItem("프로젝트 명세서");
const finalRows=finalSheet.getRange("A9:H48").values;
if(finalRows.length!==40||finalRows.filter(r=>r[1]).length!==40)throw new Error("저장 후 담당 항목 수가 맞지 않습니다.");
const finalOwners=groups.map(g=>String(finalSheet.getRange(`A${g.start}`).values[0][0]));
if(finalOwners.join("|")!==groups.map(g=>g.name).join("|"))throw new Error(`담당자 순서가 맞지 않습니다: ${finalOwners.join(",")}`);
const mainText=JSON.stringify(finalRows);
for(const required of ["CareerTools.jsx","InterviewReviewController.java","AdvertisingController.java","EmploymentReferralController.java","HomePromotionController.java","ResumeExtrasController.java","career_assessment_attempts","ad_orders","home_promotions","resume_files"]){if(!mainText.includes(required))throw new Error(`주요 기능 누락: ${required}`);}
if(finalSheet.getRange("A54:E59").values.some(r=>String(r[0]||"").includes("추가 구현 기능")))throw new Error("추가 기능이 별도 섹션으로 남았습니다.");
const preview=await saved.render({sheetName:"프로젝트 명세서",range:"A1:H59",scale:1,format:"png"});
await fs.writeFile("C:/FinalJobBridge/outputs/project-docs-completion-20261110/project-spec-single-preview-final.png",new Uint8Array(await preview.arrayBuffer()));
console.log(JSON.stringify({output,sheetCount:saved.worksheets.items.length,ownerRows:counts,projectRows:40,appendedExtraSection:false,freezePanesUnfrozen:true,summaryAndExistingTestNotePreserved:true}));
