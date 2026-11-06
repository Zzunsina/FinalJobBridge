import test from 'node:test';
import assert from 'node:assert/strict';
import { paymentMerchantId, legacyCallback, legacyPaymentParameters } from '../src/api/portoneV1.js';
const old='jb-ad-83c6d2cc-8834-4900-892b-e3ba987596e0';
const current='jb-ad-83c6d2cc88344900892be3ba987596e0';
test('new orders stay unchanged and old orders use the exact PG merchant ID',()=>{
 assert.equal(paymentMerchantId(current),current);
 assert.equal(paymentMerchantId(old),old.slice(0,40));
 assert.equal(legacyPaymentParameters({paymentId:old,customer:{}},'http://localhost').merchant_uid,old.slice(0,40));
 assert.equal(legacyCallback({imp_uid:'imp_123',merchant_uid:old.slice(0,40),success:true},old).imp_uid,'imp_123');
});
test('unrelated orders and payment cancellation remain rejected',()=>{
 assert.throws(()=>legacyCallback({imp_uid:'imp_123',merchant_uid:current,success:true},old));
 assert.throws(()=>legacyCallback({imp_uid:'imp_123',merchant_uid:old.slice(0,39),success:true},old));
 assert.throws(()=>legacyCallback({success:false,error_msg:'취소'},old));
});