// 客户配送点排序（总单列顺序）+ 矩阵「当日无单列」折叠 回归（2026-09-19，D-074/D-075）
// 前置：本地环境已启动（./dev.sh start，前端 1025 → 后端 8090）
// 覆盖：
//   1. 后端 PUT /partner/customerDept/sort：按 ids 顺序重排 sortNo，列表与矩阵列顺序随之变化
//      （矩阵列口径：有数据列在前、当日无单列在后，各自内部保持主数据顺序 —— D-055 展示优化）
//   2. 送货单据矩阵页「折叠当日无单列」开关：勾选后隐藏空列，取消恢复
//   3. 配送点管理页：「调整排序」才出现拖拽手柄列，拖动仅本地预览、点「确认排序」才落库
//   4. 配送点管理页：列表中有配送点声明班次时才出现「班次」列并按字典渲染 tag
// 写库说明：第 1 步会临时改 sortNo，用例结束按原顺序还原。
// 运行：ERP_USER=admin ERP_PASSWORD=admin123 node tests/e2e-dept-sort-and-empty-cols.mjs
import assert from 'node:assert/strict';
import { chromium } from 'playwright-core';

const base = process.env.WEB_BASE || 'http://localhost:1025';
const api = base + '/dev-api';
const user = process.env.ERP_USER || 'admin';
const pwd = process.env.ERP_PASSWORD || 'admin123';

const problems = [];
const browser = await chromium.launch({ channel: 'chrome', headless: true });
let pick = null;
let originalIds = null;
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
  const call = async (p, method = 'GET', data) => {
    const r = await page.request.fetch(api + p, {
      method,
      headers: { Authorization: 'Bearer ' + token, 'Content-Type': 'application/json' },
      ...(data === undefined ? {} : { data }),
    });
    const j = await r.json();
    assert.equal(j.code, 200, `${method} ${p}: ${j.msg}`);
    return j;
  };

  // 找有 >=2 个叶子配送点、且当天有矩阵数据的客户（用送货单据卡片总览找有数据的客户+日期）
  const today = new Date().toISOString().slice(0, 10);
  let date = null;
  let customers = [];
  for (let i = 0; i < 20 && !date; i++) {
    const d = new Date();
    d.setDate(d.getDate() - i);
    const ds = d.toISOString().slice(0, 10);
    const ov = (await call('/order/delivery/batch/order-overview?deliveryDate=' + ds)).data || [];
    if (ov.length) {
      date = ds;
      customers = ov;
    }
  }
  assert.ok(date, '近 20 天内应有已确认订单');
  for (const c of customers) {
    const pg = (await call(`/partner/customerDept/page?pageNum=1&pageSize=50&customerId=${c.customerId}&hideParent=true`)).rows || [];
    if (pg.length >= 3) {
      pick = { customerId: c.customerId, customerName: c.customerName };
      originalIds = pg.map((d) => d.id);
      break;
    }
  }
  assert.ok(pick, '需要至少一个有 3+ 叶子配送点的客户');
  console.log(`客户 ${pick.customerName}(${pick.customerId}) / 日期 ${date} / 配送点 ${originalIds.length} 个`);

  const listIds = async () =>
    ((await call(`/partner/customerDept/page?pageNum=1&pageSize=50&customerId=${pick.customerId}&hideParent=true`)).rows || []).map((d) => d.id);
  const matrixCols = async () =>
    ((await call(`/order/delivery/batch/${pick.customerId}/${date}/matrix`)).data || {}).columns || [];

  // ---- 1. 排序 → 列表与矩阵列顺序 ----
  const reversed = [...originalIds].reverse();
  await call('/partner/customerDept/sort', 'PUT', { customerId: pick.customerId, ids: reversed });
  assert.deepEqual(await listIds(), reversed, '列表顺序应按传入 ids 重排');

  const cols = await matrixCols();
  const deptIds = new Set(originalIds);
  const matrixDeptIds = cols.map((c) => c.deptId).filter((id) => deptIds.has(id));
  assert.deepEqual([...matrixDeptIds].sort(), [...originalIds].sort(), '矩阵列集合应与配送点一致');

  // 有数据列在前、空列在后；同组内部保持主数据（reversed）顺序
  const hasDataFlags = cols.filter((c) => deptIds.has(c.deptId)).map((c) => !!c.hasData);
  assert.ok(!hasDataFlags.includes(true) || hasDataFlags.indexOf(true) < hasDataFlags.lastIndexOf(false) + 1 || !hasDataFlags.includes(false), '有数据列应排在空列之前');
  const idx = new Map(reversed.map((id, i) => [id, i]));
  const deptCols = cols.filter((c) => deptIds.has(c.deptId));
  for (let i = 0; i < deptCols.length; i++) {
    for (let j = i + 1; j < deptCols.length; j++) {
      if (!!deptCols[i].hasData === !!deptCols[j].hasData) {
        assert.ok(
          idx.get(deptCols[i].deptId) < idx.get(deptCols[j].deptId),
          `同组列应保持主数据顺序：${deptCols[i].name} 应在 ${deptCols[j].name} 前`
        );
      }
    }
  }
  console.log('PASS 配送点排序 → 总单列顺序生效（有数据列在前/空列在后，组内随主数据）');

  // ---- 2. 矩阵页「折叠当日无单列」 ----
  await page.goto(`${base}/order/batch?customerId=${pick.customerId}&deliveryDate=${date}`, { waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(2200);
  const allCols = await page.locator('.batch-print-area .col-head').count();
  const emptyCount = await page.locator('.batch-print-area .col-head .col-head-sub').count();
  console.log(`矩阵列 ${allCols} 个，其中当日无单 ${emptyCount} 个`);
  if (emptyCount > 0) {
    const toggle = page.locator('.col-empty-toggle');
    assert.ok(await toggle.isVisible(), '应显示「折叠当日无单列」开关');
    await toggle.click();
    await page.waitForTimeout(600);
    const afterCols = await page.locator('.batch-print-area .col-head').count();
    console.log(`折叠后列数 ${afterCols}（期望 ${allCols - emptyCount}）`);
    assert.equal(afterCols, allCols - emptyCount, '折叠后应隐藏当日无单列');
    await toggle.click();
    await page.waitForTimeout(600);
    assert.equal(await page.locator('.batch-print-area .col-head').count(), allCols, '取消折叠应恢复全部列');
    console.log('PASS 矩阵「折叠当日无单列」开关');
  } else {
    console.log('SKIP 该客户当日无空列，跳过折叠校验');
  }
  await page.screenshot({ path: 'test-results/matrix-empty-col-toggle.png', fullPage: false });

  // ---- 2b. 列顺序：矩阵「规格」紧邻「备注」；点单「规格」紧邻「说明」（D-076） ----
  const headers = async () => (await page.locator('.batch-print-area .el-table__header th .cell').allInnerTexts()).map((t) => t.split('\n')[0].trim());
  const mh = await headers();
  const mTail = mh.slice(-3);
  console.log('矩阵末三列:', mTail.join(' | '));
  assert.deepEqual(mTail, ['规格', '备注', '合计'], '矩阵应「规格」在「备注」前一列');
  await page.locator('.el-radio-button:has-text("点单")').click();
  await page.waitForTimeout(2200);
  const ph = await headers();
  const pTail = ph.slice(-2);
  console.log('点单末两列:', pTail.join(' | '));
  assert.deepEqual(pTail, ['规格', '说明'], '点单应「规格」在「说明」前一列');
  console.log('PASS 规格列位置（矩阵/点单）');

  // ---- 3. 配送点页「调整排序」模式 - 拖拽手柄仅在排序模式下出现 ----
  await page.goto(`${base}/basicInfo/customer-dept/index/${pick.customerId}`, { waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(2000);
  assert.equal(await page.locator('.dept-drag-btn').count(), 0, '非排序模式下不应出现拖拽排序手柄列');
  assert.ok(await page.locator('.dept-sort-tip').isVisible(), '应有排序提示文案');
  await page.screenshot({ path: 'test-results/dept-normal.png', fullPage: false });

  await page.locator('button:has-text("调整排序")').click();
  await page.waitForTimeout(1500);
  const handle = await page.locator('.dept-drag-btn').count();
  console.log('排序模式拖拽手柄数:', handle);
  assert.ok(handle >= 2, '进入排序模式后应渲染每行拖拽手柄');
  assert.ok(await page.locator('button:has-text("确认排序")').isVisible(), '排序模式应有「确认排序」');
  assert.ok(await page.locator('button:has-text("取消")').isVisible(), '排序模式应有「取消」');
  console.log('PASS 调整排序入口 → 排序模式手柄 + 确认/取消');
  await page.screenshot({ path: 'test-results/dept-sort.png', fullPage: false });

  // 拖拽：把第 1 行拖到第 2 行之后，点「确认排序」才落库
  const firstIdBefore = (await listIds())[0];
  const h = await page.locator('.dept-drag-btn').first().boundingBox();
  const rows = page.locator('.el-table__body-wrapper tbody tr.el-table__row');
  const r2 = await rows.nth(1).boundingBox();
  await page.mouse.move(h.x + h.width / 2, h.y + h.height / 2);
  await page.mouse.down();
  await page.mouse.move(h.x + h.width / 2, r2.y + r2.height * 0.9, { steps: 12 });
  await page.mouse.up();
  await page.waitForTimeout(500);
  assert.equal((await listIds())[0], firstIdBefore, '拖动未确认前不应落库');
  await page.locator('button:has-text("确认排序")').click();
  await page.waitForTimeout(1800);
  const firstIdAfter = (await listIds())[0];
  console.log(`拖拽后首行 ${firstIdBefore} -> ${firstIdAfter}`);
  assert.notEqual(firstIdAfter, firstIdBefore, '确认排序后列表首行应变化');
  assert.equal(await page.locator('.dept-drag-btn').count(), 0, '确认排序后应退出排序模式（手柄列消失）');
  console.log('PASS 拖动仅本地预览，点「确认排序」才保存');

  // ---- 还原原顺序 ----
  await call('/partner/customerDept/sort', 'PUT', { customerId: pick.customerId, ids: originalIds });
  assert.deepEqual(await listIds(), originalIds, '还原失败');
  console.log('已还原原顺序');

  // ---- 4. 班次列：仅当列表中有配送点声明班次时出现，按字典渲染 tag ----
  const allCustomers = (await call('/partner/customer/list')).data || [];
  const deptPageHeads = async (customerId) => {
    await page.goto(`${base}/basicInfo/customer-dept/index/${customerId}`, { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(1800);
    return (await page.locator('.el-table__header th .cell').allInnerTexts()).map((t) => t.split('\n')[0].trim());
  };
  let shiftChecked = false;
  for (const c of allCustomers) {
    if (!c.shiftEnabled) continue;
    const depts = (await call(`/partner/customerDept/list?customerId=${c.id}&hideParent=true`)).data || [];
    const withShift = depts.filter((d) => d.shiftCodes && String(d.shiftCodes).trim());
    if (!withShift.length) continue;
    const heads = await deptPageHeads(c.id);
    assert.ok(heads.includes('班次'), '有配送点声明班次的客户应展示「班次」列');
    const tags = await page.locator('.dept-shift-tag').count();
    assert.ok(tags >= withShift.length, `班次列应渲染 tag（期望 >= ${withShift.length}，实际 ${tags}）`);
    console.log(`PASS 班次列（${c.alias || c.name}：${withShift.length} 个点声明班次，渲染 ${tags} 个 tag）`);
    await page.screenshot({ path: 'test-results/dept-shift-column.png', fullPage: false });
    shiftChecked = true;
    break;
  }
  if (!shiftChecked) console.log('SKIP 未找到「启用班次且已声明班次」的客户，跳过班次列校验');

  // 反向：无班次声明的客户不应出现「班次」列（列本身就不占位）
  let plainChecked = false;
  for (const c of allCustomers) {
    if (c.shiftEnabled) continue;
    const depts = (await call(`/partner/customerDept/list?customerId=${c.id}&hideParent=true`)).data || [];
    if (depts.some((d) => d.shiftCodes && String(d.shiftCodes).trim())) continue;
    const heads = await deptPageHeads(c.id);
    assert.ok(!heads.includes('班次'), '无班次声明的客户不应出现「班次」列');
    console.log(`PASS 无班次客户列表不含「班次」列（${c.alias || c.name}）`);
    plainChecked = true;
    break;
  }
  if (!plainChecked) console.log('SKIP 未找到「无班次声明」的客户，跳过反向校验');
} finally {
  // 兜底还原
  if (pick && originalIds) {
    try {
      const page2 = await browser.newPage();
      await page2.goto(base + '/login', { waitUntil: 'networkidle' });
      await page2.fill('input[placeholder*="账号"]', user);
      await page2.fill('input[placeholder*="密码"]', pwd);
      await page2.click('.login-button, .el-button--primary');
      await page2.waitForURL((u) => !String(u).includes('/login'));
      const tk = (await page2.context().cookies(base)).find((c) => c.name === 'Admin-Token').value;
      await page2.request.fetch(api + '/partner/customerDept/sort', {
        method: 'PUT',
        headers: { Authorization: 'Bearer ' + tk, 'Content-Type': 'application/json' },
        data: { customerId: pick.customerId, ids: originalIds },
      });
    } catch (e) { /* 忽略 */ }
  }
  await browser.close();
}

if (problems.length) {
  console.error('\n❌ 验证未通过:');
  problems.forEach((p) => console.error(' -', p));
  process.exit(1);
}
console.log('\n✅ 配送点排序 + 矩阵空列折叠 验证通过');
