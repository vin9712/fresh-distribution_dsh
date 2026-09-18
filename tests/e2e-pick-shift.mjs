// 配货总表按班次展开验证：大长江（启用班次）真实订单日 → 点小计应为「配送点×班次」；金满楼（未启用）不带班次后缀。
import assert from 'node:assert/strict';
import { chromium } from 'playwright-core';
import { mkdirSync } from 'node:fs';
const base = process.env.WEB_BASE || 'http://localhost:1025';
const api = base + '/dev-api';
mkdirSync('test-results/pick-shift', { recursive: true });
const browser = await chromium.launch({ channel: 'chrome', headless: true });
try {
  const page = await browser.newPage({ viewport: { width: 1600, height: 1000 } });
  await page.goto(base + '/login', { waitUntil: 'networkidle' });
  await page.fill('input[placeholder*="账号"]', process.env.ERP_USER);
  await page.fill('input[placeholder*="密码"]', process.env.ERP_PASSWORD);
  await page.click('.login-button, .el-button--primary');
  await page.waitForURL(u => !String(u).includes('/login'));
  const token = (await page.context().cookies(base)).find(c => c.name === 'Admin-Token').value;
  const view = async (customerId, date) => {
    const r = await page.request.fetch(`${api}/order/delivery/batch/view?customerId=${customerId}&date=${date}`,
      { headers: { Authorization: 'Bearer ' + token } });
    const j = await r.json();
    assert.equal(j.code, 200, j.msg);
    return j.data || [];
  };

  // 1) 大长江（启用班次）：真实订单日 2026-09-01，历史无班次单归白班
  const dcj = await view(11, '2026-09-01');
  assert.ok(dcj.length > 0, '大长江该日应有配货行');
  const dcjDepts = [...new Set(dcj.flatMap(r => r.depts.map(d => d.deptName)))].sort();
  assert.ok(dcjDepts.every(n => /(白班|夜班)$/.test(n)), `启用班次的客户点小计应带班次，实际 ${JSON.stringify(dcjDepts)}`);
  // 历史无班次单归白班：有数据的点小计应都是「点名+白班」
  const matrixCols = (await (await page.request.fetch(`${api}/order/delivery/batch/11/2026-09-01/matrix`,
    { headers: { Authorization: 'Bearer ' + token } })).json()).data.columns.map(c => c.name);
  const dayCols = matrixCols.filter(n => n.endsWith('白班'));
  // 配货总表只列「当日有量」的点，故是白班列的子集（历史单归白班）
  assert.ok(dcjDepts.length > 0 && dcjDepts.every(n => dayCols.includes(n)),
    `点小计应属于矩阵白班列，实际 ${JSON.stringify(dcjDepts)}，白班列 ${JSON.stringify(dayCols)}`);
  for (const row of dcj) {
    const sum = row.depts.reduce((s, d) => s + Number(d.quantity || 0), 0);
    assert.equal(Number(row.totalQuantity), sum, `总量应等于各点小计之和：${row.productName}`);
  }
  console.log(`PASS 大长江配货总表按班次展开：${dcj.length} 行，点小计 ${JSON.stringify(dcjDepts)}，总量=各点小计之和`);

  // 2) 金满楼（未启用班次）：点小计不带班次后缀（回归）
  const jml = await view(13, '2026-09-18');
  assert.ok(jml.length > 0, '金满楼该日应有配货行');
  const jmlDepts = [...new Set(jml.flatMap(r => r.depts.map(d => d.deptName)))];
  assert.ok(jmlDepts.every(n => !/(白班|夜班)$/.test(n)), `未启用班次的客户不应带班次：${JSON.stringify(jmlDepts)}`);
  assert.equal(jmlDepts.length, 7, '七配送点各一行小计');
  console.log(`PASS 未启用班次的客户配货总表不带班次：${jmlDepts.length} 个点`);

  // 3) 页面渲染：配货总表 tab 能看到带班次的点小计（用查询参数直达，免 UI 选择）
  await page.goto(base + '/order/batch?customerId=11&deliveryDate=2026-09-01', { waitUntil: 'networkidle' });
  await page.waitForTimeout(4000);
  await page.locator('.el-radio-button', { hasText: '配货总表' }).first().click();
  await page.waitForTimeout(3000);
  const body = await page.locator('body').innerText();
  assert.ok(body.includes('华铃白班') && body.includes('棠下白班'), '配货总表页面应显示带班次的点小计');
  console.log('PASS 配货总表页面显示「华铃白班 / 棠下白班」点小计');
  await page.screenshot({ path: 'test-results/pick-shift/dachangjiang-pick.png', fullPage: false });

  // 4) 同一页的矩阵总表 tab：列头应为「配送点×班次」，格值按班次对位（验证前端格键）
  await page.locator('.el-radio-button', { hasText: '矩阵总表' }).first().click();
  await page.waitForTimeout(3000);
  const matrixBody = await page.locator('body').innerText();
  for (const col of matrixCols) {
    assert.ok(matrixBody.includes(col), `矩阵总表应出现列「${col}」`);
  }
  console.log(`PASS 矩阵总表页显示全部班次列头（${matrixCols.length} 列）`);
  await page.screenshot({ path: 'test-results/pick-shift/dachangjiang-matrix-page.png', fullPage: false });
} finally { await browser.close(); }
