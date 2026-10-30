import assert from 'node:assert/strict';
import { test } from 'node:test';
import { notificationTarget } from '../src/utils/notificationTarget.js';

test('application and interview notifications use the recipient role and application id', () => {
  for (const notificationType of ['APPLICATION', 'INTERVIEW']) {
    const notification = { notificationType, relatedId: 42 };
    assert.equal(notificationTarget(notification, 'COMPANY'), '/company/applications/42');
    assert.equal(notificationTarget(notification, 'JOB_SEEKER'), '/applications/42');
    assert.equal(notificationTarget(notification, 'ADMIN'), '/admin');
  }
});

test('review and service notifications resolve to their management screens', () => {
  assert.equal(notificationTarget({ notificationType: 'JOB', relatedId: 7 }, 'COMPANY'), '/company/jobs/7/edit');
  assert.equal(notificationTarget({ notificationType: 'COMPANY' }, 'COMPANY'), '/company/profile');
  assert.equal(notificationTarget({ notificationType: 'INQUIRY' }, 'JOB_SEEKER'), '/inquiries?view=history');
  assert.equal(notificationTarget({ notificationType: 'EMPLOYMENT' }, 'JOB_SEEKER'), '/employment');
  assert.equal(notificationTarget({ notificationType: 'EMPLOYMENT', relatedId: 7 }, 'JOB_SEEKER'), '/employment/7');
  assert.equal(notificationTarget({ notificationType: 'EMPLOYMENT', relatedId: 7 }, 'COMPANY'), '/company/employment/7');
  assert.equal(notificationTarget({ notificationType: 'EMPLOYMENT', relatedId: 7 }, 'ADMIN'), '/admin/employment?focus=7');
});

test('missing ids and unknown types do not produce broken detail links', () => {
  assert.equal(notificationTarget({ notificationType: 'APPLICATION' }, 'COMPANY'), '/company/applicants');
  assert.equal(notificationTarget({ notificationType: 'JOB' }, 'COMPANY'), '/company/jobs');
  assert.equal(notificationTarget({ notificationType: 'UNKNOWN' }, 'JOB_SEEKER'), null);
});
