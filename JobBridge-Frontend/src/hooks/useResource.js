import { useCallback, useEffect, useState } from 'react';
import client, { message } from '../api/client';

export function useResource(url, { refreshInterval = 0 } = {}) {
  const [state, setState] = useState({ data: null, loading: true, error: '' });
  const load = useCallback(
    async (silent = false) => {
      if (silent !== true) setState((old) => ({ ...old, loading: true, error: '' }));
      try {
        const { data } = await client.get(url);
        setState({ data, loading: false, error: '' });
      } catch (error) {
        setState({ data: null, loading: false, error: message(error) });
      }
    },
    [url],
  );
  useEffect(() => {
    load();
  }, [load]);
  useEffect(() => {
    if (!refreshInterval) return;
    const update = () => {
      if (!document.hidden) load(true);
    };
    const timer = window.setInterval(update, refreshInterval);
    window.addEventListener('focus', update);
    return () => {
      window.clearInterval(timer);
      window.removeEventListener('focus', update);
    };
  }, [load, refreshInterval]);
  return { ...state, reload: load };
}
