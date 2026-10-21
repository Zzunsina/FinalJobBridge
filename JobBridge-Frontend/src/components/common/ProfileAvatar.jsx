import { useEffect, useState } from 'react';
import client from '../../api/client';

export default function ProfileAvatar({ user, className }) {
  const [photo, setPhoto] = useState(null);
  const userId = user?.userId;
  useEffect(() => {
    if (!userId) return;
    let active = true;
    let url;
    client.get('/me/photo', { responseType: 'blob' }).then(({ data, status }) => {
      if (active && status === 200) {
        url = URL.createObjectURL(data);
        setPhoto({ userId, url });
      }
    }).catch(() => {});
    return () => {
      active = false;
      if (url) URL.revokeObjectURL(url);
    };
  }, [userId]);
  return photo?.userId === userId && photo?.url ? (
    <img className={className} src={photo.url} alt={(user?.name || '지원자') + '님의 프로필 사진'} />
  ) : (
    <span className={className} aria-hidden="true">{user?.name?.slice(0, 1) || '나'}</span>
  );
}
