// 送货单据页「当日全部客户总览（卡片视角）」回归（2026-09-19，D-071）
// 前置：本地环境已启动（./dev.sh start，前端 1025 → 后端 8090）
// 覆盖：
//   1. 后端 /order/delivery/batch/order-overview?deliveryDate= 按**已确认订单**（status>=1）聚合客户卡片
//      （D-055 送货单视图化后无物理送货单，不能再走 t_delivery_order）
//   2. 进入送货单据页（未选客户）→ 按默认/指定配送日期自动展示客户卡片总览
//   3. 点卡片 → 进入该客户矩阵/配货/点单视图；「返回全部客户」→ 回到卡片总览
// 纯只读，不写任何数据。
// 运行：ERP_USER=admin ERP_PASSWORD=admin123 node tests/e2e-delivery-overview.mjs
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
    return j.data;
  };

  // 找一个「已确认订单」有数据的配送日期（从今天往前最多找 20 天）
  let date = null;
  let overview = [];
  for (let i = 0; i < 20; i++) {
    const d = new Date();
    d.setDate(d.getDate() - i);
    const ds = d.toISOString().slice(0, 10);
    const rows = await call('/order/delivery/batch/order-overview?deliveryDate=' + ds);
    if (rows && rows.length) {
      date = ds;
      overview = rows;
      break;
    }
  }
  assert.ok(date, '近 20 天内应有已确认订单用于校验');
  console.log(`日期 ${date}：overview ${overview.length} 个客户 ->`, overview.map((r) => `${r.customerName}:${r.orderCount}单`).join(' | '));

  // 1. 接口字段与口径
  overview.forEach((r) => {
    assert.ok(r.customerId != null && r.customerName, '卡片应有客户标识');
    assert.ok(Number(r.orderCount) >= 1, '订单数应 >= 1');
    const statusSum =
      Number(r.confirmedCount || 0) +
      Number(r.deliveredCount || 0) +
      Number(r.acceptedCount || 0) +
      Number(r.settledCount || 0);
    assert.equal(statusSum, Number(r.orderCount), `客户 ${r.customerName} 各状态数之和应等于订单数`);
  });
  console.log('PASS order-overview 接口（按已确认订单聚合，状态数自洽）');

  // 2. 页面：未选客户 → 自动展示卡片总览
  await page.goto(base + '/order/batch?deliveryDate=' + date, { waitUntil: 'domcontentloaded' });
  await page.waitForSelector('.customer-overview', { timeout: 15000 });
  await page.waitForTimeout(1200);
  const cards = await page.locator('.customer-card').count();
  console.log('卡片数:', cards, '期望:', overview.length);
  assert.equal(cards, overview.length, '卡片数应等于该日已确认订单的客户数');
  const title = await page.locator('.overview-title').innerText();
  assert.ok(title.includes(date), `总览标题应含日期 ${date}，实际 ${title}`);
  assert.equal(
    await page.locator('.batch-print-area').isVisible().catch(() => false),
    false,
    '未选客户不应显示单客户总表'
  );
  console.log('PASS 进入即展示当日客户卡片总览');

  // 3. 点卡片 → 客户视图；返回 → 卡片总览
  await page.locator('.customer-card').first().click();
  await page.waitForTimeout(2200);
  assert.ok(await page.locator('.el-radio-button:has-text("矩阵总表")').isVisible(), '点卡片应进入客户视图（口径切换可见）');
  const batchTitle = await page.locator('.batch-title').innerText();
  assert.ok(batchTitle.includes(date), '客户总表标题应含日期');
  assert.ok(await page.locator('button:has-text("返回全部客户")').isVisible(), '客户视图应提供返回全部客户');
  console.log('PASS 点卡片进入客户总表:', batchTitle.split('\n')[0]);

  await page.locator('button:has-text("返回全部客户")').click();
  await page.waitForTimeout(1500);
  assert.equal(await page.locator('.customer-card').count(), overview.length, '返回后应恢复卡片总览');
  console.log('PASS 返回全部客户总览');

  // 4. 默认配送日期（无参）也应有总览（当天可能为空，只校验结构）
  await page.goto(base + '/order/batch', { waitUntil: 'domcontentloaded' });
  await page.waitForSelector('.customer-overview', { timeout: 15000 });
  await page.waitForTimeout(1000);
  const defTitle = await page.locator('.overview-title').innerText();
  assert.ok(/当日送货总览/.test(defTitle), '默认进入应展示当日总览标题');
  console.log('PASS 默认配送日期进入即展示总览:', defTitle);

  await page.screenshot({ path: 'test-results/delivery-overview.png', fullPage: false });
} finally {
  await browser.close();
}

if (problems.length) {
  console.error('\n❌ 验证未通过:');
  problems.forEach((p) => console.error(' -', p));
  process.exit(1);
}
console.log('\n✅ 送货单据当日全部客户总览（卡片视角）验证通过');
