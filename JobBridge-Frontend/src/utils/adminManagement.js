const roleLabels = { ADMIN: '관리자', COMPANY: '기업', JOB_SEEKER: '구직자' };

export const searchFields = {
  users: ['name', 'email', 'userId', 'role'],
  companies: ['companyName', 'businessNumber', 'industry', 'companyId'],
  jobs: ['title', 'companyName', 'workLocation', 'jobId'],
  'employment-links': ['jobSeekerName', 'employmentLinkId', 'jobSeekerId', 'requestedAt'],
  notices: ['title', 'content', 'noticeId'],
  inquiries: ['authorName', 'title', 'content', 'answer', 'inquiryId'],
};

export const searchHints = {
  users: '이름, 이메일, 회원 번호, 회원 유형',
  companies: '기업명, 사업자번호, 업종',
  jobs: '공고명, 기업명, 근무 지역',
  'employment-links': '신청자 이름, 연계 번호, 구직자 번호, 신청일',
  notices: '공지 제목, 본문',
  inquiries: '작성자 이름, 문의 제목, 내용, 답변',
};

export function recordStatus(name, row) {
  if (name === 'companies') return row.approvalStatus;
  if (name === 'notices') return row.isPublished ? '게시 중' : '미게시';
  return row.status;
}

const normalize = (value) =>
  String(value ?? '')
    .normalize('NFKC')
    .toLocaleLowerCase('ko-KR');

export function filterAdminRows(rows, name, query, status) {
  const terms = normalize(query).trim().split(/\s+/).filter(Boolean);
  return rows.filter((row) => {
    if (status && recordStatus(name, row) !== status) return false;
    const text = (searchFields[name] || [])
      .map((field) => {
        const value = row[field];
        return `${normalize(value)} ${normalize(roleLabels[value])}`;
      })
      .join(' ');
    return terms.every((term) => text.includes(term));
  });
}
