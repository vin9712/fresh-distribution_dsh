// 销售订单页「看总单」入口回归（2026-09-19，D-073）
// 前置：本地环境已启动（./dev.sh start，前端 1025 → 后端 8090）
// 覆盖：客户视角主行「操作」列 —— 该客户当日订单已全部确认（无草稿）时出现「看总单」，
//       点击跳转送货单据页该客户矩阵总表；仍有草稿时不出现。
// 纯只读。
// 运行：ERP_USER=admin ERP_PASSWORD=admin123 node tests/e2e-sale-matrix-entry.mjs
import assert from 'node:assert/strict';
import { chromium } from 'playwright-core';

const base = process.env.WEB_BASE || 'http://localhost:1025';
const api = base + '/dev-api';
const user = process.env.ERP_USER || 'admin';
const pwd = process.env.ERP_PASSWORD || 'admin123';

const problems = [];
const browser = await chromium.launch({ channel: 'chrome', headless: true });
try {
  const page = await browser.newPage({ viewport: { width: 1680, height: 900 } });
  page.on('pageerror', (e) => problems.push('[pageerror] ' + e.message));
  page.on('console', (m) => {
    if (m.type() === 'error') problems.push('[console.error] ' + m.text().slice(0, 200));
  });
  page.on('response', (r) => {
    if (r.status() >= 500) problems.push('[http' + r.status() + '] ' + r.url());
  });

  await page.goto(base + '/login', { waitUntil: 'networkidle' });
  await page.fill('input[placeholder*="账号"]', user);
  await page.fill('input[placeholder*="密码"]', pwd);
  await page.click('.login-button, .el-button--primary');
  await page.waitForURL((u) => !String(u).includes('/login'), { timeout: 20000 });
  const token = (await page.context().cookies(base)).find((c) => c.name === 'Admin-Token').value;
  const call = async (p) => {
    const r = await page.request.fetch(api + p, { headers: { Authorization: 'Bearer ' + token } });
    const j = await r.json();
    assert.equal(j.code, 200, `${p}: ${j.msg}`);
    return j;
  };

  // 客户视角数据（找「全确认」与「含草稿」两类客户行）
  const cp = await call('/order/sale/customer-page?pageNum=1&pageSize=30');
  const rows = cp.rows || [];
  assert.ok(rows.length > 0, '客户视角应有数据');
  const allConfirmed = rows.find((r) => !r.draftCount && Number(r.orderCount) > 0);
  const withDraft = rows.find((r) => Number(r.draftCount) > 0);
  console.log('全确认行:', allConfirmed ? `${allConfirmed.customerName}/${allConfirmed.deliveryDate}` : '无');
  console.log('含草稿行:', withDraft ? `${withDraft.customerName}/${withDraft.deliveryDate}` : '无');

  await page.goto(base + '/order/sale', { waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(2500);

  const rowOf = (r) =>
    page
      .locator('tr.el-table__row')
      .filter({ hasText: r.customerName })
      .filter({ hasText: r.deliveryDate })
      .first();

  // 1. 全确认客户 → 有「看总单」
  if (allConfirmed) {
    const row = rowOf(allConfirmed);
    await row.scrollIntoViewIfNeeded().catch(() => {});
    const btn = row.locator('button:has-text("看总单")');
    assert.equal(await btn.count(), 1, `全确认客户 ${allConfirmed.customerName} 应有「看总单」`);
    console.log('PASS 全确认客户主行出现「看总单」');
  } else {
    console.log('SKIP 无全确认客户行，跳过正向校验');
  }

  // 2. 含草稿客户 → 无「看总单」
  if (withDraft) {
    const row = rowOf(withDraft);
    await row.scrollIntoViewIfNeeded().catch(() => {});
    const btn = row.locator('button:has-text("看总单")');
    assert.equal(await btn.count(), 0, `含草稿客户 ${withDraft.customerName} 不应有「看总单」`);
    console.log('PASS 含草稿客户主行无「看总单」');
  } else {
    console.log('SKIP 无含草稿客户行，跳过反向校验');
  }

  // 3. 点击跳转送货单据矩阵总表
  if (allConfirmed) {
    await rowOf(allConfirmed).locator('button:has-text("看总单")').click();
    await page.waitForTimeout(2500);
    const url = page.url();
    assert.ok(url.includes('/order/batch'), `应跳送货单据页，实际 ${url}`);
    assert.ok(url.includes('customerId=' + allConfirmed.customerId), 'URL 应带 customerId');
    assert.ok(url.includes('deliveryDate=' + allConfirmed.deliveryDate), 'URL 应带 deliveryDate');
    const title = await page.locator('.batch-title').innerText();
    assert.ok(/矩阵总表/.test(title), `应展示矩阵总表，实际 ${title}`);
    assert.ok(title.includes(allConfirmed.customerName), '总表标题应含客户名');
    console.log('PASS 看总单跳转矩阵总表:', title.split('\n')[0]);
    await page.screenshot({ path: 'test-results/sale-matrix-entry.png', fullPage: false });
  }
} finally {
  await browser.close();
}

if (problems.length) {
  console.error('\n❌ 验证未通过:');
  problems.forEach((p) => console.error(' -', p));
  process.exit(1);
}
console.log('\n✅ 销售订单页「看总单」入口验证通过');
