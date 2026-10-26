import assert from 'node:assert/strict';
import { test, before, after } from 'node:test';
import { createServer } from 'vite';
import React from 'react';
import { renderToStaticMarkup } from 'react-dom/server';

let server, PagedRows, AssessmentRanking, MemoryRouter;
before(async () => {
  server = await createServer({ server: { middlewareMode: true }, appType: 'custom' });
  ({ PagedRows } = await server.ssrLoadModule('/src/components/common/UI.jsx'));
  ({ default: AssessmentRanking } = await server.ssrLoadModule('/src/components/common/AssessmentRanking.jsx'));
  ({ MemoryRouter } = await import('react-router-dom'));
});
after(async () => { await server?.close(); });
function render(count, focusId) {
  const rows = Array.from({length:count}, (_,id) => ({id:String(id)}));
  return renderToStaticMarkup(React.createElement(PagedRows,{rows,focusId,idKey:'id'},
    (page) => React.createElement('div',null,page.map((row) => React.createElement('span',{'data-row':row.id,key:row.id},row.id)))));
}
test('12 rows fit on one page with no navigation', () => {
  const html=render(12);
  assert.equal((html.match(/data-row=/g)||[]).length,12);
  assert.ok(!html.includes('aria-label="페이지 이동"'));
});
test('13 rows show 12 items and a second page', () => {
  const html=render(13);
  assert.equal((html.match(/data-row=/g)||[]).length,12);
  assert.ok(html.includes('aria-label="페이지 이동"'));
  assert.ok(html.includes('>2</button>'));
});
test('focus item on the second page stays visible', () => {
  const html=render(13,'12');
  assert.equal((html.match(/data-row=/g)||[]).length,1);
  assert.ok(html.includes('data-row="12"'));
  assert.ok(html.includes('aria-current="page">2</button>'));
});

test('ranking limits to 10 while retaining the full input list and ordered ties', () => {
  const rows=Array.from({length:13},(_,i) => ({applicationId:i+1,name:'Applicant '+(i+1),score:i<2?100:90-i}));
  rows.push({applicationId:14,name:'Not tested',score:null});
  const before=JSON.stringify(rows);
  const html=renderToStaticMarkup(React.createElement(MemoryRouter,null,React.createElement(AssessmentRanking,{rows})));
  assert.equal((html.match(/<li>/g)||[]).length,10);
  assert.ok(html.indexOf('Applicant 1 ·')<html.indexOf('Applicant 2 ·'));
  assert.ok(!html.includes('Applicant 11 ·') && !html.includes('Not tested'));
  assert.equal(JSON.stringify(rows),before);
});
