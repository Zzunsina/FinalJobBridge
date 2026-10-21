export function koreanToday() {
  return new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Seoul', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date());
}

export function birthDateError(value, today = koreanToday()) {
  if (!value) return '';
  if (!/^[0-9]{8}$/.test(value)) return '생년월일은 8자리 숫자로 입력해 주세요. 예: 20000123';
  const year = Number(value.slice(0, 4)), month = Number(value.slice(4, 6)), day = Number(value.slice(6, 8));
  const date = new Date(0);
  date.setUTCFullYear(year, month - 1, day);
  if (date.getUTCFullYear() !== year || date.getUTCMonth() !== month - 1 || date.getUTCDate() !== day) return '실제 존재하는 날짜를 입력해 주세요.';
  const [ty, tm, td] = today.split('-').map(Number);
  const oldestDay = Math.min(td, new Date(Date.UTC(ty - 100, tm, 0)).getUTCDate());
  const oldest = String(ty - 100).padStart(4, '0') + String(tm).padStart(2, '0') + String(oldestDay).padStart(2, '0');
  if (value < oldest || value > today.replaceAll('-', '')) return '생년월일은 오늘부터 100년 전까지의 날짜만 입력할 수 있습니다.';
  return '';
}

export function formatMobile(value) {
  const digits = String(value || '').replace(/[^0-9]/g, '').slice(0, 11);
  if (digits.length <= 3) return digits;
  if (digits.length <= 7) return digits.slice(0, 3) + '-' + digits.slice(3);
  const split = digits.startsWith('010') || digits.length === 11 ? 7 : 6;
  return digits.slice(0, 3) + '-' + digits.slice(3, split) + '-' + digits.slice(split);
}
