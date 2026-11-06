import test from 'node:test';
import assert from 'node:assert/strict';
import {calculateSalary, progressiveTax, earnedIncomeDeduction} from './salaryCalculator.js';
import {checkKoreanText, applySpellingCorrections} from './koreanSpellChecker.js';

test('annual and monthly entry yield the same pay, with reconciled totals',()=>{
 const yearly=calculateSalary({amount:3600,nonTaxable:20,dependents:1});
 const monthly=calculateSalary({mode:'monthly',amount:300,nonTaxable:20,dependents:1});
 assert.deepEqual(yearly,monthly);assert.equal(yearly.monthlyGross,3000000);assert.equal(yearly.pension,133000);assert.equal(yearly.health,100660);assert.equal(yearly.employment,25200);
 assert.equal(yearly.monthlyNet+yearly.totalDeduction,yearly.monthlyGross);assert.equal(yearly.annualNet,yearly.monthlyNet*12);
});
test('pension uses verified 2026 H2 floor and ceiling, not all salary',()=>{
 const max=calculateSalary({amount:20000,nonTaxable:0,dependents:1});assert.equal(max.pension,313025);
 const min=calculateSalary({mode:'monthly',amount:30,nonTaxable:0,dependents:1});assert.equal(min.pension,19475);
});
test('dependents and non-taxable pay cannot increase estimated income tax',()=>{
 const base=calculateSalary({amount:6000,nonTaxable:0,dependents:1});
 const family=calculateSalary({amount:6000,nonTaxable:0,dependents:3});
 const exempt=calculateSalary({amount:6000,nonTaxable:20,dependents:1});
 assert.ok(family.incomeTax<=base.incomeTax);assert.ok(exempt.incomeTax<=base.incomeTax);assert.ok(exempt.monthlyNet>=base.monthlyNet);
});
test('salary rejects missing, negative, non-finite and inconsistent values',()=>{
 for(const values of [{amount:''},{amount:Infinity},{amount:-1},{amount:40000},{amount:3600,dependents:1.5},{amount:3600,dependents:0},{amount:3600,nonTaxable:-1},{amount:3600,nonTaxable:300},{mode:'other',amount:3600}])assert.throws(()=>calculateSalary(values));
});
test('progressive tax and earned-income deduction have continuous boundaries',()=>{
 for(const boundary of [14000000,50000000,88000000,150000000])assert.ok(progressiveTax(boundary+1)-progressiveTax(boundary)<1);
 for(const boundary of [5000000,15000000,45000000,100000000])assert.ok(Math.abs(earnedIncomeDeduction(boundary+1)-earnedIncomeDeduction(boundary))<1);
 assert.equal(progressiveTax(14000000),840000);assert.equal(progressiveTax(50000000),6240000);
});
test('spelling fixes independent, repeated and combined findings in source order',()=>{
 const text='역활을 수행할수있습니다. 역활을 다할께요.';
 const result=checkKoreanText(text);assert.equal(result.issues.length,4);
 assert.equal(applySpellingCorrections(text,result.issues),'역할을 수행할 수 있습니다. 역할을 다할게요.');
 assert.equal(applySpellingCorrections(text,result.issues.slice(0,1)),'역할을 수행할수있습니다. 역활을 다할께요.');
});
test('spelling preserves correct expressions, line breaks and non-Korean input',()=>{
 const text='책임감 있게 역할을 수행할 수 있습니다. 방금새로운 내용을 받았습니다.\nReact, JavaScript, Figma';
 assert.deepEqual(checkKoreanText(text).issues,[]);assert.equal(applySpellingCorrections(text,[]),text);
});
test('spelling validates size and refuses corrections from changed source',()=>{
 assert.throws(()=>checkKoreanText('  '));assert.throws(()=>checkKoreanText('가'.repeat(5001)));
 const result=checkKoreanText('역활');assert.throws(()=>applySpellingCorrections('내용',result.issues));
});
