export const SALARY_RULES = Object.freeze({
  year: 2026, period: '2026년 하반기', pensionRate: 0.095, pensionMin: 410000,
  pensionMax: 6590000, healthEmployeeRate: 0.03595, healthEmployeeMin: 10080,
  careRatio: 0.9448 / 7.19, employmentRate: 0.009,
});
const cut10 = (value) => Math.floor((Math.max(0, value) + 1e-7) / 10) * 10;
export function earnedIncomeDeduction(gross) {
  if (gross <= 5000000) return gross * 0.7;
  if (gross <= 15000000) return 3500000 + (gross - 5000000) * 0.4;
  if (gross <= 45000000) return 7500000 + (gross - 15000000) * 0.15;
  if (gross <= 100000000) return 12000000 + (gross - 45000000) * 0.05;
  return Math.min(20000000, 14750000 + (gross - 100000000) * 0.02);
}
export function progressiveTax(base) {
  const bands = [[14000000,.06],[50000000,.15],[88000000,.24],[150000000,.35],[300000000,.38],[500000000,.4],[1000000000,.42],[Infinity,.45]];
  let tax=0, previous=0;
  for (const [ceiling,rate] of bands) { tax+=Math.max(0,Math.min(base,ceiling)-previous)*rate; if(base<=ceiling) break; previous=ceiling; }
  return tax;
}
function creditLimit(gross) {
  if(gross<=33000000) return 740000;
  if(gross<=70000000) return Math.max(660000,740000-(gross-33000000)*.008);
  if(gross<=120000000) return Math.max(500000,660000-(gross-70000000)*.5);
  return Math.max(200000,500000-(gross-120000000)*.5);
}
function annualIncomeTax(base,gross,standardCredit=0) {
  const tax=progressiveTax(Math.max(0,base));
  const credit=Math.min(creditLimit(gross),tax<=1300000?tax*.55:715000+(tax-1300000)*.3);
  return Math.max(0,tax-credit-standardCredit);
}
export function calculateSalary({mode='annual',amount,nonTaxable=20,dependents=1}) {
  const pay=Number(amount),exempt=Number(nonTaxable),people=Number(dependents);
  if(!['annual','monthly'].includes(mode)||String(amount).trim()===''||!Number.isFinite(pay)||pay<=0) throw new Error('세전 급여를 0보다 큰 숫자로 입력해 주세요.');
  const annualGross=Math.round(pay*10000*(mode==='monthly'?12:1));
  if(annualGross>300000000) throw new Error('연봉 3억 원 이하의 급여를 입력해 주세요.');
  if(String(nonTaxable).trim()===''||!Number.isFinite(exempt)||exempt<0) throw new Error('월 비과세액을 0 이상의 숫자로 입력해 주세요.');
  if(!Number.isInteger(people)||people<1||people>11) throw new Error('부양가족 수는 본인 포함 1~11명으로 선택해 주세요.');
  const monthlyGross=Math.floor(annualGross/12),monthlyExempt=Math.round(exempt*10000);
  if(monthlyExempt>=monthlyGross) throw new Error('월 비과세액은 세전 월급보다 작아야 합니다.');
  const taxable=monthlyGross-monthlyExempt;
  const pensionBase=Math.min(SALARY_RULES.pensionMax,Math.max(SALARY_RULES.pensionMin,Math.floor(taxable/1000)*1000));
  const pension=cut10(pensionBase*SALARY_RULES.pensionRate)/2;
  const health=Math.max(SALARY_RULES.healthEmployeeMin,cut10(taxable*SALARY_RULES.healthEmployeeRate));
  const care=cut10(health*SALARY_RULES.careRatio),employment=cut10(taxable*SALARY_RULES.employmentRate);
  const taxableAnnual=taxable*12;
  const base=taxableAnnual-earnedIncomeDeduction(taxableAnnual)-people*1500000-pension*12;
  // Standard credit and special insurance deductions are alternatives, not cumulative benefits.
  const tax=Math.min(annualIncomeTax(base,taxableAnnual,130000),annualIncomeTax(base-(health+care+employment)*12,taxableAnnual));
  const incomeTax=cut10(tax/12),localTax=cut10(incomeTax*.1);
  const deductions={pension,health,care,employment,incomeTax,localTax};
  const totalDeduction=Object.values(deductions).reduce((sum,value)=>sum+value,0);
  const monthlyNet=Math.max(0,monthlyGross-totalDeduction);
  return {annualGross,monthlyGross,monthlyExempt,taxable,...deductions,totalDeduction,monthlyNet,annualNet:monthlyNet*12};
}
