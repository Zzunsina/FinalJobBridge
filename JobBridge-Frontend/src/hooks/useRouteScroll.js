import { useLayoutEffect } from 'react';
import { useLocation, useNavigationType } from 'react-router-dom';

const positions = new Map();
const instantScroll = (x, y) => window.scrollTo({ left: x, top: Math.max(0, y), behavior: 'instant' });

function snapshot(clicked) {
  const header = document.querySelector('header');
  const top = Math.max(0, header?.getBoundingClientRect().bottom || 0);
  const card = clicked?.closest?.('[data-ad-order]') || [...document.querySelectorAll('[data-ad-order]')]
    .find(element => { const rect = element.getBoundingClientRect(); return rect.bottom > top && rect.top < innerHeight; });
  return { x: window.scrollX, y: window.scrollY, anchor: card?.getAttribute('data-ad-order'), offset: card?.getBoundingClientRect().top };
}

export function useRouteScroll() {
  const location = useLocation();
  const navigationType = useNavigationType();
  useLayoutEffect(() => {
    const previous = window.history.scrollRestoration;
    window.history.scrollRestoration = 'manual';
    return () => { window.history.scrollRestoration = previous; };
  }, []);

  useLayoutEffect(() => {
    const key = location.key;
    const saved = navigationType === 'POP' ? positions.get(key) : null;
    let restoring = Boolean(saved || location.hash);
    let frame = 0, settle = 0, timeout = 0, finished = false;
    let resize, mutations;
    const remember = event => {
      if (!restoring) {
        positions.set(key, snapshot(event?.target));
        if (positions.size > 150) positions.delete(positions.keys().next().value);
      }
    };
    const stop = () => {
      if (finished) return;
      finished = true;
      restoring = false;
      cancelAnimationFrame(frame);
      clearTimeout(settle);
      clearTimeout(timeout);
      resize?.disconnect();
      mutations?.disconnect();
      document.removeEventListener('load', schedule, true);
      document.removeEventListener('error', schedule, true);
      remember();
    };
    const attempt = () => {
      if (finished) return;
      const anchor = saved?.anchor ? [...document.querySelectorAll('[data-ad-order]')].find(element => element.getAttribute('data-ad-order') === saved.anchor) : null;
      let hashTarget = null;
      try { if (location.hash) hashTarget = document.getElementById(decodeURIComponent(location.hash.slice(1))); } catch { /* Invalid hashes use the saved position. */ }
      const y = anchor ? window.scrollY + anchor.getBoundingClientRect().top - saved.offset
        : hashTarget ? window.scrollY + hashTarget.getBoundingClientRect().top - 120 : saved?.y || 0;
      const max = Math.max(0, document.documentElement.scrollHeight - innerHeight);
      const pending = document.querySelector('[data-scroll-loading="true"]') || [...document.querySelectorAll('.promotion-complete-image')].some(image => !image.complete);
      // Never clamp a saved position against the short loading page: that lands at its footer.
      if (y <= max + 1) instantScroll(saved?.x || 0, y);
      clearTimeout(settle);
      if (!pending && y <= max + 1 && (!location.hash || hashTarget || saved)) settle = window.setTimeout(stop, 200);
    };
    function schedule() {
      if (finished) return;
      cancelAnimationFrame(frame);
      frame = requestAnimationFrame(attempt);
    }
    const interrupt = event => {
      if (event.type === 'keydown' && !['ArrowDown','ArrowUp','PageDown','PageUp','Home','End',' '].includes(event.key)) return;
      stop();
    };
    const beforeLink = event => {
      if (event.target?.closest?.('a[href]')) {
        if (restoring) stop();
        remember(event);
      }
    };
    window.addEventListener('scroll', remember, { passive: true });
    window.addEventListener('pagehide', remember);
    document.addEventListener('pointerdown', beforeLink, true);
    window.addEventListener('wheel', interrupt, { passive: true });
    window.addEventListener('touchstart', interrupt, { passive: true });
    window.addEventListener('keydown', interrupt);
    if (restoring) {
      resize = new ResizeObserver(schedule);
      resize.observe(document.body);
      const main = document.getElementById('main');
      if (main) resize.observe(main);
      mutations = new MutationObserver(schedule);
      mutations.observe(document.body, { childList: true, subtree: true, attributes: true, attributeFilter: ['data-scroll-loading'] });
      document.addEventListener('load', schedule, true);
      document.addEventListener('error', schedule, true);
      timeout = window.setTimeout(() => { if (!finished) { attempt(); stop(); } }, 6000);
      attempt();
    } else {
      instantScroll(0, 0);
      remember();
    }
    return () => {
      finished = true;
      cancelAnimationFrame(frame);
      clearTimeout(settle);
      clearTimeout(timeout);
      resize?.disconnect();
      mutations?.disconnect();
      document.removeEventListener('load', schedule, true);
      document.removeEventListener('error', schedule, true);
      window.removeEventListener('scroll', remember);
      window.removeEventListener('pagehide', remember);
      document.removeEventListener('pointerdown', beforeLink, true);
      window.removeEventListener('wheel', interrupt);
      window.removeEventListener('touchstart', interrupt);
      window.removeEventListener('keydown', interrupt);
    };
  }, [location.key, location.hash, navigationType]);
}
