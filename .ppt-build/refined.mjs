
import fs from 'node:fs/promises';
import {FileBlob,PresentationFile} from '@oai/artifact-tool';
const p=await PresentationFile.importPptx(await FileBlob.load('발표자료/JobBridge 발표.pptx'));
const navy='#10253E', mint='#9DE7D3', teal='#127B6B', gray='#51647A', white='#FFFFFF', paper='#F4F8FA';
function tx(sl,str,x,y,w,h,size,color,bold=false){
 const sh=sl.shapes.add({name:str.split('\n')[0],geometry:'textbox',position:{left:x,top:y,width:w,height:h},fill:'none'});
 sh.text=str;sh.text.style={typeface:'Malgun Gothic',fontSize:size,color,bold,insets:{top:0,bottom:0,left:0,right:0}};
}
async function pic(sl,key,left,top,width,height){
 const bytes=await fs.readFile('JobBridge-Frontend/public/images/home-promotion-v1/'+key+'.png');
 sl.images.add({blob:bytes.buffer.slice(bytes.byteOffset,bytes.byteOffset+bytes.byteLength),contentType:'image/png',fit:'cover',alt:'JobBridge 서비스 홍보 이미지',position:{left,top,width,height}});
}
const slides=p.slides.items;
for(const [i,sl] of slides.entries()){
 if(i<7)for(const sh of [...sl.shapes.items])sh.delete();
 sl.background.fill=white;
}
function footer(sl,i,dark=false){
 tx(sl,'JOBBRIDGE',76,665,200,22,15,dark?mint:gray,true);
 tx(sl,String(i).padStart(2,'0'),1165,665,45,22,15,dark?mint:gray);
}
function head(sl,tag,title,subtitle,i,dark=false){
 tx(sl,tag,76,53,1120,30,19,dark?mint:teal,true);
 tx(sl,title,76,100,1120,74,43,dark?white:navy,true);
 tx(sl,subtitle,78,189,1120,52,23,dark?'#C5D6E6':gray);
 footer(sl,i,dark);
}
const [cover,roles,apply,ref,ops,stack,demo]=slides;
tx(cover,'프로젝트 발표',76,94,510,42,24,teal,true);
tx(cover,'JobBridge',69,226,950,110,73,navy,true);
tx(cover,'채용 지원과 취업연계 플랫폼',76,357,950,52,31,navy,true);
tx(cover,'공고 심사부터 지원과 면접 일정 관리까지',77,522,900,62,24,gray);
head(roles,'서비스 개요','JobBridge의 사용자와 역할','구직자, 기업, 관리자가 같은 서비스에서 채용 진행 상황을 확인합니다.',2);
const roleRows=[['구직자','공고를 탐색하고 이력서를 제출합니다.\n내 지원 내역에서 결과와 일정을 확인합니다.'],['기업','공고를 등록하고 지원자를 검토합니다.\n면접 일정을 등록하고 결과를 처리합니다.'],['관리자','기업과 공고를 심사합니다.\n취업연계 요청을 접수하고 추천을 관리합니다.']];
roleRows.forEach(([a,b],i)=>{let x=76+i*380;tx(roles,'0'+(i+1),x,297,325,75,42,teal,true);tx(roles,a,x,389,325,55,30,navy,true);tx(roles,b,x,465,355,110,22,gray);});
head(apply,'핵심 기능 01','채용공고 지원','구직자가 공고를 선택해 직접 지원하는 경로입니다.',3);
function list(sl,rows,{x=76,y=277,step=105,width=585}={}){
 rows.forEach(([a,b],i)=>{const xx=76+i*380;tx(sl,'0'+(i+1),xx,307,320,74,42,teal,true);tx(sl,a,xx,404,356,56,31,navy,true);tx(sl,b,xx,480,344,75,22,gray);});
}
list(apply,[['공고 탐색','모집 조건과 지원 기간을 확인합니다.'],['이력서 선택 및 제출','작성한 이력서를 선택해 지원합니다.'],['지원 상태 확인','기업이 검토하고, 구직자는 일정과 결과를 봅니다.']]);
head(ref,'핵심 기능 02','관리자 주도 취업연계','취업연계 신청을 받은 관리자가 구직자를 기업에 추천합니다.',4);
list(ref,[['연계 신청 접수','관리자가 구직자의 연계 요청을 확인합니다.'],['기업 추천 및 회신','기업이 추천받은 후보를 검토하고 회신합니다.'],['면접 일정 조율','기업의 일정 제안에 구직자가 응답합니다.']],{x:602,width:595});
head(ops,'운영','기업 가입과 공고 게시 심사','관리자는 기업 등록과 채용공고 게시를 별도로 심사합니다.',5);
for(const [i,[tag,title,detail]] of [['기업 가입','기업 정보 심사','가입 정보를 검토해 승인 또는 반려합니다.'],['공고 등록','게시 전 공고 심사','작성된 공고를 검토해 게시 여부를 결정합니다.']].entries()){
 const x=76+i*573;tx(ops,tag,x,302,530,43,24,teal,true);tx(ops,title,x,353,530,62,31,navy,true);tx(ops,detail,x,431,548,83,24,gray);
}
tx(ops,'운영 범위   공지사항, 고객 문의, 취업연계 요청 관리',76,508,1130,40,20,teal,true);
head(stack,'기술 구성','화면, API, 데이터베이스','React 화면은 REST API를 호출하고, 서버는 권한과 상태 변경을 처리합니다.',6);
const layers=[['화면','React · Vite','구직자, 기업, 관리자별 화면'],['API','Java 17 · Spring Boot','인증·인가, 공고 심사, 지원 및 연계 처리'],['저장','Spring Data JPA · MySQL','회원, 공고, 이력서, 지원 기록 저장']];
layers.forEach(([a,b,c],i)=>{const y=270+i*91;tx(stack,a,76,y,175,51,30,teal,true);tx(stack,b,254,y,435,51,30,navy,true);tx(stack,c,693,y+5,516,51,23,gray);});
head(demo,'라이브 시연','공고 등록부터 입사 지원까지','공고 한 건을 기준으로 세 역할의 화면을 차례로 보여줍니다.',7);
[['기업','공고 작성','심사 요청까지 진행'],['관리자','게시 승인','심사 대기 목록에서 승인'],['구직자','공고 지원','이력서 제출 후 내 지원 내역 확인']].forEach(([role,action,desc],i)=>{const x=76+i*380;tx(demo,'0'+(i+1),x,303,320,76,42,teal,true);tx(demo,role,x,393,330,39,23,teal,true);tx(demo,action,x,445,350,65,32,navy,true);tx(demo,desc,x,504,360,53,22,gray);});
for(let i=7;i<10;i++){
 const sl=slides[i];const title=['회원과 기업','채용공고와 지원','취업연계'][i-7];
 for(const sh of [...sl.shapes.items])sh.delete();
 for(const table of sl.tables.items){ for(let j=0;j<4;j++)table.getCell(0,j).fill=teal; }
 tx(sl,'부록  /  테이블 명세',76,43,1120,30,19,teal,true);
 tx(sl,title,76,85,1100,66,40,navy,true);
 tx(sl,'핵심 컬럼과 키 관계',78,164,1100,43,24,gray);
 tx(sl,'schema.sql 기준   /   PK와 FK의 자료형은 BIGINT',76,665,1030,22,15,gray);
 tx(sl,String(i+1).padStart(2,'0'),1165,665,45,22,15,gray);
}

const details=[
 [apply,'제출 기록','지원 내역은 공고, 구직자, 제출 이력서를 연결해 관리합니다.'],
 [ref,'일정 응답','구직자는 제안을 수락하거나, 변경을 요청하거나, 거절할 수 있습니다.'],
 [ops,'공고 상태','작성 중인 공고는 심사 대기를 거쳐 게시되며, 반려된 공고는 사유를 확인할 수 있습니다.'],
 [stack,'데이터 연결','입사 지원과 취업연계는 별도 기록으로 관리하며, 각각의 면접 일정을 저장합니다.'],
 [demo,'시연 확인','승인한 공고가 구직자 화면에 표시되고, 제출한 지원서가 내 지원 내역에 나타나는지 확인합니다.'],
];
for(const [sl,label,body] of details){
 tx(sl,label,96,574,158,31,17,teal,true);
 tx(sl,body,260,572,922,52,19,navy);
}

await (await PresentationFile.exportPptx(p)).save('.ppt-build/refined.pptx');
for(const [i,sl] of slides.entries()){
 const img=await p.export({slide:sl,format:'png',scale:.72});
 await fs.writeFile('.ppt-build/refined-'+(i+1)+'.png',new Uint8Array(await img.arrayBuffer()));
}



