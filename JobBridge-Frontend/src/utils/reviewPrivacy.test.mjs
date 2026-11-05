import test from 'node:test';
import assert from 'node:assert/strict';
import {reviewPrivacyMessage} from './reviewPrivacy.js';
test('review privacy flags phone and email variants before submission',()=>{
 for(const value of ['010-1234-5678','01012345678','02 123 4567','+82 10 1234 5678','０１０－１２３４－５６７８','010.1234.5678','test.person@example.com','test @ example . com','010\u200B-1234-5678'])assert.ok(reviewPrivacyMessage(value),value);
});
test('review privacy keeps normal interview experience and criticism',()=>{
 for(const value of ['2026년 11월 면접은 30분 진행했습니다.','설명이 부족해 아쉬웠습니다.','문항 1234와 5678을 확인했습니다.','기술 면접 2회',null])assert.equal(reviewPrivacyMessage(value),'');
});
