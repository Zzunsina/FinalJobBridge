import { useEffect, useState } from 'react';

async function checkEntry(reload = false) {
  const response = await fetch(`/api/auth/page-entry?reload=${reload}`, {
    credentials: 'same-origin', cache: 'no-store',
  });
  const body = await response.json();
  if (response.status === 429 && body.code === 'PAGE_RELOAD_LIMIT') {
    return { until: Date.now() + Number(body.retryAfter) * 1000 };
  }
  if (!response.ok) throw new Error('페이지 접속을 확인하지 못했습니다. 잠시 후 다시 시도해 주세요.');
  return { allowed: true };
}

// One check per document load, including React StrictMode's repeated effects.
const admission = checkEntry(performance.getEntriesByType('navigation')[0]?.type === 'reload')
  .catch(() => ({ error: true }));

export default function PageReloadGate({ children }) {
  const [state, setState] = useState({});
  const [now, setNow] = useState(Date.now());
  useEffect(() => {
    let active = true;
    admission.then((result) => { if (active) setState(result); });
    return () => { active = false; };
  }, []);
  useEffect(() => {
    if (!state.until) return;
    let active = true;
    const timer = window.setInterval(() => {
      const time = Date.now();
      setNow(time);
      if (time >= state.until) {
        window.clearInterval(timer);
        checkEntry().then((result) => { if (active) setState(result); })
          .catch(() => { if (active) setState({ error: true }); });
      }
    }, 1000);
    return () => { active = false; window.clearInterval(timer); };
  }, [state.until]);
  if (state.allowed) return children;
  return <main className="empty" aria-labelledby="reload-limit-title">
    <h1 id="reload-limit-title">{state.until ? '잠시 후 다시 이용해 주세요' : state.error ? '접속을 확인하지 못했습니다' : '접속을 확인하고 있습니다…'}</h1>
    {state.until && <><p>짧은 시간에 새로고침을 너무 많이 요청했습니다.</p>
      <p role="timer">약 {Math.max(0, Math.ceil((state.until - now) / 1000))}초 후 자동으로 다시 연결합니다.</p></>}
    {state.error && <button onClick={async () => {
      setState({});
      try { setState(await checkEntry()); } catch { setState({ error: true }); }
    }}>다시 연결</button>}
  </main>;
}
