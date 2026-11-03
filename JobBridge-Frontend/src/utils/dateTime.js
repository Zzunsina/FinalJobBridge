const dateTime = new Intl.DateTimeFormat('ko-KR', {
  timeZone: 'Asia/Seoul', year: 'numeric', month: 'numeric', day: 'numeric',
  hour: 'numeric', minute: '2-digit', hour12: true,
});
const time = new Intl.DateTimeFormat('ko-KR', {
  timeZone: 'Asia/Seoul', hour: 'numeric', minute: '2-digit', hour12: true,
});
export function formatDateTime(value) {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? '—' : dateTime.format(date);
}
export function formatTime(value = new Date()) {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? '—' : time.format(date);
}
