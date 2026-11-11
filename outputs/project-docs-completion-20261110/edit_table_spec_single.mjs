import fs from "node:fs/promises";
import {FileBlob,SpreadsheetFile} from "file:///C:/Program%20Files/WindowsApps/OpenAI.CodexPrimaryRuntime.v26-1104-2876-0_26.1104.2876.0_x64__3k8sg7r9htsxt/dependencies/node/node_modules/@oai/artifact-tool/dist/artifact_tool.mjs";

const source="C:/Users/hi101/Desktop/잡브릿지.xlsx";
const output="C:/FinalJobBridge/outputs/project-docs-completion-20261110/잡브릿지_테이블명세서_수정본.xlsx";
const workbook=await SpreadsheetFile.importXlsx(await FileBlob.load(source));
const sheet=workbook.worksheets.getItem("테이블 명세서");
const originalValues=sheet.getRange("A1:G579").values.map(row=>row.slice());
const tableCounts={};
for(const row of originalValues){
  const title=String(row[0]||"");
  const match=title.match(/^([a-z0-9_]+) 테이블명세서/);
  if(match)tableCounts[match[1]]=(tableCounts[match[1]]||0)+1;
}
const repeated=Object.entries(tableCounts).filter(([,count])=>count!==1);
if(Object.keys(tableCounts).length!==49||repeated.length)throw new Error(`원본 표의 테이블 수가 예상과 다릅니다: ${JSON.stringify({count:Object.keys(tableCounts).length,repeated})}`);
const dataRows=originalValues.filter((row,index)=>index>=11&&row[0]&&row[1]&&row[0]!=="테이블명"&&!String(row[0]).includes("테이블명세서"));
if(dataRows.length!==376)throw new Error(`원본 컬럼 수가 예상과 다릅니다: ${dataRows.length}`);

// The existing sheet already contains the four ad-table definitions. The prior appended block duplicated them.
sheet.getRange("B4").values=[["v1.3 · 현재 프로젝트 DB 구조 및 광고 테이블 확인 · 2026-11-10"]];
sheet.getRange("B5").values=[["49개 테이블 · 376개 컬럼 · 신규 DB 통합 스키마 및 기능별 DDL 기준(광고 정의 포함)"]];
sheet.freezePanes.unfreeze();
workbook.recalculate();
for(const other of [...workbook.worksheets.items])if(other.name!=="테이블 명세서")other.delete();
if(workbook.worksheets.items.length!==1||workbook.worksheets.items[0].name!=="테이블 명세서")throw new Error("테이블 명세서 단독 파일 구성이 아닙니다.");
const xlsx=await SpreadsheetFile.exportXlsx(workbook);await xlsx.save(output);

const saved=await SpreadsheetFile.importXlsx(await FileBlob.load(output));
const finalSheet=saved.worksheets.getItem("테이블 명세서");
const finalValues=finalSheet.getRange("A1:G579").values;
const titleCount=finalValues.filter(row=>/^([a-z0-9_]+) 테이블명세서/.test(String(row[0]||""))).length;
const finalDataRows=finalValues.filter((row,index)=>index>=11&&row[0]&&row[1]&&row[0]!=="테이블명"&&!String(row[0]).includes("테이블명세서"));
if(titleCount!==49||finalDataRows.length!==376)throw new Error(`저장 후 테이블/컬럼 수가 다릅니다: ${titleCount}/${finalDataRows.length}`);
for(let r=0;r<originalValues.length;r++)for(let c=0;c<7;c++){
  if(r===3&&c===1||r===4&&c===1)continue;
  if(finalValues[r][c]!==originalValues[r][c])throw new Error(`기존 명세 값이 바뀌었습니다: ${r+1},${c+1}`);
}
const adNames=["ad_products","ad_orders","ad_feature_upgrades","ad_order_simulations"];
const adCounts=Object.fromEntries(adNames.map(name=>[name,finalValues.filter(row=>row[0]===`${name} 테이블명세서 · ${name==="ad_products"?"광고 상품":name==="ad_orders"?"광고 주문 및 결제":name==="ad_feature_upgrades"?"광고 기능 DB 변경 기록":"광고 주문 테스트 기록"}`).length]));
if(Object.values(adCounts).some(count=>count!==1))throw new Error(`광고 테이블 중복 또는 누락: ${JSON.stringify(adCounts)}`);
const top=await saved.render({sheetName:"테이블 명세서",range:"A1:G45",scale:1,format:"png"});
const ads=await saved.render({sheetName:"테이블 명세서",range:"A465:G532",scale:1,format:"png"});
await fs.writeFile("C:/FinalJobBridge/outputs/project-docs-completion-20261110/table-spec-top.png",new Uint8Array(await top.arrayBuffer()));
await fs.writeFile("C:/FinalJobBridge/outputs/project-docs-completion-20261110/table-spec-ads.png",new Uint8Array(await ads.arrayBuffer()));
console.log(JSON.stringify({output,sheetCount:saved.worksheets.items.length,tableCount:titleCount,columnCount:finalDataRows.length,adTables:adCounts,duplicateAppendedBlocksRemoved:true,existingCellValuesPreserved:true,freezePanesUnfrozen:true}));
