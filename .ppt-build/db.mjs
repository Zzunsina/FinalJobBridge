
import fs from 'node:fs/promises';
import {FileBlob,PresentationFile} from '@oai/artifact-tool';
const p=await PresentationFile.importPptx(await FileBlob.load('발표자료/JobBridge 발표.pptx'));
const sql=await fs.readFile('JobBridge/employment-backend/src/main/resources/db/schema.sql','utf8');
const defs=Object.fromEntries([...sql.matchAll(/CREATE TABLE IF NOT EXISTS (\w+) \(([\s\S]*?)\);/g)].map(m=>[m[1],m[2]]));
const groups=[
 ['회원과 기업',[
 ['users','user_id login_id email role status','회원 식별과 로그인, 권한 및 계정 상태'],
 ['job_seekers','job_seeker_id user_id desired_job desired_region','구직자 프로필과 희망 직무·지역'],
 ['companies','company_id user_id company_name business_number approval_status','기업 정보와 가입 심사 상태']]],
 ['채용공고와 지원',[
 ['job_postings','job_id company_id title start_date end_date status','공고의 게시 기간과 심사 상태'],
 ['resumes','resume_id job_seeker_id title is_default','구직자 이력서와 기본 이력서 여부'],
 ['applications','application_id job_id job_seeker_id resume_id status','제출한 이력서와 지원 진행 상태'],
 ['interviews','interview_id application_id interview_at interview_type status','입사 지원 면접 일정과 진행 상태']]],
 ['취업연계',[
 ['employment_links','employment_link_id job_seeker_id manager_user_id status','구직자 신청과 관리자 처리 상태'],
 ['employment_referrals','referral_id employment_link_id company_id review_status','기업 추천과 기업의 검토 상태'],
 ['employment_interview_proposals','proposal_id referral_id interview_at user_response','면접 일정 제안과 구직자 응답'],
 ['employment_onboarding','referral_id start_at joined_at','입사 예정일과 입사 확인 시각']]]
];
const ink='#162E49',blue='#245CA7',muted='#526681';
function text(sl,name,value,left,top,width,height,size,color,bold=false){
 const sh=sl.shapes.add({name,geometry:'textbox',position:{left,top,width,height},fill:'none'});
 sh.text=value;sh.text.style={typeface:'Malgun Gothic',fontSize:size,bold,color,verticalAlignment:'middle',insets:{left:0,right:0,top:0,bottom:0}};
}
for(const [i,[topic,items]] of groups.entries()){
 const sl=p.slides.add();sl.background.fill='#FFFFFF';
 text(sl,'Title','테이블 명세 · '+topic,88,66,1104,82,50,ink,true);
 text(sl,'Subtitle','핵심 컬럼의 자료형과 기본키·외래키 관계',90,158,1100,50,26,muted);
 const values=[['테이블','기본키와 외래키','주요 컬럼 및 자료형','저장 내용']];
 for(const [name,fields,desc] of items){
  const ddl=defs[name];
  const pk=ddl.match(/(\w+)\s+BIGINT(?: AUTO_INCREMENT)? PRIMARY KEY/)[1];
  const fks=[...ddl.matchAll(/FOREIGN KEY\s*\((\w+)\)/g)].map(m=>m[1]);
  const list=fields.split(' ');
  const keys=['PK  '+pk,...list.filter(f=>fks.includes(f)).map(f=>'FK  '+f)].join('\n');
  const details=list.filter(f=>f!==pk&&!fks.includes(f)).map(f=>{
   const t=ddl.match(new RegExp('\\b'+f+'\\s+([A-Z]+(?:\\([0-9,]+\\))?)'))?.[1];
   if(!t)throw Error(name+'.'+f);
   return f+'  '+t;
  }).join('\n');
  values.push([name.replace('employment_interview_proposals','employment_interview_\nproposals'),keys,details,desc]);
 }
 const table=sl.tables.add({rows:values.length,columns:4,left:90,top:224,width:1100,height:400,columnWidths:[265,215,295,325],values});
 table.styleOptions={headerRow:false,bandedRows:false};
 table.borders.assign({style:'solid',fill:'#D7E2EF',width:1});
 for(let row=0;row<values.length;row++){
  table.rows[row].height=row===0?45:(items.length===3?103:88);
  for(let col=0;col<4;col++){
   const cell=table.getCell(row,col);
   cell.fill=row===0?blue:row%2?'#F1F6FC':'#FFFFFF';
   cell.text.style={typeface:'Malgun Gothic',fontSize:17,color:row===0?'#FFFFFF':ink,bold:row===0||col===0};
  }
 }
 text(sl,'Source','JobBridge  /  schema.sql 기준 · PK/FK 자료형 BIGINT',92,650,900,25,16,muted);
 text(sl,'Slide number',String(i+8).padStart(2,'0'),1151,650,50,25,16,muted);
 const blob=await p.export({slide:sl,format:'png',scale:0.8});
 await fs.writeFile('.ppt-build/db-'+i+'.png',new Uint8Array(await blob.arrayBuffer()));
}
await (await PresentationFile.exportPptx(p)).save('.ppt-build/with-db.pptx');

