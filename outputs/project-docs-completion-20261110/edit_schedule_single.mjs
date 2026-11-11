import fs from "node:fs/promises";
import {FileBlob,SpreadsheetFile} from "file:///C:/Program%20Files/WindowsApps/OpenAI.CodexPrimaryRuntime.v26-1104-2876-0_26.1104.2876.0_x64__3k8sg7r9htsxt/dependencies/node/node_modules/@oai/artifact-tool/dist/artifact_tool.mjs";

const source="C:/Users/hi101/Desktop/잡브릿지.xlsx";
const output="C:/FinalJobBridge/outputs/project-docs-completion-20261110/잡브릿지_팀일정표_수정본.xlsx";
const workbook=await SpreadsheetFile.importXlsx(await FileBlob.load(source));
const schedule=workbook.worksheets.getItem("일정표");
const fixedRanges=["A1:H5","A7:H7","A9:H9","A11:H11"];
const fixedValues=fixedRanges.map(range=>JSON.stringify(schedule.getRange(range).values));

// Keep the existing calendar, dates, sizing, colors, merged cells and notes. Replace task text only.
schedule.getRange("B6:F6").values=[[
  "팀 킥오프·요구사항 확정\n6개 분야·30개 직무 범위 정리",
  "역할 분장·WBS 작성\n화면·DB·API 기준 합의",
  "공통 화면·인증 기반 구성\n개발 환경·DB 연결",
  "회원·기업 정보 흐름 구현\n기업 승인 기준 연결",
  "이력서·공고 기본 기능 구현\n개발용 기업·공고 데이터 준비"
]];
schedule.getRange("B8:F8").values=[[
  "공고·기업 목록과 상세 구현\n공개 조회 API 연결",
  "검색·지역 필터·정렬 구현\n공고 작성·심사 흐름 연결",
  "입사지원·이력서 선택 구현\n지원 시점 정보 저장",
  "지원자 관리·면접·알림 연결\n관리자 심사 기능 통합",
  "역할별 화면·API 연결\n권한·오류 흐름 점검"
]];
schedule.getRange("B10:F10").values=[[
  "적성검사·진로탐색 구현\n응시 결과·이력 연결",
  "맞춤법·급여 도구 구현\n취업연계 추천·면접 제안",
  "면접 후기·참석 확인 구현\n증빙·중재 흐름 연결",
  "광고 주문·테스트 결제 연결\n홈 프로모션 관리 구현",
  "광고 심사·게시·환불 연결\n통합 시나리오 점검"
]];
schedule.getRange("B12:E12").values=[[
  "통합 테스트·오류 수정\n팀별 결과 취합",
  "요구사항·WBS·테이블 명세\n코드 기준 최종 대조",
  "최종 회귀 확인\n발표·시연 리허설",
  "프로젝트 발표\n최종 산출물 제출"
]];

for(const sheet of [...workbook.worksheets.items])if(sheet.name!=="일정표")sheet.delete();
workbook.recalculate();
if(workbook.worksheets.items.length!==1||workbook.worksheets.items[0].name!=="일정표")throw new Error("일정표 단독 파일 구성이 아닙니다.");
const xlsx=await SpreadsheetFile.exportXlsx(workbook);await xlsx.save(output);

const saved=await SpreadsheetFile.importXlsx(await FileBlob.load(output));
const finalSheet=saved.worksheets.getItem("일정표");
if(JSON.stringify(finalSheet.getRange("B6:F6").values)!==JSON.stringify(schedule.getRange("B6:F6").values))throw new Error("1주 차 팀 일정이 저장되지 않았습니다.");
for(let i=0;i<fixedRanges.length;i++)if(JSON.stringify(finalSheet.getRange(fixedRanges[i]).values)!==fixedValues[i])throw new Error(`기존 날짜·제목 범위가 바뀌었습니다: ${fixedRanges[i]}`);
const preview=await saved.render({sheetName:"일정표",range:"A1:H12",scale:1,format:"png"});
await fs.writeFile("C:/FinalJobBridge/outputs/project-docs-completion-20261110/schedule-team-preview.png",new Uint8Array(await preview.arrayBuffer()));
console.log(JSON.stringify({output,sheetCount:saved.worksheets.items.length,changedRanges:["B6:F6","B8:F8","B10:F10","B12:E12"],datesAndFormattingPreserved:true,teamSchedule:true}));
