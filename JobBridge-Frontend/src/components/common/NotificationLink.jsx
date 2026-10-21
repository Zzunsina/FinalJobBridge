import { useEffect } from 'react';
import { Link } from 'react-router-dom';
import { useResource } from '../../hooks/useResource';

export default function NotificationLink() {
  const resource = useResource('/notifications', { refreshInterval: 30000 });
  const reload = resource.reload;
  useEffect(() => {
    window.addEventListener('notifications-changed', reload);
    return () => window.removeEventListener('notifications-changed', reload);
  }, [reload]);
  const unread = (resource.data || []).filter((n) => !n.isRead).length;
  return (
    <Link
      className="notification-link"
      to="/notifications"
      aria-label={`내 알림${unread ? `, 미확인 ${unread}개` : ''}`}
    >
      알림{' '}
      {unread > 0 && <span className="notification-count">{unread > 99 ? '99+' : unread}</span>}
    </Link>
  );
}
