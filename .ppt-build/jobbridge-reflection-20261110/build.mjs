import fs from "node:fs/promises";
import { FileBlob, PresentationFile } from "file:///C:/Program%20Files/WindowsApps/OpenAI.CodexPrimaryRuntime.v26-1104-2876-0_26.1104.2876.0_x64__3k8sg7r9htsxt/dependencies/node/node_modules/@oai/artifact-tool/dist/artifact_tool.mjs";

const source = "C:/Users/hi101/Desktop/JOBBRIDGE (1).pptx";
const buildDir = "C:/FinalJobBridge/.ppt-build/jobbridge-reflection-20261110";
await fs.mkdir(buildDir, { recursive: true });
const presentation = await PresentationFile.importPptx(await FileBlob.load(source));
if (presentation.slides.items.length !== 82) throw new Error(`Expected 82 slides; found ${presentation.slides.items.length}`);
const slide = presentation.slides.items[80];
const title = presentation.resolve("sh/na5kje1o");
const placeholder = presentation.resolve("sh/ihkzuxs3");
if (title.text.toString?.() && !String(title.text).includes("프로젝트 소감과 개선 방향")) throw new Error("Target slide title changed; aborting.");
const before = await presentation.export({ slide, format: "png", scale: 1 });
await fs.writeFile(`${buildDir}/slide-before.png`, new Uint8Array(await before.arrayBuffer()));

const reflections = [
  { name: "서상훈", role: "프론트엔드", text: "공개 채용부터 관리자 화면까지 구현하며 사용자별 동선과 공통 UI의 중요성을 배웠습니다. 다음에는 화면 규칙을 초기에 정리해 조정 시간을 줄이고 싶습니다." },
  { name: "이석현", role: "프론트엔드", text: "이력서·지원·취업연계 화면을 연결하며 API 상태와 화면 흐름을 함께 설계해야 함을 느꼈습니다. 다음에는 연동 규칙을 더 일찍 공유하겠습니다." },
  { name: "전진영", role: "백엔드", text: "인증·이력서·진로검사 기능을 맡으며 권한과 데이터 검증이 서비스 신뢰에 직결됨을 배웠습니다. 예외 흐름과 데이터 제약을 더 일찍 합의하고 싶습니다." },
  { name: "김경우", role: "백엔드", text: "관리자 운영·문의·알림·취업연계를 다루며 운영 흐름도 서비스의 중요한 일부임을 배웠습니다. 다음에는 상태 변경과 알림 조건을 시나리오로 먼저 맞추겠습니다." },
  { name: "정영록", role: "백엔드", text: "공고·지원·면접과 광고 주문을 구현하며 기능 간 상태 연결의 중요성을 느꼈습니다. 외부 결제는 테스트와 운영 환경을 나눠 더 꼼꼼히 검증하고 싶습니다." },
];

function addText(name, text, position, style) {
  const shape = slide.shapes.add({
    geometry: "textbox",
    name,
    position,
    fill: "none",
    line: { style: "solid", fill: "none", width: 0 },
  });
  shape.text = text;
  shape.text.style = style;
  return shape;
}

const left = 52;
const width = 856;
const cardHeight = 50;
const gap = 6;
const firstTop = 121;
reflections.forEach((item, index) => {
  const top = firstTop + index * (cardHeight + gap);
  slide.shapes.add({
    geometry: "roundRect",
    name: `Retrospective card ${index + 1}`,
    position: { left, top, width, height: cardHeight },
    fill: "#FFFFFF",
    line: { style: "solid", fill: "#DDE5F0", width: 0.8 },
  });
  slide.shapes.add({
    geometry: "rect",
    name: `Retrospective accent ${index + 1}`,
    position: { left: left + 1, top: top + 9, width: 3, height: 32 },
    fill: "#2471E6",
    line: { style: "solid", fill: "none", width: 0 },
  });
  addText(`Retrospective name ${item.name}`, item.name,
    { left: left + 16, top: top + 7, width: 100, height: 18 },
    { fontSize: 14, bold: true, color: "#0F172A" });
  addText(`Retrospective role ${item.name}`, item.role,
    { left: left + 16, top: top + 27, width: 100, height: 14 },
    { fontSize: 10, color: "#2471E6" });
  slide.shapes.add({
    geometry: "rect",
    name: `Retrospective divider ${index + 1}`,
    position: { left: left + 126, top: top + 9, width: 1, height: 32 },
    fill: "#E4EAF2",
    line: { style: "solid", fill: "none", width: 0 },
  });
  addText(`Retrospective reflection ${item.name}`, item.text,
    { left: left + 143, top: top + 6, width: width - 160, height: 39 },
    { fontSize: 12.5, color: "#334155", alignment: "left" });
});

placeholder.text.replace(
  "개인별 소감 문구는 실제 발표자가 작성해 반영",
  "공통 개선 방향 · 기능 간 API·상태 기준을 초기에 합의하고, 예외 흐름까지 통합 검증하겠습니다."
);
const after = await presentation.export({ slide, format: "png", scale: 1 });
await fs.writeFile(`${buildDir}/slide-after.png`, new Uint8Array(await after.arrayBuffer()));
const layout = await slide.export({ format: "layout" });
await fs.writeFile(`${buildDir}/slide-after.layout.json`, await layout.text());
const output = await PresentationFile.exportPptx(presentation);
await output.save(`${buildDir}/candidate.pptx`);
const summary = await presentation.inspect({ kind: "slide,textbox,shape", search: "프로젝트 소감과 개선 방향", maxChars: 5000 });
await fs.writeFile(`${buildDir}/inspect-after.ndjson`, summary.ndjson, "utf8");
console.log(JSON.stringify({ slideCount: presentation.slides.items.length, targetIndex: 80, addedReflectionRows: reflections.length, candidate: `${buildDir}/candidate.pptx` }));

