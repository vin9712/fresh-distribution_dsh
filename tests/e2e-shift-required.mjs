// 验证：启用班次的客户「不选班次不能下单，提示请选择班次」，且必填星号与送货单位一致；
// 未启用班次的客户不受影响（字段不渲染、保存不被班次规则拦）。只读，不保存订单。
import assert from 'node:assert/strict';
import { chromium } from 'playwright-core';
import { mkdirSync } from 'node:fs';
const base = process.env.WEB_BASE || 'http://localhost:1025';
mkdirSync('test-results/shift-order', { recursive: true });
const browser = await chromium.launch({ channel: 'chrome', headless: true });
try {
  const page = await browser.newPage({ viewport: { width: 1600, height: 1000 } });
  await page.goto(base + '/login', { waitUntil: 'networkidle' });
  await page.fill('input[placeholder*="账号"]', process.env.ERP_USER);
  await page.fill('input[placeholder*="密码"]', process.env.ERP_PASSWORD);
  await page.click('.login-button, .el-button--primary');
  await page.waitForURL(u => !String(u).includes('/login'));
  const menus = page.locator('.el-cascader-menu');

  /** 打开下单页 → 设远期日期 → 选客户/配送点 */
  async function openAndPick(customer, dept) {
    await page.goto(base + '/order/sale-detail/index/', { waitUntil: 'networkidle' });
    await page.waitForTimeout(2500);
    await page.locator('.el-form .el-date-editor input').first().fill('2026-12-31');
    await page.keyboard.press('Enter');
    await page.waitForTimeout(600);
    await page.locator('.dept-cascader-line .el-cascader').first().click();
    await page.waitForTimeout(500);
    await menus.nth(0).getByText(customer, { exact: true }).first().hover();
    await page.waitForTimeout(800);
    await menus.nth(1).getByText(dept, { exact: true }).first().click();
    await page.waitForTimeout(1600);
  }
  const formErrors = () => page.locator('.el-form-item__error').allInnerTexts();

  // 1) 大长江 + 华铃：不选班次 → 保存被拦，提示「请选择班次」
  await openAndPick('大长江', '华铃');
  const shiftItem = page.locator('.el-form-item', { hasText: '班次' }).first();
  // Element Plus 把必填标记放在 .el-form-item.is-required 上（与送货单位同一机制）
  assert.ok((await shiftItem.evaluate(el => el.className)).includes('is-required'), '班次应带必填星号（与送货单位一致）');
  const deptRequired = await page.locator('.el-form-item', { hasText: '送货单位' }).first().evaluate(el => el.className);
  assert.ok(deptRequired.includes('is-required'), '送货单位本身就是必填（对照）');
  await page.locator('button', { hasText: '保存' }).first().click();
  await page.waitForTimeout(1800);
  const errs = await formErrors();
  assert.ok(errs.includes('请选择班次'), `应提示「请选择班次」，实际 ${JSON.stringify(errs)}`);
  console.log('PASS 大长江 + 华铃：班次必填（星号）且未选时保存被拦并提示「请选择班次」');
  await page.screenshot({ path: 'test-results/shift-order/shift-required.png', fullPage: false });

  // 2) 选了班次后该错误消失（其余错误不影响判断）
  const select = shiftItem.locator('.el-select').first();
  await select.click();
  await page.waitForTimeout(500);
  await page.locator('.el-select-dropdown:visible .el-select-dropdown__item', { hasText: '夜班' }).first().click();
  await page.waitForTimeout(1500);
  const errs2 = await formErrors();
  assert.ok(!errs2.includes('请选择班次'), `选了班次后不应再有班次错误，实际 ${JSON.stringify(errs2)}`);
  console.log('PASS 选了班次后「请选择班次」错误消失');

  // 3) 未启用班次的客户（金满楼）：无班次字段，保存不因班次被拦
  await openAndPick('金满楼', '中厨');
  assert.equal(await page.locator('.el-form-item', { hasText: '班次' }).count(), 0, '未启用班次的客户不应有班次字段');
  await page.locator('button', { hasText: '保存' }).first().click();
  await page.waitForTimeout(1800);
  const errs3 = await formErrors();
  assert.ok(!errs3.includes('请选择班次'), `未启用班次的客户不应出现班次错误，实际 ${JSON.stringify(errs3)}`);
  console.log('PASS 未启用班次的客户不受班次必填影响');
} finally { await browser.close(); }
