import assert from 'node:assert/strict';
import { test } from 'node:test';
import { employmentStage } from '../src/utils/employmentStage.js';

test('company review and interview coordination remain distinct before a schedule is confirmed', () => {
  assert.equal(employmentStage({ status: 'MATCHING', referralStatus: 'PENDING' }), '기업 검토 중');
  assert.equal(employmentStage({ status: 'MATCHING', referralStatus: 'INTERVIEW_REQUESTED' }), '기업 면접 제안 대기');
  assert.equal(employmentStage({ status: 'MATCHING', referralStatus: 'INTERVIEW_REQUESTED', proposalId: 1 }), '면접 제안 확인 중');
  assert.equal(employmentStage({ status: 'MATCHING', referralStatus: 'INTERVIEW_REQUESTED', proposalId: 1, proposalResponse: 'CHANGE_REQUESTED' }), '기업 일정 재제안 대기');
  assert.equal(employmentStage({ status: 'INTERVIEW', referralStatus: 'INTERVIEW_REQUESTED' }), '면접 예정');
});
test('hiring response advances the displayed step while final completion stays with the administrator', () => {
  assert.equal(employmentStage({ status: 'INTERVIEW', referralStatus: 'HIRE_REQUESTED' }), '최종 합격·입사 안내 대기');
  assert.equal(employmentStage({ status: 'EMPLOYED', referralStatus: 'HIRE_REQUESTED', joinedAt: '2026-10-30T00:00:00Z' }), '취업 완료');
});
test('declined or canceled recommendations return to matching without closing the application', () => {
  for (const referralStatus of ['DECLINED', 'CANCELED']) assert.equal(employmentStage({ status: 'MATCHING', referralStatus }), '기업 매칭 중');
  assert.equal(employmentStage({ status: 'CLOSED', referralStatus: 'CANCELED' }), '취업연계 종료');
});
