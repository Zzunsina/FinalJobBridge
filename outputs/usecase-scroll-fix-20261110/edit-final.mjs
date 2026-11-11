import fs from "node:fs/promises";
import { FileBlob, SpreadsheetFile } from "file:///C:/Program%20Files/WindowsApps/OpenAI.CodexPrimaryRuntime.v26-1104-2876-0_26.1104.2876.0_x64__3k8sg7r9htsxt/dependencies/node/node_modules/@oai/artifact-tool/dist/artifact_tool.mjs";
const source = "C:/FinalJobBridge/outputs/usecase-scroll-fix-20261110/잡브릿지_유스케이스명세서_스크롤수정.xlsx";
const outputPath = "C:/FinalJobBridge/outputs/usecase-scroll-fix-20261110/잡브릿지_유스케이스명세서_최종수정.xlsx";
const workbook = await SpreadsheetFile.importXlsx(await FileBlob.load(source));
const sheet = workbook.worksheets.getItem("유스케이스 명세서");
sheet.getRange("C41").values = [["광고 주문·테스트 결제"]];
sheet.getRange("E41").values = [["광고 상품·기간 가용성 확인 → 주문 생성 → PortOne 테스트 결제 요청 → 서버 결제 검증 또는 웹훅 처리"]];
sheet.getRange("F41").values = [["PortOne Sandbox(INICIS) 테스트 결제 기준. 결제 전 주문 철회 가능. 운영 실결제 계약·키와 구분"]];
sheet.getRange("A59").values = [["G08"]];
sheet.freezePanes.unfreeze();
workbook.recalculate();
for (const [name, range] of [["ads", "A39:F43"], ["home", "A57:F62"]]) {
  const preview = await workbook.render({ sheetName: "유스케이스 명세서", range, scale: 1, format: "png" });
  await fs.writeFile(`C:/FinalJobBridge/outputs/usecase-scroll-fix-20261110/${name}-changed.png`, new Uint8Array(await preview.arrayBuffer()));
}
const file = await SpreadsheetFile.exportXlsx(workbook);
await file.save(outputPath);
console.log(JSON.stringify({ outputPath, sheet: sheet.name, freezePaneAfter: sheet.freezePanes.getLocation?.() ?? null }));
