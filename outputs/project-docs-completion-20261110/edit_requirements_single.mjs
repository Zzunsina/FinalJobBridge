import fs from "node:fs/promises";
import {FileBlob,SpreadsheetFile} from "file:///C:/Program%20Files/WindowsApps/OpenAI.CodexPrimaryRuntime.v26-1104-2876-0_26.1104.2876.0_x64__3k8sg7r9htsxt/dependencies/node/node_modules/@oai/artifact-tool/dist/artifact_tool.mjs";

const templatePath="C:/Users/hi101/Desktop/잡브릿지.xlsx";
const contentPath="C:/FinalJobBridge/outputs/project-docs-completion-20261110/잡브릿지_프로젝트기준_전체보완.xlsx";
const outputPath="C:/FinalJobBridge/outputs/project-docs-completion-20261110/잡브릿지_요구사항정의서_수정본.xlsx";
const workbook=await SpreadsheetFile.importXlsx(await FileBlob.load(templatePath));
const styleBook=await SpreadsheetFile.importXlsx(await FileBlob.load(templatePath));
const contentBook=await SpreadsheetFile.importXlsx(await FileBlob.load(contentPath));
const requirements=workbook.worksheets.getItem("요구사항 정의서");
const styleSheet=styleBook.worksheets.getItem("요구사항 정의서");
const contentSheet=contentBook.worksheets.getItem("요구사항 정의서");

const contentRows=contentSheet.getRange("A8:K88").values;
const records=contentRows.filter(row=>row[2]).map(row=>({
  id:String(row[2]),
  values:row.slice()
}));
const idParts=id=>{
  const match=id.match(/^SFR-(\d+)-(\d+)$/);
  if(!match)throw new Error(`요구사항 ID 형식이 예상과 다릅니다: ${id}`);
  return [Number(match[1]),Number(match[2])];
};
records.sort((a,b)=>{
  const [ag,ai]=idParts(a.id),[bg,bi]=idParts(b.id);
  return ag-bg||ai-bi;
});
if(records.length!==81)throw new Error(`요구사항이 81개가 아닙니다: ${records.length}`);
if(new Set(records.map(r=>r.id)).size!==records.length)throw new Error("중복된 요구사항 ID가 있습니다.");

// Extend the existing requirement-table row style for the added rows.
requirements.getRange("A73:K88").copyFrom(styleSheet.getRange("A8:K23"),"all");
requirements.getRange("A73:K88").format.rowHeight=styleSheet.getRange("A8").format.rowHeight;
requirements.getRange("A2").values=contentSheet.getRange("A2").values;
requirements.getRange("B6").values=contentSheet.getRange("B6").values;
const outputRows=records.map((record,index)=>{
  const row=record.values;
  row[0]=index+1;
  return row;
});
requirements.getRange("A8:K88").values=outputRows;
requirements.getRange("J8:K88").setNumberFormat("yyyy-mm-dd");
requirements.getRange("A8:K88").format.wrapText=true;

// Match status colors to the three status labels already used in the template.
const statusStyle={"완료":"F8","부분 완료":"F13","기능 미제공":"F18"};
for(let i=0;i<outputRows.length;i++){
  const targetRow=8+i,status=String(outputRows[i][5]||""),sample=statusStyle[status];
  if(!sample)throw new Error(`템플릿 상태 색을 찾지 못했습니다: ${status}`);
  const cell=requirements.getRange(`F${targetRow}`);
  cell.copyFrom(styleSheet.getRange(sample),"all");
  cell.values=[[status]];
}
requirements.freezePanes.unfreeze();
workbook.recalculate();

for(const sheet of [...workbook.worksheets.items])if(sheet.name!=="요구사항 정의서")sheet.delete();
if(workbook.worksheets.items.length!==1||workbook.worksheets.items[0].name!=="요구사항 정의서")throw new Error("요구사항 정의서 단독 파일 구성이 아닙니다.");
const xlsx=await SpreadsheetFile.exportXlsx(workbook);await xlsx.save(outputPath);

const saved=await SpreadsheetFile.importXlsx(await FileBlob.load(outputPath));
const finalSheet=saved.worksheets.getItem("요구사항 정의서");
const finalRows=finalSheet.getRange("A8:K88").values;
const finalIds=finalRows.map(row=>String(row[2]||""));
if(finalRows.filter(row=>row[2]).length!==81)throw new Error("저장 후 요구사항 개수가 바뀌었습니다.");
if(finalIds.join("|")!==records.map(record=>record.id).join("|"))throw new Error("요구사항이 ID 순서로 정렬되지 않았습니다.");
if(finalRows.some((row,index)=>row[2]&&Number(row[0])!==index+1))throw new Error("요구사항 번호가 연속되지 않습니다.");
const preview=await saved.render({sheetName:"요구사항 정의서",range:"A1:K88",scale:0.75,format:"png"});
await fs.writeFile("C:/FinalJobBridge/outputs/project-docs-completion-20261110/requirements-single-preview.png",new Uint8Array(await preview.arrayBuffer()));
console.log(JSON.stringify({output:outputPath,sheetCount:saved.worksheets.items.length,requirementCount:finalRows.length,sortedByRequirementId:true,partialStatusColorTemplate:"F13",unfrozen:true,templateFormattingPreserved:true}));
