import fs from "node:fs/promises";
import {FileBlob,SpreadsheetFile} from "file:///C:/Program%20Files/WindowsApps/OpenAI.CodexPrimaryRuntime.v26-1104-2876-0_26.1104.2876.0_x64__3k8sg7r9htsxt/dependencies/node/node_modules/@oai/artifact-tool/dist/artifact_tool.mjs";

const formatPath="C:/FinalJobBridge/outputs/requirements-definition-update-20261110/잡브릿지_통합테스트_단독시트.xlsx";
const dataPath="C:/FinalJobBridge/outputs/requirements-definition-update-20261110/잡브릿지_통합테스트_수정본_최종.xlsx";
const output="C:/FinalJobBridge/outputs/requirements-definition-update-20261110/잡브릿지_통합테스트_표준형_최종.xlsx";
if(await fs.stat(output).then(()=>true).catch(()=>false))throw new Error("출력 파일이 이미 있어 덮어쓰지 않습니다.");

const wb=await SpreadsheetFile.importXlsx(await FileBlob.load(formatPath));
const styleWb=await SpreadsheetFile.importXlsx(await FileBlob.load(formatPath));
const dataWb=await SpreadsheetFile.importXlsx(await FileBlob.load(dataPath));
const sheet=wb.worksheets.getItem("통합테스트");
const style=styleWb.worksheets.getItem("통합테스트");
const dataSheet=dataWb.worksheets.getItem("통합테스트");
const sourceRows=dataSheet.getRange("A7:O50").values;
if(sourceRows.length!==44||sourceRows.slice(0,43).some((r,i)=>r[1]!==`IT-${String(i+1).padStart(3,"0")}`)||sourceRows[43][1]!=="AUTO-043")throw new Error("통합 시나리오 ID가 예상과 다릅니다.");

const areas={1:"회원",2:"채용",3:"기업",4:"관리자",5:"이력서",6:"입사지원",7:"면접",8:"알림",9:"취업연계",10:"고객센터",11:"보안·오류",12:"공통 화면",13:"미디어",14:"개발 시드",15:"통합 흐름",16:"유료광고",17:"인증·카카오",18:"진로·적성",19:"진로탐색",20:"취업도구",21:"면접후기",22:"면접 참석확인",23:"홈 프로모션",24:"첨부파일",25:"유료광고"};
const parseSteps=value=>{
  const text=String(value??"");
  const match=text.match(/^사전조건:\s*([\s\S]*?)\n절차:\s*([\s\S]*)$/);
  return match?{pre:match[1].trim(),steps:match[2].trim()}:{pre:"",steps:text};
};
const rows=[];
for(let i=0;i<43;i++){
  const r=sourceRows[i],parsed=parseSteps(r[8]);
  const id=String(r[1]);
  const area=areas[Number(r[3])]||"공통";
  rows.push([
    i+1,id,area,r[4],r[2],parsed.pre,parsed.steps,r[12],"","미실행",r[10],null,
    `우선순위 ${r[5]} · ${r[7]}\n${r[13]}`
  ]);
}
const auto=sourceRows[43];
rows.push([
  44,auto[1],"자동 테스트",auto[4],auto[2],"기존 프로젝트의 백엔드 자동 테스트 확인 기록 (실행일·세부 환경 미기재)",
  "기존 확인 결과를 문서에 보존합니다. 이번 시트 수정에서는 테스트를 다시 실행하지 않았습니다.",
  "이전 기록: 백엔드 테스트 43개 통과", "이전 기록에서 백엔드 테스트 43개 통과", "이전 기록 · 통과",
  "기존 검증 환경 (상세 구성 기록 없음)",null,
  "기존 프로젝트 확인 기록. 수행일자는 원본 기록에 없어 비워 둠."
]);

sheet.getRange("A1").values=[["JobBridge 통합 테스트 케이스"]];
sheet.getRange("A2").values=[["범위: 현재 구현 기준 수동 통합 시나리오 43건과 이전 자동 테스트 확인 기록 1건"]];
sheet.getRange("A3").values=[["담당: 프론트엔드 서상훈·이석현 / 백엔드 전진영·김경우·정영록"]];
sheet.getRange("A4").values=[["수동 시나리오의 실제 결과와 실행일은 테스트 실행 후 기록합니다. 이번 문서 수정에서는 수동 시나리오를 실행하지 않았습니다."]];
sheet.getRange("A5").values=[["환경 기준: 격리 H2·테스트 계정 사용. 개발 시드는 격리 환경에서만 확인하며 공유 DB 변경 및 운영 PG 실결제는 범위에 포함하지 않습니다."]];
const headers=["No","테스트 ID","기능 영역","관련 요구사항","테스트 시나리오","사전조건·테스트 데이터","테스트 절차","기대 결과","실제 결과","판정 상태","테스트 환경","실행일자","근거·비고"];
sheet.getRange("A7:M7").values=[headers];
sheet.getRange("A8:M90").clear({applyTo:"contents"});
for(let i=0;i<rows.length;i++){
  const dest=8+i;
  sheet.getRange(`A${dest}:M${dest}`).copyFrom(style.getRange(`A${8+(i%2)}:M${8+(i%2)}`),"all");
  sheet.getRange(`A${dest}:M${dest}`).values=[rows[i]];
  sheet.getRange(`A${dest}:M${dest}`).format.wrapText=true;
  const widths=[5,12,15,24,30,30,34,35,32,18,24,13,40];
  const lineCounts=rows[i].map((value,col)=>String(value??"").split("\n").reduce((sum,line)=>sum+Math.max(1,Math.ceil([...line].length/widths[col])),0));
  sheet.getRange(`A${dest}:M${dest}`).format.rowHeight=Math.min(126,Math.max(36,Math.max(...lineCounts)*11+8));
}
sheet.getRange("L8:L51").setNumberFormat("yyyy-mm-dd");
sheet.freezePanes.unfreeze();
wb.recalculate();
if(wb.worksheets.items.length!==1||sheet.name!=="통합테스트")throw new Error("통합테스트 단독 시트 구성이 아닙니다.");
const xlsx=await SpreadsheetFile.exportXlsx(wb);await xlsx.save(output);

const saved=await SpreadsheetFile.importXlsx(await FileBlob.load(output));
const final=saved.worksheets.getItem("통합테스트");
const check=final.getRange("A8:M51").values;
if(check.length!==44||check.some(r=>!r[1]))throw new Error("저장 후 통합테스트 항목 수가 맞지 않습니다.");
if(check.slice(0,43).some(r=>r[9]!=="미실행"||(r[8]!==""&&r[8]!==null)||r[11]!==null))throw new Error("미실행 시나리오의 결과/일자 상태가 잘못되었습니다.");
if(check[43][1]!=="AUTO-043"||!String(check[43][8]).includes("43개 통과"))throw new Error("기존 자동 테스트 기록 확인 실패");
const all=JSON.stringify(final.getRange("A1:M51").values).toLowerCase();
if(all.includes("flyway"))throw new Error("Flyway 관련 내용이 남아 있습니다.");
const preview=await saved.render({sheetName:"통합테스트",range:"A1:M51",scale:1,format:"png"});
await fs.writeFile("C:/FinalJobBridge/outputs/requirements-definition-update-20261110/integration-standard-final-preview.png",new Uint8Array(await preview.arrayBuffer()));
console.log(JSON.stringify({output,sheetCount:saved.worksheets.items.length,manualScenarios:43,priorAutoTestRecordPreserved:true,flywayRemoved:true,actualResultsAndDatesBlankForNotRun:true,freezePanesUnfrozen:true}));
