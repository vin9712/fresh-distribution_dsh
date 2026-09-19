// 销售订单草稿门禁 + 草稿箱陈旧清理 回归（2026-09-19）
// 前置：本地环境已启动（./dev.sh start，前端 1025 → 后端 8090）
// 覆盖：
//   1. 后端 /order/sale/by-codes 按编号批量返回 id/status/updateTime
//   2. 草稿门禁：未选送货单位、或启用班次的客户未选班次 → 不生成草稿；选了班次才落草稿且带 shiftCode
//   3. 草稿箱陈旧清理：同编号订单已保存/已推进（编辑草稿）、编号已落库（新单草稿）→ 打开草稿箱即清除
// 写库说明：仅第 2 步正向用例会双写一条草稿（本地+后端），用例结束即清理；其余均只读/本地注入。
// 运行：ERP_USER=admin ERP_PASSWORD=admin123 node tests/e2e-sale-draft-gate.mjs
import assert from 'node:assert/strict';
import { chromium } from 'playwright-core';

const base = process.env.WEB_BASE || 'http://localhost:1025';
const api = base + '/dev-api';
const user = process.env.ERP_USER || 'admin';
const pwd = process.env.ERP_PASSWORD || 'admin123';

const browser = await chromium.launch({ channel: 'chrome', headless: true });
try {
  const page = await browser.newPage({ viewport: { width: 1680, height: 900 } });
  await page.goto(base + '/login', { waitUntil: 'networkidle' });
  await page.fill('input[placeholder*="账号"]', user);
  await page.fill('input[placeholder*="密码"]', pwd);
  await page.click('.login-button, .el-button--primary');
  await page.waitForURL((u) => !String(u).includes('/login'), { timeout: 20000 });
  const token = (await page.context().cookies(base)).find((c) => c.name === 'Admin-Token').value;
  const call = async (p, method = 'GET') => {
    const r = await page.request.fetch(api + p, {
      method,
      headers: { Authorization: 'Bearer ' + token, 'Content-Type': 'application/json' },
    });
    const j = await r.json();
    assert.equal(j.code, 200, `${method} ${p}: ${j.msg}`);
    return j.data;
  };
  const info = await page.request.fetch(api + '/getInfo', { headers: { Authorization: 'Bearer ' + token } });
  const uid = (await info.json()).user.userId;

  // ---- 1. by-codes 接口 ----
  const conf = await call('/order/sale/recent/list?recentDays=30&statuses=1');
  assert.ok(conf.length > 0, '需要已确认单用于陈旧判定');
  const sample = conf[0];
  const briefs = await call('/order/sale/by-codes?codes=' + encodeURIComponent(sample.code));
  assert.equal(briefs.length, 1, 'by-codes 应返回该编号订单');
  assert.equal(briefs[0].code, sample.code);
  assert.ok(briefs[0].updateTime === null || typeof briefs[0].updateTime === 'string');
  assert.deepEqual(await call('/order/sale/by-codes?codes='), [], '空 codes 应返回空数组');
  console.log('PASS by-codes 接口:', JSON.stringify(briefs));

  // 找启用班次的客户 + 配送点
  const customers = await call('/partner/customer/list');
  const depts = await call('/partner/customerDept/list');
  let pick = null;
  for (const c of customers) {
    if (!c.shiftEnabled) continue;
    const d = depts.find((x) => String(x.customerId) === String(c.id) && (x.shiftCodes || '').trim());
    if (d) { pick = { customer: c, dept: d }; break; }
  }

  const draftKeys = async () => page.evaluate((u) =>
    Object.keys(localStorage).filter((k) => k.startsWith(`saleDraft:u${u}:`)), uid);

  await page.goto(base + '/order/sale-detail/index/', { waitUntil: 'domcontentloaded' });
  await page.waitForSelector('.order-card .el-cascader', { timeout: 15000 });
  await page.waitForTimeout(1500);
  const before = await draftKeys();
  const gateKey = pick ? `saleDraft:u${uid}:new:${pick.dept.id}` : null;

  // ---- 2. 草稿门禁（组件级，避免被录入门禁拦住无法构造状态） ----
  if (pick) {
    const gate = await page.evaluate(({ custId, deptId }) => {
      const el = document.querySelector('.app-container');
      const vm = el.__vueParentComponent.proxy;
      const mk = (dept, shift) => {
        vm.orderForm.customerId = custId;
        vm.orderForm.customerDeptId = dept;
        vm.orderForm.orderId = null;
        vm.orderForm.shiftCode = shift;
        vm.orderForm.remark = 'gate-test';
        vm.orderDetailList = [{ productName: '测试菜', productUnit: '斤', num: '2.00', productPrice: '1.00', skuId: 1 }];
        vm.originalOrderDetailList = [];
        vm.originalOrderForm = { orderId: null, orderCode: 'X', customerId: custId, customerDeptId: dept, shiftCode: shift, deliveryDate: '2026-12-31', remark: null };
        vm.saveDraftIfMeaningful();
        // 找到本次写入的 key（new:<dept>）
        const k = Object.keys(localStorage).find((x) => x.startsWith('saleDraft:') && x.endsWith(`:new:${dept}`));
        const v = k ? localStorage.getItem(k) : null;
        if (k) localStorage.removeItem(k);
        return v;
      };
      vm.orderForm.customerId = custId;
      const shiftEnabled = vm.shiftEnabledForOrder;
      const noDept = mk(null, null);
      const noShift = mk(deptId, null);
      const withShift = mk(deptId, 'DAY');
      if (vm._draftTimer) { clearTimeout(vm._draftTimer); vm._draftTimer = null; }
      return { shiftEnabled, noDept, noShift, withShift };
    }, { custId: pick.customer.id, deptId: pick.dept.id });

    assert.equal(gate.shiftEnabled, true, '所选客户应启用班次');
    assert.equal(gate.noDept, null, '未选送货单位不应生成草稿');
    assert.equal(gate.noShift, null, '启用班次但未选班次不应生成草稿');
    assert.ok(gate.withShift, '选了班次应生成草稿');
    const payload = JSON.parse(gate.withShift);
    assert.equal(payload.shiftCode, 'DAY', '草稿应持久化 shiftCode');
    console.log('PASS 草稿门禁：无送货单位/缺班次不落草稿，含班次才落（带 shiftCode）');

    // 清理正向用例写入的后端草稿
    await call('/order/saleDraft/' + encodeURIComponent(gateKey), 'DELETE').catch(() => {});
    // 重载页面，清掉组件级注入的状态
    await page.goto(base + '/order/sale-detail/index/', { waitUntil: 'domcontentloaded' });
    await page.waitForSelector('.recent-order-card', { timeout: 15000 });
    await page.waitForTimeout(1200);
  } else {
    console.log('SKIP 无启用班次的客户，跳过草稿门禁校验');
  }

  // ---- 3. 草稿箱陈旧清理 ----
  const staleA = `saleDraft:u${uid}:order:${sample.id}`;
  const staleB = `saleDraft:u${uid}:new:${pick ? pick.dept.id : 'nodept'}`;
  await page.evaluate(({ staleA, staleB, sample, deptId }) => {
    const now = new Date().toISOString();
    const b = { ver: 2, rev: 1, savedAt: now, details: [{ productName: 'x', num: '1.00', productUnit: '斤' }] };
    localStorage.setItem(staleA, JSON.stringify({ ...b, key: staleA, orderId: sample.id, orderCode: sample.code, customerId: sample.customerId, customerDeptId: sample.customerDeptId, shiftCode: 'DAY', baseUpdateTime: '2000-01-01 00:00:00' }));
    localStorage.setItem(staleB, JSON.stringify({ ...b, key: staleB, orderId: null, orderCode: sample.code, customerId: sample.customerId, customerDeptId: deptId, shiftCode: 'DAY', baseUpdateTime: null }));
  }, { staleA, staleB, sample, deptId: pick ? pick.dept.id : null });

  await page.click('.draft-box-btn');
  await page.waitForTimeout(1800);
  const remaining = await page.evaluate(({ staleA, staleB }) => ({
    a: localStorage.getItem(staleA), b: localStorage.getItem(staleB),
  }), { staleA, staleB });
  assert.equal(remaining.a, null, '同编号已保存/已推进的编辑草稿应被清理');
  assert.equal(remaining.b, null, '同编号已落库的新单草稿应被清理');
  console.log('PASS 草稿箱陈旧清理（编辑草稿 + 新单草稿）');
  await page.keyboard.press('Escape');
  await page.waitForTimeout(300);

  // ---- 清理本次用例产生的本地草稿 ----
  const finalKeys = await draftKeys();
  const toClean = finalKeys.filter((k) => !before.includes(k));
  for (const k of toClean) await page.evaluate((key) => localStorage.removeItem(key), k);
  if (toClean.length) console.log('已清理本地草稿:', toClean.length);

  console.log('\n✅ 草稿门禁 + 草稿箱陈旧清理 验证通过');
} finally {
  await browser.close();
}
