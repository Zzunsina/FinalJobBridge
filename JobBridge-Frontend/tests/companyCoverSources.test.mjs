import assert from 'node:assert/strict';
import { test } from 'node:test';
import { companyCoverSources } from '../src/utils/companyCoverSources.js';

test('a company cover uses its profile image before the generic industry image', () => {
  assert.deepEqual(companyCoverSources({ companyId:24, theme:'commerce' }), ['/api/companies/24/image','/images/company-covers-v2/commerce.jpg']);
});
test('an explicit job banner remains the first image, followed by the matching company profile', () => {
  assert.deepEqual(companyCoverSources({ companyId:12, bannerImageUrl:' https://example.com/banner.jpg ', theme:'design' }), ['https://example.com/banner.jpg','/api/companies/12/image','/images/company-covers-v2/design.jpg']);
});
test('private company management screens can use their own profile endpoint', () => {
  assert.deepEqual(companyCoverSources({ profileImageUrl:'/api/company/image', theme:'software' }), ['/api/company/image','/images/company-covers-v2/software.jpg']);
});
test('missing company ids do not generate broken profile links', () => {
  assert.deepEqual(companyCoverSources({ companyId:null }), ['/images/company-covers-v2/business.jpg']);
});
