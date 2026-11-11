import fs from "node:fs/promises";
import {FileBlob,SpreadsheetFile} from "file:///C:/Program%20Files/WindowsApps/OpenAI.CodexPrimaryRuntime.v26-1104-2876-0_26.1104.2876.0_x64__3k8sg7r9htsxt/dependencies/node/node_modules/@oai/artifact-tool/dist/artifact_tool.mjs";

const source="C:/Users/hi101/Desktop/잡브릿지.xlsx";
const completed="C:/FinalJobBridge/outputs/project-docs-completion-20261110/잡브릿지_프로젝트기준_전체보완.xlsx";
const output="C:/FinalJobBridge/outputs/project-docs-completion-20261110/잡브릿지_WBS_최종수정.xlsx";
const sourceBook=await SpreadsheetFile.importXlsx(await FileBlob.load(source));
const completedBook=await SpreadsheetFile.importXlsx(await FileBlob.load(completed));
const styleBook=await SpreadsheetFile.importXlsx(await FileBlob.load(source));
const sourceWbs=sourceBook.worksheets.getItem("WBS");
const completedWbs=completedBook.worksheets.getItem("WBS");
const styleWbs=styleBook.worksheets.getItem("WBS");

const original=sourceWbs.getRange("A8:J108").values;
const ownerRole={"공통":"공통","서상훈":"프론트엔드","이석현":"프론트엔드","전진영":"백엔드","정영록":"백엔드","김경우":"백엔드"};
let phase="",owner="",category="";
const tasks=[];
for(let i=0;i<original.length;i++){
  const row=original[i],sourceRow=i+8;
  if(row[0])phase=row[0];
  if(row[1])owner=row[1];
  if(row[2])category=row[2];
  if(!row[3])continue;
  const role=owner==="공통"?(sourceRow<=15?"프로젝트 준비":"통합·문서화"):ownerRole[owner];
  tasks.push({role,owner,category,task:row[3],progress:row[4],start:row[5],end:row[6],duration:row[7],status:row[8],memo:row[9],sourceRow,styleRow:sourceRow,order:sourceRow});
}

const added=completedWbs.getRange("A109:J125").values;
const addedCategory=(r)=>{
  const owner=r[1],task=r[3];
  if(owner==="서상훈")return task.includes("광고")?"기업 광고":"관리자 화면";
  if(owner==="이석현")return task.includes("OAuth")?"인증·회원 화면":"구직자 화면";
  if(owner==="전진영")return task.includes("첨부")?"이력서 첨부 API":"진로·적성 API";
  if(owner==="정영록")return task.includes("후기")?"면접후기 API":"유료광고 API";
  if(owner==="김경우")return task.includes("프로모션")?"홈 프로모션 API":"취업연계 API";
  return "문서화";
};
const sampleRows={"공통":106,"서상훈":16,"이석현":34,"전진영":52,"정영록":70,"김경우":88};
const ownerFeatureOrder={
  "프로젝트 준비":["계획·설계"],
  "서상훈":["공통 화면","공개 조회","관리자 화면","기업 광고"],
  "이석현":["인증·회원 화면","구직자 화면","기업 화면"],
  "전진영":["환경·초기 데이터","회원 인증 API","회원 정보 API","이력서·관심 공고 API","진로·적성 API","이력서 첨부 API","인증·회원 테스트"],
  "정영록":["공개 채용 API","기업 정보 API","기업 공고 API","지원·면접 API","면접후기 API","유료광고 API","이미지·기능 테스트"],
  "김경우":["관리자 API","취업연계 API","공지 API","홈 프로모션 API","고객센터","알림·공개 공지","운영 기능 테스트"],
  "통합·문서화":["통합 검증","문서화"]
};
for(let i=0;i<added.length;i++){
  const r=added[i],newOwner=r[1],newCategory=addedCategory(r);
  const role=newOwner==="공통"?"통합·문서화":ownerRole[newOwner];
  const existingStyle=tasks.find(t=>t.owner===newOwner&&t.category===newCategory);
  tasks.push({role,owner:newOwner,category:newCategory,task:r[3],progress:r[4],start:r[5],end:r[6],duration:r[7],status:r[8],memo:r[9],sourceRow:null,styleRow:existingStyle?.sourceRow??sampleRows[newOwner],order:200+i});
}

const roleOrder=["프로젝트 준비","프론트엔드","백엔드","통합·문서화"];
const ownersByRole={"프로젝트 준비":["공통"],"프론트엔드":["서상훈","이석현"],"백엔드":["전진영","정영록","김경우"],"통합·문서화":["공통"]};
const excelColumn=(index)=>{let name="";while(index>0){const remainder=(index-1)%26;name=String.fromCharCode(65+remainder)+name;index=Math.floor((index-1)/26);}return name;};
const ordered=[];
for(const role of roleOrder){
  for(const teamOwner of ownersByRole[role]){
    const categoryOrder=role==="프로젝트 준비"?ownerFeatureOrder[role]:role==="통합·문서화"?ownerFeatureOrder[role]:ownerFeatureOrder[teamOwner];
    const list=tasks.filter(t=>t.role===role&&t.owner===teamOwner);
    list.sort((a,b)=>{
      const ai=categoryOrder.indexOf(a.category),bi=categoryOrder.indexOf(b.category);
      return (ai<0?999:ai)-(bi<0?999:bi)||a.order-b.order;
    });
    ordered.push(...list);
  }
}
if(ordered.length!==116)throw new Error(`예상 116개 작업과 다릅니다: ${ordered.length}`);

// The original template deliberately leaves two spacer rows between the two frontend owners.
const layout=[];
const ownerRanges=[];
const roleRanges=[];
let row=8;
for(const role of roleOrder){
  const roleStart=row;
  for(const teamOwner of ownersByRole[role]){
    const group=ordered.filter(t=>t.role===role&&t.owner===teamOwner);
    if(!group.length)continue;
    const start=row;
    for(const t of group){layout.push({row,task:t});row++;}
    if(role==="프론트엔드"&&teamOwner==="서상훈"){
      layout.push({row,task:null},{row:row+1,task:null});row+=2;
    }
    ownerRanges.push({role,owner:teamOwner,start,end:row-1-(role==="프론트엔드"&&teamOwner==="서상훈"?2:0),mergeEnd:row-1});
  }
  roleRanges.push({role,start:roleStart,end:row-1});
}
const lastRow=row-1;
if(lastRow!==125||layout.filter(x=>x.task).length!==116)throw new Error(`WBS 행 범위 오류: ${lastRow}`);

// Copy each row's visual style from the original template before rewriting the content.
styleWbs.getRange("A8:C108").unmerge();
const outBook=sourceBook;
const outWbs=outBook.worksheets.getItem("WBS");
outWbs.getRange("A8:C125").unmerge();
for(const item of layout){
  const styleRow=item.task?.styleRow??32+(item.row-(ownerRanges.find(x=>x.owner==="서상훈")?.end??34)+1);
  const safeStyleRow=item.task?.styleRow??(item.row<37?32:33);
  outWbs.getRange(`A${item.row}:AI${item.row}`).copyFrom(styleWbs.getRange(`A${safeStyleRow}:AI${safeStyleRow}`),"all");
}
outWbs.getRange(`A8:J${lastRow}`).clear({applyTo:"contents"});
const rows=Array.from({length:lastRow-7},()=>Array(10).fill(null));
for(const item of layout){
  if(!item.task)continue;
  const t=item.task;
  rows[item.row-8]=[t.role,t.owner,t.category,t.task,t.progress,t.start,t.end,t.duration,t.status,t.memo];
}
outWbs.getRange(`A8:J${lastRow}`).values=rows;
outWbs.getRange(`A8:J${lastRow}`).format.rowHeight=48;
outWbs.getRange(`E8:E${lastRow}`).setNumberFormat("0%");
outWbs.getRange(`F8:G${lastRow}`).setNumberFormat("yyyy-mm-dd");
outWbs.getRange(`A8:J${lastRow}`).format.wrapText=true;

// Restore the template's role → owner → feature hierarchy merges.
for(const r of roleRanges){if(r.end>r.start)outWbs.getRange(`A${r.start}:A${r.end}`).merge();}
for(const r of ownerRanges){if(r.mergeEnd>r.start)outWbs.getRange(`B${r.start}:B${r.mergeEnd}`).merge();}
for(const ownerGroup of ownerRanges){
  let start=ownerGroup.start;
  while(start<=ownerGroup.end){
    while(start<=ownerGroup.end&&!rows[start-8]?.[2])start++;
    if(start>ownerGroup.end)break;
    let end=start;
    while(end<ownerGroup.end&&rows[end-7]?.[2]===rows[start-8]?.[2])end++;
    if(end>start)outWbs.getRange(`C${start}:C${end}`).merge();
    start=end+1;
  }
}

// Rebuild the original calendar bars while leaving its headers, widths and table styling intact.
const firstDay=46314,dayColumns=25;
outWbs.getRange(`K8:AI${lastRow}`).format.fill="#FFFFFF";
for(let d=0;d<dayColumns;d++)if(d%7===5||d%7===6){const col=excelColumn(11+d);outWbs.getRange(`${col}8:${col}${lastRow}`).format.fill="#B7B7B7";}
for(const item of layout){
  if(!item.task)continue;
  const t=item.task;let d=0;
  while(d<dayColumns){
    const serial=firstDay+d,active=serial>=t.start&&serial<=t.end&&d%7<5;
    if(!active){d++;continue;}
    const start=d;
    while(d+1<dayColumns&&d%7<4&&firstDay+d+1>=t.start&&firstDay+d+1<=t.end)d++;
    const startCol=excelColumn(11+start),endCol=excelColumn(11+d);
    outWbs.getRange(`${startCol}${item.row}:${endCol}${item.row}`).format.fill="#D9EEE7";
    d++;
  }
}
outWbs.freezePanes.unfreeze();
outBook.recalculate();

// This handoff is intentionally a one-sheet workbook; the source workbook stays untouched.
for(const s of [...outBook.worksheets.items])if(s.name!=="WBS")s.delete();
if(outBook.worksheets.items.length!==1||outBook.worksheets.items[0].name!=="WBS")throw new Error("WBS 단독 파일 구성이 아닙니다.");
const xlsx=await SpreadsheetFile.exportXlsx(outBook);await xlsx.save(output);
const saved=await SpreadsheetFile.importXlsx(await FileBlob.load(output));
const check=saved.worksheets.getItem("WBS");
const savedRows=check.getRange("A8:J125").values;
const taskRows=savedRows.filter(r=>r[3]);
if(taskRows.length!==116)throw new Error(`저장 후 작업 수 불일치: ${taskRows.length}`);
if(taskRows.some(r=>r[0]==="기능 보완"))throw new Error("임시 기능 보완 분류가 남았습니다.");
if(taskRows.filter(r=>r[0]==="프론트엔드").some(r=>!["서상훈","이석현"].includes(r[1])))throw new Error("프론트엔드 담당 구분 오류");
if(taskRows.filter(r=>r[0]==="백엔드").some(r=>!["전진영","정영록","김경우"].includes(r[1])))throw new Error("백엔드 담당 구분 오류");
const png=await saved.render({sheetName:"WBS",range:"A1:AI125",scale:0.65,format:"png"});
await fs.writeFile("C:/FinalJobBridge/outputs/project-docs-completion-20261110/wbs-single-preview.png",new Uint8Array(await png.arrayBuffer()));
console.log(JSON.stringify({output,sheetCount:saved.worksheets.items.length,taskCount:taskRows.length,ownerCounts:Object.fromEntries(["서상훈","이석현","전진영","정영록","김경우"].map(name=>[name,taskRows.filter(r=>r[1]===name).length])),spacerRowsPreserved:true,templateStylesUsed:true,panesUnfrozen:true}));
