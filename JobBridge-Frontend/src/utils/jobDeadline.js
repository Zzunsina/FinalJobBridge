const DAY = 86400000;
function calendarDay(value) {
  if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(value)) return null;
  const stamp=Date.parse(value + 'T00:00:00Z');
  return Number.isFinite(stamp) && new Date(stamp).toISOString().slice(0,10) === value ? stamp / DAY : null;
}
export function jobDeadline(job, now = new Date()) {
  const parts=new Intl.DateTimeFormat('en-US', { timeZone:'Asia/Seoul', year:'numeric', month:'2-digit', day:'2-digit' }).formatToParts(now);
  const fields=Object.fromEntries(parts.map(({ type, value }) => [type,value]));
  const today=calendarDay(`${fields.year}-${fields.month}-${fields.day}`);
  const end=calendarDay(job.endDate), start=calendarDay(job.startDate);
  const days=end === null ? null : end-today;
  if ((job.status && job.status !== 'PUBLISHED') || (days !== null && days < 0)) return { accepting:false, tone:'closed', label:'접수 마감', note:'접수가 마감된 공고입니다.' };
  if (start !== null && start > today) return { accepting:false, tone:'scheduled', label:'접수 예정', note:`${job.startDate}부터 접수할 수 있어요.` };
  if (!job.accepting) return { accepting:false, tone:'closed', label:'접수 마감', note:'현재 지원할 수 없는 공고입니다.' };
  if (days === 0) return { accepting:true, tone:'today', label:'D-DAY · 오늘 마감', note:'오늘 23:59까지 접수할 수 있어요.' };
  if (days !== null && days <= 7) return { accepting:true, tone:'urgent', label:`D-${days} · 마감 임박`, note:'' };
  return { accepting:true, tone:'open', label:days === null ? '접수 중' : `D-${days} · 접수 중`, note:'' };
}
