import { useEffect, useState } from 'react';
import { jobDeadline } from '../../utils/jobDeadline';

export function useJobDeadline(job) {
  const [now, setNow] = useState(() => new Date());
  useEffect(() => {
    const update=() => setNow(new Date());
    const timer=window.setInterval(update,30000);
    window.addEventListener('focus',update);
    document.addEventListener('visibilitychange',update);
    return () => { window.clearInterval(timer); window.removeEventListener('focus',update); document.removeEventListener('visibilitychange',update); };
  }, []);
  return jobDeadline(job || {},now);
}

export default function JobDeadline({ endDate, deadline }) {
  return <div className="application-deadline">
    <p className="application-deadline-date">접수 마감 <strong>{endDate || '별도 안내'}</strong></p>
    <div className="application-deadline-state" aria-live="polite"><span className={'application-dday application-dday-' + deadline.tone}>{deadline.label}</span>
      {deadline.note && <small>{deadline.note}</small>}
    </div>
  </div>;
}
