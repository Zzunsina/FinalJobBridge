
import fs from 'node:fs/promises';
import {FileBlob,PresentationFile} from '@oai/artifact-tool';
const p=await PresentationFile.importPptx(await FileBlob.load('발표자료/JobBridge 발표 수정본.pptx'));
const db='JobBridge/employment-backend/src/main/resources/db/';
const files=['schema.sql',...(await fs.readdir(db)).filter(f=>f.endsWith('.sql')&&f!=='schema.sql')];
const defs=new Map();
for(const file of files){
 const sql=await fs.readFile(db+file,'utf8');
 for(const m of sql.matchAll(/CREATE TABLE IF NOT EXISTS (\w+)\s*\(([\s\S]*?)\)\s*(?:ENGINE[^;]*)?;/gi))
  if(!defs.has(m[1]))defs.set(m[1],{ddl:m[2],file});
}
const groups=[
 ['회원 및 인증','users job_seekers companies auth_sessions password_reset_tokens user_password_history user_signup_consents account_suspensions'],
 ['프로필 및 콘텐츠','user_profile_photos company_images notices notice_images customer_faqs inquiries notifications'],
 ['채용과 지원','job_postings job_bookmarks resumes resume_files applications application_snapshots interviews interview_attendance_requests'],
 ['취업연계','employment_links employment_preferences employment_referrals employment_interview_coordination employment_interview_proposals employment_onboarding employment_company_reviews'],
 ['진로·평가 및 후기','career_assessment_attempts career_exploration_attempts resume_assessments job_assessment_settings application_assessments interview_reviews interview_review_moderation member_reports review_business_holidays'],
 ['광고 및 홈 운영','ad_products ad_orders ad_order_simulations ad_feature_upgrades home_promotions home_promotion_deletions']
];
const names=groups.flatMap(([,n])=>n.split(' '));
if(new Set(names).size!==defs.size||names.some(n=>!defs.has(n))) throw Error(JSON.stringify([...defs.keys()].filter(n=>!names.includes(n))));
const ink='#10253E',teal='#127B6B',gray='#51647A';
function tx(sl,t,left,top,width,height,fontSize,color,bold=false){
 const sh=sl.shapes.add({name:t,geometry:'textbox',position:{left,top,width,height},fill:'none'});
 sh.text=t;sh.text.style={typeface:'Malgun Gothic',fontSize,color,bold,insets:{left:0,right:0,top:0,bottom:0}};
}
for(const [i,[topic,group]] of groups.entries()){
 const sl=p.slides.add();sl.background.fill='#FFFFFF';
 tx(sl,'부록  /  전체 테이블 목록',76,43,1120,30,19,teal,true);
 tx(sl,topic,76,85,1100,64,40,ink,true);
 tx(sl,'SQL 정의 기준  '+group.split(' ').length+'개  /  전체 '+defs.size+'개',78,158,1100,42,23,gray);
 const vals=[['테이블','기본키 (PK)','외래키가 참조하는 테이블','정의 위치']];
 for(const name of group.split(' ')){
  const {ddl,file}=defs.get(name);
  const pk=ddl.match(/PRIMARY KEY\s*\(([^)]+)\)/i)?.[1]??ddl.match(/\b(\w+)\s+[A-Z]+(?:\([\d,]+\))?(?:\s+NOT NULL)?(?:\s+AUTO_INCREMENT)?\s+PRIMARY KEY/i)?.[1]??'—';
  const refs=[...new Set([...ddl.matchAll(/REFERENCES\s+(\w+)/gi)].map(m=>m[1]))];
  vals.push([name,pk,refs.join(', ')||'—',file]);
 }
 const tb=sl.tables.add({rows:vals.length,columns:4,left:76,top:215,width:1128,height:420,columnWidths:[320,205,351,252],values:vals});
 tb.styleOptions={headerRow:false,bandedRows:false};
 tb.borders.assign({style:'solid',fill:'#D7E2EF',width:1});
 for(let j=0;j<vals.length;j++){
  tb.rows[j].height=j===0?37:42;
  for(let k=0;k<4;k++){
   const cell=tb.getCell(j,k);cell.fill=j===0?teal:j%2?'#F1F6FC':'#FFFFFF';
   cell.text.style={typeface:'Malgun Gothic',fontSize:16,color:j===0?'#FFFFFF':ink,bold:j===0||k===0};
  }
 }
 tx(sl,'같은 테이블의 중복 정의는 한 번만 표시',76,665,1020,25,15,gray);
 tx(sl,String(i+11),1165,665,45,25,15,gray);
 const img=await p.export({slide:sl,format:'png',scale:.72});
 await fs.writeFile('.ppt-build/catalog-'+i+'.png',new Uint8Array(await img.arrayBuffer()));
}
await (await PresentationFile.exportPptx(p)).save('.ppt-build/catalog.pptx');
console.log(defs.size);

