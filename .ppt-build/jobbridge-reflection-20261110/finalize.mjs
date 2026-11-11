import path from "node:path";
import { pathToFileURL } from "node:url";
process.env.RUNTIME_NODE_MODULES = "C:/Program Files/WindowsApps/OpenAI.CodexPrimaryRuntime.v26-1104-2876-0_26.1104.2876.0_x64__3k8sg7r9htsxt/dependencies/node/node_modules";
const skillDir = "C:/Users/hi101/.codex/plugins/cache/openai-primary-runtime/presentations/26.1104.22356/skills/Presentations";
const { finalizePresentation } = await import(pathToFileURL(path.join(skillDir, "container_tools/artifact_tool_utils.mjs")).href);
const result = await finalizePresentation({
  workspaceDir: "C:/FinalJobBridge",
  candidatePath: "C:/FinalJobBridge/.ppt-build/jobbridge-reflection-20261110/candidate.pptx",
  finalPath: "C:/FinalJobBridge/outputs/jobbridge-retrospective-final-20261110/JOBBRIDGE_프로젝트소감추가본.pptx",
  pythonExecutable: "C:/Program Files/WindowsApps/OpenAI.CodexPrimaryRuntime.v26-1104-2876-0_26.1104.2876.0_x64__3k8sg7r9htsxt/dependencies/python/python.exe",
  integrityValidatorPath: path.join(skillDir, "container_tools/inspect_presentation_package_integrity.py"),
  layoutValidatorPath: path.join(skillDir, "container_tools/inspect_presentation_layout_geometry.py"),
  layoutArgs: ["--expected-slide-size-emu", "9144000,5143500", "--validate-bullet-geometry", "--validate-heading-fit"],
  explicitTotalSlideCount: 82,
  requiredNativeTableOwnerSlides: [],
  requiredNativeChartOwnerSlides: [],
  verifyArtifactToolImport: true,
  receiptPath: "C:/FinalJobBridge/.ppt-build/jobbridge-reflection-20261110/validation.json",
});
console.log(JSON.stringify(result));


