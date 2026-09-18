// 班次在「最近订单」的可见性验证：临时建一条草稿单（华铃·夜班）→ 查 API 与页面 → 删除并核对清理。
// 用完即删；删除失败会显式报错，不静默留下测试单。
import assert from 'node:assert/strict';
import { chromium } from 'playwright-core';
import { execFileSync } from 'node:child_process';
const base = process.env.WEB_BASE || 'http://localhost:1025';
const api = base + '/dev-api';
const sql = t => execFileSync('mysql', ['-h', process.env.MYSQL_HOST, '-u', process.env.MYSQL_USER, '--default-character-set=utf8mb4', '--batch', '--skip-column-names', process.env.MYSQL_DATABASE, '-e', t], { encoding: 'utf8' }).trim();
const today = new Date().toISOString().slice(0, 10);
let orderId = null;
const browser = await chromium.launch({ channel: 'chrome', headless: true });
try {
  const page = await browser.newPage({ viewport: { width: 1600, height: 1000 } });
  await page.goto(base + '/login', { waitUntil: 'networkidle' });
  await page.fill('input[placeholder*="账号"]', process.env.ERP_USER);
  await page.fill('input[placeholder*="密码"]', process.env.ERP_PASSWORD);
  await page.click('.login-button, .el-button--primary');
  await page.waitForURL(u => !String(u).includes('/login'));
  const token = (await page.context().cookies(base)).find(c => c.name === 'Admin-Token').value;
  const call = async (path, method = 'GET', data) => {
    const r = await page.request.fetch(api + path, {
      method,
      headers: { Authorization: 'Bearer ' + token, 'Content-Type': 'application/json' },
      ...(data === undefined ? {} : { data }),
    });
    const j = await r.json();
    assert.equal(j.code, 200, `${method} ${path}: ${j.msg}`);
    return j;
  };

  // 0) 未启用班次的客户（金满楼）：送货单位不带班次后缀（回归）
  const jm = (await call('/order/sale/recent/list?customerId=13&recentDays=30')).data;
  assert.ok(jm.length > 0, '金满楼应有历史草稿单可对照');
  for (const o of jm) assert.ok(!/-(白班|夜班)$/.test(o.deliveryName), `未启用班次的客户不应带班次后缀：${o.deliveryName}`);
  console.log(`PASS 未启用班次的客户送货单位不带班次后缀（${jm.length} 单对照）`);

  // 1) 临时建单：大长江 / 华铃（DAY,NIGHT）/ 夜班
  // RuoYi AjaxResult 对 String 结果放在 msg 字段
  const code = (await call('/order/sale/code')).msg;
  assert.ok(typeof code === 'string' && code.length > 0, `取号应返回单号字符串，实际 ${JSON.stringify(code)}`);
  const created = (await call('/order/sale/create', 'POST', {
    customerId: 11,
    customerDeptId: 7,
    shiftCode: 'NIGHT',
    orderCode: code,
    deliveryDate: today,
    remark: '班次展示验证（自动删除）',
    orderDetails: [{ skuId: 3, productName: '大白菜', productUnit: '斤', productPrice: 6.0, num: 1 }],
  })).data;
  orderId = created.id;
  assert.equal(created.shiftCode, 'NIGHT', '订单应落班次 NIGHT');
  console.log(`PASS 建单落班次：${created.code} shift=${created.shiftCode}`);

  // 2) 最近订单接口：送货单位带班次
  const recent = (await call('/order/sale/recent/list?customerId=11&recentDays=3')).data;
  const mine = recent.find(o => o.id === orderId);
  assert.ok(mine, '新建草稿单应出现在最近订单');
  assert.ok(mine.deliveryName.endsWith('-夜班'), `送货单位应带班次后缀，实际「${mine.deliveryName}」`);
  assert.equal(mine.deliveryName, '大长江-华铃-夜班');
  console.log(`PASS 最近订单送货单位：${mine.deliveryName}`);

  // 3) 页面：最近订单表格可见班次
  await page.goto(base + '/order/sale-detail/index/', { waitUntil: 'networkidle' });
  await page.waitForTimeout(4000);
  const body = await page.locator('body').innerText();
  assert.ok(body.includes('大长江-华铃-夜班'), '页面最近订单应显示带班次的送货单位');
  console.log('PASS 页面最近订单显示「大长江-华铃-夜班」');
  await page.screenshot({ path: 'test-results/shift-order/recent-order-shift.png', fullPage: false });
} finally {
  if (orderId != null) {
    const left = sql(`SELECT COUNT(*) FROM t_sale_order WHERE id=${orderId} AND is_deleted=0`);
    if (left !== '0') {
      // 走业务删除（逻辑删除 + 明细级联），不裸删
      const page2 = await browser.newPage();
      await page2.goto(base + '/login', { waitUntil: 'networkidle' });
      await page2.fill('input[placeholder*="账号"]', process.env.ERP_USER);
      await page2.fill('input[placeholder*="密码"]', process.env.ERP_PASSWORD);
      await page2.click('.login-button, .el-button--primary');
      await page2.waitForURL(u => !String(u).includes('/login'));
      const token = (await page2.context().cookies(base)).find(c => c.name === 'Admin-Token').value;
      await page2.request.fetch(api + '/order/sale/' + orderId, { method: 'DELETE', headers: { Authorization: 'Bearer ' + token } });
      await page2.close();
    }
    const after = sql(`SELECT COUNT(*) FROM t_sale_order WHERE id=${orderId} AND is_deleted=0`);
    assert.equal(after, '0', `临时验证单未清理干净（id=${orderId}）`);
    console.log(`PASS 临时验证单已删除（id=${orderId}，库内已无有效行）`);
  }
  await browser.close();
}
