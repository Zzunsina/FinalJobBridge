const literalRules = [
 ['됬','됐','잘못 쓰기 쉬운 활용입니다.'],['되요','돼요','‘되어요’를 줄인 말은 ‘돼요’입니다.'],
 ['할께','할게','약속·의지를 나타낼 때는 ‘-ㄹ게’를 씁니다.'],['갈께','갈게','‘-ㄹ게’를 씁니다.'],
 ['볼께','볼게','‘-ㄹ게’를 씁니다.'],['줄께','줄게','‘-ㄹ게’를 씁니다.'],['쓸께','쓸게','‘-ㄹ게’를 씁니다.'],
 ['몇일','며칠','날짜나 기간을 나타낼 때는 ‘며칠’입니다.'],['왠만','웬만','‘웬만하다’가 바른 표현입니다.'],
 ['웬지','왠지','‘왜인지’가 줄어든 말은 ‘왠지’입니다.'],['어의없','어이없','‘어이없다’가 바른 표현입니다.'],
 ['오랫만','오랜만','‘오래간만’의 준말은 ‘오랜만’입니다.'],['역활','역할','맡은 일이나 기능은 ‘역할’입니다.'],
 ['설겆이','설거지','‘설거지’가 바른 표현입니다.'],['희안','희한','‘희한하다’가 바른 표현입니다.'],
 ['꼼꼼이','꼼꼼히','‘꼼꼼히’가 바른 표현입니다.'],['곰곰히','곰곰이','‘곰곰이’가 바른 표현입니다.'],
 ['일일히','일일이','‘일일이’가 바른 표현입니다.'],['깨끗히','깨끗이','‘깨끗이’가 바른 표현입니다.'],
 ['틈틈히','틈틈이','‘틈틈이’가 바른 표현입니다.'],['곧곧이','곧곧이',''],
 ['통채로','통째로','전부라는 뜻에는 ‘통째로’를 씁니다.'],['뵈요','봬요','‘뵈어요’를 줄이면 ‘봬요’입니다.'],
 ['내 꺼','내 거','‘것’을 구어적으로 이르는 말은 ‘거’입니다.'],['되서','돼서','‘되어서’의 준말은 ‘돼서’입니다.'],
 ['금새','금세','‘금시에’가 줄어든 말은 ‘금세’입니다.'],['봣','봤','‘보았다’를 줄인 활용입니다.'],
 ['너무나도도','너무나도','중복된 조사를 확인해 주세요.'],['뒷처리','뒤처리','‘뒤처리’가 바른 표현입니다.'],
 ['구지','굳이','일부러라는 뜻에는 ‘굳이’를 씁니다.'],['쫒아가','쫓아가','뒤를 따라갈 때는 ‘쫓아가다’입니다.'],
];
const rules=literalRules.filter(([from,to])=>from!==to).map(([from,to,message],index)=>({id:'word-'+index,type:'맞춤법',pattern:new RegExp(['구지','금새'].includes(from)?'(?<![가-힣])'+from+'(?![가-힣])':from,'gu'),replace:()=>to,message}));
rules.push(
 {id:'can',type:'띄어쓰기',pattern:/(할|될|볼|갈|올|알|받을|만날|일할|도울)\s*수\s*(있|없)/gu,replace:(_match,verb,state)=>`${verb} 수 ${state}`,message:'가능성을 나타내는 의존 명사 ‘수’는 앞뒤를 띄어 씁니다.'},
 {id:'outside',type:'띄어쓰기',pattern:/수\s+밖에/gu,replace:()=> '수밖에',message:'‘수밖에’는 붙여 씁니다.'},
 {id:'thanks',type:'띄어쓰기',pattern:/감사\s+합니다/gu,replace:()=> '감사합니다',message:'‘감사하다’의 활용은 붙여 씁니다.'},
 {id:'effort',type:'띄어쓰기',pattern:/최선\s+을/gu,replace:()=> '최선을',message:'조사는 앞말에 붙여 씁니다.'},
);
export function checkKoreanText(source) {
 if(typeof source!=='string'||!source.trim())throw new Error('검사할 내용을 입력해 주세요.');
 if(source.length>5000)throw new Error('한 번에 5,000자까지 검사할 수 있습니다.');
 const candidates=[];
 for(const rule of rules){for(const match of source.matchAll(rule.pattern)){const after=rule.replace(...match);if(after!==match[0])candidates.push({id:`${rule.id}:${match.index}`,start:match.index,end:match.index+match[0].length,before:match[0],after,type:rule.type,message:rule.message});}}
 // Prefer longer matches at the same position; never apply intersecting edits.
 candidates.sort((a,b)=>a.start-b.start||(b.end-b.start)-(a.end-a.start));
 const issues=[];let end=-1;
 for(const candidate of candidates){if(candidate.start>=end){issues.push(candidate);end=candidate.end;}}
 return {source,issues};
}
export function applySpellingCorrections(source,issues) {
 let result=source;
 for(const item of [...issues].sort((a,b)=>b.start-a.start)){if(source.slice(item.start,item.end)!==item.before)throw new Error('내용이 변경되었습니다. 다시 검사해 주세요.');result=result.slice(0,item.start)+item.after+result.slice(item.end);}
 return result;
}
