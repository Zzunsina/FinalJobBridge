import test from 'node:test';
import assert from 'node:assert/strict';
import client, { isReplacedSession } from '../src/api/client.js';
const replaced = (config) => ({ config, response: { status: 401, data: { message: '다른 곳에서 로그인되어 로그아웃되었습니다. 다시 로그인해 주세요.', fieldErrors: [{ field: 'sessionReplaced' }] } } });
test('replacement is distinguished from wrong password and temporary server errors',()=>{
 assert.ok(isReplacedSession(replaced({})));
 assert.ok(!isReplacedSession({response:{status:401,data:{message:'아이디 또는 비밀번호를 확인해 주세요.'}}}));
 assert.ok(!isReplacedSession({response:{status:500,data:{fieldErrors:[{field:'sessionReplaced'}]}}}));
});
test('replacement announces logout without attempting token refresh',async()=>{
 globalThis.window = new EventTarget();const events=[];window.addEventListener('jobbridge:session-ended',e=>events.push(e.detail));
 const calls=[];client.defaults.adapter=async config=>{calls.push(config.url);throw replaced(config);};
 await assert.rejects(client.get('/me'));assert.deepEqual(calls,['/me']);assert.equal(events.length,1);assert.equal(events[0].replaced,true);
});
test('expired access token cannot recover after replacement, and signals only once',async()=>{
 globalThis.window=new EventTarget();let events=0;window.addEventListener('jobbridge:session-ended',()=>events++);
 client.defaults.adapter=async config=>{if(config.url==='/auth/refresh')throw replaced(config);throw {config,response:{status:401,data:{message:'로그인이 만료되었습니다.'}}};};
 await assert.rejects(client.get('/me'));assert.equal(events,1);
});
test('ordinary access expiry refreshes normally without forced logout',async()=>{
 globalThis.window=new EventTarget();let events=0,refreshCalls=0;window.addEventListener('jobbridge:session-ended',()=>events++);
 client.defaults.adapter=async config=>{
  if(config.url==='/auth/refresh'){refreshCalls++;return {data:{userId:1},status:200,config,headers:{}};}
  if(!config.retried)throw {config,response:{status:401,data:{message:'로그인이 만료되었습니다.'}}};
  return {data:{userId:1},status:200,config,headers:{}};
 };
 assert.equal((await client.get('/me')).data.userId,1);assert.equal(refreshCalls,1);assert.equal(events,0);
});