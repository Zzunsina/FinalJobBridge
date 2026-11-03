export function employmentStage(record) {
  if (record.status === 'CLOSED') return '취업연계 종료';
  if (record.joinedAt) return '취업 완료';
  if (record.referralStatus === 'HIRE_REQUESTED') return record.startAt ? '최종 합격·입사 예정' : '최종 합격·입사 안내 대기';
  if (record.status === 'EMPLOYED') return '취업 완료';

  if (record.status === 'INTERVIEW') return '면접 예정';
  if (record.referralStatus === 'INTERVIEW_REQUESTED') return record.proposalResponse === 'CHANGE_REQUESTED' ? '기업 일정 재제안 대기' : record.proposalId ? '면접 제안 확인 중' : '기업 면접 제안 대기';
  if (record.referralStatus === 'PENDING') return '기업 검토 중';
  return { REQUESTED: '신청 접수', COUNSELING: '기업 매칭 중', MATCHING: '기업 매칭 중' }[record.status] || record.status;
}
