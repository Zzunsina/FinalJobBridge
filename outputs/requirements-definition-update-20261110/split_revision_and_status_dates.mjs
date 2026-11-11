import fs from "node:fs/promises";
import { FileBlob, SpreadsheetFile } from "file:///C:/Program%20Files/WindowsApps/OpenAI.CodexPrimaryRuntime.v26-1104-2876-0_26.1104.2876.0_x64__3k8sg7r9htsxt/dependencies/node/node_modules/@oai/artifact-tool/dist/artifact_tool.mjs";

const dir = "C:/FinalJobBridge/outputs/requirements-definition-update-20261110";
const source = dir + "/잡브릿지_요구사항정의서_최종현황_단독시트.xlsx";
const out = dir + "/잡브릿지_요구사항정의서_일정표대조_최종.xlsx";
const oldRevisionDate = 46318; // Original sheet date: 2026-10-23.
const statusReviewDate = 46336; // 2026-11-10.

const wb = await SpreadsheetFile.importXlsx(await FileBlob.load(source));
if (wb.worksheets.items.length !== 1) throw new Error("Expected the standalone requirements sheet.");
const sheet = wb.worksheets.getItem("요구사항 정의서");
const before = sheet.getRange("A1:K73").values;

sheet.getRange("A7").values = [["날짜 기준"]];
sheet.getRange("B7").values = [["일정표는 계획이며 실제 완료일과 구분되어 있습니다. 항목별 실제 완료일이 없어 계획일은 개정일자로 쓰지 않았습니다. 원래 개정일(2026-10-23)은 유지하고, 코드 현황 확인일은 별도 열에 기록했습니다. 구현 노트를 고친 SFR-012-04/-06만 개정일을 2026-11-10으로 갱신했습니다. 백엔드 43개 통과는 기존 확인 기록입니다."]];
sheet.getRange("K8").values = [["현황 확인일"]];
sheet.getRange("K9:K73").values = Array.from({ length: 65 }, () => [statusReviewDate]);
sheet.getRange("J9:J73").values = Array.from({ length: 65 }, () => [oldRevisionDate]);

const ids = sheet.getRange("C9:C73").values.flat();
for (let i = 0; i < ids.length; i++) {
  if (["SFR-012-04", "SFR-012-06"].includes(ids[i])) sheet.getRange(`J${i + 9}`).values = [[statusReviewDate]];
}

sheet.getRange("K:K").format.columnWidth = 16;
sheet.getRange("J9:K73").setNumberFormat("yyyy-mm-dd");
sheet.getRange("J9:K73").format.horizontalAlignment = "center";
sheet.getRange("K8").format.fill = "#214B40";
sheet.getRange("K8").format.font = { name: "맑은 고딕", size: 9, bold: true, color: "#FFFFFF" };
sheet.getRange("K8").format.horizontalAlignment = "center";
for (let row = 9; row <= 73; row++) {
  sheet.getRange(`K${row}`).format.fill = row % 2 === 0 ? "#F2F7F4" : "#FFFFFF";
}
const note = sheet.getRange("A7:K7");
note.format.rowHeight = 32;
note.format.wrapText = false;
sheet.getRange("A7").format.fill = "#4C806F";
sheet.getRange("A7").format.font = { name: "맑은 고딕", size: 9, bold: true, color: "#FFFFFF" };
sheet.getRange("B7:K7").format.fill = "#E5EFEA";
sheet.getRange("B7:K7").format.font = { name: "맑은 고딕", size: 9, color: "#49675C" };

for (let i = 0; i < ids.length; i++) {
  const row = i + 9;
  const expectedRevision = ["SFR-012-04", "SFR-012-06"].includes(ids[i]) ? statusReviewDate : oldRevisionDate;
  if (sheet.getRange(`J${row}`).values[0][0] !== expectedRevision || sheet.getRange(`K${row}`).values[0][0] !== statusReviewDate) {
    throw new Error(`Date mismatch at ${ids[i]}.`);
  }
  if (sheet.getRange(`F${row}`).values[0][0] !== before[row - 1][5]) throw new Error(`Progress status changed at ${ids[i]}.`);
}

for (const [tag, range] of [["top", "A1:K24"], ["bottom", "A50:K73"]]) {
  const preview = await wb.render({ sheetName: "요구사항 정의서", range, scale: 1, format: "png" });
  await fs.writeFile(dir + `/date-preview-${tag}.png`, new Uint8Array(await preview.arrayBuffer()));
}
const file = await SpreadsheetFile.exportXlsx(wb);
await file.save(out);
const saved = await SpreadsheetFile.importXlsx(await FileBlob.load(out));
if (saved.worksheets.items.length !== 1) throw new Error("Output must contain only the requirements definition sheet.");
const result = saved.worksheets.getItem("요구사항 정의서");
const savedRows = result.getRange("C9:K73").values;
if (savedRows.length !== 65 || savedRows.some((r) => r[8] !== statusReviewDate)) throw new Error("Saved status check dates failed verification.");
console.log(JSON.stringify({ out, sheetCount: saved.worksheets.items.length, requirements: savedRows.length, originalRevisionDatePreserved: true, currentStatusReviewDate: "2026-11-10", scheduleUsedAsActual: false }));
