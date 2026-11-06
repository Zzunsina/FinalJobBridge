
import fs from 'node:fs/promises';
import {FileBlob,PresentationFile} from '@oai/artifact-tool';
const p=await PresentationFile.importPptx(await FileBlob.load('피피티.pptx'));
const cover=p.slides.items[0];
async function render(slide,file){const blob=await p.export({slide,format:'png',scale:0.8});await fs.writeFile(file,new Uint8Array(await blob.arrayBuffer()));}
try{await render(cover,'.ppt-build/original.png')}catch(e){console.log(e.message)}
function tx(sl,name,str,x,y,w,h,size,color,bold=false){
 const sh=sl.shapes.add({name,geometry:'textbox',position:{left:x,top:y,width:w,height:h},fill:'none'});
 sh.text=str;sh.text.style={typeface:'Malgun Gothic',fontSize:size,bold,color,verticalAlignment:'middle',insets:{left:0,right:0,top:0,bottom:0}};return sh;
}
const ink='#162E49', blue='#245CA7', muted='#526681';
for(const sh of [...cover.shapes.items]) sh.delete();
tx(cover,'Project name','JobBridge',88,208,1104,110,88,ink,true);
tx(cover,'Subtitle','채용과 취업연계를 한곳에서',92,333,1096,70,43,blue,true);
tx(cover,'Presentation type','프로젝트 발표',94,517,900,45,24,muted);
const slides=[
['프로젝트 배경','공고 지원과 취업연계 진행을 한 서비스에서 확인합니다.',[
['구직자','지원 결과와 면접 일정을 지원 내역에서 확인합니다.'],
['기업','공고를 등록하고 지원자 검토부터 면접 일정까지 관리합니다.'],
['관리자','기업과 공고를 심사하고 취업연계 요청을 처리합니다.']]],
['채용공고 지원','구직자가 제출한 지원서를 기준으로 채용 절차가 이어집니다.',[
['01  공고 탐색','공고 목록과 상세에서 모집 조건을 확인합니다.'],
['02  이력서 제출','작성한 이력서를 선택해 해당 공고에 지원합니다.'],
['03  면접과 결과','기업이 지원자를 검토하고, 구직자가 상태와 일정을 확인합니다.']]],
['관리자 주도 취업연계','관리자가 신청을 접수하고 구직자를 기업에 추천합니다.',[
['01  연계 신청','구직자가 취업연계를 신청하면 관리자가 요청을 확인합니다.'],
['02  기업 추천','관리자가 후보를 추천하고, 기업이 검토 결과를 회신합니다.'],
['03  면접 조율','기업이 면접 일정을 제안하고 구직자가 응답합니다.']]],
['기업과 관리자 운영','기업 가입과 공고 게시에 각각 심사 단계가 있습니다.',[
['기업 심사','관리자가 가입한 기업 정보를 확인하고 승인 또는 반려합니다.'],
['공고 심사','기업이 작성한 공고를 검토한 뒤 게시 여부를 결정합니다.'],
['운영 기능','관리자 화면에서 공지, 문의, 취업연계 요청을 관리합니다.']]],
['시스템 구성','React 화면이 REST API를 호출하고 서버가 업무 규칙을 처리합니다.',[
['프런트엔드','React · Vite   /   역할별 화면과 공고·지원 정보 조회'],
['백엔드','Java 17 · Spring Boot   /   인증·인가, 지원 및 연계 상태 처리'],
['데이터','Spring Data JPA · MySQL   /   회원, 공고, 이력서, 지원 기록 저장']]],
['시연 순서','공고 승인부터 지원 상태 확인까지 역할을 바꿔가며 보여줍니다.',[
['01  기업 계정','공고를 작성하고 심사를 요청합니다.'],
['02  관리자 계정','심사 대기 공고를 확인하고 게시를 승인합니다.'],
['03  구직자 계정','게시된 공고에 지원하고 내 지원 내역을 확인합니다.']]]
];
for(const [i,[title,desc,rows]] of slides.entries()){
 const sl=p.slides.add();sl.background.fill='#FFFFFF';
 tx(sl,'Title',title,88,66,1104,82,53,ink,true);
 tx(sl,'Summary',desc,90,162,1102,53,29,muted);
 rows.forEach(([label,body],j)=>{let y=266+j*102;tx(sl,'Label '+j,label,92,y,238,54,26,blue,true);tx(sl,'Detail '+j,body,336,y,852,54,27,ink);});
 tx(sl,'Footer','JobBridge',92,650,250,25,16,muted);
 tx(sl,'Slide number',String(i+2).padStart(2,'0'),1151,650,50,25,16,muted);
}
const draft='C:/FinalJobBridge/.ppt-build/draft.pptx';
await (await PresentationFile.exportPptx(p)).save(draft);
const {finalizePresentation}=await import(new URL('file:///'+"C:/Users/hi101/.codex/plugins/cache/openai-primary-runtime/presentations/26.1030.10813/skills/presentations"+'/container_tools/artifact_tool_utils.mjs'));
try{await finalizePresentation({workspaceDir:'C:/FinalJobBridge',candidatePath:draft,finalPath:'C:/FinalJobBridge/발표자료/JobBridge 발표.pptx',pythonExecutable:"C:/Program Files/WindowsApps/OpenAI.CodexPrimaryRuntime.v26-1030-493-0_26.1030.493.0_x64__3k8sg7r9htsxt/dependencies/python/python.exe",integrityValidatorPath:"C:/Users/hi101/.codex/plugins/cache/openai-primary-runtime/presentations/26.1030.10813/skills/presentations/container_tools/inspect_presentation_package_integrity.py",layoutValidatorPath:"C:/Users/hi101/.codex/plugins/cache/openai-primary-runtime/presentations/26.1030.10813/skills/presentations/container_tools/inspect_presentation_layout_geometry.py",layoutArgs:['--expected-slide-size-emu','12192000,6858000'],verifyArtifactToolImport:true,receiptPath:'C:/FinalJobBridge/.ppt-build/validation.json'});}catch(e){console.log(e.stack);}
for(const [i,sl] of p.slides.items.entries())try{await render(sl,'.ppt-build/slide-'+(i+1)+'.png')}catch(e){console.log(e.message)}

