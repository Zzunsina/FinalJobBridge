export const NO_CHANGES = '변경된 내용이 없습니다.';
export function isExistingRecord(record = {}) {
  return !record.profileMissing && Object.entries(record).some(([key,value]) => (key === 'key' ? value && value !== 'new' : (key === 'id' || /Id$/.test(key)) && value != null && value !== ''));
}
function stable(value) {
 if (value == null || value === '') return '';
 if (Array.isArray(value)) return value.map(stable);
 if (typeof value === 'object') return Object.fromEntries(Object.keys(value).sort().map(key=>[key,stable(value[key])]));
 return String(value);
}
export function hasFormChanges(before,after,fields) {
 return fields.filter(field=>!field.visibleWhen || field.visibleWhen(after)).some(field=>{
  const get=record=>field.type==='checkbox'?Boolean(record[field.name]):stable(record[field.name]);
  return JSON.stringify(get(before))!==JSON.stringify(get(after));
 });
}
