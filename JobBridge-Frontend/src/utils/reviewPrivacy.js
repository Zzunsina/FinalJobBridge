export function reviewPrivacyMessage(value) {
  const text=String(value??'').normalize('NFKC').replace(/[\u200B-\u200D\uFEFF]/g,'');
  const email=/[A-Z0-9._%+-]+\s*@\s*[A-Z0-9-]+(?:\s*\.\s*[A-Z0-9-]+)+/i;
  const phone=/(?<![0-9])(?:0(?:1[016789]|2|[3-6][1-5]|70)|\+?82[\s().-]*(?:1[016789]|2|[3-6][1-5]|70))[\s().-]*[0-9]{3,4}[\s().-]*[0-9]{4}(?![0-9])/;
  return email.test(text)||phone.test(text)?'전화번호나 이메일이 포함되어 있습니다. 개인정보를 지운 뒤 다시 요청해 주세요.':'';
}
